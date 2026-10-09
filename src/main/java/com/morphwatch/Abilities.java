package com.morphwatch;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Each form has four powers. Regular: G (power 1, hold to charge) and H (power 2).
 * Super: B and N, which live in {@link SuperPowers}.
 * "s" is the strength: 1.0 normal, x2 when charged, more with a better watch or a golden form.
 */
public final class Abilities {
    /** Hold G this many ticks (1.5 seconds) for a charged power. */
    public static final int CHARGE_TICKS = 30;

    private Abilities() {}

    public static void use(ServerPlayer player, int slot, int chargeTicks, int targetId) {
        if (!MorphData.isWearing(player)) {
            WatchActions.tell(player, "Wear the Morph Watch first (right-click it)", ChatFormatting.RED);
            return;
        }
        MorphForm form = MorphData.getForm(player);
        if (form == MorphForm.NONE) {
            WatchActions.tell(player, "Transform first: scan a mob with V, then press X and left-click", ChatFormatting.RED);
            return;
        }

        CompoundTag data = MorphData.root(player);
        String cdKey = MorphData.cdKey(slot);
        boolean isSuper = slot >= 3;
        long now = player.level().getGameTime();
        long readyAt = data.getLong(cdKey);
        if (now < readyAt) {
            long secs = Math.max(1, (readyAt - now + 19) / 20);
            WatchActions.tell(player, (isSuper ? "Super power recharging... " : "Power recharging... ") + secs + "s",
                    ChatFormatting.YELLOW);
            return;
        }

        boolean charged = slot == 1 && chargeTicks >= CHARGE_TICKS;
        float s = MorphData.powerMultiplier(player, form) * (charged ? 2.0F : 1.0F);
        boolean used = MobPowers.has(form) ? MobPowers.use(player, form, slot, s, targetId) : switch (slot) {
            case 1 -> power1(player, form, s);
            case 2 -> power2(player, form, s, targetId);
            case 3 -> SuperPowers.superB(player, form, s, targetId);
            default -> SuperPowers.superN(player, form, s, targetId);
        };
        if (!used) return;

        int base = switch (slot) {
            case 1 -> form.cooldown1();
            case 2 -> form.cooldown2();
            case 3 -> SuperPowers.B_COOLDOWN;
            default -> SuperPowers.N_COOLDOWN;
        };
        long cooldown = Math.max(5, Math.round(base * MorphData.cooldownMultiplier(player) * (charged ? 1.5 : 1.0)));
        data.putLong(cdKey, now + cooldown);
        data.putLong(MorphData.cdLenKey(slot), cooldown);
        if (isSuper) {
            WatchActions.tell(player, "SUPER POWER: " + SuperPowers.name(form, slot) + "!", ChatFormatting.GOLD);
            player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
                    30, 0.5, 0.8, 0.5, 0.3);
        }

        // The mob's own noise with every power
        if (form.sound() != null) sound(player, form.sound(), 1.0F);
        if (charged) {
            player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
                    25, 0.4, 0.6, 0.4, 0.3);
            MorphAdvancements.award(player, MorphAdvancements.FULLY_CHARGED);
        }
        if (slot == 1) {
            MorphAdvancements.award(player, MorphAdvancements.POWER_UP);
            MorphData.markPowerUsed(player, form);
            if (MorphData.powersUsedCount(player) >= MorphForm.mobs().size()) {
                MorphAdvancements.award(player, MorphAdvancements.ALL_POWERS);
            }
        }
        MorphData.sync(player);
    }

    // ================================================================ G powers

    private static boolean power1(ServerPlayer p, MorphForm form, float s) {
        return switch (form) {
            case CHICKEN -> {
                int eggs = s >= 2 ? 3 : 1;
                for (int i = 0; i < eggs; i++) p.spawnAtLocation(Items.EGG);
                sound(p, SoundEvents.CHICKEN_EGG, 1.0F);
                yield true;
            }
            case CAT -> {
                p.heal(4.0F * s);
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) (100 * s), 0));
                sound(p, SoundEvents.CAT_PURR, 1.0F);
                particles(p, ParticleTypes.HEART, 10);
                yield true;
            }
            case CREEPER -> {
                // The player is the source, so the blast doesn't hurt them.
                p.level().explode(p, p.getX(), p.getY(), p.getZ(), Math.min(6.0F, 3.0F * s), Level.ExplosionInteraction.MOB);
                yield true;
            }
            case SPIDER -> shootWeb(p, s);
            case ZOMBIE -> {
                for (LivingEntity e : nearby(p, 6.0 * s, form)) {
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
                }
                particles(p, ParticleTypes.SMOKE, 30);
                yield true;
            }
            case SKELETON -> {
                int arrows = s >= 2 ? 5 : 1;
                for (int i = 0; i < arrows; i++) {
                    Arrow arrow = new Arrow(p.level(), p);
                    arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                    arrow.setBaseDamage(arrow.getBaseDamage() * Math.max(1.0F, s * 0.75F));
                    float spread = arrows == 1 ? 0 : (i - arrows / 2) * 6.0F;
                    arrow.shootFromRotation(p, p.getXRot(), p.getYRot() + spread, 0.0F, 3.0F, 1.0F);
                    p.level().addFreshEntity(arrow);
                }
                sound(p, SoundEvents.SKELETON_SHOOT, 1.0F);
                yield true;
            }
            case BLAZE -> {
                int balls = s >= 2 ? 3 : 1;
                for (int i = 0; i < balls; i++) {
                    float spread = balls == 1 ? 0 : (i - 1) * 10.0F;
                    Vec3 dir = Vec3.directionFromRotation(p.getXRot(), p.getYRot() + spread);
                    SmallFireball ball = new SmallFireball(p.level(), p, dir.x, dir.y, dir.z);
                    ball.setPos(p.getX() + dir.x, p.getEyeY() - 0.1 + dir.y, p.getZ() + dir.z);
                    p.level().addFreshEntity(ball);
                }
                sound(p, SoundEvents.BLAZE_SHOOT, 1.0F);
                yield true;
            }
            case ENDERMAN -> teleportToLook(p, Math.min(96.0, 48.0 * s));
            case WITHER_SKELETON -> {
                for (LivingEntity e : nearby(p, 6.0 * s, form)) {
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, (int) (100 * s), 1));
                }
                particles(p, ParticleTypes.SQUID_INK, 30);
                sound(p, SoundEvents.WITHER_SHOOT, 1.4F);
                yield true;
            }
            case IRON_GOLEM -> {
                for (LivingEntity e : nearby(p, 5.0 * s, form)) {
                    e.hurt(p.damageSources().playerAttack(p), 8.0F * s);
                    Vec3 away = e.position().subtract(p.position());
                    Vec3 push = new Vec3(away.x, 0, away.z).normalize().scale(1.2).add(0, 0.6, 0);
                    e.setDeltaMovement(e.getDeltaMovement().add(push));
                    e.hurtMarked = true;
                }
                sound(p, SoundEvents.IRON_GOLEM_ATTACK, 1.0F);
                p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 6, 2.0, 0.1, 2.0, 0.0);
                yield true;
            }
            case BAT -> {
                List<LivingEntity> found = nearby(p, 32.0 * s, null);
                for (LivingEntity e : found) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
                WatchActions.tell(p, "Echolocation: " + found.size() + " creatures spotted", ChatFormatting.DARK_PURPLE);
                sound(p, SoundEvents.BAT_TAKEOFF, 0.6F);
                yield true;
            }
            case SNOW_GOLEM -> {
                int balls = s >= 2 ? 9 : 3;
                for (int i = 0; i < balls; i++) {
                    Snowball ball = new Snowball(p.level(), p);
                    ball.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, 1.6F, 6.0F);
                    p.level().addFreshEntity(ball);
                }
                sound(p, SoundEvents.SNOW_GOLEM_SHOOT, 1.0F);
                yield true;
            }
            default -> false;
        };
    }

    // ================================================================ H powers

    private static boolean power2(ServerPlayer p, MorphForm form, float s, int targetId) {
        Vec3 look = p.getLookAngle();
        return switch (form) {
            case CHICKEN -> {   // Flap: big flutter upward
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x, 0.8 + 0.2 * s, v.z);
                p.hurtMarked = true;
                particles(p, ParticleTypes.CLOUD, 12);
                sound(p, SoundEvents.CHICKEN_HURT, 1.4F);
                yield true;
            }
            case CAT -> {       // Pounce
                p.setDeltaMovement(look.x * 1.2 * s, 0.5, look.z * 1.2 * s);
                p.hurtMarked = true;
                sound(p, SoundEvents.CAT_HISS, 1.0F);
                yield true;
            }
            case CREEPER -> {   // Hiss: scare mobs away
                for (LivingEntity e : nearby(p, 8.0 * s, form)) {
                    Vec3 away = e.position().subtract(p.position()).normalize().scale(1.5).add(0, 0.3, 0);
                    e.setDeltaMovement(away);
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                }
                sound(p, SoundEvents.CREEPER_HURT, 0.6F);
                yield true;
            }
            case SPIDER -> {    // Spider sense
                for (LivingEntity e : nearby(p, 24.0 * s, null)) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
                sound(p, SoundEvents.SPIDER_STEP, 0.6F);
                yield true;
            }
            case ZOMBIE -> {    // Undead toughness
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, (int) (200 * s), 1));
                particles(p, ParticleTypes.ENCHANTED_HIT, 15);
                yield true;
            }
            case SKELETON -> {  // Bone shield
                p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, s >= 1.5F ? 2 : 1));
                particles(p, ParticleTypes.CRIT, 20);
                sound(p, SoundEvents.SKELETON_HURT, 0.8F);
                yield true;
            }
            case BLAZE -> {     // Flame burst
                for (LivingEntity e : nearby(p, 5.0 * s, form)) e.setSecondsOnFire(5);
                particles(p, ParticleTypes.FLAME, 60);
                sound(p, SoundEvents.FIRECHARGE_USE, 1.0F);
                yield true;
            }
            case ENDERMAN -> {  // Escape: random teleport, like a chorus fruit
                boolean done = false;
                double range = 16.0 * s;
                for (int i = 0; i < 16 && !done; i++) {
                    double x = p.getX() + (p.getRandom().nextDouble() - 0.5) * 2 * range;
                    double y = p.getY() + (p.getRandom().nextInt(16) - 8);
                    double z = p.getZ() + (p.getRandom().nextDouble() - 0.5) * 2 * range;
                    double oldX = p.getX(), oldY = p.getY(), oldZ = p.getZ();
                    done = p.randomTeleport(x, y, z, true);
                    if (done) {
                        p.serverLevel().sendParticles(ParticleTypes.PORTAL, oldX, oldY + 1, oldZ, 40, 0.3, 0.8, 0.3, 0.2);
                    }
                }
                if (done) sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0F);
                yield done;
            }
            case WITHER_SKELETON -> {   // Wither skull
                WitherSkull skull = new WitherSkull(p.level(), p, look.x, look.y, look.z);
                skull.setPos(p.getX() + look.x, p.getEyeY() - 0.1 + look.y, p.getZ() + look.z);
                skull.setDangerous(s >= 2);
                p.level().addFreshEntity(skull);
                sound(p, SoundEvents.WITHER_SHOOT, 1.0F);
                yield true;
            }
            case IRON_GOLEM -> {    // Toss the mob you're looking at
                Entity target = targetId >= 0 ? p.level().getEntity(targetId) : null;
                if (!(target instanceof LivingEntity living) || target.distanceTo(p) > 6.0F) {
                    WatchActions.tell(p, "Look at a mob up close to toss it", ChatFormatting.GRAY);
                    yield false;
                }
                living.hurt(p.damageSources().playerAttack(p), 4.0F * s);
                living.setDeltaMovement(living.getDeltaMovement().add(0, 1.0 * s, 0));
                living.hurtMarked = true;
                sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.8F);
                yield true;
            }
            case BAT -> {       // Screech: confuse nearby mobs
                for (LivingEntity e : nearby(p, 8.0 * s, form)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                }
                particles(p, ParticleTypes.NOTE, 15);
                sound(p, SoundEvents.BAT_HURT, 0.5F);
                yield true;
            }
            case SNOW_GOLEM -> {    // Freeze: chill mobs and lay snow
                for (LivingEntity e : nearby(p, 6.0 * s, form)) {
                    e.setTicksFrozen(Math.max(e.getTicksFrozen(), 300));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
                }
                if (p.mayBuild()) laySnow(p, 3);
                particles(p, ParticleTypes.SNOWFLAKE, 60);
                sound(p, SoundEvents.POWDER_SNOW_PLACE, 1.0F);
                yield true;
            }
            default -> false;
        };
    }

    // ================================================================ helpers

    static boolean shootWeb(ServerPlayer p, float s) {
        HitResult hit = p.pick(24.0D, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            WatchActions.tell(p, "Look at a block to web it", ChatFormatting.GRAY);
            return false;
        }
        if (!p.mayBuild()) return false;
        BlockPos center = blockHit.getBlockPos().relative(blockHit.getDirection());
        int r = s >= 2 ? 1 : 0;
        boolean placed = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                if (p.level().getBlockState(pos).isAir()) {
                    p.level().setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
                    placed = true;
                }
            }
        }
        if (placed) sound(p, SoundEvents.SPIDER_AMBIENT, 1.2F);
        return placed;
    }

    static boolean teleportToLook(ServerPlayer p, double range) {
        HitResult hit = p.pick(range, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            WatchActions.tell(p, "Look at a block to teleport there", ChatFormatting.GRAY);
            return false;
        }
        BlockPos base = blockHit.getBlockPos().relative(blockHit.getDirection());
        for (int up = 0; up <= 2; up++) {
            double x = base.getX() + 0.5, y = base.getY() + up, z = base.getZ() + 0.5;
            AABB box = p.getBoundingBox().move(x - p.getX(), y - p.getY(), z - p.getZ());
            if (p.level().noCollision(p, box)) {
                ServerLevel level = p.serverLevel();
                level.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.3, 0.8, 0.3, 0.2);
                p.connection.teleport(x, y, z, p.getYRot(), p.getXRot());
                p.fallDistance = 0.0F;
                level.sendParticles(ParticleTypes.PORTAL, x, y + 1, z, 40, 0.3, 0.8, 0.3, 0.2);
                sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0F);
                return true;
            }
        }
        WatchActions.tell(p, "No room to teleport there", ChatFormatting.GRAY);
        return false;
    }

    static void laySnow(ServerPlayer p, int radius) {
        Level level = p.level();
        BlockPos feet = p.blockPosition();
        BlockState snow = Blocks.SNOW.defaultBlockState();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = 1; dy >= -2; dy--) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir() && snow.canSurvive(level, pos)
                            && !level.getBlockState(pos.relative(Direction.DOWN)).isAir()) {
                        level.setBlockAndUpdate(pos, snow);
                        break;
                    }
                }
            }
        }
    }

    /** Living things near the player, leaving out the player and (optionally) mobs of your own kind. */
    static List<LivingEntity> nearby(ServerPlayer p, double radius, MorphForm excludeKinOf) {
        AABB area = p.getBoundingBox().inflate(radius);
        return p.level().getEntitiesOfClass(LivingEntity.class, area,
                t -> t != p && t.isAlive() && (excludeKinOf == null || !excludeKinOf.isKin(t.getType())));
    }

    static void sound(ServerPlayer p, SoundEvent sound, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, 1.0F, pitch);
    }

    static void particles(ServerPlayer p, ParticleOptions type, int count) {
        p.serverLevel().sendParticles(type, p.getX(), p.getY() + 0.8, p.getZ(), count, 0.6, 0.6, 0.6, 0.05);
    }
}

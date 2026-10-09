package com.morphwatch;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The two SUPER powers every mob has: B and N. They're big and flashy and take longer to
 * recharge than the regular G and H powers. "s" is the strength (better watch / golden form).
 */
public final class SuperPowers {
    /** Base recharge times (ticks) before the watch tier speeds them up. */
    public static final int B_COOLDOWN = 400;   // 20 seconds
    public static final int N_COOLDOWN = 300;   // 15 seconds

    public static final String HELPER_TAG = "morphwatch_helper";
    private static final String HELPER_UNTIL = "morphwatch_until";
    private static final String HELPER_OWNER = "morphwatch_owner";
    private static final int HELPER_TICKS = 600;    // zombie helpers stay 30 seconds

    private static final Set<Mob> HELPERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final List<ThrownTnt> TNT = new ArrayList<>();

    private record ThrownTnt(PrimedTnt tnt, UUID owner, float power) {}

    private SuperPowers() {}

    /** The name shown on screen when you use the power. */
    public static String name(MorphForm form, int slot) {
        boolean r = slot == 3;
        return switch (form) {
            case CHICKEN -> r ? "Egg Storm" : "Super Flutter";
            case CAT -> r ? "Claw Dash" : "Scare Creepers";
            case CREEPER -> r ? "TNT Throw" : "Camouflage";
            case SPIDER -> r ? "Web Net" : "Wall Leap";
            case ZOMBIE -> r ? "Zombie Helpers" : "Life Bite";
            case SKELETON -> r ? "Flaming Arrow Volley" : "Dodge Roll";
            case BLAZE -> r ? "Flamethrower" : "Rocket Boost";
            case ENDERMAN -> r ? "Block Throw" : "Blink Behind";
            case WITHER_SKELETON -> r ? "Spin Slash" : "Shadow Cloak";
            case IRON_GOLEM -> r ? "Mega Punch" : "Iron Wall";
            case BAT -> r ? "Sonic Wave" : "Night Dash";
            case SNOW_GOLEM -> r ? "Freeze Ray" : "Snow Slide";
            default -> MobPowers.name(form, slot);
        };
    }

    // ================================================================ B super powers

    public static boolean superB(ServerPlayer p, MorphForm form, float s, int targetId) {
        Vec3 look = p.getLookAngle();
        ServerLevel level = p.serverLevel();
        return switch (form) {
            case CHICKEN -> {   // Egg Storm: a fan of eggs, everything in front gets pelted
                for (int i = 0; i < 16; i++) {
                    ThrownEgg egg = new ThrownEgg(level, p);
                    float yaw = p.getYRot() + (i - 7.5F) * 4.0F;
                    float pitch = p.getXRot() - 5.0F + (p.getRandom().nextFloat() - 0.5F) * 14.0F;
                    egg.shootFromRotation(p, pitch, yaw, 0.0F, 1.6F, 1.0F);
                    level.addFreshEntity(egg);
                }
                for (LivingEntity e : cone(p, 12, 0.8, form)) {
                    e.hurt(p.damageSources().thrown(p, p), 3.0F * s);
                    e.knockback(0.6, p.getX() - e.getX(), p.getZ() - e.getZ());
                }
                Abilities.sound(p, SoundEvents.CHICKEN_EGG, 0.7F);
                Abilities.sound(p, SoundEvents.EGG_THROW, 0.8F);
                yield true;
            }
            case CAT -> {       // Claw Dash: zoom forward, slashing everything in the way
                Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                for (LivingEntity e : cone(p, 7, 0.6, form)) {
                    e.hurt(p.damageSources().playerAttack(p), 6.0F * s);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, 0, 0, 0, 0);
                }
                p.setDeltaMovement(flat.x * 2.2, 0.3, flat.z * 2.2);
                p.hurtMarked = true;
                p.fallDistance = 0;
                trail(p, ParticleTypes.CRIT, 7, 4);
                Abilities.sound(p, SoundEvents.CAT_HISS, 1.4F);
                Abilities.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F);
                yield true;
            }
            case CREEPER -> {   // TNT Throw: a lit TNT that blows up where it lands (you're safe)
                PrimedTnt tnt = new PrimedTnt(level, p.getX() + look.x, p.getEyeY() - 0.3 + look.y, p.getZ() + look.z, p);
                tnt.setFuse(40);
                tnt.setDeltaMovement(look.scale(1.3).add(0, 0.25, 0));
                level.addFreshEntity(tnt);
                TNT.add(new ThrownTnt(tnt, p.getUUID(), Math.min(6.0F, 3.5F * s)));
                Abilities.sound(p, SoundEvents.TNT_PRIMED, 1.0F);
                yield true;
            }
            case SPIDER -> {    // Web Net: every mob in front is webbed, slowed and poisoned
                List<LivingEntity> hit = cone(p, 14, 0.75, form);
                if (hit.isEmpty()) yield noTarget(p, "Look toward some mobs to throw a web net");
                ItemParticleOption web = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB));
                for (LivingEntity e : hit) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (120 * s), 4));
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, (int) (100 * s), 0));
                    if (p.mayBuild() && level.getBlockState(e.blockPosition()).isAir()) {
                        level.setBlockAndUpdate(e.blockPosition(), Blocks.COBWEB.defaultBlockState());
                    }
                    beam(level, p.getEyePosition(), e.position().add(0, e.getBbHeight() / 2, 0), web, 1);
                }
                Abilities.sound(p, SoundEvents.SPIDER_AMBIENT, 0.6F);
                yield true;
            }
            case ZOMBIE -> {    // Zombie Helpers: 2 zombies (3 when stronger) fight for you for 30 seconds
                spawnHelpers(p, EntityType.ZOMBIE, s >= 1.5F ? 3 : 2);
                Abilities.sound(p, SoundEvents.ZOMBIE_AMBIENT, 0.6F);
                yield true;
            }
            case SKELETON -> {  // Flaming Arrow Volley
                for (int i = 0; i < 9; i++) {
                    Arrow arrow = new Arrow(level, p);
                    arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                    arrow.setBaseDamage(arrow.getBaseDamage() * Math.max(1.0F, s * 0.75F));
                    arrow.setSecondsOnFire(100);
                    arrow.setCritArrow(true);
                    arrow.shootFromRotation(p, p.getXRot(), p.getYRot() + (i - 4) * 6.0F, 0.0F, 3.0F, 1.0F);
                    level.addFreshEntity(arrow);
                }
                Abilities.sound(p, SoundEvents.SKELETON_SHOOT, 0.8F);
                Abilities.sound(p, SoundEvents.FIRECHARGE_USE, 1.2F);
                yield true;
            }
            case BLAZE -> {     // Flamethrower
                Vec3 eye = p.getEyePosition();
                for (double d = 1.0; d <= 10.0; d += 0.6) {
                    Vec3 at = eye.add(look.scale(d));
                    double spread = d * 0.12;
                    level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 6, spread, spread, spread, 0.02);
                }
                for (LivingEntity e : cone(p, 10, 0.8, form)) {
                    e.setSecondsOnFire(8);
                    e.hurt(p.damageSources().playerAttack(p), 5.0F * s);
                }
                Abilities.sound(p, SoundEvents.BLAZE_SHOOT, 0.7F);
                Abilities.sound(p, SoundEvents.FIRECHARGE_USE, 0.6F);
                yield true;
            }
            case ENDERMAN -> {  // Block Throw: hurl a chunk of the ground at a mob
                LivingEntity target = lookTarget(p, 24, targetId);
                if (target == null) yield noTarget(p, "Look at a mob to throw a block at it");
                BlockState ground = level.getBlockState(p.blockPosition().below());
                if (ground.isAir()) ground = Blocks.DIRT.defaultBlockState();
                beam(level, p.getEyePosition(), target.position().add(0, target.getBbHeight() / 2, 0),
                        new BlockParticleOption(ParticleTypes.BLOCK, ground), 4);
                target.hurt(p.damageSources().playerAttack(p), 10.0F * s);
                target.knockback(1.5, p.getX() - target.getX(), p.getZ() - target.getZ());
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                        target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 40, 0.4, 0.4, 0.4, 0.1);
                Abilities.sound(p, SoundEvents.ENDERMAN_SCREAM, 1.0F);
                Abilities.sound(p, SoundEvents.STONE_BREAK, 0.8F);
                yield true;
            }
            case WITHER_SKELETON -> {   // Spin Slash: hits everything around you
                List<LivingEntity> hit = Abilities.nearby(p, 5.0 * Math.min(s, 1.6F), form);
                for (LivingEntity e : hit) {
                    e.hurt(p.damageSources().playerAttack(p), 8.0F * s);
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                    e.knockback(0.8, p.getX() - e.getX(), p.getZ() - e.getZ());
                }
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8;
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + Math.cos(a) * 2.5, p.getY() + 1,
                            p.getZ() + Math.sin(a) * 2.5, 1, 0, 0, 0, 0);
                }
                Abilities.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F);
                Abilities.sound(p, SoundEvents.WITHER_SKELETON_AMBIENT, 0.8F);
                yield true;
            }
            case IRON_GOLEM -> {    // Mega Punch: sends a mob flying
                LivingEntity target = lookTarget(p, 6, targetId);
                if (target == null) yield noTarget(p, "Get close and look at a mob to mega punch it");
                target.hurt(p.damageSources().playerAttack(p), 16.0F * s);
                Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                target.setDeltaMovement(flat.x * 3.0, 0.9, flat.z * 3.0);
                target.hurtMarked = true;
                level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1, target.getZ(), 3, 0.3, 0.3, 0.3, 0);
                Abilities.sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.6F);
                Abilities.sound(p, SoundEvents.GENERIC_EXPLODE, 1.6F);
                yield true;
            }
            case BAT -> {       // Sonic Wave
                Vec3 eye = p.getEyePosition();
                for (double d = 1.5; d <= 14; d += 2.0) {
                    Vec3 at = eye.add(look.scale(d));
                    level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
                for (LivingEntity e : cone(p, 14, 0.7, form)) {
                    e.hurt(p.damageSources().sonicBoom(p), 5.0F * s);
                    e.knockback(1.5, p.getX() - e.getX(), p.getZ() - e.getZ());
                    e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                }
                Abilities.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.6F);
                yield true;
            }
            case SNOW_GOLEM -> {    // Freeze Ray
                LivingEntity target = lookTarget(p, 24, targetId);
                if (target == null) yield noTarget(p, "Look at a mob to hit it with the freeze ray");
                beam(level, p.getEyePosition(), target.position().add(0, target.getBbHeight() / 2, 0), ParticleTypes.SNOWFLAKE, 3);
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), 400));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (120 * s), 5));
                target.hurt(p.damageSources().freeze(), 6.0F * s);
                level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1, target.getZ(), 50, 0.5, 0.8, 0.5, 0.05);
                Abilities.sound(p, SoundEvents.POWDER_SNOW_BREAK, 0.6F);
                Abilities.sound(p, SoundEvents.GLASS_BREAK, 1.4F);
                yield true;
            }
            default -> false;
        };
    }

    // ================================================================ N super powers

    public static boolean superN(ServerPlayer p, MorphForm form, float s, int targetId) {
        Vec3 look = p.getLookAngle();
        ServerLevel level = p.serverLevel();
        return switch (form) {
            case CHICKEN -> {   // Super Flutter: rocket up, float down, blow mobs away
                p.setDeltaMovement(p.getDeltaMovement().x, 1.4, p.getDeltaMovement().z);
                p.hurtMarked = true;
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, (int) (200 * s), 0));
                for (LivingEntity e : Abilities.nearby(p, 5, form)) e.knockback(1.5, p.getX() - e.getX(), p.getZ() - e.getZ());
                level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 40, 1.0, 0.2, 1.0, 0.1);
                Abilities.sound(p, SoundEvents.CHICKEN_HURT, 0.6F);
                Abilities.sound(p, SoundEvents.ENDER_DRAGON_FLAP, 1.6F);
                yield true;
            }
            case CAT -> {       // Scare Creepers: creepers run for it, other monsters are spooked
                for (LivingEntity e : Abilities.nearby(p, 24 * Math.min(s, 1.5F), form)) {
                    if (e instanceof Creeper c) {
                        c.knockback(2.5, p.getX() - c.getX(), p.getZ() - c.getZ());
                        c.setTarget(null);
                        c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2));
                        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, c.getX(), c.getY() + 2, c.getZ(), 3, 0.3, 0.2, 0.3, 0);
                    } else if (e instanceof Enemy && e.distanceTo(p) < 10) {
                        e.knockback(1.0, p.getX() - e.getX(), p.getZ() - e.getZ());
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                    }
                }
                Abilities.sound(p, SoundEvents.CAT_HISS, 0.6F);
                Abilities.sound(p, SoundEvents.CAT_AMBIENT, 0.8F);
                yield true;
            }
            case CREEPER -> cloak(p, (int) (200 * s), false, ParticleTypes.HAPPY_VILLAGER);   // Camouflage
            case SPIDER -> {    // Wall Leap: a huge jump wherever you look
                p.setDeltaMovement(look.x * 1.6, Math.max(0.6, look.y * 1.6 + 0.4), look.z * 1.6);
                p.hurtMarked = true;
                p.fallDistance = 0;
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0));
                trail(p, ParticleTypes.CRIT, 6, 3);
                Abilities.sound(p, SoundEvents.SPIDER_AMBIENT, 1.5F);
                yield true;
            }
            case ZOMBIE -> {    // Life Bite: bite a mob and heal what you take
                LivingEntity target = lookTarget(p, 5, targetId);
                if (target == null) yield noTarget(p, "Get close and look at a mob to bite it");
                float amount = 6.0F * s;
                target.hurt(p.damageSources().playerAttack(p), amount);
                p.heal(amount);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.4, 0.3, 0.1);
                level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.8, p.getZ(), 5, 0.4, 0.3, 0.4, 0);
                Abilities.sound(p, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1.4F);
                Abilities.sound(p, SoundEvents.GENERIC_EAT, 0.8F);
                yield true;
            }
            case SKELETON -> {  // Dodge Roll: jump back and nothing can hurt you for a moment
                Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                p.setDeltaMovement(-flat.x * 1.4, 0.35, -flat.z * 1.4);
                p.hurtMarked = true;
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 4));
                level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
                Abilities.sound(p, SoundEvents.SKELETON_STEP, 0.6F);
                Abilities.sound(p, SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0F);
                yield true;
            }
            case BLAZE -> {     // Rocket Boost
                p.setDeltaMovement(look.x * 2.0, look.y * 2.0 + 0.5, look.z * 2.0);
                p.hurtMarked = true;
                p.fallDistance = 0;
                level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 50, 0.3, 0.2, 0.3, 0.15);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY(), p.getZ(), 15, 0.3, 0.2, 0.3, 0.05);
                Abilities.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.7F);
                Abilities.sound(p, SoundEvents.BLAZE_SHOOT, 1.2F);
                yield true;
            }
            case ENDERMAN -> {  // Blink Behind: teleport right behind a mob
                LivingEntity target = lookTarget(p, 24, targetId);
                if (target == null) yield noTarget(p, "Look at a mob to blink behind it");
                Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
                Vec3 behind = target.position().subtract(facing.scale(target.getBbWidth() / 2 + 1.2));
                AABB box = p.getBoundingBox().move(behind.x - p.getX(), behind.y - p.getY(), behind.z - p.getZ());
                if (!level.noCollision(p, box)) {
                    behind = target.position().add(p.position().subtract(target.position()).normalize().scale(1.5));
                }
                float yaw = (float) (Mth.atan2(target.getZ() - behind.z, target.getX() - behind.x) * Mth.RAD_TO_DEG) - 90.0F;
                level.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.3, 0.8, 0.3, 0.2);
                p.connection.teleport(behind.x, behind.y, behind.z, yaw, 10.0F);
                p.fallDistance = 0;
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 1));
                level.sendParticles(ParticleTypes.PORTAL, behind.x, behind.y + 1, behind.z, 40, 0.3, 0.8, 0.3, 0.2);
                Abilities.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.7F);
                yield true;
            }
            case WITHER_SKELETON -> {   // Shadow Cloak
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, (int) (200 * s), 1));
                yield cloak(p, (int) (200 * s), true, ParticleTypes.LARGE_SMOKE);
            }
            case IRON_GOLEM -> {    // Iron Wall: almost nothing gets through
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, (int) (200 * s), 2));
                p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, (int) (200 * s), 3));
                for (LivingEntity e : Abilities.nearby(p, 4, form)) e.knockback(1.2, p.getX() - e.getX(), p.getZ() - e.getZ());
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()),
                        p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 0.8, 0.6, 0.1);
                Abilities.sound(p, SoundEvents.ANVIL_LAND, 0.6F);
                Abilities.sound(p, SoundEvents.IRON_GOLEM_REPAIR, 0.8F);
                yield true;
            }
            case BAT -> {       // Night Dash: shoot forward, invisible
                p.setDeltaMovement(look.scale(2.2));
                p.hurtMarked = true;
                p.fallDistance = 0;
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, (int) (100 * s), 0, false, false));
                level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 0.8, p.getZ(), 30, 0.4, 0.4, 0.4, 0.05);
                Abilities.sound(p, SoundEvents.BAT_TAKEOFF, 0.8F);
                yield true;
            }
            case SNOW_GOLEM -> {    // Snow Slide: zoom along, leaving snow behind
                Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                p.setDeltaMovement(flat.x * 1.4, 0.2, flat.z * 1.4);
                p.hurtMarked = true;
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, (int) (200 * s), 2));
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, (int) (200 * s), 1));
                level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 0.3, p.getZ(), 50, 0.6, 0.2, 0.6, 0.1);
                Abilities.sound(p, SoundEvents.SNOW_GOLEM_AMBIENT, 1.0F);
                Abilities.sound(p, SoundEvents.POWDER_SNOW_STEP, 0.8F);
                yield true;
            }
            default -> false;
        };
    }

    // ================================================================ cloaks, helpers, TNT

    /** Invisible, and mobs lose track of you until the cloak runs out (unless you hit them). */
    static boolean cloak(ServerPlayer p, int ticks, boolean speedy, ParticleOptions puff) {
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false));
        MorphData.root(p).putLong(MorphData.CLOAK, p.level().getGameTime() + ticks);
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) {
            m.setTarget(null);
        }
        p.serverLevel().sendParticles(puff, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
        Abilities.sound(p, speedy ? SoundEvents.WITHER_AMBIENT : SoundEvents.CREEPER_PRIMED, speedy ? 1.4F : 0.5F);
        return true;
    }

    /** Helpers that fight monsters for you for 30 seconds, then vanish in a puff. */
    static void spawnHelpers(ServerPlayer p, EntityType<? extends Mob> type, int count) {
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < count; i++) {
            Mob m = type.create(level);
            if (m == null) continue;
            double angle = p.getRandom().nextDouble() * Math.PI * 2;
            m.moveTo(p.getX() + Math.cos(angle) * 1.8, p.getY(), p.getZ() + Math.sin(angle) * 1.8, p.getYRot(), 0);
            if (m instanceof Zombie) {
                m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));   // no burning in the sun
                m.setDropChance(EquipmentSlot.HEAD, 0.0F);
            }
            if (m instanceof AbstractPiglin piglin) {
                piglin.setImmuneToZombification(true);
                hand(m, Items.GOLDEN_SWORD);
            }
            if (m instanceof ZombifiedPiglin) hand(m, Items.GOLDEN_SWORD);
            if (m instanceof Pillager) hand(m, Items.CROSSBOW);
            if (m instanceof Vindicator) hand(m, Items.IRON_AXE);
            if (m instanceof Raider raider) raider.setCanJoinRaid(false);
            if (m instanceof Wolf wolf) wolf.tame(p);
            if (m instanceof IronGolem golem) golem.setPlayerCreated(true);
            if (m instanceof Vex vex) vex.setLimitedLife(HELPER_TICKS + 40);
            m.setCanPickUpLoot(false);
            m.addTag(HELPER_TAG);
            m.getPersistentData().putLong(HELPER_UNTIL, level.getGameTime() + HELPER_TICKS);
            m.getPersistentData().putUUID(HELPER_OWNER, p.getUUID());
            m.setCustomName(Component.literal(p.getName().getString() + "'s helper").withStyle(ChatFormatting.GREEN));
            level.addFreshEntity(m);
            HELPERS.add(m);
            level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1, m.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
        }
    }

    private static void hand(Mob m, net.minecraft.world.item.Item item) {
        m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        m.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    /** Point a helper at an enemy (angry mobs like bees need to be made angry, piglins think with a "brain"). */
    private static void helperAttack(Mob mob, LivingEntity enemy) {
        mob.setTarget(enemy);
        if (mob instanceof NeutralMob neutral) {
            neutral.setPersistentAngerTarget(enemy.getUUID());
            neutral.startPersistentAngerTimer();
        }
        if (mob instanceof AbstractPiglin) {
            mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, enemy);
        }
    }

    public static boolean isHelper(Entity e) {
        return e != null && e.getTags().contains(HELPER_TAG);
    }

    public static boolean isCloaked(Player p) {
        return p.level().getGameTime() < MorphData.root(p).getLong(MorphData.CLOAK);
    }

    /** Should this mob be stopped from going after this target? (zombie helpers, cloaked players) */
    public static boolean blockTarget(LivingEntity mob, LivingEntity target) {
        if (target == null) return false;
        if (mob.getTags().contains(HELPER_TAG)) {
            // Helpers only fight monsters, never you, other players, villagers or other helpers
            return !(target instanceof Enemy) || target.getTags().contains(HELPER_TAG);
        }
        return target instanceof Player p && isCloaked(p) && mob.getLastHurtByMob() != p;
    }

    /** A zombie helper came back into the world (chunk reload / restart): keep track of it. */
    public static void onJoin(Entity entity) {
        if (entity instanceof Mob mob && mob.getTags().contains(HELPER_TAG)) HELPERS.add(mob);
    }

    /** Once a tick per world: thrown TNT and zombie helpers. */
    public static void tickLevel(ServerLevel level) {
        // Our TNT explodes as us, so the blast never hurts the player who threw it
        for (int i = TNT.size() - 1; i >= 0; i--) {
            ThrownTnt t = TNT.get(i);
            if (t.tnt.level() != level) continue;
            if (t.tnt.isRemoved()) {
                TNT.remove(i);
            } else if (t.tnt.getFuse() <= 1) {
                TNT.remove(i);
                Player owner = level.getPlayerByUUID(t.owner);
                t.tnt.discard();
                level.explode(owner != null ? owner : t.tnt, t.tnt.getX(), t.tnt.getY(), t.tnt.getZ(), t.power,
                        Level.ExplosionInteraction.MOB);
            }
        }

        if (level.getGameTime() % 20 != 0 || HELPERS.isEmpty()) return;
        long now = level.getGameTime();
        for (Mob mob : new ArrayList<>(HELPERS)) {
            if (mob.level() != level) continue;
            if (mob.isRemoved() || !mob.isAlive()) {
                HELPERS.remove(mob);
                continue;
            }
            if (now >= mob.getPersistentData().getLong(HELPER_UNTIL)) {
                level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
                mob.discard();
                HELPERS.remove(mob);
                continue;
            }
            Player owner = mob.getPersistentData().hasUUID(HELPER_OWNER)
                    ? level.getPlayerByUUID(mob.getPersistentData().getUUID(HELPER_OWNER)) : null;
            if (owner == null) continue;
            LivingEntity enemy = owner.getLastHurtMob();
            if (enemy == null || !enemy.isAlive()) enemy = owner.getLastHurtByMob();
            if ((mob.getTarget() == null || !mob.getTarget().isAlive()) && enemy != null && enemy.isAlive()
                    && enemy.distanceTo(mob) < 20 && !blockTarget(mob, enemy)) {
                helperAttack(mob, enemy);
            } else if (mob.getTarget() == null) {
                List<Mob> monsters = level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(12),
                        m -> m instanceof Enemy && m.isAlive() && !m.getTags().contains(HELPER_TAG));
                if (!monsters.isEmpty()) helperAttack(mob, monsters.get(0));
                else if (mob.distanceToSqr(owner) > 16) mob.getNavigation().moveTo(owner, 1.2);
            }
        }
    }

    // ================================================================ aiming helpers

    /** The mob you're aiming at: the one under the crosshair, or the first one along your view. */
    static LivingEntity lookTarget(ServerPlayer p, double range, int targetId) {
        Entity picked = targetId >= 0 ? p.level().getEntity(targetId) : null;
        if (picked instanceof LivingEntity living && living.isAlive() && living.distanceTo(p) <= range) return living;

        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB area = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, area,
                e -> e instanceof LivingEntity && e != p && e.isAlive() && !e.isSpectator(), eye.distanceToSqr(end));
        if (hit != null && hit.getEntity() instanceof LivingEntity living) return living;

        // A little aim help: the closest mob nearly in front of you
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : cone(p, range, 0.97, null)) {
            double d = e.distanceToSqr(p);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** Mobs in front of you (within range, inside the cone, that you can see). */
    static List<LivingEntity> cone(ServerPlayer p, double range, double minDot, MorphForm excludeKin) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Abilities.nearby(p, range, excludeKin)) {
            if (e.getTags().contains(HELPER_TAG)) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d > range || d < 0.01) continue;
            if (to.scale(1.0 / d).dot(look) >= minDot && p.hasLineOfSight(e)) out.add(e);
        }
        return out;
    }

    static boolean noTarget(ServerPlayer p, String message) {
        WatchActions.tell(p, message, ChatFormatting.GRAY);
        return false;
    }

    static void beam(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions type, int perStep) {
        Vec3 step = to.subtract(from);
        int steps = (int) Math.ceil(step.length() * 2);
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.add(step.scale(i / (double) steps));
            level.sendParticles(type, at.x, at.y, at.z, perStep, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** Particles along the way you're facing, for dashes. */
    static void trail(ServerPlayer p, ParticleOptions type, int length, int perStep) {
        Vec3 look = p.getLookAngle();
        for (int i = 0; i < length; i++) {
            Vec3 at = p.position().add(look.scale(i)).add(0, 0.8, 0);
            p.serverLevel().sendParticles(type, at.x, at.y, at.z, perStep, 0.3, 0.3, 0.3, 0.05);
        }
    }
}

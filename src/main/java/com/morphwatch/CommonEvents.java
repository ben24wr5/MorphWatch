package com.morphwatch;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MorphWatchMod.MODID)
public final class CommonEvents {
    private static final float PLAYER_WIDTH = 0.6F;
    private static final float PLAYER_HEIGHT = 1.8F;
    private static final float PLAYER_EYE = 1.62F;

    private CommonEvents() {}

    /** Small forms get a small hitbox and a low camera. */
    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;
        MorphForm form = MorphData.getForm(player);
        if (form == MorphForm.NONE) return;
        Pose pose = event.getPose();
        if (pose == Pose.SLEEPING || pose == Pose.DYING) return;

        EntityDimensions mob = form.type().getDimensions();
        // Slimes and magma cubes are drawn at size 2
        if (form == MorphForm.SLIME || form == MorphForm.MAGMA_CUBE) mob = mob.scale(0.51F);
        float width = Math.min(mob.width, PLAYER_WIDTH);
        float height = Math.min(mob.height, PLAYER_HEIGHT);
        float eye = height >= PLAYER_HEIGHT ? PLAYER_EYE : height * 0.85F;
        event.setNewSize(EntityDimensions.scalable(width, height));
        event.setNewEyeHeight(eye);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        MorphForm form = MorphData.getForm(player);

        // Spider wall-climbing runs on both sides because movement is client-driven.
        if (form.climbsWalls() && player.horizontalCollision) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, player.isShiftKeyDown() ? 0.0 : 0.2, v.z);
            player.fallDistance = 0.0F;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) return;

        Transformer.tick(serverPlayer);
        if (form != MorphForm.NONE) {
            if (player.tickCount % 10 == 0) {
                MorphData.applyFormEffects(serverPlayer, form);
                if (MorphData.isGolden(player, form)) {
                    serverPlayer.serverLevel().sendParticles(ParticleTypes.WAX_ON,
                            player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(),
                            3, 0.3, 0.4, 0.3, 0.0);
                }
            }
            if (player.tickCount % 20 == 0) {
                MobFriends.tick(serverPlayer, form);
            }
        }
        updateFlight(serverPlayer, form);
    }

    private static void updateFlight(ServerPlayer player, MorphForm form) {
        CompoundTag data = MorphData.root(player);
        var abilities = player.getAbilities();
        if (form.canFly()) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                player.onUpdateAbilities();
                data.putBoolean(MorphData.FLIGHT, true);
            }
        } else if (data.getBoolean(MorphData.FLIGHT)) {
            data.remove(MorphData.FLIGHT);
            if (!player.isCreative() && !player.isSpectator()) {
                abilities.mayfly = false;
                abilities.flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && MorphData.getForm(player).noFallDamage()) {
            event.setDistance(0.0F);
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------- disguise

    /** Mobs of your kind won't target you, unless you've just hit them. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof Player player)) return;
        MorphForm form = MorphData.getForm(player);
        LivingEntity mob = event.getEntity();
        if (form != MorphForm.NONE && form.isKin(mob.getType()) && mob.getLastHurtByMob() != player) {
            event.setCanceled(true);
        }
    }

    /** Zombie helpers never turn on you, and cloaked players can't be found. */
    @SubscribeEvent
    public static void onChangeTargetSuper(LivingChangeTargetEvent event) {
        if (!event.getEntity().level().isClientSide() && SuperPowers.blockTarget(event.getEntity(), event.getNewTarget())) {
            event.setCanceled(true);
        }
    }

    /** Your helpers can never hurt players (magma cubes burn whatever they touch). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        // Nothing can hurt you while your transformation sequence plays
        if (event.getEntity() instanceof ServerPlayer p
                && p.level().getGameTime() < MorphData.root(p).getLong(Transformer.SEQUENCE_SHIELD)) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof Player && (SuperPowers.isHelper(event.getSource().getEntity())
                || SuperPowers.isHelper(event.getSource().getDirectEntity()))) {
            event.setCanceled(true);
        }
    }

    /** Stubborn, Unstoppable, Shell Close...: you can't be knocked back. */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof Player player && MorphData.active(player, MorphData.STEADY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            SuperPowers.tickLevel(level);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) SuperPowers.onJoin(event.getEntity());
    }

    // ---------------------------------------------------- mob attacks + friends

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        Entity source = event.getSource().getEntity();

        // You punched something while transformed
        if (source instanceof ServerPlayer player && event.getSource().getDirectEntity() == player) {
            MorphForm form = MorphData.getForm(player);
            if (form != MorphForm.NONE) {
                float mult = MorphData.powerMultiplier(player, form);
                event.setAmount(event.getAmount() + MobAttacks.bonusDamage(form) * mult);
                MobAttacks.onHit(player, form, victim, mult);
                MobFriends.help(player, form, victim);
            }
        }
        // Spikes: whatever hits you gets hurt back
        if (victim instanceof ServerPlayer player && source instanceof LivingEntity attacker && attacker != player
                && MorphData.active(player, MorphData.SPIKES)) {
            attacker.hurt(player.damageSources().thorns(player), 3.0F);
        }
        // Something hit you while transformed
        if (victim instanceof ServerPlayer player && source instanceof LivingEntity attacker && attacker != player) {
            MorphForm form = MorphData.getForm(player);
            if (form != MorphForm.NONE) MobFriends.help(player, form, attacker);
        }
    }

    // ---------------------------------------------------------- auto-escape

    /** About to drop below a quarter of your hearts: the watch turns you human and heals you. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MorphForm form = MorphData.getForm(player);
        if (form == MorphForm.NONE || !MorphData.isWearing(player)) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        float after = player.getHealth() - event.getAmount();
        if (after > player.getMaxHealth() * 0.25F) return;

        CompoundTag data = MorphData.root(player);
        long now = player.level().getGameTime();
        if (now < data.getLong(MorphData.ESCAPE)) return;
        data.putLong(MorphData.ESCAPE, now + MorphData.escapeCooldown(player));

        event.setCanceled(true);
        Transformer.cancel(data);
        Transformer.playAnimation(player, form, MorphForm.NONE, Transformer.MIN_ANIM_TICKS);
        MorphData.setForm(player, MorphForm.NONE);
        player.heal(6.0F);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
        player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
                40, 0.5, 0.8, 0.5, 0.4);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
        WatchActions.tell(player, "AUTO-ESCAPE! The watch saved you", ChatFormatting.GOLD);
        MorphAdvancements.award(player, MorphAdvancements.CLOSE_CALL);
        MorphData.sync(player);
    }

    // ------------------------------------------------------------- syncing

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            MorphData.syncTo(target, viewer);
            Transformer.sendDialTo(target, viewer);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Transformer.cancel(MorphData.root(event.getEntity()));
        MorphData.migrateOldWatch(event.getEntity());
        resync(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Transformer.forget(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        resync(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        resync(event.getEntity());
    }

    /**
     * Your watch, scans and achievements stay with you when you die.
     * Dying turns you back into a human and resets power cooldowns.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag oldRoot = MorphData.root(event.getOriginal()).copy();
        Transformer.cancel(oldRoot);
        if (event.isWasDeath()) {
            oldRoot.remove(MorphData.FORM);
            oldRoot.remove(MorphData.FLIGHT);
            oldRoot.remove(MorphData.CD1);
            oldRoot.remove(MorphData.CD2);
            oldRoot.remove(MorphData.CD3);
            oldRoot.remove(MorphData.CD4);
            oldRoot.remove(MorphData.CLOAK);
            oldRoot.remove(MorphData.STEADY);
            oldRoot.remove(MorphData.SPIKES);
        }
        event.getEntity().getPersistentData().put(MorphData.ROOT, oldRoot);
    }

    private static void resync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.refreshDimensions();
            MorphData.applyHealth(serverPlayer, MorphData.getForm(serverPlayer));
            MorphData.sync(serverPlayer);
        }
    }
}

package com.morphwatch;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** What your punches do in each form (Blaze sets things on fire, Wither Skeleton withers...). */
public final class MobAttacks {
    private MobAttacks() {}

    /** Extra damage added to your punch. */
    public static float bonusDamage(MorphForm form) {
        return switch (form) {
            case CAT, ZOMBIE -> 2.0F;
            case SPIDER -> 1.0F;
            case ENDERMAN, WITHER_SKELETON -> 3.0F;
            case IRON_GOLEM -> 6.0F;
            default -> 0.0F;
        };
    }

    public static void onHit(ServerPlayer player, MorphForm form, LivingEntity victim, float mult) {
        int t = (int) (20 * mult); // one "second" scaled by strength
        switch (form) {
            case SPIDER -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2 * t, 1));
            case ZOMBIE -> victim.addEffect(new MobEffectInstance(MobEffects.HUNGER, 7 * t, 0));
            case SKELETON -> {
                double dx = victim.getX() - player.getX(), dz = victim.getZ() - player.getZ();
                victim.knockback(0.6 * mult, -dx, -dz);
            }
            case BLAZE -> victim.setSecondsOnFire(Math.max(2, (int) (4 * mult)));
            case WITHER_SKELETON -> victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 5 * t, 0));
            case IRON_GOLEM -> {
                victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.5 * mult, 0));
                victim.hurtMarked = true;
            }
            case SNOW_GOLEM -> {
                victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), 160));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3 * t, 1));
            }
            case CHICKEN -> victim.knockback(0.3, player.getX() - victim.getX(), player.getZ() - victim.getZ());
            default -> { }
        }
    }
}

package com.morphwatch;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;

import java.util.Comparator;
import java.util.List;

/** Mobs of your kind follow you around and join your fights. */
public final class MobFriends {
    private static final double RANGE = 16.0D;
    private static final int MAX_FRIENDS = 6;

    private MobFriends() {}

    private static List<Mob> friends(ServerPlayer player, MorphForm form) {
        List<Mob> list = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
                m -> m.isAlive() && form.isKin(m.getType()) && !(m instanceof TamableAnimal t && t.isTame()));
        list.sort(Comparator.comparingDouble(m -> m.distanceToSqr(player)));
        return list.size() > MAX_FRIENDS ? list.subList(0, MAX_FRIENDS) : list;
    }

    /** Called once a second: friends walk over to you if they have nothing better to do. */
    public static void tick(ServerPlayer player, MorphForm form) {
        for (Mob mob : friends(player, form)) {
            if (mob.getTarget() == player) mob.setTarget(null);
            if (mob.getTarget() == null && mob.distanceToSqr(player) > 25.0D) {
                mob.getNavigation().moveTo(player, 1.1D);
            }
        }
    }

    /** You hit something, or something hit you: your friends go after it. */
    public static void help(ServerPlayer player, MorphForm form, LivingEntity enemy) {
        if (enemy == player || !enemy.isAlive() || form.isKin(enemy.getType())) return;
        for (Mob mob : friends(player, form)) {
            mob.setTarget(enemy);
        }
    }
}

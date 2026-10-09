package com.morphwatch.client;

import com.morphwatch.MorphForm;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** One fake mob per morphed player, drawn in place of the player's model. */
public final class MorphRenderCache {
    private static final Map<UUID, LivingEntity> CACHE = new HashMap<>();

    private MorphRenderCache() {}

    public static LivingEntity get(Player player, MorphForm form) {
        LivingEntity mob = CACHE.get(player.getUUID());
        if (mob == null || mob.getType() != form.type() || mob.level() != player.level()) {
            mob = form.type().create(player.level());
            if (mob == null) return null;
            if (mob instanceof EnderDragon dragon) {
                dragon.setSilent(true);         // its own flying code runs, but no roars
            } else if (mob instanceof Mob m) {
                m.setNoAi(true);
            }
            if (mob instanceof Bat bat) bat.setResting(false);
            if (mob instanceof net.minecraft.world.entity.monster.Slime slime) slime.setSize(2, false);
            CACHE.put(player.getUUID(), mob);
        }
        return mob;
    }

    /** Make the mob copy the player's position, rotation and animation state. */
    public static void copyPose(Player player, LivingEntity mob) {
        mob.setPos(player.getX(), player.getY(), player.getZ());
        mob.xo = player.xo;
        mob.yo = player.yo;
        mob.zo = player.zo;
        mob.xOld = player.xOld;
        mob.yOld = player.yOld;
        mob.zOld = player.zOld;

        mob.setYRot(player.getYRot());
        mob.yRotO = player.yRotO;
        mob.setXRot(player.getXRot());
        mob.xRotO = player.xRotO;
        mob.yBodyRot = player.yBodyRot;
        mob.yBodyRotO = player.yBodyRotO;
        mob.yHeadRot = player.yHeadRot;
        mob.yHeadRotO = player.yHeadRotO;

        mob.tickCount = player.tickCount;
        mob.setOnGround(player.onGround());
        mob.setShiftKeyDown(player.isShiftKeyDown());
        mob.setSprinting(player.isSprinting());
        mob.setInvisible(player.isInvisible());

        mob.swinging = player.swinging;
        mob.swingTime = player.swingTime;
        mob.attackAnim = player.attackAnim;
        mob.oAttackAnim = player.oAttackAnim;
        mob.hurtTime = player.hurtTime;
        mob.hurtDuration = player.hurtDuration;
        mob.deathTime = player.deathTime;

        // Show other players' names above their mob form (but not your own in F5).
        if (player != Minecraft.getInstance().player) {
            mob.setCustomName(player.getDisplayName());
            mob.setCustomNameVisible(true);
        } else {
            mob.setCustomName(null);
            mob.setCustomNameVisible(false);
        }
    }

    /** Called every client tick so legs/wings move at the player's walking speed. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            CACHE.clear();
            return;
        }
        Iterator<Map.Entry<UUID, LivingEntity>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LivingEntity> entry = it.next();
            Player player = mc.level.getPlayerByUUID(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            entry.getValue().walkAnimation.update(player.walkAnimation.speed(), 1.0F);
            if (entry.getValue() instanceof EnderDragon dragon) tickDragon(player, dragon);
        }
    }

    /**
     * The dragon is drawn from its own flight history, not its rotation, so it has to run its
     * flying code to turn and flap. Its head points the opposite way to other mobs.
     */
    private static void tickDragon(Player player, EnderDragon dragon) {
        dragon.setPos(player.getX(), player.getY(), player.getZ());
        dragon.setYRot(player.getYRot() + 180.0F);
        try {
            dragon.aiStep();
        } catch (RuntimeException ignored) {
            // never let the dragon's own code break the game
        }
        dragon.setPos(player.getX(), player.getY(), player.getZ());
    }

    public static void clear() {
        CACHE.clear();
    }
}

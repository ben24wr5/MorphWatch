package com.morphwatch;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server-side actions for the watch on the player's wrist. */
public final class WatchActions {
    private static final double SCAN_RANGE = 8.0D;

    private WatchActions() {}

    /** Strap the watch in this stack onto the wrist. */
    public static void putOn(ServerPlayer player, ItemStack stack, int tier) {
        if (MorphData.isWearing(player)) {
            tell(player, "You're already wearing a Morph Watch (press J to take it off)", ChatFormatting.YELLOW);
            return;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        MorphData.setTier(player, tier);
        MorphData.sync(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_GOLD, SoundSource.PLAYERS, 1.0F, 1.2F);
        tell(player, "Morph Watch on! Look at a mob and press V to scan it", ChatFormatting.GOLD);
    }

    /** V while looking at a mob: scan it (shoots a beam) and unlock it. Then C transforms you into it. */
    public static void scan(ServerPlayer player, int targetId) {
        if (!requireWatch(player)) return;
        Entity entity = player.level().getEntity(targetId);
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(player) > SCAN_RANGE) {
            return;
        }
        MorphForm form = MorphForm.byType(target.getType());
        if (form == null) {
            tell(player, "The watch can't copy that creature", ChatFormatting.RED);
            return;
        }

        scanBeam(player, target);

        boolean wasUnlocked = MorphData.isUnlocked(player, form);
        boolean wasGolden = MorphData.isGolden(player, form);
        boolean counted = MorphData.addScan(player, form, target.getUUID());
        MorphData.markRecent(player, form);
        int count = MorphData.scanCount(player, form);

        if (!wasUnlocked) {
            tell(player, "New mob unlocked: " + form.displayName().getString() + "! Press X and right-click to transform",
                    ChatFormatting.GREEN);
            MorphAdvancements.award(player, MorphAdvancements.FIRST_SCAN);
            if (MorphData.unlockedCount(player) >= MorphForm.mobs().size()) {
                MorphAdvancements.award(player, MorphAdvancements.ALL_SCANS);
            }
        } else if (!wasGolden && MorphData.isGolden(player, form)) {
            tell(player, "GOLDEN " + form.displayName().getString().toUpperCase() + " UNLOCKED!", ChatFormatting.GOLD);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
            MorphAdvancements.award(player, MorphAdvancements.GOLDEN_FORM);
        } else if (counted && count < MorphData.GOLDEN_SCANS) {
            tell(player, form.displayName().getString() + " scans: " + count + "/" + MorphData.GOLDEN_SCANS
                    + " (10 = Golden form)", ChatFormatting.YELLOW);
        }

        if (MorphData.getForm(player) == form) {
            // Already this mob: refresh hearts in case it just turned golden.
            MorphData.applyHealth(player, form);
        } else if (wasUnlocked) {
            tell(player, form.displayName().getString() + " scanned! Press X and right-click to transform", ChatFormatting.AQUA);
        }
        MorphData.sync(player);
    }

    /** Picked a mob in the menu. */
    public static void select(ServerPlayer player, int ordinal) {
        if (!requireWatch(player)) return;
        MorphForm form = MorphForm.byOrdinal(ordinal);
        if (!MorphData.isUnlocked(player, form)) {
            tell(player, "Scan a " + form.displayName().getString() + " first to unlock it", ChatFormatting.RED);
            return;
        }
        Transformer.begin(player, form);
    }

    public static void human(ServerPlayer player) {
        Transformer.begin(player, MorphForm.NONE);
    }

    /** J: back to human and the watch goes back into your inventory. */
    public static void takeOff(ServerPlayer player) {
        int tier = MorphData.tier(player);
        if (tier <= 0) return;
        Transformer.cancel(MorphData.root(player));
        Transformer.setDial(player, -1);
        if (MorphData.getForm(player) != MorphForm.NONE) {
            Transformer.playAnimation(player, MorphData.getForm(player), MorphForm.NONE, Transformer.MIN_ANIM_TICKS);
        }
        MorphData.setForm(player, MorphForm.NONE);
        MorphData.setTier(player, 0);
        MorphData.sync(player);
        ItemStack watch = new ItemStack(MorphWatchMod.watchForTier(tier));
        if (!player.getInventory().add(watch)) {
            player.drop(watch, false);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 1.0F);
        tell(player, "Morph Watch taken off", ChatFormatting.GRAY);
    }

    /** A line of sparks from the wrist to the mob being scanned. */
    private static void scanBeam(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        Vec3 from = player.position().add(0, player.getBbHeight() * 0.55, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 step = to.subtract(from);
        int points = Math.max(4, (int) (step.length() * 4));
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(step.scale(i / (double) points));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
            }
        }
        level.sendParticles(ParticleTypes.GLOW, to.x, to.y, to.z, 15, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
    }

    private static boolean requireWatch(ServerPlayer player) {
        if (MorphData.isWearing(player)) return true;
        tell(player, "Wear the Morph Watch first (right-click it)", ChatFormatting.RED);
        return false;
    }

    static void tell(ServerPlayer p, String text, ChatFormatting color) {
        p.displayClientMessage(Component.literal(text).withStyle(color), true);
    }
}

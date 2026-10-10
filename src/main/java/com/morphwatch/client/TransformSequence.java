package com.morphwatch.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Your own transformation sequence (when you turn into a mob), like the cartoon's:
 *
 *   0-16   the whole screen floods bright green
 *  16-50   your body changes into the mob in front of the green-and-black splash background,
 *          spinning, with green energy and light rays behind you
 *  50-56   black screen with a streak of light
 *  56-68   close-up of the new mob
 *  68-80   the new mob poses, light rays behind it
 *  80-90   white flash, then back to the game
 *
 * The camera turns round to face you, nothing can hurt you and you can't move until it ends.
 */
public final class TransformSequence {
    public static final float GREEN_END = 16;
    public static final float MORPH_START = 16;
    public static final float MORPH_END = 50;
    public static final float STREAK_START = 50;
    public static final float CLOSE_START = 56;
    public static final float POSE_START = 68;
    public static final float FLASH_START = 80;

    private static float lockedYaw;

    private TransformSequence() {}

    static void begin(Player player) {
        lockedYaw = player.getYRot();
    }

    /** Your sequence, if one is playing. */
    static TransformAnims.Anim active() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        TransformAnims.Anim anim = TransformAnims.get(mc.player);
        return anim != null && anim.sequence ? anim : null;
    }

    public static boolean isPlaying() {
        return active() != null;
    }

    static float time(TransformAnims.Anim anim, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player.level().getGameTime() + partialTick - anim.startTick;
    }

    // ------------------------------------------------------------------ camera

    /** Keep you looking straight ahead so the camera frames you properly. */
    static void lockView() {
        Minecraft mc = Minecraft.getInstance();
        if (active() == null || mc.player == null) return;
        mc.player.setYRot(lockedYaw);
        mc.player.yRotO = lockedYaw;
        mc.player.setXRot(0.0F);
        mc.player.xRotO = 0.0F;
    }

    /** How much to zoom (multiplies the field of view). */
    static double fovMultiplier(float partialTick) {
        TransformAnims.Anim anim = active();
        if (anim == null) return 1.0;
        float t = time(anim, partialTick);
        if (t < MORPH_START) return Mth.lerp(ease(t / MORPH_START), 1.0F, 0.75F);
        if (t < STREAK_START) return 0.75F;
        if (t < POSE_START) return 0.32F;                                   // close-up (starts behind the black screen)
        if (t < FLASH_START) return Mth.lerp(ease((t - POSE_START) / 4.0F), 0.32F, 0.85F);
        return Mth.lerp(ease((t - FLASH_START) / 10.0F), 0.85F, 1.0F);
    }

    /** Tip the camera down a little to aim at your body (or the head and chest in the close-up). */
    static float pitchOffset(float partialTick) {
        TransformAnims.Anim anim = active();
        Minecraft mc = Minecraft.getInstance();
        if (anim == null || mc.player == null) return 0.0F;
        float t = time(anim, partialTick);
        float now = anim.startTick + t;
        float height = Math.min(4.0F, TransformAnims.visualHeight(anim.drawForm(now)) * TransformAnims.scaleFor(anim, now));
        float aim = t >= CLOSE_START && t < POSE_START ? height * 0.78F : height * 0.5F;
        float eye = mc.player.getEyeHeight();
        return (float) Math.toDegrees(Math.atan2(eye - aim, 4.0));
    }

    // ------------------------------------------------------------------ screen

    /** Full-screen parts: the green flood, the black screen with its light streak, the white flash. */
    static void renderOverlay(GuiGraphics g, int w, int h, float partialTick) {
        TransformAnims.Anim anim = active();
        if (anim == null) return;
        float t = time(anim, partialTick);

        // Bright green flood
        if (t < GREEN_END + 2) {
            float a = t < 4 ? t / 4 : t < GREEN_END - 3 ? 1.0F : 1.0F - (t - (GREEN_END - 3)) / 5.0F;
            a = Mth.clamp(a, 0.0F, 1.0F);
            if (a > 0) {
                g.fill(0, 0, w, h, argb(a, 0x4CFF26));
                // brighter glow in the middle
                int gw = (int) (w * (0.3F + 0.4F * Mth.clamp(t / GREEN_END, 0, 1)));
                int gh = (int) (h * (0.3F + 0.4F * Mth.clamp(t / GREEN_END, 0, 1)));
                g.fill(w / 2 - gw / 2, h / 2 - gh / 2, w / 2 + gw / 2, h / 2 + gh / 2, argb(a * 0.45F, 0xB8FF8C));
            }
        }

        // Black screen with a streak of light
        if (t >= STREAK_START - 1 && t < CLOSE_START + 1) {
            float a = t < STREAK_START ? t - (STREAK_START - 1) : t > CLOSE_START ? 1.0F - (t - CLOSE_START) : 1.0F;
            a = Mth.clamp(a, 0.0F, 1.0F);
            g.fill(0, 0, w, h, argb(a, 0x000000));
            float s = Mth.clamp((t - STREAK_START) / (CLOSE_START - STREAK_START), 0.0F, 1.0F);
            g.pose().pushPose();
            g.pose().translate(w / 2.0F, h / 2.0F, 0.0F);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-32.0F));
            int cx = (int) ((s * 2.0F - 1.0F) * w * 0.8F);
            int len = w / 3;
            g.fill(cx - len, -7, cx + len, 7, argb(a * 0.25F, 0xFFF4B0));
            g.fill(cx - len, -3, cx + len, 3, argb(a * 0.6F, 0xFFFFD8));
            g.fill(cx - len / 2, -1, cx + len / 2, 1, argb(a, 0xFFFFFF));
            g.pose().popPose();
        }

        // White flash
        if (t >= FLASH_START) {
            float a = t < FLASH_START + 2 ? (t - FLASH_START) / 2 : 1.0F - (t - (FLASH_START + 3)) / 6.0F;
            a = Mth.clamp(a, 0.0F, 1.0F);
            if (a > 0) g.fill(0, 0, w, h, argb(a, 0xFFFFFF));
        }
    }

    /** 0..1: how strongly the light rays shine behind you. */
    static float rayStrength(float partialTick) {
        TransformAnims.Anim anim = active();
        if (anim == null) return 0.0F;
        float t = time(anim, partialTick);
        if (t < MORPH_START || t >= FLASH_START + 4) return 0.0F;
        if (t < STREAK_START) return Mth.clamp((t - MORPH_START) / 6.0F, 0.0F, 0.7F);
        if (t < POSE_START) return 0.5F;
        return 1.0F;
    }

    // ------------------------------------------------------------------ sounds

    static void tickSounds(Minecraft mc, Player player, int tick) {
        switch (tick) {
            case 1 -> play(mc, player, SoundEvents.BEACON_ACTIVATE, 1.6F, 1.0F);
            case 16 -> play(mc, player, SoundEvents.EVOKER_CAST_SPELL, 1.2F, 0.9F);
            case 33 -> play(mc, player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.4F, 1.0F);
            case 50 -> play(mc, player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8F, 1.0F);
            case 56 -> play(mc, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F);
            case 68 -> play(mc, player, SoundEvents.PLAYER_LEVELUP, 1.2F, 0.6F);
            case 80 -> play(mc, player, SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.0F);
            default -> { }
        }
    }

    private static void play(Minecraft mc, Player player, SoundEvent sound, float pitch, float volume) {
        mc.level.playLocalSound(player.getX(), player.getY() + 1, player.getZ(), sound, SoundSource.PLAYERS,
                volume, pitch, false);
    }

    // ------------------------------------------------------------------ helpers

    private static float ease(float t) {
        return TransformAnims.ease(t);
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255) << 24) | rgb;
    }
}

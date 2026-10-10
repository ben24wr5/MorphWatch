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
 *   0-12   close-up of your watch: your hand slams down on the dial and it sinks in
 *  10-28   the whole screen floods bright green
 *  28-62   your body changes into the mob in front of the green-and-black splash background,
 *          spinning, with green energy and light rays behind you
 *  62-68   black screen with a streak of light
 *  68-80   close-up of the new mob's chest with the watch symbol on it
 *  80-92   the new mob poses, light rays behind it, the symbol on its chest
 *  92-102  white flash, then back to the game
 *
 * The camera turns round to face you, nothing can hurt you and you can't move until it ends.
 */
public final class TransformSequence {
    public static final float SLAM_END = 12;
    public static final float SLAM_HIT = 8;
    public static final float GREEN_START = 10;
    public static final float GREEN_END = 28;
    public static final float MORPH_START = 28;
    public static final float MORPH_END = 62;
    public static final float STREAK_START = 62;
    public static final float CLOSE_START = 68;
    public static final float POSE_START = 80;
    public static final float FLASH_START = 92;

    private static final net.minecraft.resources.ResourceLocation EMBLEM =
            new net.minecraft.resources.ResourceLocation(com.morphwatch.MorphWatchMod.MODID, "textures/misc/watch_emblem.png");

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

    public static final int WHITE = 0xFFFFFF;
    public static final int RED = 0xFF2020;

    /**
     * Turning back to human starts with the badge on your chest flashing white and red.
     * Returns the colour at time t (sequence ticks; the flash runs before the green flood), or -1.
     */
    public static int badgeFlash(TransformAnims.Anim anim, float t) {
        if (anim == null || !anim.sequence || anim.to != com.morphwatch.MorphForm.NONE || t >= SLAM_END) return -1;
        int step = Math.floorMod((int) Math.floor(t), 6);
        return step < 3 ? WHITE : RED;
    }

    /** The slam close-up: time into it (0..SLAM_END), or -1 when it isn't showing. */
    public static float slamTime(float partialTick) {
        TransformAnims.Anim anim = active();
        if (anim == null || anim.to == com.morphwatch.MorphForm.NONE) return -1.0F;   // no slam when turning human
        float t = time(anim, partialTick);
        return t < SLAM_END ? Math.max(0.0F, t) : -1.0F;
    }

    /** Zoom for your hands and watch during the slam close-up. */
    static double handFovMultiplier(float partialTick) {
        float t = slamTime(partialTick);
        return t < 0 ? 1.0 : 1.0 - 0.3 * ease(t / 5.0F);
    }

    /** How much to zoom (multiplies the field of view). */
    static double fovMultiplier(float partialTick) {
        TransformAnims.Anim anim = active();
        if (anim == null) return 1.0;
        float t = time(anim, partialTick);
        if (t < SLAM_END) {
            // Back to human: zoom in on the flashing badge
            if (anim.to == com.morphwatch.MorphForm.NONE) return Mth.lerp(ease((t + 12.0F) / 5.0F), 1.0F, 0.4F);
            return 1.0F;
        }
        if (t < MORPH_START) return Mth.lerp(ease((t - SLAM_END) / (MORPH_START - SLAM_END)), 1.0F, 0.75F);
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
        if (t < SLAM_END && anim.to != com.morphwatch.MorphForm.NONE) return 0.0F;   // first person during the slam
        float now = anim.startTick + t;
        float height = Math.min(4.0F, TransformAnims.visualHeight(anim.drawForm(now)) * TransformAnims.scaleFor(anim, now));
        boolean onBadge = (t >= CLOSE_START && t < POSE_START) || t < SLAM_END;
        float aim = onBadge ? height * 0.6F : height * 0.5F;   // close-ups: the chest symbol / flashing badge
        float eye = mc.player.getEyeHeight();
        return (float) Math.toDegrees(Math.atan2(eye - aim, 4.0));
    }

    // ------------------------------------------------------------------ screen

    /** Full-screen parts: the green flood, the black screen with its light streak, the white flash. */
    static void renderOverlay(GuiGraphics g, int w, int h, float partialTick) {
        TransformAnims.Anim anim = active();
        if (anim == null) return;
        float t = time(anim, partialTick);

        // Bright green flood (right after your hand hits the watch)
        if (t >= GREEN_START && t < GREEN_END + 2) {
            float a = t < GREEN_START + 3 ? (t - GREEN_START) / 3 : t < GREEN_END - 3 ? 1.0F : 1.0F - (t - (GREEN_END - 3)) / 5.0F;
            a = Mth.clamp(a, 0.0F, 1.0F);
            if (a > 0) {
                g.fill(0, 0, w, h, argb(a, 0x4CFF26));
                // brighter glow in the middle
                float grow = Mth.clamp((t - GREEN_START) / (GREEN_END - GREEN_START), 0, 1);
                int gw = (int) (w * (0.3F + 0.4F * grow));
                int gh = (int) (h * (0.3F + 0.4F * grow));
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
            case 8 -> play(mc, player, SoundEvents.ANVIL_PLACE, 1.6F, 0.5F);           // the slam
            case 10 -> play(mc, player, SoundEvents.BEACON_ACTIVATE, 1.6F, 1.0F);
            case 28 -> play(mc, player, SoundEvents.EVOKER_CAST_SPELL, 1.2F, 0.9F);
            case 45 -> play(mc, player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.4F, 1.0F);
            case 62 -> play(mc, player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8F, 1.0F);
            case 68 -> play(mc, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F);
            case 80 -> play(mc, player, SoundEvents.PLAYER_LEVELUP, 1.2F, 0.6F);
            case 92 -> play(mc, player, SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.0F);
            default -> { }
        }
    }

    private static void play(Minecraft mc, Player player, SoundEvent sound, float pitch, float volume) {
        mc.level.playLocalSound(player.getX(), player.getY() + 1, player.getZ(), sound, SoundSource.PLAYERS,
                volume, pitch, false);
    }

    // ------------------------------------------------------------------ chest symbol

    /** The watch symbol glowing on the new mob's chest (close-up and pose). Drawn after mobs, in the world. */
    static void renderEmblem(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.Camera camera, float partialTick) {
        TransformAnims.Anim anim = active();
        Minecraft mc = Minecraft.getInstance();
        if (anim == null || mc.player == null) return;
        float t = time(anim, partialTick);
        if (t < CLOSE_START - 1 || t > FLASH_START + 3) return;
        float alpha = Mth.clamp((t - (CLOSE_START - 1)) / 2.0F, 0.0F, 1.0F);
        float now = anim.startTick + t;
        com.morphwatch.MorphForm form = anim.drawForm(now);
        float height = TransformAnims.visualHeight(form) * TransformAnims.scaleFor(anim, now);
        float width = form == com.morphwatch.MorphForm.NONE ? 0.6F : Math.min(3.0F, form.type().getDimensions().width);

        net.minecraft.world.phys.Vec3 cam = camera.getPosition();
        net.minecraft.world.phys.Vec3 base = mc.player.getPosition(partialTick);
        net.minecraft.world.phys.Vec3 chest = base.add(0, height * 0.62, 0);
        net.minecraft.world.phys.Vec3 toCam = cam.subtract(chest);
        toCam = new net.minecraft.world.phys.Vec3(toCam.x, 0, toCam.z).normalize();
        net.minecraft.world.phys.Vec3 at = chest.add(toCam.scale(width * 0.5 + 0.06)).subtract(cam);
        float size = Mth.clamp(height * 0.11F, 0.12F, 0.7F);
        org.joml.Vector3f left = new org.joml.Vector3f(camera.getLeftVector()).mul(size);
        org.joml.Vector3f up = new org.joml.Vector3f(camera.getUpVector()).mul(size);

        net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        net.minecraft.client.renderer.RenderType type = net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(EMBLEM);
        com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(type);
        com.mojang.blaze3d.vertex.PoseStack.Pose pose = poseStack.last();
        int a = (int) (255 * alpha);
        emblemVertex(vc, pose, at, left, up, 1, 1, 0, 0, a);
        emblemVertex(vc, pose, at, left, up, 1, -1, 0, 1, a);
        emblemVertex(vc, pose, at, left, up, -1, -1, 1, 1, a);
        emblemVertex(vc, pose, at, left, up, -1, 1, 1, 0, a);
        buffers.endBatch(type);
    }

    private static void emblemVertex(com.mojang.blaze3d.vertex.VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                     net.minecraft.world.phys.Vec3 at, org.joml.Vector3f left, org.joml.Vector3f up,
                                     float l, float u, float tu, float tv, int alpha) {
        float x = (float) at.x + left.x() * l + up.x() * u;
        float y = (float) at.y + left.y() * l + up.y() * u;
        float z = (float) at.z + left.z() * l + up.z() * u;
        vc.vertex(pose.pose(), x, y, z).color(255, 255, 255, alpha).uv(tu, tv)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0.0F, 0.0F, 1.0F).endVertex();
    }

    // ------------------------------------------------------------------ helpers

    private static float ease(float t) {
        return TransformAnims.ease(t);
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255) << 24) | rgb;
    }
}

package com.morphwatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.morphwatch.MorphData;
import com.morphwatch.MorphForm;
import com.morphwatch.MorphWatchMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The dial display: a see-through green ring lying flat around the watch face (like the
 * Omnitrix), split into segments, with small glowing mob icons around the top half. The mob
 * you've picked sits at the top in the brightest segment; scrolling slides the icons round.
 * Locked mobs show a "?".
 *
 * Drawn in its own flat space, in pixels: x = right, y = up (top of the ring), and the viewer
 * looks at it from the +z side.
 */
final class DialRing {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MorphWatchMod.MODID, "textures/misc/hologram.png");

    static final float INNER = 2.4F;          // just outside the gold case
    static final float OUTER = 4.9F;
    private static final float SLOT_DEGREES = 38.0F;
    private static final float TOP_HALF = 95.0F;   // icons live between -95 and +95 degrees
    private static final float ICON = 1.4F;
    private static final float ICON_SELECTED = 1.9F;

    private static final int[] LIME = {190, 255, 70};
    private static final int[] LIME_BRIGHT = {225, 255, 120};
    private static final int[] GREEN_DARK = {110, 215, 40};
    private static final int[] ICON_COLOUR = {240, 255, 200};
    private static final int[] ICON_GOLD = {255, 215, 90};

    private static final String[] QUESTION = {
            ".###.",
            "#...#",
            "....#",
            "...#.",
            "..#..",
            ".....",
            "..#.."};

    private DialRing() {}

    /**
     * Draws the ring. open = 0..1 (it grows out of the watch as the dial opens and shrinks
     * back as it closes). selected = the mob at the top; order = the dial's list.
     * slide = how far (in slots, -1..1) the icons still have to slide to reach their places.
     */
    static void draw(PoseStack poseStack, MultiBufferSource buffers, Player player, MorphForm selected,
                     List<MorphForm> order, float open, float slide, float now) {
        if (open <= 0.01F || selected == null || order.isEmpty()) return;
        poseStack.pushPose();
        poseStack.scale(open, open, open);

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        float flicker = 0.92F + 0.08F * (float) Math.sin(now * 0.8F);

        // Upper half: lighter segments (the middle one brightest). Lower half: one darker band.
        float half = SLOT_DEGREES / 2.0F;
        float gap = 1.6F;
        for (int k = -2; k <= 2; k++) {
            float a0 = k * SLOT_DEGREES - half + gap / 2;
            float a1 = k * SLOT_DEGREES + half - gap / 2;
            if (k == -2) a0 = -TOP_HALF;
            if (k == 2) a1 = TOP_HALF;
            int[] c = k == 0 ? LIME_BRIGHT : LIME;
            arc(vc, pose, a0, a1, INNER, OUTER, 0.0F, c, (int) ((k == 0 ? 165 : 130) * flicker));
        }
        arc(vc, pose, TOP_HALF + gap, 360.0F - TOP_HALF - gap, INNER, OUTER, 0.0F, GREEN_DARK, (int) (115 * flicker));
        // Thin bright edges
        arc(vc, pose, 0, 360, OUTER - 0.18F, OUTER, 0.02F, LIME_BRIGHT, (int) (200 * flicker));
        arc(vc, pose, 0, 360, INNER, INNER + 0.14F, 0.02F, LIME_BRIGHT, (int) (180 * flicker));

        // Icons round the top half
        int index = Math.max(0, order.indexOf(selected));
        int n = order.size();
        float mid = (INNER + OUTER) / 2.0F;
        for (int k = -3; k <= 3; k++) {
            float angle = (k + slide) * SLOT_DEGREES;
            if (Math.abs(angle) > TOP_HALF - 8.0F) continue;
            MorphForm form = order.get(Math.floorMod(index + k, n));
            // Grow to full size as it reaches the top
            float nearTop = 1.0F - Math.min(1.0F, Math.abs(angle) / SLOT_DEGREES);
            float size = ICON + (ICON_SELECTED - ICON) * nearTop;
            double rad = Math.toRadians(angle);
            float x = (float) Math.sin(rad) * mid;
            float y = (float) Math.cos(rad) * mid;
            boolean golden = MorphData.isGolden(player, form);
            int[] colour = golden ? ICON_GOLD : ICON_COLOUR;
            int alpha = (int) ((nearTop > 0.5F ? 245 : 200) * flicker);
            if (MorphData.isUnlocked(player, form)) {
                drawMob(poseStack, buffers, player, form, x, y, size, colour, alpha, now);
            } else {
                drawQuestion(vc, poseStack.last(), x, y, size, colour, alpha);
            }
        }
        poseStack.popPose();
    }

    /** A flat glowing mob icon centred on (x, y). */
    private static void drawMob(PoseStack poseStack, MultiBufferSource buffers, Player player, MorphForm form,
                                float x, float y, float size, int[] colour, int alpha, float now) {
        LivingEntity mob = Hologram.model(form, player.level());
        if (mob == null) return;
        mob.tickCount = (int) now;
        float h = Math.max(0.2F, mob.getBbHeight());
        float w = Math.max(0.2F, mob.getBbWidth() * 1.3F);
        // size is in pixels; the model is in blocks (16 pixels)
        float scale = size / Math.max(h, w);
        poseStack.pushPose();
        poseStack.translate(x, y - h * scale / 2.0F, 0.15F);
        poseStack.scale(scale, scale, scale * 0.12F);   // squashed flat onto the ring
        MultiBufferSource holo = type -> new HoloVertexConsumer(
                buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), colour[0], colour[1], colour[2], alpha);
        EntityRenderer<? super LivingEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob);
        try {
            renderer.render(mob, 0.0F, 0.0F, poseStack, holo, LightTexture.FULL_BRIGHT);
        } catch (RuntimeException ignored) {
            // a mob model that can't be drawn as an icon just doesn't show
        }
        poseStack.popPose();
    }

    /** A pixel-art "?" centred on (x, y). */
    private static void drawQuestion(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float size,
                                     int[] colour, int alpha) {
        float px = size / 7.0F;
        float left = x - 2.5F * px;
        float top = y + 3.5F * px;
        for (int row = 0; row < QUESTION.length; row++) {
            for (int col = 0; col < 5; col++) {
                if (QUESTION[row].charAt(col) != '#') continue;
                float x0 = left + col * px, x1 = x0 + px;
                float y1 = top - row * px, y0 = y1 - px;
                quad(vc, pose, x0, y0, x1, y1, 0.12F, colour, alpha);
            }
        }
    }

    /** A flat ring piece from angle a0 to a1 (degrees from the top, clockwise to the right). */
    private static void arc(VertexConsumer vc, PoseStack.Pose pose, float a0, float a1, float r0, float r1, float z,
                            int[] c, int alpha) {
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) / 6.0F));
        for (int i = 0; i < steps; i++) {
            double t0 = Math.toRadians(a0 + (a1 - a0) * i / steps);
            double t1 = Math.toRadians(a0 + (a1 - a0) * (i + 1) / steps);
            float s0 = (float) Math.sin(t0), c0 = (float) Math.cos(t0);
            float s1 = (float) Math.sin(t1), c1 = (float) Math.cos(t1);
            vertex(vc, pose, s0 * r0, c0 * r0, z, c, alpha);
            vertex(vc, pose, s0 * r1, c0 * r1, z, c, alpha);
            vertex(vc, pose, s1 * r1, c1 * r1, z, c, alpha);
            vertex(vc, pose, s1 * r0, c1 * r0, z, c, alpha);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
                             int[] c, int alpha) {
        vertex(vc, pose, x0, y0, z, c, alpha);
        vertex(vc, pose, x1, y0, z, c, alpha);
        vertex(vc, pose, x1, y1, z, c, alpha);
        vertex(vc, pose, x0, y1, z, c, alpha);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int[] c, int alpha) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], alpha).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0.0F, 0.0F, 1.0F).endVertex();
    }
}

package com.morphwatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.morphwatch.MorphWatchMod;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The small 3D watch-face badge every transformed mob wears on its chest: a little gold disc with
 * the watch face on the front. While you turn back into a human it flashes white and red.
 */
final class MorphBadge {
    private static final ResourceLocation FACE =
            new ResourceLocation(MorphWatchMod.MODID, "textures/misc/watch_emblem.png");
    private static final ResourceLocation FACE_WHITE =
            new ResourceLocation(MorphWatchMod.MODID, "textures/misc/watch_emblem_white.png");
    private static final ResourceLocation WHITE =
            new ResourceLocation(MorphWatchMod.MODID, "textures/misc/hologram.png");
    private static final int SIDES = 16;

    private MorphBadge() {}

    /**
     * Draws the badge on the mob's chest. The pose is at the mob's feet.
     * flashRgb = -1 for the normal watch face, otherwise the whole badge glows this colour.
     */
    static void render(PoseStack poseStack, MultiBufferSource buffers, int light, LivingEntity mob,
                       float partialTick, int flashRgb) {
        float height = mob.getBbHeight();
        float width = Math.min(3.0F, mob.getBbWidth());
        float bodyYaw = Mth.rotLerp(partialTick, mob.yBodyRotO, mob.yBodyRot);
        float r = Mth.clamp(height * 0.06F, 0.07F, 0.3F);
        float depth = r * 0.35F;

        poseStack.pushPose();
        poseStack.translate(0.0F, height * 0.6F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));   // +z is the way the mob faces
        poseStack.translate(0.0F, 0.0F, width * 0.5F + 0.01F);
        PoseStack.Pose pose = poseStack.last();

        boolean flashing = flashRgb >= 0;
        int fr = flashing ? (flashRgb >> 16) & 255 : 255;
        int fg = flashing ? (flashRgb >> 8) & 255 : 255;
        int fb = flashing ? flashRgb & 255 : 255;
        int faceLight = flashing ? LightTexture.FULL_BRIGHT : light;

        // Gold rim (the side of the disc)
        VertexConsumer rim = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        int rr = flashing ? fr : 214, rg = flashing ? fg : 166, rb = flashing ? fb : 48;
        for (int i = 0; i < SIDES; i++) {
            double a0 = i * Math.PI * 2 / SIDES, a1 = (i + 1) * Math.PI * 2 / SIDES;
            float x0 = (float) Math.cos(a0) * r * 0.96F, y0 = (float) Math.sin(a0) * r * 0.96F;
            float x1 = (float) Math.cos(a1) * r * 0.96F, y1 = (float) Math.sin(a1) * r * 0.96F;
            float nx = (float) Math.cos((a0 + a1) / 2), ny = (float) Math.sin((a0 + a1) / 2);
            vertex(rim, pose, x0, y0, 0.0F, 0.5F, 0.5F, rr, rg, rb, faceLight, nx, ny, 0);
            vertex(rim, pose, x1, y1, 0.0F, 0.5F, 0.5F, rr, rg, rb, faceLight, nx, ny, 0);
            vertex(rim, pose, x1, y1, depth, 0.5F, 0.5F, rr, rg, rb, faceLight, nx, ny, 0);
            vertex(rim, pose, x0, y0, depth, 0.5F, 0.5F, rr, rg, rb, faceLight, nx, ny, 0);
        }

        // The watch face on the front (round thanks to the picture's see-through corners)
        VertexConsumer face = buffers.getBuffer(flashing
                ? RenderType.entityTranslucentEmissive(FACE_WHITE) : RenderType.entityCutoutNoCull(FACE));
        vertex(face, pose, -r, r, depth, 0.0F, 0.0F, fr, fg, fb, faceLight, 0, 0, 1);
        vertex(face, pose, -r, -r, depth, 0.0F, 1.0F, fr, fg, fb, faceLight, 0, 0, 1);
        vertex(face, pose, r, -r, depth, 1.0F, 1.0F, fr, fg, fb, faceLight, 0, 0, 1);
        vertex(face, pose, r, r, depth, 1.0F, 0.0F, fr, fg, fb, faceLight, 0, 0, 1);

        poseStack.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int r, int g, int b, int light, float nx, float ny, float nz) {
        vc.vertex(pose.pose(), x, y, z).color(r, g, b, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(pose.normal(), nx, ny, nz).endVertex();
    }
}

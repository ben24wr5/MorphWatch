package com.morphwatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.morphwatch.MorphWatchMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * While you transform, a big green alien-style picture fills the view behind you (like the
 * transformation scenes in the cartoon). It is drawn as a giant picture just behind you, facing
 * the camera, before mobs are drawn, so you stay in front of it.
 */
final class TransformBackground {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MorphWatchMod.MODID, "textures/misc/transform_bg.png");

    private TransformBackground() {}

    static void render(PoseStack poseStack, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float alpha = TransformAnims.backgroundAlpha(mc.level.getGameTime() + partialTick);
        if (alpha <= 0.01F) return;

        Vec3 cam = camera.getPosition();
        Vec3 body = mc.player.getPosition(partialTick).add(0, mc.player.getBbHeight() * 0.5, 0);
        Vec3 toPlayer = body.subtract(cam);
        double dist = toPlayer.length();
        if (dist < 0.8) return;   // first person: nothing to stand in front of

        // A few blocks behind you, square to the camera, big enough to fill the whole view
        Vec3 dir = toPlayer.scale(1.0 / dist);
        double behind = dist + 3.0;
        Vec3 centre = dir.scale(behind);
        double halfH = behind * Math.tan(Math.toRadians(mc.options.fov().get() * 0.5)) * 1.6;
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double halfW = halfH * aspect;
        Vector3f up = new Vector3f(camera.getUpVector());
        Vector3f left = new Vector3f(camera.getLeftVector());

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(TEXTURE);
        VertexConsumer vc = buffers.getBuffer(type);
        PoseStack.Pose pose = poseStack.last();
        int a = (int) (255 * alpha);
        corner(vc, pose, centre, left, up, halfW, halfH, 0.0F, 0.0F, a);    // top left
        corner(vc, pose, centre, left, up, halfW, -halfH, 0.0F, 1.0F, a);   // bottom left
        corner(vc, pose, centre, left, up, -halfW, -halfH, 1.0F, 1.0F, a);  // bottom right
        corner(vc, pose, centre, left, up, -halfW, halfH, 1.0F, 0.0F, a);   // top right
        buffers.endBatch(type);
    }

    private static void corner(VertexConsumer vc, PoseStack.Pose pose, Vec3 centre, Vector3f left, Vector3f up,
                               double l, double u, float tu, float tv, int alpha) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float x = (float) (centre.x + left.x() * l + up.x() * u);
        float y = (float) (centre.y + left.y() * l + up.y() * u);
        float z = (float) (centre.z + left.z() * l + up.z() * u);
        vc.vertex(m, x, y, z).color(255, 255, 255, alpha).uv(tu, tv)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0.0F, 0.0F, 1.0F).endVertex();
    }
}

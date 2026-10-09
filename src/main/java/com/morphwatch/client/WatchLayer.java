package com.morphwatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.morphwatch.MorphData;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * Draws the Morph Watch on the player's left wrist.
 * While the dial is up, the real left arm is hidden and this draws a raised arm instead,
 * held up like you're checking the time, with the dial facing your eyes. The hologram
 * then rises straight out of the watch face.
 */
public class WatchLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public WatchLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!MorphData.isWearing(player) || player.isInvisible()) return;
        PlayerModel<AbstractClientPlayer> parent = this.getParentModel();

        if (!Hologram.raisesArm(player)) {
            // Arm hanging normally: watch with the dial on the outside of the wrist
            poseStack.pushPose();
            parent.leftArm.translateAndRotate(poseStack);
            WatchModel.renderWatch(poseStack, buffers, packedLight, player, false, ageInTicks, 0.0F);
            poseStack.popPose();
            return;
        }

        // Dial up: draw our own raised left arm (the real one is hidden for this frame)
        PlayerModel<AbstractClientPlayer> spare = WatchModel.armModel(player);
        if (spare == null) return;
        ModelPart arm = spare.leftArm;
        arm.x = parent.leftArm.x;
        arm.y = parent.leftArm.y;
        arm.z = parent.leftArm.z;
        float rise = Hologram.raiseProgress(player, partialTick);
        float raisedX = Mth.clamp(parent.head.xRot * 0.6F - 1.25F, -2.4F, -0.6F);
        arm.xRot = Mth.lerp(rise, parent.leftArm.xRot, raisedX);
        arm.yRot = Mth.lerp(rise, parent.leftArm.yRot, parent.head.yRot * 0.5F + 0.55F);
        arm.zRot = Mth.lerp(rise, parent.leftArm.zRot, 0.0F);
        WatchModel.renderArm(poseStack, buffers, packedLight, player, arm, spare.leftSleeve, PlayerModelPart.LEFT_SLEEVE);

        poseStack.pushPose();
        arm.translateAndRotate(poseStack);
        float pop = rise;  // the dial face pops out by 1 pixel as it opens
        WatchModel.renderWatch(poseStack, buffers, packedLight, player, true, ageInTicks, pop);
        Hologram.captureDialPoint(player, WatchModel.dialPoint(poseStack, player, pop));
        poseStack.popPose();
    }
}

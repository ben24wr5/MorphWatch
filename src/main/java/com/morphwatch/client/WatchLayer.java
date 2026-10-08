package com.morphwatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.morphwatch.MorphData;
import com.morphwatch.MorphWatchMod;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws the Morph Watch strapped around the player's left wrist, with a glowing ring. */
public class WatchLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MorphWatchMod.MODID, "textures/entity/watch_worn.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(MorphWatchMod.MODID, "textures/entity/watch_glow.png");

    private final ModelPart watch;
    private final ModelPart face;

    public WatchLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
        super(parent);
        this.watch = createModel(slim).bakeRoot();
        this.face = this.watch.getChild("face");
    }

    /**
     * Coordinates are in the left arm's own space (pixels): the arm runs from y=-2 (shoulder)
     * to y=10 (hand), x=-1 to 3 (or 2 for slim arms), z=-2 to 2. The outside of the arm is +x.
     */
    private static LayerDefinition createModel(boolean slim) {
        float armWidth = slim ? 3.0F : 4.0F;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("band", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.0F, 7.0F, -2.0F, armWidth, 2.0F, 4.0F, new CubeDeformation(0.3F)),
                PartPose.ZERO);
        // 3x3 round dial sitting on the outside of the strap, like a real wristwatch.
        root.addOrReplaceChild("face", CubeListBuilder.create()
                        .texOffs(0, 8)
                        .addBox(-1.0F + armWidth, 6.5F, -1.5F, 1.0F, 3.0F, 3.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!MorphData.isWearing(player) || player.isInvisible()) return;
        poseStack.pushPose();
        this.getParentModel().leftArm.translateAndRotate(poseStack);
        this.watch.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                packedLight, OverlayTexture.NO_OVERLAY);

        // Glow ring: green when the R power is ready, red while it recharges. Gently pulses.
        long now = player.level().getGameTime();
        boolean ready = now >= MorphData.root(player).getLong(MorphData.CD1);
        float pulse = 0.75F + 0.25F * Mth.sin(ageInTicks * 0.2F);
        float r = ready ? 0.25F : 1.0F;
        float g = ready ? 1.0F : 0.2F;
        float b = ready ? 0.35F : 0.2F;
        this.face.render(poseStack, buffers.getBuffer(RenderType.eyes(GLOW)), packedLight, OverlayTexture.NO_OVERLAY,
                r * pulse, g * pulse, b * pulse, 1.0F);
        poseStack.popPose();
    }
}

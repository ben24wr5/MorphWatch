package com.morphwatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.morphwatch.MorphData;
import com.morphwatch.MorphWatchMod;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Vector3f;

/**
 * The watch's 3D model, plus a spare player arm we can pose ourselves (for raising your arm to
 * look at the watch). Coordinates are in the left arm's own space, in pixels: the arm runs from
 * y=-2 (shoulder) to y=10 (hand), x=-1 to 3 (or 2 for slim arms), z=-2 to 2.
 * The outside of the arm is +x and the front is -z.
 */
public final class WatchModel {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MorphWatchMod.MODID, "textures/entity/watch_worn.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(MorphWatchMod.MODID, "textures/entity/watch_glow.png");

    /** Wrist height (pixels down the arm) where the dial sits. */
    public static final float WRIST_Y = 8.0F;
    /** Front of the gold case on the strap (pixels, -z is out from the front of the wrist). */
    static final float CASE_FRONT_Z = -3.3F;
    /** Front of the dial when it's closed (it sticks out of the case a little). */
    static final float FACE_FRONT_Z = -3.6F;
    /** How far the dial pops up out of its case when the dial opens (pixels). */
    static final float POP_DISTANCE = 2.0F;

    private static final Parts WIDE = new Parts(false);
    private static final Parts SLIM = new Parts(true);
    private static PlayerModel<AbstractClientPlayer> wideArmModel;
    private static PlayerModel<AbstractClientPlayer> slimArmModel;

    private WatchModel() {}

    /** One set of watch pieces for a wide or slim arm. */
    static final class Parts {
        final float armWidth;
        final ModelPart band;
        final ModelPart sideFace;   // dial on the outside of the arm (arm hanging down)
        final ModelPart topFace;    // dial on the front of the wrist (arm raised: it faces your eyes)
        final ModelPart housing;    // gold case on the strap that the top dial sits in
        final ModelPart riser;      // gold stem that lifts the dial out of its case

        Parts(boolean slim) {
            this.armWidth = slim ? 3.0F : 4.0F;
            ModelPart root = create(armWidth).bakeRoot();
            this.band = root.getChild("band");
            this.sideFace = root.getChild("face");
            this.topFace = root.getChild("top_face");
            this.housing = root.getChild("housing");
            this.riser = root.getChild("riser");
        }

        /** Centre of the arm across its width, in pixels. */
        float centreX() {
            return -1.0F + armWidth / 2.0F;
        }
    }

    private static LayerDefinition create(float armWidth) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("band", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.0F, 7.0F, -2.0F, armWidth, 2.0F, 4.0F, new CubeDeformation(0.3F)),
                PartPose.ZERO);
        root.addOrReplaceChild("face", CubeListBuilder.create()
                        .texOffs(0, 8)
                        .addBox(-1.0F + armWidth, 6.5F, -1.5F, 1.0F, 3.0F, 3.0F),
                PartPose.ZERO);
        // Front of the wrist: a gold case sitting right on the strap, the dial sits in it.
        // The strap's front surface is at z = -2.3 (it's puffed out by 0.3).
        root.addOrReplaceChild("housing", CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(-1.0F, 6.0F, CASE_FRONT_Z, armWidth, 4.0F, -CASE_FRONT_Z - 2.3F),
                PartPose.ZERO);
        root.addOrReplaceChild("riser", CubeListBuilder.create()
                        .texOffs(12, 16)
                        .addBox(-1.0F + armWidth / 2.0F - 1.0F, 7.0F, -1.0F, 2.0F, 2.0F, 1.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("top_face", CubeListBuilder.create()
                        .texOffs(16, 8)
                        .addBox(-1.0F + (armWidth - 3.0F) / 2.0F, 6.5F, FACE_FRONT_Z, 3.0F, 3.0F, 1.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    static Parts parts(Player player) {
        return isSlim(player) ? SLIM : WIDE;
    }

    static boolean isSlim(Player player) {
        return player instanceof AbstractClientPlayer p && "slim".equals(p.getModelName());
    }

    /** Called when the game (re)loads its models. */
    static void bakeArms(EntityModelSet models) {
        wideArmModel = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
        slimArmModel = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    /** Our own copy of the player model, used only to draw a raised left arm. */
    static PlayerModel<AbstractClientPlayer> armModel(Player player) {
        return isSlim(player) ? slimArmModel : wideArmModel;
    }

    /**
     * Draws the player's left arm (and sleeve) at the pose the arm part already has,
     * with the player's own skin.
     */
    static void renderArm(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                          ModelPart arm, ModelPart sleeve, PlayerModelPart sleevePart) {
        RenderType skin = RenderType.entityTranslucent(player.getSkinTextureLocation());
        arm.render(poseStack, buffers.getBuffer(skin), light, OverlayTexture.NO_OVERLAY);
        sleeve.copyFrom(arm);
        if (player.isModelPartShown(sleevePart)) {
            sleeve.render(poseStack, buffers.getBuffer(skin), light, OverlayTexture.NO_OVERLAY);
        }
    }

    /**
     * Draws the watch in arm space (call after moving the pose stack onto the arm).
     * raised = the dial is on the front of the wrist in its gold case (arm held up).
     * pop = 0..1, how far the dial has popped up out of its case on a stem.
     * dialTurn = how far the dial has been turned, in degrees (it turns when you scroll).
     */
    static void renderWatch(PoseStack poseStack, MultiBufferSource buffers, int light, Player player,
                            boolean raised, float ageInTicks, float pop, float dialTurn) {
        Parts p = parts(player);
        VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        p.band.render(poseStack, metal, light, OverlayTexture.NO_OVERLAY);
        ModelPart face = p.sideFace;

        poseStack.pushPose();
        if (raised) {
            face = p.topFace;
            p.housing.render(poseStack, metal, light, OverlayTexture.NO_OVERLAY);
            float lift = POP_DISTANCE * Mth.clamp(pop, 0.0F, 1.0F);
            // The stem joining the case to the popped-up dial. It stops just under the dial
            // so it never pokes through the watch face.
            float stem = lift - 0.6F;
            if (stem > 0.01F) {
                poseStack.pushPose();
                poseStack.translate(0.0F, 0.0F, CASE_FRONT_Z / 16.0F);
                poseStack.scale(1.0F, 1.0F, stem);
                p.riser.render(poseStack, metal, light, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
            poseStack.translate(0.0F, 0.0F, -lift / 16.0F);
            // Turn the dial about its own centre
            float cx = p.centreX() / 16.0F, cy = WRIST_Y / 16.0F, cz = (FACE_FRONT_Z + 0.5F) / 16.0F;
            poseStack.translate(cx, cy, cz);
            poseStack.mulPose(Axis.ZP.rotationDegrees(dialTurn));
            poseStack.translate(-cx, -cy, -cz);
        }
        face.render(poseStack, metal, light, OverlayTexture.NO_OVERLAY);

        // Glow ring: green when the R power is ready, red while it recharges. Gently pulses.
        long now = player.level().getGameTime();
        boolean ready = now >= MorphData.root(player).getLong(MorphData.CD1);
        float pulse = 0.75F + 0.25F * Mth.sin(ageInTicks * 0.2F);
        float r = ready ? 0.25F : 1.0F;
        float g = ready ? 1.0F : 0.2F;
        float b = ready ? 0.35F : 0.2F;
        face.render(poseStack, buffers.getBuffer(RenderType.eyes(GLOW)), light, OverlayTexture.NO_OVERLAY,
                r * pulse, g * pulse, b * pulse, 1.0F);
        poseStack.popPose();
    }

    /** The middle of the raised dial (inside the face), in the current pose's space. */
    static Vector3f dialCentre(PoseStack poseStack, Player player, float pop) {
        Parts p = parts(player);
        float lift = POP_DISTANCE * Mth.clamp(pop, 0.0F, 1.0F);
        Vector3f v = new Vector3f(p.centreX() / 16.0F, WRIST_Y / 16.0F, (FACE_FRONT_Z - lift + 0.5F) / 16.0F);
        return poseStack.last().pose().transformPosition(v);
    }

    /** The point just above the raised dial, in the current pose's space. The hologram rises from here. */
    static Vector3f dialPoint(PoseStack poseStack, Player player, float pop) {
        Parts p = parts(player);
        float lift = POP_DISTANCE * Mth.clamp(pop, 0.0F, 1.0F);
        Vector3f v = new Vector3f(p.centreX() / 16.0F, WRIST_Y / 16.0F, (FACE_FRONT_Z - lift - 0.05F) / 16.0F);
        return poseStack.last().pose().transformPosition(v);
    }
}

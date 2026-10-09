package com.morphwatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.morphwatch.MorphData;
import com.morphwatch.MorphForm;
import com.morphwatch.MorphWatchMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The glowing mob silhouette that rises out of the watch face while the dial is up.
 * Everyone nearby can see it. Golden forms glow gold, the rest glow hologram-blue.
 * While the dial is up you hold your arm up (see WatchLayer) and the hologram sits on the watch.
 */
public final class Hologram {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MorphWatchMod.MODID, "textures/misc/hologram.png");
    private static final int RISE_TICKS = 6;
    private static final int POP_TICKS = 4;
    private static final float HOLO_HEIGHT = 0.5F;
    private static final float HOLO_HEIGHT_HAND = 0.2F;

    /** Other players' open dials: player -> (mob ordinal, when it opened/changed). */
    private record Remote(int ordinal, long openedAt, long changedAt) {}

    /** Where each player's watch face was drawn this frame (camera space), so the hologram can sit on it. */
    private record Captured(Vector3f viewPoint, int frame) {}

    private static final Map<UUID, Remote> REMOTE = new HashMap<>();
    private static final Map<UUID, Captured> DIAL_POINTS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_WORLD_POINT = new HashMap<>();
    private static final Map<MorphForm, LivingEntity> MODELS = new EnumMap<>(MorphForm.class);
    private static Level modelLevel;
    private static int frame = 0;
    private static boolean drawingWorldEntities = false;

    private Hologram() {}

    public static void setRemote(Player player, int ordinal) {
        long now = player.level().getGameTime();
        if (ordinal < 0) {
            REMOTE.remove(player.getUUID());
            return;
        }
        Remote old = REMOTE.get(player.getUUID());
        REMOTE.put(player.getUUID(), new Remote(ordinal, old != null ? old.openedAt() : now, now));
    }

    public static void clear() {
        REMOTE.clear();
        DIAL_POINTS.clear();
        LAST_WORLD_POINT.clear();
        MODELS.clear();
        modelLevel = null;
    }

    private static LivingEntity model(MorphForm form, Level level) {
        if (modelLevel != level) {
            MODELS.clear();
            modelLevel = level;
        }
        return MODELS.computeIfAbsent(form, f -> {
            LivingEntity e = f.type().create(level);
            if (e instanceof Bat bat) bat.setResting(false);
            return e;
        });
    }

    // ------------------------------------------------------------ dial state

    /**
     * Which mob this player's dial shows, MorphForm.NONE for an empty dial (nothing scanned yet),
     * or null if their dial is down.
     */
    static MorphForm shownForm(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) {
            if (!ClientState.dialOpen) return null;
            MorphForm selected = Dial.selected(player);
            return selected == null ? MorphForm.NONE : selected;
        }
        Remote remote = REMOTE.get(player.getUUID());
        return remote == null ? null : MorphForm.byOrdinal(remote.ordinal());
    }

    private static long openedAt(Player player) {
        if (player == Minecraft.getInstance().player) return ClientState.dialOpenedAt;
        Remote remote = REMOTE.get(player.getUUID());
        return remote == null ? 0 : remote.openedAt();
    }

    private static long changedAt(Player player) {
        if (player == Minecraft.getInstance().player) return ClientState.dialChangedAt;
        Remote remote = REMOTE.get(player.getUUID());
        return remote == null ? 0 : remote.changedAt();
    }

    /** True while this player should hold their arm up to look at the watch (dial up, in human form). */
    public static boolean raisesArm(Player player) {
        return shownForm(player) != null && MorphData.isWearing(player)
                && MorphData.getForm(player) == MorphForm.NONE && TransformAnims.get(player) == null;
    }

    /** 0 -> 1 as the arm lifts up after the dial opens. */
    public static float raiseProgress(Player player, float partialTick) {
        float now = player.level().getGameTime() + partialTick;
        return TransformAnims.ease((now - openedAt(player)) / RISE_TICKS);
    }

    // --------------------------------------------- where the watch face is

    /** Called as the world's entities start drawing, so we know which watch positions are fresh. */
    public static void beginEntities() {
        frame++;
        drawingWorldEntities = true;
    }

    public static void endEntities() {
        drawingWorldEntities = false;
    }

    /** WatchLayer reports where it drew the raised dial (only counts while drawing the world, not menus). */
    static void captureDialPoint(Player player, Vector3f viewPoint) {
        if (drawingWorldEntities) {
            DIAL_POINTS.put(player.getUUID(), new Captured(viewPoint, frame));
        }
    }

    /** Fallback spot just above the wrist when we don't have a drawn arm to use (e.g. while you're a mob). */
    private static Vec3 fallbackAnchor(Player player, float partialTick) {
        float yaw = (float) Math.toRadians(Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot));
        Vec3 pos = player.getPosition(partialTick);
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        float w = Math.max(0.3F, player.getBbWidth());
        float h = player.getBbHeight();
        return pos.add(left.scale(w * 0.6)).add(forward.scale(0.2)).add(0, h * 0.5, 0);
    }

    /** Fallback for your own first-person view while you're a mob (no arm to hold up). */
    private static Vec3 firstPersonFallback(Player player, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        float headYaw = (float) Math.toRadians(player.getViewYRot(partialTick));
        Vec3 left = new Vec3(Mth.cos(headYaw), 0, Mth.sin(headYaw));
        return eye.add(look.scale(1.1)).add(left.scale(0.42)).add(0, -0.42, 0);
    }

    // ------------------------------------------------------------ ticking

    /** A few sparks drifting up from the watch face into the hologram. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (mc.level.getGameTime() % 3 != 0) return;
        for (Player player : mc.level.players()) {
            if (shownForm(player) == null) continue;
            boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
            if (firstPerson) continue;
            Vec3 base = LAST_WORLD_POINT.getOrDefault(player.getUUID(), fallbackAnchor(player, 1.0F));
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, base.x, base.y, base.z, 0, 0.06, 0);
        }
    }

    // ------------------------------------------------------------ drawing

    /** Draws every open dial's hologram in the world. Called after particles are drawn. */
    public static void render(PoseStack poseStack, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        Matrix4f toWorld = new Matrix4f(poseStack.last().pose()).invert();
        boolean drewAny = false;

        for (Player player : mc.level.players()) {
            MorphForm form = shownForm(player);
            if (form == null) {
                LAST_WORLD_POINT.remove(player.getUUID());
                continue;
            }
            boolean self = player == mc.player;
            boolean firstPerson = self && mc.options.getCameraType().isFirstPerson();
            if (player.isInvisible() && !self) continue;
            // In first person, your own raised arm and its hologram are drawn with your hand instead.
            if (firstPerson && raisesArm(player)) continue;

            float now = mc.level.getGameTime() + partialTick;
            float rise = TransformAnims.ease((now - openedAt(player)) / RISE_TICKS);
            float pop = 0.7F + 0.3F * TransformAnims.ease((now - changedAt(player)) / POP_TICKS);

            Vec3 face;
            Captured captured = DIAL_POINTS.get(player.getUUID());
            if (captured != null && captured.frame() == frame && raisesArm(player)) {
                // The real spot where the watch face was drawn this frame
                Vector3f w = toWorld.transformPosition(new Vector3f(captured.viewPoint()));
                face = cam.add(w.x(), w.y(), w.z());
            } else {
                face = firstPerson ? firstPersonFallback(player, partialTick) : fallbackAnchor(player, partialTick);
            }
            LAST_WORLD_POINT.put(player.getUUID(), face);

            Vec3 base = face.add(0, 0.04 + 0.12 * rise, 0).subtract(cam);
            poseStack.pushPose();
            poseStack.translate(base.x, base.y, base.z);
            drawHolo(mc, poseStack, buffers, player, form, HOLO_HEIGHT * rise * pop, now, partialTick,
                    camera.rotation(), false);
            poseStack.popPose();
            drewAny = true;
        }
        if (drewAny) buffers.endBatch();
    }

    /**
     * First person: lifts your left arm into view with the watch facing you, and puts the
     * hologram right on the watch face. Called from the hand-drawing event (camera space).
     */
    public static void renderFirstPerson(PoseStack poseStack, MultiBufferSource buffers, int light,
                                         AbstractClientPlayer player, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        PlayerModel<AbstractClientPlayer> spare = WatchModel.armModel(player);
        if (spare == null) return;
        float now = player.level().getGameTime() + partialTick;
        float rise = raiseProgress(player, partialTick);
        float pop = 0.7F + 0.3F * TransformAnims.ease((now - changedAt(player)) / POP_TICKS);

        // Camera space: x right, y up, -z away from you. The forearm comes up from the bottom left,
        // pointing up and to the right, with the front of the wrist (and the dial) facing you.
        Vector3f along = new Vector3f(0.55F, 0.62F, -0.3F).normalize();     // shoulder -> hand
        Vector3f faceOut = new Vector3f(0.0F, 0.15F, 1.0F);                  // dial points at you
        faceOut.sub(new Vector3f(along).mul(faceOut.dot(along))).normalize();
        Vector3f modelY = along;
        Vector3f modelZ = new Vector3f(faceOut).negate();                    // the arm's front is -z
        Vector3f modelX = new Vector3f(modelY).cross(modelZ).normalize();
        Matrix3f basis = new Matrix3f(
                modelX.x(), modelX.y(), modelX.z(),
                modelY.x(), modelY.y(), modelY.z(),
                modelZ.x(), modelZ.y(), modelZ.z());
        Quaternionf rotation = new Quaternionf().setFromNormalized(basis);

        WatchModel.Parts parts = WatchModel.parts(player);
        Vector3f wrist = new Vector3f(-0.2F, -0.24F - (1.0F - rise) * 0.6F, -0.55F);
        Vector3f wristLocal = new Vector3f(parts.centreX() / 16.0F, WatchModel.WRIST_Y / 16.0F, 0.0F);
        Vector3f origin = new Vector3f(wrist).sub(rotation.transform(new Vector3f(wristLocal)));

        ModelPart arm = spare.leftArm;
        arm.x = 0;
        arm.y = 0;
        arm.z = 0;
        arm.xRot = 0;
        arm.yRot = 0;
        arm.zRot = 0;

        poseStack.pushPose();
        poseStack.translate(origin.x(), origin.y(), origin.z());
        poseStack.mulPose(rotation);
        WatchModel.renderArm(poseStack, buffers, light, player, arm, spare.leftSleeve);
        WatchModel.renderWatch(poseStack, buffers, light, player, true, now);
        Vector3f dial = WatchModel.dialPoint(poseStack, player);
        poseStack.popPose();

        // Hologram standing on the watch face
        MorphForm form = shownForm(player);
        if (form == null) return;
        poseStack.pushPose();
        poseStack.translate(dial.x(), dial.y() + 0.02F + 0.05F * rise, dial.z());
        drawHolo(mc, poseStack, buffers, player, form, HOLO_HEIGHT_HAND * rise * pop, now, partialTick,
                new Quaternionf(), true);
        poseStack.popPose();
    }

    /**
     * Draws one hologram standing at the current pose origin, `height` tall.
     * textRotation turns the "?" to face the viewer; cameraSpace = true when drawing with your hand.
     */
    private static void drawHolo(Minecraft mc, PoseStack poseStack, MultiBufferSource buffers, Player player,
                                 MorphForm form, float height, float now, float partialTick,
                                 Quaternionf textRotation, boolean cameraSpace) {
        if (height <= 0.001F) return;
        if (form == MorphForm.NONE) {
            drawEmpty(mc, poseStack, buffers, height, now, textRotation, cameraSpace);
            return;
        }
        LivingEntity mob = model(form, player.level());
        if (mob == null) return;
        mob.tickCount = (int) now;

        boolean golden = MorphData.isGolden(player, form);
        int r = golden ? 255 : 90, g = golden ? 200 : 225, b = golden ? 60 : 255;
        int flicker = (int) (20 * Mth.sin(now * 0.9F));
        MultiBufferSource holo = type -> new HoloVertexConsumer(
                buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), r, g, b, 170 + flicker);

        float size = Math.max(mob.getBbHeight(), mob.getBbWidth());
        float scale = height / Math.max(0.2F, size);
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(now * 3.0F));
        EntityRenderer<? super LivingEntity> renderer = mc.getEntityRenderDispatcher().getRenderer(mob);
        renderer.render(mob, 0.0F, partialTick, poseStack, holo, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    /** Empty dial: a spinning blue watch with a big gold "?" floating above it. */
    private static void drawEmpty(Minecraft mc, PoseStack poseStack, MultiBufferSource buffers, float height,
                                  float now, Quaternionf textRotation, boolean cameraSpace) {
        MultiBufferSource holo = type -> new HoloVertexConsumer(
                buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), 90, 225, 255, 180);

        poseStack.pushPose();
        poseStack.translate(0, height * 0.3F, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(now * 4.0F));
        poseStack.scale(height * 0.9F, height * 0.9F, height * 0.9F);
        mc.getItemRenderer().renderStatic(new ItemStack(MorphWatchMod.MORPH_WATCH.get()), ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, holo, mc.level, 0);
        poseStack.popPose();

        // The "?" always faces you, like a name tag
        poseStack.pushPose();
        poseStack.translate(0, height * 0.95F, 0);
        poseStack.mulPose(textRotation);
        float textScale = 0.025F * height / HOLO_HEIGHT * 1.6F;
        if (cameraSpace) {
            poseStack.scale(textScale, -textScale, -textScale);
        } else {
            poseStack.scale(-textScale, -textScale, textScale);
        }
        Font font = mc.font;
        String q = "?";
        float bob = Mth.sin(now * 0.15F) * 1.5F;
        font.drawInBatch(q, -font.width(q) / 2.0F, bob, 0xFFE0B040, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}

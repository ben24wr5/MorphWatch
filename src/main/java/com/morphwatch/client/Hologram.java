package com.morphwatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.world.entity.player.PlayerModelPart;
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
 * The dial display on the watch: while the dial is up you hold your arm up, the watch face
 * pops out, a beam of light shines up from it and a hologram of the selected mob stands on
 * top of the watch. Scanned mobs show their hologram, locked ones show a "?". Turning the
 * dial shrinks the old hologram into the watch and grows the new one out of it.
 * Everyone nearby sees it. Holograms are green, golden forms are gold.
 */
public final class Hologram {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MorphWatchMod.MODID, "textures/misc/hologram.png");
    private static final int RISE_TICKS = 6;
    private static final int SWITCH_TICKS = 6;
    private static final int CLOSE_TICKS = 7;
    /** Each click of the dial turns the watch face this much. */
    private static final float DEGREES_PER_CLICK = 30.0F;
    /** How big the dial display is: holograms are scaled to fit inside this (blocks). */
    private static final float HOLO_SIZE_WORLD = 0.5F;
    private static final float HOLO_SIZE_HAND = 0.3F;

    private static final int[] GREEN = {120, 255, 90};
    private static final int[] GOLD = {255, 205, 60};

    /** Other players' dials (closedAt < 0 while open). */
    private record Remote(int ordinal, int prevOrdinal, long openedAt, long changedAt, long closedAt,
                          int turnSteps, int prevTurnSteps) {}

    /** Where each player's watch face was drawn this frame (camera space). */
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
        Remote old = REMOTE.get(player.getUUID());
        if (ordinal < 0) {
            // Closing: keep it a moment so it can fold away
            if (old != null && old.closedAt() < 0) {
                REMOTE.put(player.getUUID(), new Remote(old.ordinal(), old.ordinal(), old.openedAt(), now - 100,
                        now, old.turnSteps(), old.turnSteps()));
            }
            return;
        }
        if (old == null || old.closedAt() >= 0) {
            int steps = old == null ? 0 : old.turnSteps();
            REMOTE.put(player.getUUID(), new Remote(ordinal, ordinal, now, now - 100, -1, steps, steps));
        } else if (old.ordinal() != ordinal) {
            int n = MorphForm.mobs().size();
            int diff = Math.floorMod(ordinal - old.ordinal(), n);
            int dir = diff <= n / 2 ? 1 : -1;
            REMOTE.put(player.getUUID(), new Remote(ordinal, old.ordinal(), old.openedAt(), now, -1,
                    old.turnSteps() + dir, old.turnSteps()));
        }
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

    /** Is this player's dial up, or still folding away after being closed? */
    static boolean dialVisible(Player player) {
        long now = player.level().getGameTime();
        if (player == Minecraft.getInstance().player) {
            long since = now - ClientState.dialClosedAt;
            return ClientState.dialOpen || (since >= 0 && since < CLOSE_TICKS);
        }
        Remote remote = REMOTE.get(player.getUUID());
        return remote != null && (remote.closedAt() < 0 || now - remote.closedAt() < CLOSE_TICKS);
    }

    private static long closedAt(Player player) {
        if (player == Minecraft.getInstance().player) return ClientState.dialOpen ? -1 : ClientState.dialClosedAt;
        Remote remote = REMOTE.get(player.getUUID());
        return remote == null ? -1 : remote.closedAt();
    }

    /** Which mob this player's dial shows (while it's up or folding away), or null. */
    static MorphForm shownForm(Player player) {
        if (!dialVisible(player)) return null;
        if (player == Minecraft.getInstance().player) return Dial.selected();
        Remote remote = REMOTE.get(player.getUUID());
        return remote == null ? null : MorphForm.byOrdinal(remote.ordinal());
    }

    /** How far the dial face has been turned, in degrees, smoothly following each scroll click. */
    static float dialTurnDegrees(Player player, float partialTick) {
        int steps, prev;
        if (player == Minecraft.getInstance().player) {
            steps = ClientState.dialTurnSteps;
            prev = ClientState.dialTurnPrevSteps;
        } else {
            Remote remote = REMOTE.get(player.getUUID());
            if (remote == null) return 0.0F;
            steps = remote.turnSteps();
            prev = remote.prevTurnSteps();
        }
        float now = player.level().getGameTime() + partialTick;
        float t = TransformAnims.ease((now - changedAt(player)) / SWITCH_TICKS);
        return Mth.lerp(t, prev, steps) * -DEGREES_PER_CLICK;
    }

    /** -1, 0 or 1: which way the dial is turning right now (for the hand twist). */
    static float turnTwist(Player player, float partialTick) {
        float now = player.level().getGameTime() + partialTick;
        float t = (now - changedAt(player)) / SWITCH_TICKS;
        if (t < 0.0F || t > 1.0F) return 0.0F;
        int dir = player == Minecraft.getInstance().player
                ? Integer.signum(ClientState.dialTurnSteps - ClientState.dialTurnPrevSteps) : 1;
        return dir * Mth.sin((float) Math.PI * t);
    }

    private static MorphForm previousForm(Player player) {
        if (player == Minecraft.getInstance().player) return Dial.previous();
        Remote remote = REMOTE.get(player.getUUID());
        return remote == null ? MorphForm.NONE : MorphForm.byOrdinal(remote.prevOrdinal());
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

    /** True while this player holds their arm up to look at the watch (dial up, in human form). */
    public static boolean raisesArm(Player player) {
        return shownForm(player) != null && MorphData.isWearing(player)
                && MorphData.getForm(player) == MorphForm.NONE && TransformAnims.get(player) == null;
    }

    /**
     * 0 -> 1 as the arm lifts and the dial pops open, and back 1 -> 0 as it folds away
     * after closing (the hologram shrinks into the watch, the dial sinks into its case,
     * the arm comes down).
     */
    public static float raiseProgress(Player player, float partialTick) {
        float now = player.level().getGameTime() + partialTick;
        float open = TransformAnims.ease((now - openedAt(player)) / RISE_TICKS);
        long closed = closedAt(player);
        if (closed >= 0) {
            open *= 1.0F - TransformAnims.ease((now - closed) / CLOSE_TICKS);
        }
        return open;
    }

    // --------------------------------------------- where the watch face is

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

    /** Fallback spot beside a player who has no arm to hold up (e.g. while they're a mob). */
    private static Vec3 fallbackAnchor(Player player, float partialTick) {
        float yaw = (float) Math.toRadians(Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot));
        Vec3 pos = player.getPosition(partialTick);
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        float w = Math.max(0.3F, player.getBbWidth());
        float h = player.getBbHeight();
        return pos.add(left.scale(w * 0.6)).add(forward.scale(0.2)).add(0, h * 0.5, 0);
    }

    /** Fallback for your own first-person view while you're a mob. */
    private static Vec3 firstPersonFallback(Player player, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        float headYaw = (float) Math.toRadians(player.getViewYRot(partialTick));
        Vec3 left = new Vec3(Mth.cos(headYaw), 0, Mth.sin(headYaw));
        return eye.add(look.scale(1.1)).add(left.scale(0.42)).add(0, -0.42, 0);
    }

    // ------------------------------------------------------------ ticking

    /** A few sparks drifting up from the watch face. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        long gameTime = mc.level.getGameTime();
        REMOTE.values().removeIf(r -> r.closedAt() >= 0 && gameTime - r.closedAt() > CLOSE_TICKS + 2);
        if (mc.level.getGameTime() % 3 != 0) return;
        for (Player player : mc.level.players()) {
            if (shownForm(player) == null) continue;
            boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
            if (firstPerson) continue;
            Vec3 base = LAST_WORLD_POINT.getOrDefault(player.getUUID(), fallbackAnchor(player, 1.0F));
            mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, base.x, base.y + 0.05, base.z, 0, 0.02, 0);
        }
    }

    // ------------------------------------------------------------ drawing in the world

    /** Draws every open dial in the world (third person, front view, and other players). */
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
            // In first person your raised arm and its display are drawn with your hands instead.
            if (firstPerson && raisesArm(player)) continue;

            Vec3 face;
            Captured captured = DIAL_POINTS.get(player.getUUID());
            if (captured != null && captured.frame() == frame && raisesArm(player)) {
                Vector3f w = toWorld.transformPosition(new Vector3f(captured.viewPoint()));
                face = cam.add(w.x(), w.y(), w.z());
            } else {
                face = firstPerson ? firstPersonFallback(player, partialTick) : fallbackAnchor(player, partialTick);
            }
            LAST_WORLD_POINT.put(player.getUUID(), face);

            // Holograms turn to face whoever is looking
            Vec3 toCam = cam.subtract(face);
            float faceYaw = (float) Math.toDegrees(Math.atan2(-toCam.x, toCam.z));

            Vec3 base = face.subtract(cam);
            poseStack.pushPose();
            poseStack.translate(base.x, base.y, base.z);
            drawDisplay(mc, poseStack, buffers, player, form, HOLO_SIZE_WORLD, faceYaw, partialTick,
                    camera.rotation(), false);
            poseStack.popPose();
            drewAny = true;
        }
        if (drewAny) buffers.endBatch();
    }

    // ------------------------------------------------------------ first person

    /**
     * First person: your left arm comes up in front of you with the watch face pointing up,
     * your right hand comes in from the right, and the display stands on the watch.
     * Called from the hand-drawing event (camera space: x right, y up, -z forward).
     */
    public static void renderFirstPerson(PoseStack poseStack, MultiBufferSource buffers, int light,
                                         AbstractClientPlayer player, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        PlayerModel<AbstractClientPlayer> spare = WatchModel.armModel(player);
        if (spare == null) return;
        float rise = raiseProgress(player, partialTick);
        float now = player.level().getGameTime() + partialTick;
        WatchModel.Parts parts = WatchModel.parts(player);
        boolean slim = WatchModel.isSlim(player);

        // Left forearm: pointing forward and a little up and right, watch face up (tilted toward you)
        Vector3f wrist = new Vector3f(-0.15F, -0.24F - (1.0F - rise) * 0.7F, -0.62F);
        Quaternionf leftRot = armRotation(new Vector3f(0.22F, 0.28F, -1.0F), new Vector3f(0.0F, 1.0F, 0.35F));
        Vector3f leftWristLocal = new Vector3f(parts.centreX() / 16.0F, WatchModel.WRIST_Y / 16.0F, 0.0F);
        Vector3f leftOrigin = new Vector3f(wrist).sub(leftRot.transform(new Vector3f(leftWristLocal)));

        ModelPart leftArm = spare.leftArm;
        resetPart(leftArm);
        poseStack.pushPose();
        poseStack.translate(leftOrigin.x(), leftOrigin.y(), leftOrigin.z());
        poseStack.mulPose(leftRot);
        WatchModel.renderArm(poseStack, buffers, light, player, leftArm, spare.leftSleeve, PlayerModelPart.LEFT_SLEEVE);
        float pop = rise;
        WatchModel.renderWatch(poseStack, buffers, light, player, true, now, pop, dialTurnDegrees(player, partialTick));
        Vector3f dial = WatchModel.dialPoint(poseStack, player, pop);
        poseStack.popPose();

        // Right hand reaching in from the right, ready to turn the dial
        float rightCentreX = slim ? -0.5F : -1.0F;
        Vector3f handTarget = new Vector3f(wrist).add(0.17F, -0.06F, 0.08F);
        Quaternionf rightRot = armRotation(new Vector3f(-0.55F, 0.35F, -0.9F), new Vector3f(0.0F, 1.0F, 0.35F));
        Vector3f rightHandLocal = new Vector3f(rightCentreX / 16.0F, 10.0F / 16.0F, 0.0F);
        Vector3f rightOrigin = new Vector3f(handTarget).sub(rightRot.transform(new Vector3f(rightHandLocal)));
        ModelPart rightArm = spare.rightArm;
        resetPart(rightArm);
        poseStack.pushPose();
        poseStack.translate(rightOrigin.x(), rightOrigin.y(), rightOrigin.z());
        poseStack.mulPose(rightRot);
        // Twist the hand as it turns the dial
        float twist = turnTwist(player, partialTick) * 35.0F;
        poseStack.translate(rightCentreX / 16.0F, 0.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(twist));
        poseStack.translate(-rightCentreX / 16.0F, 0.0F, 0.0F);
        WatchModel.renderArm(poseStack, buffers, light, player, rightArm, spare.rightSleeve, PlayerModelPart.RIGHT_SLEEVE);
        poseStack.popPose();

        // The display standing on the watch face
        MorphForm form = shownForm(player);
        if (form == null) return;
        poseStack.pushPose();
        poseStack.translate(dial.x(), dial.y(), dial.z());
        float sway = 12.0F * Mth.sin(now * 0.05F);
        drawDisplay(mc, poseStack, buffers, player, form, HOLO_SIZE_HAND, sway, partialTick, new Quaternionf(), true);
        poseStack.popPose();
    }

    /**
     * Rotation that lays an arm (its +y running shoulder -> hand) along `along`, with the
     * front of the arm (-z, where the raised dial sits) facing `faceOut`.
     */
    private static Quaternionf armRotation(Vector3f along, Vector3f faceOut) {
        Vector3f y = new Vector3f(along).normalize();
        Vector3f out = new Vector3f(faceOut);
        out.sub(new Vector3f(y).mul(out.dot(y))).normalize();
        Vector3f z = new Vector3f(out).negate();
        Vector3f x = new Vector3f(y).cross(z).normalize();
        Matrix3f basis = new Matrix3f(x.x(), x.y(), x.z(), y.x(), y.y(), y.z(), z.x(), z.y(), z.z());
        return new Quaternionf().setFromNormalized(basis);
    }

    private static void resetPart(ModelPart part) {
        part.x = 0;
        part.y = 0;
        part.z = 0;
        part.xRot = 0;
        part.yRot = 0;
        part.zRot = 0;
    }

    // ------------------------------------------------------------ the display itself

    /**
     * Beam + hologram standing at the current pose origin (the watch face).
     * size = the box every hologram is scaled to fit inside. faceYaw turns mobs toward the viewer.
     */
    private static void drawDisplay(Minecraft mc, PoseStack poseStack, MultiBufferSource buffers, Player player,
                                    MorphForm form, float size, float faceYaw, float partialTick,
                                    Quaternionf textRotation, boolean cameraSpace) {
        float now = player.level().getGameTime() + partialTick;
        float open = raiseProgress(player, partialTick);
        float sw = (now - changedAt(player)) / SWITCH_TICKS;

        // Turning the dial: the old hologram shrinks into the watch, then the new one grows out
        MorphForm showing = form;
        float grow = 1.0F;
        boolean switching = sw < 1.0F;
        if (switching) {
            if (sw < 0.5F) {
                showing = previousForm(player);
                grow = 1.0F - TransformAnims.ease(sw * 2.0F);
            } else {
                grow = TransformAnims.ease((sw - 0.5F) * 2.0F);
            }
        }
        grow *= open;

        boolean golden = showing != MorphForm.NONE && MorphData.isGolden(player, showing);
        int[] color = golden ? GOLD : GREEN;

        // Beam of light from the watch face, brighter while switching
        float beamAlpha = (switching ? 0.75F : 0.45F) * open;
        drawBeam(poseStack, buffers, size * 0.16F, size * 0.32F, size * 0.42F * Math.max(open, 0.2F), color, beamAlpha);

        if (grow <= 0.01F || showing == MorphForm.NONE) return;
        float flicker = 0.85F + 0.15F * Mth.sin(now * 0.9F);
        if (!MorphData.isUnlocked(player, showing)) {
            drawQuestionMark(mc, poseStack, buffers, size * grow, now, color, textRotation, cameraSpace);
            return;
        }
        LivingEntity mob = model(showing, player.level());
        if (mob == null) return;
        mob.tickCount = (int) now;
        int alpha = (int) (190 * flicker);
        MultiBufferSource holo = type -> new HoloVertexConsumer(
                buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), color[0], color[1], color[2], alpha);

        // Fit inside the dial: never taller or wider than `size`
        float fit = Math.min(size / Math.max(0.2F, mob.getBbHeight()), size / Math.max(0.2F, mob.getBbWidth() * 1.2F));
        float scale = fit * grow;
        poseStack.pushPose();
        poseStack.translate(0.0F, size * 0.04F, 0.0F);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(-faceYaw));
        EntityRenderer<? super LivingEntity> renderer = mc.getEntityRenderDispatcher().getRenderer(mob);
        renderer.render(mob, 0.0F, partialTick, poseStack, holo, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    /** Locked mob: a big glowing "?" standing on the watch, always facing you. */
    private static void drawQuestionMark(Minecraft mc, PoseStack poseStack, MultiBufferSource buffers, float size,
                                         float now, int[] color, Quaternionf textRotation, boolean cameraSpace) {
        poseStack.pushPose();
        poseStack.translate(0.0F, size * 0.5F + Mth.sin(now * 0.15F) * size * 0.04F, 0.0F);
        poseStack.mulPose(textRotation);
        Font font = mc.font;
        float textScale = size / font.lineHeight * 0.9F;
        if (cameraSpace) {
            poseStack.scale(textScale, -textScale, -textScale);
        } else {
            poseStack.scale(-textScale, -textScale, textScale);
        }
        String q = "?";
        int rgb = 0xFF000000 | (color[0] << 16) | (color[1] << 8) | color[2];
        font.drawInBatch(q, -font.width(q) / 2.0F, -font.lineHeight / 2.0F, rgb, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    /**
     * A see-through glowing square cone shining up from the watch face:
     * `bottom` and `top` are half-widths, fading out toward the top.
     */
    private static void drawBeam(PoseStack poseStack, MultiBufferSource buffers, float bottom, float top, float height,
                                 int[] color, float alpha) {
        if (alpha <= 0.01F || height <= 0.001F) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int a0 = (int) (200 * alpha);
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            float[] c1 = corners[i];
            float[] c2 = corners[(i + 1) % 4];
            float nx = (c1[0] + c2[0]) * 0.5F;
            float nz = (c1[1] + c2[1]) * 0.5F;
            // Each side drawn both ways round so it shows from inside and outside
            for (int side = 0; side < 2; side++) {
                float[] a = side == 0 ? c1 : c2;
                float[] b = side == 0 ? c2 : c1;
                float s = side == 0 ? 1.0F : -1.0F;
                beamVertex(vc, m, n, a[0] * bottom, 0.0F, a[1] * bottom, color, a0, 0.0F, 1.0F, nx * s, nz * s);
                beamVertex(vc, m, n, b[0] * bottom, 0.0F, b[1] * bottom, color, a0, 1.0F, 1.0F, nx * s, nz * s);
                beamVertex(vc, m, n, b[0] * top, height, b[1] * top, color, 0, 1.0F, 0.0F, nx * s, nz * s);
                beamVertex(vc, m, n, a[0] * top, height, a[1] * top, color, 0, 0.0F, 0.0F, nx * s, nz * s);
            }
        }
    }

    private static void beamVertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                                   int[] color, int alpha, float u, float v, float nx, float nz) {
        vc.vertex(m, x, y, z).color(color[0], color[1], color[2], alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, nx, 0.0F, nz).endVertex();
    }
}

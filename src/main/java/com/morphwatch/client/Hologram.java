package com.morphwatch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.morphwatch.MorphData;
import com.morphwatch.MorphForm;
import com.morphwatch.MorphWatchMod;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The glowing mob silhouette that rises out of the watch while the dial is up.
 * Everyone nearby can see it. Golden forms glow gold, the rest glow hologram-blue.
 */
public final class Hologram {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MorphWatchMod.MODID, "textures/misc/hologram.png");
    private static final int RISE_TICKS = 6;
    private static final int POP_TICKS = 4;
    private static final float HOLO_HEIGHT = 0.55F;
    private static final float HOLO_HEIGHT_FIRST_PERSON = 0.42F;

    /** Other players' open dials: player -> (mob ordinal, when it opened/changed). */
    private record Remote(int ordinal, long openedAt, long changedAt) {}

    private static final Map<UUID, Remote> REMOTE = new HashMap<>();
    private static final Map<MorphForm, LivingEntity> MODELS = new EnumMap<>(MorphForm.class);
    private static Level modelLevel;

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

    /** A few sparks drifting from the watch up into the hologram. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (mc.level.getGameTime() % 3 != 0) return;
        for (Player player : mc.level.players()) {
            MorphForm form = shownForm(mc, player);
            if (form == null) continue;
            boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
            Vec3 base = anchor(player, 1.0F, firstPerson);
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, base.x, base.y, base.z, 0, 0.06, 0);
        }
    }

    /**
     * Which mob this player's dial shows, MorphForm.NONE for an empty dial (nothing scanned yet),
     * or null if their dial is down.
     */
    private static MorphForm shownForm(Minecraft mc, Player player) {
        if (player == mc.player) {
            if (!ClientState.dialOpen) return null;
            MorphForm selected = Dial.selected(player);
            return selected == null ? MorphForm.NONE : selected;
        }
        Remote remote = REMOTE.get(player.getUUID());
        return remote == null ? null : MorphForm.byOrdinal(remote.ordinal());
    }

    /**
     * Where the hologram sits. For other players (and you in third person) it floats just above
     * their left wrist. In first person, where you can't see your wrist, it floats low on the left
     * of your view, as if you'd raised the watch.
     */
    private static Vec3 anchor(Player player, float partialTick, boolean firstPerson) {
        float yaw = (float) Math.toRadians(Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot));
        Vec3 pos = player.getPosition(partialTick);
        if (firstPerson) {
            Vec3 eye = player.getEyePosition(partialTick);
            Vec3 look = player.getViewVector(partialTick);
            float headYaw = (float) Math.toRadians(player.getViewYRot(partialTick));
            Vec3 left = new Vec3(Mth.cos(headYaw), 0, Mth.sin(headYaw));
            return eye.add(look.scale(1.1)).add(left.scale(0.42)).add(0, -0.42, 0);
        }
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        float w = Math.max(0.3F, player.getBbWidth());
        float h = player.getBbHeight();
        return pos.add(left.scale(w * 0.6)).add(forward.scale(0.2)).add(0, h * 0.5, 0);
    }

    /** Draws every open dial's hologram. Called after the world's entities are drawn. */
    public static void render(PoseStack poseStack, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        boolean drewAny = false;

        for (Player player : mc.level.players()) {
            MorphForm form = shownForm(mc, player);
            if (form == null) continue;
            boolean self = player == mc.player;
            boolean firstPerson = self && mc.options.getCameraType().isFirstPerson();
            if (player.isInvisible() && !self) continue;

            long openedAt, changedAt;
            if (self) {
                openedAt = ClientState.dialOpenedAt;
                changedAt = ClientState.dialChangedAt;
            } else {
                Remote remote = REMOTE.get(player.getUUID());
                openedAt = remote.openedAt();
                changedAt = remote.changedAt();
            }
            float now = mc.level.getGameTime() + partialTick;
            float rise = TransformAnims.ease((now - openedAt) / RISE_TICKS);
            float pop = 0.7F + 0.3F * TransformAnims.ease((now - changedAt) / POP_TICKS);

            Vec3 base = anchor(player, partialTick, firstPerson).add(0, 0.12 + 0.18 * rise, 0);
            float target = firstPerson ? HOLO_HEIGHT_FIRST_PERSON : HOLO_HEIGHT;

            if (form == MorphForm.NONE) {
                renderEmpty(mc, poseStack, camera, buffers, base.subtract(cam), target * rise * pop, now);
                drewAny = true;
                continue;
            }

            LivingEntity mob = model(form, mc.level);
            if (mob == null) continue;
            mob.tickCount = (int) now;

            boolean golden = MorphData.isGolden(player, form);
            int r = golden ? 255 : 90, g = golden ? 200 : 225, b = golden ? 60 : 255;
            int flicker = (int) (20 * Mth.sin(now * 0.9F));
            MultiBufferSource holo = type -> new HoloVertexConsumer(
                    buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), r, g, b, 170 + flicker);

            float size = Math.max(mob.getBbHeight(), mob.getBbWidth());
            float scale = target / Math.max(0.2F, size) * rise * pop;
            if (scale <= 0.001F) continue;

            poseStack.pushPose();
            poseStack.translate(base.x - cam.x, base.y - cam.y, base.z - cam.z);
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees(now * 3.0F));
            EntityRenderer<? super LivingEntity> renderer = mc.getEntityRenderDispatcher().getRenderer(mob);
            renderer.render(mob, 0.0F, partialTick, poseStack, holo, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
            drewAny = true;
        }
        if (drewAny) buffers.endBatch();
    }

    /** Empty dial: a spinning blue watch with a big gold "?" floating above it. */
    private static void renderEmpty(Minecraft mc, PoseStack poseStack, Camera camera,
                                    MultiBufferSource.BufferSource buffers, Vec3 at, float height, float now) {
        if (height <= 0.001F) return;
        MultiBufferSource holo = type -> new HoloVertexConsumer(
                buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), 90, 225, 255, 180);

        poseStack.pushPose();
        poseStack.translate(at.x, at.y + height * 0.3, at.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(now * 4.0F));
        poseStack.scale(height * 0.9F, height * 0.9F, height * 0.9F);
        mc.getItemRenderer().renderStatic(new ItemStack(MorphWatchMod.MORPH_WATCH.get()), ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, holo, mc.level, 0);
        poseStack.popPose();

        // The "?" always faces you, like a name tag
        poseStack.pushPose();
        poseStack.translate(at.x, at.y + height * 0.95, at.z);
        poseStack.mulPose(camera.rotation());
        float textScale = 0.025F * height / HOLO_HEIGHT * 1.6F;
        poseStack.scale(-textScale, -textScale, textScale);
        Font font = mc.font;
        String q = "?";
        float bob = Mth.sin(now * 0.15F) * 1.5F;
        Matrix4f matrix = poseStack.last().pose();
        font.drawInBatch(q, -font.width(q) / 2.0F, bob, 0xFFE0B040, false, matrix, buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}

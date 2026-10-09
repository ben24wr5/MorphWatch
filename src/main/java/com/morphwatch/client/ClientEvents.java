package com.morphwatch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.morphwatch.MorphData;
import com.morphwatch.MorphForm;
import com.morphwatch.MorphWatchMod;
import com.morphwatch.network.ModNetwork;
import com.morphwatch.network.WatchActionPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ClientEvents {
    private static final String CATEGORY = "key.categories.morphwatch";
    public static final KeyMapping POWER_KEY = new KeyMapping(
            "key.morphwatch.ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping POWER2_KEY = new KeyMapping(
            "key.morphwatch.ability2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping TRANSFORM_KEY = new KeyMapping(
            "key.morphwatch.transform", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping DIAL_KEY = new KeyMapping(
            "key.morphwatch.dial", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping SLAM_KEY = new KeyMapping(
            "key.morphwatch.slam", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    public static final KeyMapping TAKE_OFF_KEY = new KeyMapping(
            "key.morphwatch.take_off", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = MorphWatchMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(POWER_KEY);
            event.register(POWER2_KEY);
            event.register(TRANSFORM_KEY);
            event.register(DIAL_KEY);
            event.register(SLAM_KEY);
            event.register(TAKE_OFF_KEY);
        }

        /** Add the worn-watch layer to both player models (classic and slim arms). */
        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            WatchModel.bakeArms(event.getEntityModels());
            for (String skin : event.getSkins()) {
                PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) {
                    renderer.addLayer(new WatchLayer(renderer));
                }
            }
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("morph_watch_hud", HudOverlay.INSTANCE);
        }
    }

    @Mod.EventBusSubscriber(modid = MorphWatchMod.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private static boolean wasReady1 = true;
        private static boolean wasReady2 = true;
        private static double scrollBuffer = 0;
        /** Whether each Pre pushed a pose that its Post must pop. */
        private static final Deque<Boolean> PUSHED = new ArrayDeque<>();

        // ------------------------------------------------------------ drawing

        /**
         * Draw the player as their mob, and play the transform animation (spin + shrink/grow).
         * Runs last so nothing that cancels the render before us leaves a pose unpopped.
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
            Player player = event.getEntity();
            float partialTick = event.getPartialTick();
            TransformAnims.Anim anim = TransformAnims.get(player);
            float now = TransformAnims.now(player, partialTick);
            MorphForm form = anim != null ? anim.drawForm(now) : MorphData.getForm(player);
            PoseStack poseStack = event.getPoseStack();

            if (form == MorphForm.NONE) {
                // Dial up: hide the real left arm, WatchLayer draws it raised up to look at the watch
                if (Hologram.raisesArm(player)) {
                    event.getRenderer().getModel().leftArm.visible = false;
                    event.getRenderer().getModel().leftSleeve.visible = false;
                }
                // Normal player model; only touch it while animating
                if (anim != null) {
                    poseStack.pushPose();
                    applyAnim(poseStack, anim, now);
                    PUSHED.push(true);
                } else {
                    PUSHED.push(false);
                }
                return;
            }

            LivingEntity mob = MorphRenderCache.get(player, form);
            if (mob == null) {
                PUSHED.push(false);
                return;
            }
            event.setCanceled(true);
            MorphRenderCache.copyPose(player, mob);
            float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());
            EntityRenderer<? super LivingEntity> renderer =
                    Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob);
            poseStack.pushPose();
            if (anim != null) applyAnim(poseStack, anim, now);
            renderer.render(mob, yaw, partialTick, poseStack, event.getMultiBufferSource(), event.getPackedLight());
            poseStack.popPose();
        }

        @SubscribeEvent
        public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
            if (!PUSHED.isEmpty() && PUSHED.pop()) {
                event.getPoseStack().popPose();
            }
            event.getRenderer().getModel().leftArm.visible = true;
        }

        /** First person: with the dial up, your left arm comes up into view showing the watch and hologram. */
        @SubscribeEvent
        public static void onRenderHand(RenderHandEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !Hologram.raisesArm(mc.player)) return;
            // Both hands are busy with the watch: hide the normal hands and draw ours once
            event.setCanceled(true);
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                Hologram.renderFirstPerson(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                        mc.player, event.getPartialTick());
            }
        }

        private static void applyAnim(PoseStack poseStack, TransformAnims.Anim anim, float now) {
            poseStack.mulPose(Axis.YP.rotationDegrees(TransformAnims.spinDegrees(anim, now)));
            float s = TransformAnims.scaleFor(anim, now);
            poseStack.scale(s, s, s);
        }

        /** Hide the human arm in first person while transformed. */
        @SubscribeEvent
        public static void onRenderArm(RenderArmEvent event) {
            if (MorphData.getForm(event.getPlayer()) != MorphForm.NONE) {
                event.setCanceled(true);
            }
        }

        /** The hologram rising out of the watch while a dial is up. */
        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            RenderLevelStageEvent.Stage stage = event.getStage();
            if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
                Hologram.beginEntities();
            } else if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                Hologram.endEntities();
            } else if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                Hologram.render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
            }
        }

        /** The screen shakes when you slam the watch. */
        @SubscribeEvent
        public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            if (ClientState.shakeTicks <= 0) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            float pt = (float) event.getPartialTick();
            float strength = Math.max(0.0F, (ClientState.shakeTicks - pt) / ClientState.SHAKE_TICKS);
            float time = mc.player.tickCount + pt;
            event.setRoll(event.getRoll() + Mth.sin(time * 2.7F) * 3.0F * strength);
            event.setYaw(event.getYaw() + Mth.cos(time * 3.3F) * 1.2F * strength);
        }

        // ------------------------------------------------------------ input

        /** While the dial is up, the scroll wheel turns it instead of changing your hotbar slot. */
        @SubscribeEvent
        public static void onScroll(InputEvent.MouseScrollingEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (!ClientState.dialOpen || mc.screen != null || mc.player == null) return;
            event.setCanceled(true);
            scrollBuffer += event.getScrollDelta();
            while (scrollBuffer >= 1.0) {
                scrollBuffer -= 1.0;
                Dial.turn(mc, 1);    // scroll up: go right (next mob)
            }
            while (scrollBuffer <= -1.0) {
                scrollBuffer += 1.0;
                Dial.turn(mc, -1);   // scroll down: go left (previous mob)
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            MorphRenderCache.tick();
            TransformAnims.tick(mc);
            Hologram.tick(mc);
            if (ClientState.flashTicks > 0) ClientState.flashTicks--;
            if (ClientState.shakeTicks > 0) ClientState.shakeTicks--;

            if (mc.player == null) return;
            boolean inGame = mc.screen == null;

            // The dial goes down if a menu opens or you die
            if (ClientState.dialOpen && (!inGame || !mc.player.isAlive())) {
                Dial.close(mc, true);
                scrollBuffer = 0;
            }

            // R: tap for a normal power, hold for a charged one.
            boolean clicked = false;
            while (POWER_KEY.consumeClick()) clicked = true;
            if (inGame && POWER_KEY.isDown()) {
                ClientState.charging = true;
                ClientState.chargeTicks++;
            } else if (ClientState.charging || clicked) {
                if (inGame) send(WatchActionPacket.POWER_1, ClientState.chargeTicks);
                ClientState.charging = false;
                ClientState.chargeTicks = 0;
            }

            while (POWER2_KEY.consumeClick()) {
                if (inGame) send(WatchActionPacket.POWER_2, crosshairTarget(mc));
            }

            // G: look at a mob to scan it; otherwise open the mob menu. Sneak + G = human.
            while (TRANSFORM_KEY.consumeClick()) {
                if (!inGame) continue;
                if (!MorphData.isWearing(mc.player)) {
                    send(WatchActionPacket.SCAN, -1); // server explains how to put it on
                } else if (mc.player.isShiftKeyDown()) {
                    send(WatchActionPacket.HUMAN, 0);
                } else if (crosshairTarget(mc) >= 0) {
                    send(WatchActionPacket.SCAN, crosshairTarget(mc));
                } else {
                    Dial.close(mc, true);
                    mc.setScreen(new MorphMenuScreen());
                }
            }

            // X: pop the dial up / put it away.  C: slam!
            while (DIAL_KEY.consumeClick()) {
                if (inGame) {
                    Dial.toggle(mc);
                    scrollBuffer = 0;
                }
            }
            while (SLAM_KEY.consumeClick()) {
                if (inGame) Dial.slam(mc);
            }

            while (TAKE_OFF_KEY.consumeClick()) {
                if (inGame) {
                    Dial.close(mc, true);
                    send(WatchActionPacket.TAKE_OFF, 0);
                }
            }

            readyBeep(mc);
        }

        /** Beep when a power finishes recharging. */
        private static void readyBeep(Minecraft mc) {
            Player player = mc.player;
            if (player == null || !MorphData.isWearing(player) || MorphData.getForm(player) == MorphForm.NONE) {
                wasReady1 = true;
                wasReady2 = true;
                return;
            }
            CompoundTag data = MorphData.root(player);
            long now = player.level().getGameTime();
            boolean ready1 = now >= data.getLong(MorphData.CD1);
            boolean ready2 = now >= data.getLong(MorphData.CD2);
            if ((ready1 && !wasReady1) || (ready2 && !wasReady2)) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 2.0F, 0.6F));
            }
            wasReady1 = ready1;
            wasReady2 = ready2;
        }

        private static int crosshairTarget(Minecraft mc) {
            Entity target = mc.crosshairPickEntity;
            return target instanceof LivingEntity && !(target instanceof Player) ? target.getId() : -1;
        }

        private static void send(int action, int arg) {
            ModNetwork.CHANNEL.sendToServer(new WatchActionPacket(action, arg));
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            MorphRenderCache.clear();
            TransformAnims.clear();
            Hologram.clear();
            ClientState.reset();
            PUSHED.clear();
            scrollBuffer = 0;
        }
    }
}

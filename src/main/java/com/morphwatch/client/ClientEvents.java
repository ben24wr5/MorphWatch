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
    // New key names (not the old ones) so the new default keys apply even if old ones were saved
    public static final KeyMapping SUPER_1_KEY = new KeyMapping(
            "key.morphwatch.super_b", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    public static final KeyMapping SUPER_2_KEY = new KeyMapping(
            "key.morphwatch.super_n", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    public static final KeyMapping POWER_KEY = new KeyMapping(
            "key.morphwatch.power_g", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping POWER2_KEY = new KeyMapping(
            "key.morphwatch.power_h", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping SCAN_KEY = new KeyMapping(
            "key.morphwatch.scan_mob", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping DIAL_KEY = new KeyMapping(
            "key.morphwatch.dial", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping HUMAN_KEY = new KeyMapping(
            "key.morphwatch.human", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_COMMA, CATEGORY);
    public static final KeyMapping TAKE_OFF_KEY = new KeyMapping(
            "key.morphwatch.take_off", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = MorphWatchMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        /** The Watch Workbench screen. */
        @SubscribeEvent
        public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                    MorphWatchMod.WORKBENCH_MENU.get(), WatchWorkbenchScreen::new));
        }

        /** The watch icon's strap takes the colour it was dyed. */
        @SubscribeEvent
        public static void onItemColours(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
            event.register((stack, layer) -> layer == 1 ? WatchModel.strapTint(com.morphwatch.MorphWatchItem.strap(stack)) : -1,
                    MorphWatchMod.MORPH_WATCH.get());
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(SUPER_1_KEY);
            event.register(SUPER_2_KEY);
            event.register(POWER_KEY);
            event.register(POWER2_KEY);
            event.register(SCAN_KEY);
            event.register(DIAL_KEY);
            event.register(HUMAN_KEY);
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
        private static final boolean[] WAS_READY = {true, true, true, true};
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
            if (mc.player == null) return;
            // Transformation sequence: close-up of your hand slamming the watch
            float slam = TransformSequence.slamTime(event.getPartialTick());
            if (slam >= 0.0F) {
                event.setCanceled(true);
                if (event.getHand() == InteractionHand.MAIN_HAND) {
                    Hologram.renderSlam(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                            mc.player, event.getPartialTick(), slam);
                }
                return;
            }
            if (!Hologram.raisesArm(mc.player)) return;
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
            } else if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
                // Your transformation background goes in before mobs and players are drawn
                TransformBackground.render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
            } else if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                Hologram.endEntities();
                TransformSequence.renderEmblem(event.getPoseStack(), event.getCamera(), event.getPartialTick());
            } else if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                Hologram.render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
            }
        }

        /** The screen shakes when you slam the watch. */
        @SubscribeEvent
        public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            // Transformation sequence: aim at your body
            if (TransformSequence.isPlaying()) {
                event.setPitch(event.getPitch() + TransformSequence.pitchOffset((float) event.getPartialTick()));
            }
            if (ClientState.shakeTicks <= 0) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            float pt = (float) event.getPartialTick();
            float strength = Math.max(0.0F, (ClientState.shakeTicks - pt) / ClientState.SHAKE_TICKS);
            float time = mc.player.tickCount + pt;
            event.setRoll(event.getRoll() + Mth.sin(time * 2.7F) * 3.0F * strength);
            event.setYaw(event.getYaw() + Mth.cos(time * 3.3F) * 1.2F * strength);
        }

        /** Transformation sequence: zoom in and out. */
        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov event) {
            if (!TransformSequence.isPlaying()) return;
            if (event.usedConfiguredFov()) {
                event.setFOV(event.getFOV() * TransformSequence.fovMultiplier((float) event.getPartialTick()));
            } else {
                event.setFOV(event.getFOV() * TransformSequence.handFovMultiplier((float) event.getPartialTick()));
            }
        }

        /** Transformation sequence: you stand still, looking straight ahead. */
        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) TransformSequence.lockView();
        }

        @SubscribeEvent
        public static void onMovementInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
            if (!TransformSequence.isPlaying()) return;
            var input = event.getInput();
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        }

        /** Transformation sequence: hide the hotbar, hearts and so on (it's a cut-scene). */
        @SubscribeEvent
        public static void onGuiOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
            if (TransformSequence.isPlaying() && !event.getOverlay().id().getNamespace().equals(MorphWatchMod.MODID)) {
                event.setCanceled(true);
            }
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

        /**
         * Left-click while the dial is up: slam the watch and transform into the mob on the dial.
         * Left-click holding a gold ingot, diamond or emerald (dial down): put it into the watch.
         */
        @SubscribeEvent
        public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            if (!event.isAttack() || mc.screen != null || mc.player == null) return;
            if (ClientState.dialOpen) {
                event.setCanceled(true);
                event.setSwingHand(false);
                Dial.slam(mc);
                return;
            }
            net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
            if (MorphData.isWearing(mc.player) && (held.is(net.minecraft.world.item.Items.GOLD_INGOT)
                    || held.is(net.minecraft.world.item.Items.DIAMOND) || held.is(net.minecraft.world.item.Items.EMERALD))) {
                event.setCanceled(true);
                event.setSwingHand(true);
                send(WatchActionPacket.UPGRADE, 0);
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

            // B and N: super powers
            while (SUPER_1_KEY.consumeClick()) {
                if (inGame) send(WatchActionPacket.SUPER_1, crosshairTarget(mc));
            }
            while (SUPER_2_KEY.consumeClick()) {
                if (inGame) send(WatchActionPacket.SUPER_2, crosshairTarget(mc));
            }

            // G: tap for a normal power, hold for a charged one.  H: the other power.
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

            // V: look at a mob and press V to scan it (then X + left-click transforms you). Sneak + V = back to human.
            while (SCAN_KEY.consumeClick()) {
                if (!inGame) continue;
                if (!MorphData.isWearing(mc.player)) {
                    send(WatchActionPacket.SCAN, -1); // server explains how to put it on
                } else if (mc.player.isShiftKeyDown()) {
                    send(WatchActionPacket.HUMAN, 0);
                } else if (crosshairTarget(mc) >= 0) {
                    Entity target = mc.level.getEntity(crosshairTarget(mc));
                    MorphForm scanned = target == null ? null : MorphForm.byType(target.getType());
                    if (scanned != null) Dial.pointAt(scanned);
                    send(WatchActionPacket.SCAN, crosshairTarget(mc));
                } else {
                    mc.player.displayClientMessage(net.minecraft.network.chat.Component
                            .literal("Look at a mob and press V to scan it")
                            .withStyle(net.minecraft.ChatFormatting.GRAY), true);
                }
            }

            // X: pop the dial up / put it away (left-click while it's up transforms you)
            while (DIAL_KEY.consumeClick()) {
                if (inGame) {
                    Dial.toggle(mc);
                    scrollBuffer = 0;
                }
            }
            // , (comma): back to human
            while (HUMAN_KEY.consumeClick()) {
                if (inGame) send(WatchActionPacket.HUMAN, 0);
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
                java.util.Arrays.fill(WAS_READY, true);
                return;
            }
            CompoundTag data = MorphData.root(player);
            long now = player.level().getGameTime();
            boolean beep = false, superBeep = false;
            for (int slot = 1; slot <= 4; slot++) {
                boolean ready = now >= data.getLong(MorphData.cdKey(slot));
                if (ready && !WAS_READY[slot - 1]) {
                    if (slot >= 3) superBeep = true;
                    else beep = true;
                }
                WAS_READY[slot - 1] = ready;
            }
            if (superBeep) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.0F));
            } else if (beep) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 2.0F, 0.6F));
            }
        }

        private static int crosshairTarget(Minecraft mc) {
            Entity target = mc.crosshairPickEntity;
            // The Ender Dragon is made of parts: aim at any part to get the dragon
            if (target instanceof net.minecraftforge.entity.PartEntity<?> part) target = part.getParent();
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

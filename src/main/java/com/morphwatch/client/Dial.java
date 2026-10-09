package com.morphwatch.client;

import com.morphwatch.MorphData;
import com.morphwatch.MorphForm;
import com.morphwatch.network.ModNetwork;
import com.morphwatch.network.WatchActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The watch dial. X pops it up, the scroll wheel turns it through your scanned mobs,
 * C slams the watch and transforms you.
 */
public final class Dial {
    private Dial() {}

    /** Only the mobs you've scanned are on the dial. */
    public static List<MorphForm> choices(Player player) {
        List<MorphForm> list = new ArrayList<>();
        for (MorphForm form : MorphForm.mobs()) {
            if (MorphData.isUnlocked(player, form)) list.add(form);
        }
        return list;
    }

    public static MorphForm selected(Player player) {
        List<MorphForm> list = choices(player);
        if (list.isEmpty()) return null;
        int i = Math.floorMod(ClientState.dialIndex, list.size());
        return list.get(i);
    }

    public static void toggle(Minecraft mc) {
        if (ClientState.dialOpen) {
            close(mc, true);
        } else {
            open(mc);
        }
    }

    public static void open(Minecraft mc) {
        Player player = mc.player;
        if (player == null) return;
        if (!MorphData.isWearing(player)) {
            player.displayClientMessage(Component.literal("Wear the Morph Watch first (right-click it)")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        List<MorphForm> list = choices(player);
        // Start on your current mob if it's on the dial. With nothing scanned yet the dial
        // still pops up, showing a "?" hologram.
        int start = list.indexOf(MorphData.getForm(player));
        ClientState.dialIndex = Math.max(0, start);
        ClientState.dialOpen = true;
        long now = player.level().getGameTime();
        ClientState.dialOpenedAt = now;
        ClientState.dialChangedAt = now;
        click(1.4F);
        sendDial(selected(player));
    }

    public static void close(Minecraft mc, boolean tellServer) {
        if (!ClientState.dialOpen) return;
        ClientState.dialOpen = false;
        if (tellServer && mc.player != null) {
            ModNetwork.CHANNEL.sendToServer(new WatchActionPacket(WatchActionPacket.DIAL, -1));
        }
    }

    /** Scroll down = next mob (right), scroll up = previous mob (left). */
    public static void turn(Minecraft mc, int steps) {
        Player player = mc.player;
        if (player == null || steps == 0) return;
        List<MorphForm> list = choices(player);
        if (list.isEmpty()) return;
        ClientState.dialIndex = Math.floorMod(ClientState.dialIndex + steps, list.size());
        ClientState.dialChangedAt = player.level().getGameTime();
        click(steps > 0 ? 1.8F : 1.6F);
        sendDial(selected(player));
    }

    /** C: slam the watch. Shakes the screen and transforms into the mob on the dial. */
    public static void slam(Minecraft mc) {
        Player player = mc.player;
        if (player == null) return;
        if (!ClientState.dialOpen) {
            player.displayClientMessage(Component.literal("Press X to pop up the dial first")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        MorphForm form = selected(player);
        ClientState.shakeTicks = ClientState.SHAKE_TICKS;
        if (form == null) {
            // Empty dial: still a satisfying slam, but nothing to turn into yet
            close(mc, true);
            player.displayClientMessage(Component.literal("Scan a mob first: look at it and press G")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        close(mc, false);
        ModNetwork.CHANNEL.sendToServer(new WatchActionPacket(WatchActionPacket.SLAM, form.ordinal()));
    }

    /** Tells the server what the dial shows so friends see the hologram (0 = empty dial). */
    private static void sendDial(MorphForm form) {
        int ordinal = form == null ? 0 : form.ordinal();
        ModNetwork.CHANNEL.sendToServer(new WatchActionPacket(WatchActionPacket.DIAL, ordinal));
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LEVER_CLICK, pitch, 0.5F));
    }
}

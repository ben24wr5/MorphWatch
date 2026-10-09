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

import java.util.List;

/**
 * The watch dial. X pops it up (and puts it away), the scroll wheel turns it
 * (up = right / next, down = left / previous), C slams the watch and transforms you.
 * Every mob is on the dial: scanned ones show their hologram, locked ones show a "?".
 */
public final class Dial {
    private Dial() {}

    public static List<MorphForm> choices() {
        return MorphForm.mobs();
    }

    public static MorphForm selected() {
        List<MorphForm> list = choices();
        return list.get(Math.floorMod(ClientState.dialIndex, list.size()));
    }

    /** The mob the dial showed before the last turn (for the shrink/grow switch animation). */
    public static MorphForm previous() {
        List<MorphForm> list = choices();
        return list.get(Math.floorMod(ClientState.dialPrevIndex, list.size()));
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
        // Start on your current mob, otherwise where you left the dial last time
        int current = choices().indexOf(MorphData.getForm(player));
        if (current >= 0) ClientState.dialIndex = current;
        ClientState.dialPrevIndex = ClientState.dialIndex;
        ClientState.dialOpen = true;
        long now = player.level().getGameTime();
        ClientState.dialOpenedAt = now;
        ClientState.dialChangedAt = now - 100; // no switch animation on open
        click(1.4F);
        sendDial();
    }

    public static void close(Minecraft mc, boolean tellServer) {
        if (!ClientState.dialOpen) return;
        ClientState.dialOpen = false;
        if (tellServer && mc.player != null) {
            ModNetwork.CHANNEL.sendToServer(new WatchActionPacket(WatchActionPacket.DIAL, -1));
        }
        click(1.0F);
    }

    /** steps > 0 = right (next mob), steps < 0 = left (previous mob). */
    public static void turn(Minecraft mc, int steps) {
        Player player = mc.player;
        if (player == null || steps == 0) return;
        ClientState.dialPrevIndex = ClientState.dialIndex;
        ClientState.dialIndex = Math.floorMod(ClientState.dialIndex + steps, choices().size());
        ClientState.dialChangedAt = player.level().getGameTime();
        click(steps > 0 ? 1.8F : 1.6F);
        sendDial();
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
        MorphForm form = selected();
        if (!MorphData.isUnlocked(player, form)) {
            // Locked mob: a little buzz, the dial stays up
            player.displayClientMessage(Component.literal("That mob is locked: find it, look at it and press G to scan it")
                    .withStyle(ChatFormatting.YELLOW), true);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.8F));
            return;
        }
        ClientState.shakeTicks = ClientState.SHAKE_TICKS;
        close(mc, false);
        ModNetwork.CHANNEL.sendToServer(new WatchActionPacket(WatchActionPacket.SLAM, form.ordinal()));
    }

    /** Tells the server what the dial shows so everyone nearby sees the hologram. */
    private static void sendDial() {
        ModNetwork.CHANNEL.sendToServer(new WatchActionPacket(WatchActionPacket.DIAL, selected().ordinal()));
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LEVER_CLICK, pitch, 0.5F));
    }
}

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
 * (up = right / next, down = left / previous), right-click slams the watch and transforms you.
 * Every mob is on the dial: scanned ones show their hologram (the one you scanned last comes
 * first), locked ones show a "?".
 */
public final class Dial {
    private Dial() {}

    /** Your scanned mobs first (the one you scanned last at the front), then the locked ones. */
    public static List<MorphForm> choices() {
        Player player = Minecraft.getInstance().player;
        return player == null ? MorphForm.mobs() : MorphData.dialOrder(player);
    }

    public static MorphForm selected() {
        if (ClientState.dialForm == null) ClientState.dialForm = choices().get(0);
        return ClientState.dialForm;
    }

    /** The mob the dial showed before the last turn (for the shrink/grow switch animation). */
    public static MorphForm previous() {
        return ClientState.dialPrevForm == null ? selected() : ClientState.dialPrevForm;
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
        // Always start on the mob you scanned last
        ClientState.dialForm = choices().get(0);
        ClientState.dialPrevForm = ClientState.dialForm;
        ClientState.dialOpen = true;
        long now = player.level().getGameTime();
        ClientState.dialOpenedAt = now;
        ClientState.dialClosedAt = -1000;
        ClientState.dialTurnPrevSteps = ClientState.dialTurnSteps;
        ClientState.dialChangedAt = now - 100; // no switch animation on open
        click(1.4F);
        sendDial();
    }

    /** You just scanned this mob: the dial moves to it so C transforms you into it. */
    public static void pointAt(MorphForm form) {
        if (form == null || form == selected()) return;
        ClientState.dialPrevForm = selected();
        ClientState.dialForm = form;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) ClientState.dialChangedAt = mc.player.level().getGameTime();
        if (ClientState.dialOpen) sendDial();
    }

    public static void close(Minecraft mc, boolean tellServer) {
        if (!ClientState.dialOpen) return;
        ClientState.dialOpen = false;
        if (mc.player != null) ClientState.dialClosedAt = mc.player.level().getGameTime();
        if (tellServer && mc.player != null) {
            ModNetwork.CHANNEL.sendToServer(new WatchActionPacket(WatchActionPacket.DIAL, -1));
        }
        click(1.0F);
    }

    /** steps > 0 = right (next mob), steps < 0 = left (previous mob). */
    public static void turn(Minecraft mc, int steps) {
        Player player = mc.player;
        if (player == null || steps == 0) return;
        List<MorphForm> list = choices();
        int i = Math.max(0, list.indexOf(selected()));
        ClientState.dialPrevForm = selected();
        ClientState.dialForm = list.get(Math.floorMod(i + steps, list.size()));
        ClientState.dialTurnPrevSteps = ClientState.dialTurnSteps;
        ClientState.dialTurnSteps += steps;
        ClientState.dialChangedAt = player.level().getGameTime();
        click(steps > 0 ? 1.8F : 1.6F);
        sendDial();
    }

    /** Right-click with the dial up: slam the watch. Shakes the screen and transforms into the mob on the dial. */
    public static void slam(Minecraft mc) {
        Player player = mc.player;
        if (player == null) return;
        // Only called with the dial up: transforms into the mob on the dial
        MorphForm form = selected();
        if (!MorphData.isUnlocked(player, form)) {
            // Locked mob: a little buzz, the dial stays up
            player.displayClientMessage(Component.literal("That mob is locked: find it, look at it and press V to scan it")
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

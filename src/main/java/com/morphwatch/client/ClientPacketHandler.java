package com.morphwatch.client;

import com.morphwatch.MorphData;
import com.morphwatch.MorphForm;
import com.morphwatch.network.DialStatePacket;
import com.morphwatch.network.SyncMorphPacket;
import com.morphwatch.network.TransformAnimPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void handleSync(SyncMorphPacket msg) {
        Player player = player(msg.entityId());
        if (player == null) return;
        MorphForm before = MorphData.getForm(player);
        MorphData.applyFromServer(player, msg.data());
        // Flash for changes that didn't come with an animation (the animation flashes on its own).
        if (player == Minecraft.getInstance().player && MorphData.getForm(player) != before
                && TransformAnims.get(player) == null) {
            ClientState.flash();
        }
        // Watch taken off: drop the dial
        if (player == Minecraft.getInstance().player && !MorphData.isWearing(player)) {
            Dial.close(Minecraft.getInstance(), false);
        }
    }

    public static void handleAnim(TransformAnimPacket msg) {
        Player player = player(msg.entityId());
        if (player == null) return;
        // Negative ticks: quick animation only, no cut-scene
        TransformAnims.start(player, MorphForm.byOrdinal(msg.from()), MorphForm.byOrdinal(msg.to()),
                Math.abs(msg.ticks()), msg.ticks() > 0);
    }

    public static void handleDial(DialStatePacket msg) {
        Player player = player(msg.entityId());
        if (player == null || player == Minecraft.getInstance().player) return;
        Hologram.setRemote(player, msg.formOrdinal());
    }

    private static Player player(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(entityId);
        return entity instanceof Player p ? p : null;
    }
}

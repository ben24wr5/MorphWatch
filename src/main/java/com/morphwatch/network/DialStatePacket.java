package com.morphwatch.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: another player's dial is showing this mob (or -1 = closed). */
public record DialStatePacket(int entityId, int formOrdinal) {

    public static void encode(DialStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.formOrdinal);
    }

    public static DialStatePacket decode(FriendlyByteBuf buf) {
        return new DialStatePacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(DialStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.morphwatch.client.ClientPacketHandler.handleDial(msg)));
        ctx.get().setPacketHandled(true);
    }
}

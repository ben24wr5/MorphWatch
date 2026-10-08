package com.morphwatch.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: play the transform animation on this player, from one form to another. */
public record TransformAnimPacket(int entityId, int from, int to, int ticks) {

    public static void encode(TransformAnimPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.from);
        buf.writeVarInt(msg.to);
        buf.writeVarInt(msg.ticks);
    }

    public static TransformAnimPacket decode(FriendlyByteBuf buf) {
        return new TransformAnimPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(TransformAnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.morphwatch.client.ClientPacketHandler.handleAnim(msg)));
        ctx.get().setPacketHandled(true);
    }
}

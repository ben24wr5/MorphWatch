package com.morphwatch.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the full watch data for the player with this entity id. */
public record SyncMorphPacket(int entityId, CompoundTag data) {

    public static void encode(SyncMorphPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeNbt(msg.data);
    }

    public static SyncMorphPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        CompoundTag tag = buf.readNbt();
        return new SyncMorphPacket(id, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(SyncMorphPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.morphwatch.client.ClientPacketHandler.handleSync(msg)));
        ctx.get().setPacketHandled(true);
    }
}

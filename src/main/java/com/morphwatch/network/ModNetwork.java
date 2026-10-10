package com.morphwatch.network;

import com.morphwatch.MorphWatchMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    private static final String PROTOCOL = "7";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MorphWatchMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static boolean registered = false;

    private ModNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(0, SyncMorphPacket.class, SyncMorphPacket::encode, SyncMorphPacket::decode,
                SyncMorphPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, WatchActionPacket.class, WatchActionPacket::encode, WatchActionPacket::decode,
                WatchActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, TransformAnimPacket.class, TransformAnimPacket::encode, TransformAnimPacket::decode,
                TransformAnimPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, DialStatePacket.class, DialStatePacket::encode, DialStatePacket::decode,
                DialStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}

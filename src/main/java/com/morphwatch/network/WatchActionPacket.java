package com.morphwatch.network;

import com.morphwatch.Abilities;
import com.morphwatch.Transformer;
import com.morphwatch.WatchActions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player pressed one of the watch keys or clicked the mob menu. */
public record WatchActionPacket(int action, int arg) {
    public static final int POWER_1 = 0;   // G: arg = how many ticks G was held (charge)
    public static final int POWER_2 = 1;   // H: arg = entity under the crosshair, or -1
    public static final int SCAN = 2;      // arg = entity under the crosshair
    public static final int SELECT = 3;    // arg = form ordinal picked in the menu
    public static final int HUMAN = 4;
    public static final int TAKE_OFF = 5;
    public static final int DIAL = 6;      // arg = form ordinal shown on the dial, or -1 = closed
    public static final int SLAM = 7;      // arg = form ordinal to slam into
    public static final int SUPER_R = 8;   // arg = entity under the crosshair, or -1
    public static final int SUPER_T = 9;   // arg = entity under the crosshair, or -1

    public static void encode(WatchActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeVarInt(msg.arg);
    }

    public static WatchActionPacket decode(FriendlyByteBuf buf) {
        return new WatchActionPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(WatchActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.isAlive()) return;
            switch (msg.action) {
                case POWER_1 -> Abilities.use(player, 1, msg.arg, -1);
                case POWER_2 -> Abilities.use(player, 2, 0, msg.arg);
                case SCAN -> WatchActions.scan(player, msg.arg);
                case SELECT -> WatchActions.select(player, msg.arg);
                case HUMAN -> WatchActions.human(player);
                case TAKE_OFF -> WatchActions.takeOff(player);
                case DIAL -> Transformer.setDial(player, msg.arg);
                case SLAM -> Transformer.slam(player, msg.arg);
                case SUPER_R -> Abilities.use(player, 3, 0, msg.arg);
                case SUPER_T -> Abilities.use(player, 4, 0, msg.arg);
                default -> { }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

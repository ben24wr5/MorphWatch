package com.morphwatch;

import com.morphwatch.network.DialStatePacket;
import com.morphwatch.network.ModNetwork;
import com.morphwatch.network.TransformAnimPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every transformation goes through here so it gets the slam, the animation,
 * the delay (shorter on better watches) and the cooldown afterwards.
 */
public final class Transformer {
    public static final String PENDING = "pending";
    public static final String PENDING_AT = "pendingAt";
    public static final String TRANSFORM_CD = "tcd";
    public static final String TRANSFORM_CD_LEN = "tcdlen";

    /** Shortest the animation ever plays, even on a Netherite watch that transforms instantly. */
    public static final int MIN_ANIM_TICKS = 12;
    /** Your own transformation sequence (turning into a mob) lasts this long; you can't be hurt meanwhile. */
    public static final int SEQUENCE_TICKS = 102;
    public static final String SEQUENCE_SHIELD = "seqshield";
    /** Turning back to human starts the cut-scene this far in (there's no watch slam: you're a mob). */
    public static final int SEQUENCE_HUMAN_SKIP = 12;

    /** Which mob each player's open dial is showing (only while the dial is up). Server memory only. */
    private static final Map<UUID, Integer> OPEN_DIALS = new HashMap<>();

    private Transformer() {}

    /** Gold watch: 1 second to transform. Diamond: half a second. Netherite: instant. */
    /** Gold upgrade: transforms faster. */
    public static int delayTicks(Player player) {
        return MorphData.hasUpgrade(player, MorphData.GOLD) ? 10 : 20;
    }

    /** Wait after a transform before the next one. */
    public static int cooldownTicks(Player player) {
        return MorphData.hasUpgrade(player, MorphData.GOLD) ? 40 : 60;
    }

    /**
     * Start a transformation. Turning back to human skips the cooldown so you can always get out.
     * Returns true if it started.
     */
    public static boolean begin(ServerPlayer player, MorphForm target) {
        CompoundTag data = MorphData.root(player);
        MorphForm current = MorphData.getForm(player);
        if (data.contains(PENDING)) {
            WatchActions.tell(player, "Already transforming...", ChatFormatting.YELLOW);
            return false;
        }
        if (current == target) {
            if (target != MorphForm.NONE) {
                WatchActions.tell(player, "You're already a " + target.displayName().getString(), ChatFormatting.GRAY);
            }
            return false;
        }
        long now = player.level().getGameTime();
        if (target != MorphForm.NONE && now < data.getLong(TRANSFORM_CD)) {
            long secs = Math.max(1, (data.getLong(TRANSFORM_CD) - now + 19) / 20);
            WatchActions.tell(player, "Watch cooling down... " + secs + "s", ChatFormatting.YELLOW);
            return false;
        }

        int delay = delayTicks(player);
        playAnimation(player, current, target, Math.max(delay, MIN_ANIM_TICKS), true);
        // Your cut-scene plays (turning back to human skips the watch-slam part); you can't be hurt meanwhile
        data.putLong(SEQUENCE_SHIELD, now + SEQUENCE_TICKS - (target == MorphForm.NONE ? SEQUENCE_HUMAN_SKIP : 0));

        // The slam
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.35F, 1.8F);

        if (delay <= 0) {
            finish(player, target);
        } else {
            data.putString(PENDING, target.id());
            data.putLong(PENDING_AT, now + delay);
        }
        return true;
    }

    /** Called every tick: completes a transformation once its delay is over. */
    public static void tick(ServerPlayer player) {
        CompoundTag data = MorphData.root(player);
        if (!data.contains(PENDING)) return;
        if (player.level().getGameTime() < data.getLong(PENDING_AT)) return;
        MorphForm target = MorphForm.byId(data.getString(PENDING));
        finish(player, target);
    }

    private static void finish(ServerPlayer player, MorphForm target) {
        CompoundTag data = MorphData.root(player);
        data.remove(PENDING);
        data.remove(PENDING_AT);
        if (target != MorphForm.NONE) {
            int cd = cooldownTicks(player);
            data.putLong(TRANSFORM_CD, player.level().getGameTime() + cd);
            data.putLong(TRANSFORM_CD_LEN, cd);
        }
        MorphData.setForm(player, target);
    }

    /** Clears a half-finished transformation (on death, logout...). */
    public static void cancel(CompoundTag data) {
        data.remove(PENDING);
        data.remove(PENDING_AT);
    }

    /** Tells everyone nearby (and the player) to play the transform animation. */
    /** Back to human with the cut-scene (auto-escape, taking the watch off). You can't be hurt during it. */
    public static void playHumanSequence(ServerPlayer player, MorphForm from) {
        playAnimation(player, from, MorphForm.NONE, MIN_ANIM_TICKS, true);
        MorphData.root(player).putLong(SEQUENCE_SHIELD,
                player.level().getGameTime() + SEQUENCE_TICKS - SEQUENCE_HUMAN_SKIP);
    }

    /** A quick animation only: no cut-scene. */
    public static void playAnimation(ServerPlayer player, MorphForm from, MorphForm to, int ticks) {
        playAnimation(player, from, to, ticks, false);
    }

    /**
     * sequence = the player sees their own transformation cut-scene (everyone else sees the quick
     * animation). Sent as negative ticks when there's no cut-scene.
     */
    public static void playAnimation(ServerPlayer player, MorphForm from, MorphForm to, int ticks, boolean sequence) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new TransformAnimPacket(player.getId(), from.ordinal(), to.ordinal(), sequence ? ticks : -ticks));
    }

    // ------------------------------------------------------------------ dial

    /** The player turned or opened their dial (ordinal) or closed it (-1). Others see the hologram. */
    public static void setDial(ServerPlayer player, int ordinal) {
        // Every mob is on the dial; locked ones show a "?" hologram (viewers check the synced scans).
        boolean open = ordinal >= 0 && ordinal < MorphForm.values().length && MorphData.isWearing(player);
        if (open) OPEN_DIALS.put(player.getUUID(), ordinal);
        else OPEN_DIALS.remove(player.getUUID());
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player),
                new DialStatePacket(player.getId(), open ? ordinal : -1));
    }

    /** A newly-arrived viewer needs to know if this player's dial is up. */
    public static void sendDialTo(ServerPlayer target, ServerPlayer viewer) {
        Integer ordinal = OPEN_DIALS.get(target.getUUID());
        if (ordinal != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new DialStatePacket(target.getId(), ordinal));
        }
    }

    public static void forget(ServerPlayer player) {
        OPEN_DIALS.remove(player.getUUID());
    }

    /** C on the dial: slam the watch and transform into the mob it shows. */
    public static void slam(ServerPlayer player, int ordinal) {
        setDial(player, -1);
        if (!MorphData.isWearing(player)) return;
        MorphForm form = MorphForm.byOrdinal(ordinal);
        if (form == MorphForm.NONE) return;
        if (!MorphData.isUnlocked(player, form)) {
            WatchActions.tell(player, "That mob is locked: find it, look at it and press V to scan it",
                    ChatFormatting.YELLOW);
            return;
        }
        begin(player, form);
    }
}

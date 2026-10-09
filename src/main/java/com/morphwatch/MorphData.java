package com.morphwatch;

import com.morphwatch.network.ModNetwork;
import com.morphwatch.network.SyncMorphPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Everything the watch remembers about a player, kept in one NBT compound inside the player's
 * persistent data ("morphwatch"). The server owns it and sends a copy to clients.
 */
public final class MorphData {
    public static final String ROOT = "morphwatch";
    public static final String FORM = "form";
    public static final String TIER = "tier";            // 0 = not wearing, 1 iron, 2 diamond, 3 netherite
    public static final String FLIGHT = "flight";
    public static final String CD1 = "cd1";              // game time when power 1 is ready
    public static final String CD1_LEN = "cd1len";
    public static final String CD2 = "cd2";
    public static final String CD2_LEN = "cd2len";
    public static final String CD3 = "cd3";              // super power B
    public static final String CD3_LEN = "cd3len";
    public static final String CD4 = "cd4";              // super power N
    public static final String CD4_LEN = "cd4len";
    public static final String CLOAK = "cloak";          // game time when Camouflage / Shadow Cloak ends
    public static final String ESCAPE = "escape";        // game time when auto-escape is ready
    public static final String SCANS = "scans";          // form id -> list of scanned mob UUIDs
    public static final String POWERS_USED = "powers";   // forms whose R power you've used

    public static final int GOLDEN_SCANS = 10;
    public static final int EFFECT_DURATION = 300;
    private static final UUID HEALTH_ID = UUID.fromString("7d1a6c52-3f0e-4a8b-9b8e-5e2f0c1d7a41");

    private MorphData() {}

    /** Cooldown key for a power slot: 1 = G, 2 = H, 3 = B (super), 4 = N (super). */
    public static String cdKey(int slot) {
        return switch (slot) { case 1 -> CD1; case 2 -> CD2; case 3 -> CD3; default -> CD4; };
    }

    public static String cdLenKey(int slot) {
        return switch (slot) { case 1 -> CD1_LEN; case 2 -> CD2_LEN; case 3 -> CD3_LEN; default -> CD4_LEN; };
    }

    // ------------------------------------------------------------------ raw data

    public static CompoundTag root(Player player) {
        CompoundTag pd = player.getPersistentData();
        if (!pd.contains(ROOT, Tag.TAG_COMPOUND)) {
            pd.put(ROOT, new CompoundTag());
        }
        return pd.getCompound(ROOT);
    }

    public static MorphForm getForm(Player player) {
        return MorphForm.byId(root(player).getString(FORM));
    }

    public static int tier(Player player) {
        return root(player).getInt(TIER);
    }

    public static boolean isWearing(Player player) {
        return tier(player) > 0;
    }

    public static void setTier(Player player, int tier) {
        if (tier <= 0) root(player).remove(TIER);
        else root(player).putInt(TIER, tier);
    }

    public static int scanCount(Player player, MorphForm form) {
        return root(player).getCompound(SCANS).getList(form.id(), Tag.TAG_STRING).size();
    }

    public static boolean isUnlocked(Player player, MorphForm form) {
        return form == MorphForm.NONE || scanCount(player, form) > 0;
    }

    public static boolean isGolden(Player player, MorphForm form) {
        return form != MorphForm.NONE && scanCount(player, form) >= GOLDEN_SCANS;
    }

    public static int unlockedCount(Player player) {
        int n = 0;
        for (MorphForm f : MorphForm.mobs()) if (isUnlocked(player, f)) n++;
        return n;
    }

    /** Records a scan of this particular mob. Returns false if that exact mob was already counted. */
    public static boolean addScan(Player player, MorphForm form, UUID mobId) {
        CompoundTag root = root(player);
        if (!root.contains(SCANS, Tag.TAG_COMPOUND)) root.put(SCANS, new CompoundTag());
        CompoundTag scans = root.getCompound(SCANS);
        ListTag list = scans.getList(form.id(), Tag.TAG_STRING);
        String id = mobId.toString();
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(id)) return false;
        }
        if (list.size() >= GOLDEN_SCANS) return false;
        list.add(StringTag.valueOf(id));
        scans.put(form.id(), list);
        return true;
    }

    /** Remembers that this form's R power was used. Returns true if it's the first time. */
    public static boolean markPowerUsed(Player player, MorphForm form) {
        CompoundTag root = root(player);
        ListTag list = root.getList(POWERS_USED, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(form.id())) return false;
        }
        list.add(StringTag.valueOf(form.id()));
        root.put(POWERS_USED, list);
        return true;
    }

    public static int powersUsedCount(Player player) {
        return root(player).getList(POWERS_USED, Tag.TAG_STRING).size();
    }

    // -------------------------------------------------------------- multipliers

    /** Diamond and netherite watches recharge faster. */
    public static double cooldownMultiplier(int tier) {
        return switch (tier) { case 2 -> 0.7; case 3 -> 0.45; default -> 1.0; };
    }

    /** How strong powers and mob attacks are: watch tier x golden form. */
    public static float powerMultiplier(Player player, MorphForm form) {
        float tierMult = switch (tier(player)) { case 2 -> 1.25F; case 3 -> 1.5F; default -> 1.0F; };
        return tierMult * (isGolden(player, form) ? 1.5F : 1.0F);
    }

    public static int escapeCooldown(int tier) {
        return switch (tier) { case 2 -> 900; case 3 -> 600; default -> 1200; };
    }

    // ------------------------------------------------------------ transforming

    /** Client side: replace the data with what the server sent. */
    public static void applyFromServer(Player player, CompoundTag data) {
        player.getPersistentData().put(ROOT, data);
        player.refreshDimensions();
    }

    /** Server side: transform a player, with health, effects, a flash and syncing. */
    public static void setForm(ServerPlayer player, MorphForm form) {
        MorphForm old = getForm(player);
        if (old == form) return;

        removeFormEffects(player, old);
        if (form == MorphForm.NONE) root(player).remove(FORM);
        else root(player).putString(FORM, form.id());
        player.refreshDimensions();
        applyHealth(player, form);
        applyFormEffects(player, form);
        if (form != MorphForm.NONE) calmKin(player, form);
        sync(player);

        // Puff + the mob's own noise (the gold burst, flash and jingle are drawn by each client)
        ServerLevel level = player.serverLevel();
        double x = player.getX(), y = player.getY() + 0.8, z = player.getZ();
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 20, 0.4, 0.7, 0.4, 0.08);
        level.sendParticles(ParticleTypes.POOF, x, y, z, 15, 0.4, 0.6, 0.4, 0.02);
        if (form.sound() != null) {
            level.playSound(null, x, y, z, form.sound(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        if (form == MorphForm.NONE) {
            player.displayClientMessage(Component.literal("Back to human").withStyle(ChatFormatting.AQUA), true);
        } else {
            Component name = isGolden(player, form)
                    ? Component.literal("Golden ").append(form.displayName()).withStyle(ChatFormatting.GOLD)
                    : form.displayName().copy().withStyle(ChatFormatting.AQUA);
            player.displayClientMessage(Component.literal("Transformed into ").withStyle(ChatFormatting.AQUA).append(name), true);
        }
    }

    /** Hearts match the mob: an Iron Golem gets 50 hearts, a Chicken 2. Golden forms get +50%. */
    public static void applyHealth(ServerPlayer player, MorphForm form) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        float oldMax = player.getMaxHealth();
        float fraction = oldMax > 0 ? player.getHealth() / oldMax : 1.0F;
        maxHealth.removeModifier(HEALTH_ID);
        if (form != MorphForm.NONE) {
            double target = form.maxHealth() * (isGolden(player, form) ? 1.5 : 1.0);
            double add = target - maxHealth.getBaseValue();
            if (add != 0) {
                maxHealth.addPermanentModifier(new AttributeModifier(HEALTH_ID, "Morph Watch health", add,
                        AttributeModifier.Operation.ADDITION));
            }
        }
        float newMax = player.getMaxHealth();
        if (newMax != oldMax) {
            player.setHealth(Math.max(1.0F, fraction * newMax));
        }
    }

    public static void applyFormEffects(ServerPlayer player, MorphForm form) {
        for (MorphForm.Effect e : form.effects()) {
            player.addEffect(new MobEffectInstance(e.effect(), EFFECT_DURATION, e.amplifier(), true, false, true));
        }
    }

    public static void removeFormEffects(ServerPlayer player, MorphForm form) {
        for (MorphForm.Effect e : form.effects()) {
            MobEffectInstance current = player.getEffect(e.effect());
            if (current != null && current.isAmbient() && current.getDuration() <= EFFECT_DURATION) {
                player.removeEffect(e.effect());
            }
        }
    }

    /** Disguise: mobs of your new kind that were chasing you lose interest. */
    private static void calmKin(ServerPlayer player, MorphForm form) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0D),
                m -> m.getTarget() == player && form.isKin(m.getType()))) {
            mob.setTarget(null);
        }
    }

    // ------------------------------------------------------------------ syncing

    public static void sync(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new SyncMorphPacket(player.getId(), root(player).copy()));
    }

    public static void syncTo(ServerPlayer target, ServerPlayer viewer) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new SyncMorphPacket(target.getId(), root(target).copy()));
    }
}

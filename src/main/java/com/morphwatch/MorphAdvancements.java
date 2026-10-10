package com.morphwatch;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Grants the mod's achievements (data/morphwatch/advancements/*.json). */
public final class MorphAdvancements {
    public static final String FIRST_SCAN = "first_scan";
    public static final String ALL_SCANS = "all_scans";
    public static final String POWER_UP = "power_up";
    public static final String ALL_POWERS = "all_powers";
    public static final String FULLY_CHARGED = "fully_charged";
    public static final String GOLDEN_FORM = "golden_form";
    public static final String FIRST_UPGRADE = "diamond_watch";
    public static final String ALL_UPGRADES = "netherite_watch";

    private MorphAdvancements() {}

    public static void award(ServerPlayer player, String name) {
        Advancement advancement = player.server.getAdvancements()
                .getAdvancement(new ResourceLocation(MorphWatchMod.MODID, name));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "done");
        }
    }
}

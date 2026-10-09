package com.morphwatch;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class MorphWatchItem extends Item {
    private static final int USE_COOLDOWN = 10;
    /** The gems in this watch (MorphData.GOLD / DIAMOND / EMERALD bits), kept while it's off your wrist. */
    public static final String UPGRADES_TAG = "MorphUpgrades";

    public static final String STRAP_TAG = "StrapColor";
    public static final String STRAP_NAME_TAG = "StrapName";
    /** The strap starts red. */
    public static final int DEFAULT_STRAP = 0xB01E24;

    public static int strap(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(STRAP_TAG) ? stack.getTag().getInt(STRAP_TAG) : DEFAULT_STRAP;
    }

    public static void setStrap(ItemStack stack, int rgb, String name) {
        stack.getOrCreateTag().putInt(STRAP_TAG, rgb);
        if (name != null) stack.getOrCreateTag().putString(STRAP_NAME_TAG, name);
    }

    /** A watch with these gems and this strap colour. */
    public static ItemStack makeWatch(int bits, int strap, String strapName) {
        ItemStack stack = withUpgrades(bits);
        if (strap != DEFAULT_STRAP) setStrap(stack, strap, strapName);
        return stack;
    }

    public MorphWatchItem(Properties properties) {
        super(properties);
    }

    public static int upgrades(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(UPGRADES_TAG) : 0;
    }

    public static ItemStack withUpgrades(int bits) {
        ItemStack stack = new ItemStack(MorphWatchMod.MORPH_WATCH.get());
        if (bits != 0) stack.getOrCreateTag().putInt(UPGRADES_TAG, bits);
        return stack;
    }

    /** Upgraded watches shimmer. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return upgrades(stack) != 0 || super.isFoil(stack);
    }

    /** Right-click: strap the watch onto your wrist. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            WatchActions.putOn(serverPlayer, stack);
            player.getCooldowns().addCooldown(this, USE_COOLDOWN);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (stack.hasTag() && stack.getTag().contains(STRAP_NAME_TAG)) {
            String name = stack.getTag().getString(STRAP_NAME_TAG).replace('_', ' ');
            tooltip.add(Component.literal("Strap: " + name).withStyle(ChatFormatting.GRAY));
        }
        int bits = upgrades(stack);
        if (bits != 0) {
            tooltip.add(Component.literal("Upgrades:").withStyle(ChatFormatting.LIGHT_PURPLE));
            if ((bits & MorphData.GOLD) != 0)
                tooltip.add(Component.literal(" Gold: faster recharge").withStyle(ChatFormatting.GOLD));
            if ((bits & MorphData.DIAMOND) != 0)
                tooltip.add(Component.literal(" Diamond: stronger powers").withStyle(ChatFormatting.AQUA));
            if ((bits & MorphData.EMERALD) != 0)
                tooltip.add(Component.literal(" Emerald: 5 more hearts").withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.literal("Right-click: wear it on your wrist").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("While worn:").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(" V at a mob: scan it   , (comma): human").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" X: dial, scroll to pick, left-click: transform").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" B, N: super powers").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(" G: power (hold to charge)   H: power").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" J: take the watch off").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Upgrade: hold a gold ingot, diamond or emerald").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal(" and left-click while wearing the watch").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("Watch Workbench: dye the strap or add gems").withStyle(ChatFormatting.DARK_GREEN));
    }
}

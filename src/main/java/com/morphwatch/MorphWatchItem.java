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
    private final int tier;

    public MorphWatchItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    /** Right-click: strap the watch onto your wrist. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            WatchActions.putOn(serverPlayer, stack, tier);
            player.getCooldowns().addCooldown(this, USE_COOLDOWN);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (tier == 2) {
            tooltip.add(Component.literal("Diamond: faster recharge, stronger powers").withStyle(ChatFormatting.AQUA));
        } else if (tier == 3) {
            tooltip.add(Component.literal("Netherite: fastest recharge, strongest powers").withStyle(ChatFormatting.DARK_PURPLE));
        }
        tooltip.add(Component.literal("Right-click: wear it on your wrist").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("While worn:").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(" V at a mob: scan it   C: transform").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" X: dial (scroll to pick)   Sneak + V: human").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" R: power (hold to charge)   Z: second power").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" J: take the watch off").withStyle(ChatFormatting.GRAY));
    }
}

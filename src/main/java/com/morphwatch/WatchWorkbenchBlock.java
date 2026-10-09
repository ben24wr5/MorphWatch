package com.morphwatch;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Watch Workbench. Right-click it to open a crafting-table-style screen: put your Morph Watch
 * in with dyes to change the strap colour, or with a gold ingot, diamond or emerald to add that gem.
 */
public class WatchWorkbenchBlock extends Block {
    public WatchWorkbenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new WatchWorkbenchMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                Component.translatable("container.morphwatch.watch_workbench")));
        return InteractionResult.CONSUME;
    }

    private static Component line(ServerPlayer player, int bit, String name, String bonus, ChatFormatting colour) {
        boolean has = MorphData.hasUpgrade(player, bit);
        return Component.literal((has ? " [x] " : " [ ] ") + name + ": " + bonus + (has ? "" : " (missing)"))
                .withStyle(has ? colour : ChatFormatting.DARK_GRAY);
    }
}

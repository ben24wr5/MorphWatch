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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Watch Workbench. Right-click it while wearing the watch:
 *  - holding a gold ingot, diamond or emerald: puts it into the watch (same as left-clicking with it),
 *  - otherwise: shows which gems your watch has and which are still missing.
 * (Watch styles - faces, strap colours and so on - will be added here later.)
 */
public class WatchWorkbenchBlock extends Block {
    public WatchWorkbenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!MorphData.isWearing(sp)) {
            WatchActions.tell(sp, "Put on your Morph Watch, then use the workbench to upgrade it", ChatFormatting.YELLOW);
            return InteractionResult.CONSUME;
        }
        ItemStack held = sp.getMainHandItem();
        if (held.is(Items.GOLD_INGOT) || held.is(Items.DIAMOND) || held.is(Items.EMERALD)) {
            WatchActions.upgrade(sp);
            return InteractionResult.CONSUME;
        }
        level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
        sp.sendSystemMessage(Component.literal("Your Morph Watch:").withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(line(sp, MorphData.GOLD, "Gold ingot", "everything recharges faster", ChatFormatting.GOLD));
        sp.sendSystemMessage(line(sp, MorphData.DIAMOND, "Diamond", "stronger powers", ChatFormatting.AQUA));
        sp.sendSystemMessage(line(sp, MorphData.EMERALD, "Emerald", "5 more hearts", ChatFormatting.GREEN));
        sp.sendSystemMessage(Component.literal("Hold a missing one and right-click the workbench to add it")
                .withStyle(ChatFormatting.GRAY));
        return InteractionResult.CONSUME;
    }

    private static Component line(ServerPlayer player, int bit, String name, String bonus, ChatFormatting colour) {
        boolean has = MorphData.hasUpgrade(player, bit);
        return Component.literal((has ? " [x] " : " [ ] ") + name + ": " + bonus + (has ? "" : " (missing)"))
                .withStyle(has ? colour : ChatFormatting.DARK_GRAY);
    }
}

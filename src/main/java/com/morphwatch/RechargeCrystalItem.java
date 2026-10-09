package com.morphwatch;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

/** Right-click: every power (G, H, B, N) and the next transform are ready again at once. Used up. */
public class RechargeCrystalItem extends Item {
    public RechargeCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!MorphData.isWearing(player)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.literal("Wear the Morph Watch first (right-click it)")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            CompoundTag data = MorphData.root(sp);
            for (int slot = 1; slot <= 4; slot++) data.remove(MorphData.cdKey(slot));
            data.remove(Transformer.TRANSFORM_CD);
            MorphData.sync(sp);
            if (!sp.getAbilities().instabuild) stack.shrink(1);
            sp.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.15F), 1.5F),
                    sp.getX(), sp.getY() + 1.0, sp.getZ(), 40, 0.5, 0.8, 0.5, 0.0);
            sp.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, sp.getX(), sp.getY() + 1.0, sp.getZ(),
                    25, 0.4, 0.6, 0.4, 0.2);
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.4F);
            sp.displayClientMessage(Component.literal("Recharged! All your powers are ready").withStyle(ChatFormatting.RED), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click: all your watch powers are ready again").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("(G, H, B, N and the next transform). Used up.").withStyle(ChatFormatting.GRAY));
    }
}

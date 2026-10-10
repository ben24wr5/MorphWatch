package com.morphwatch;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Watch Workbench screen: a 3x3 grid like a crafting table. Put your Morph Watch in with
 * dyes to change the strap colour, and/or a gold ingot, diamond or emerald to add that gem.
 * The changed watch appears on the right.
 *
 * While you're wearing the watch you can leave it on: put just dyes and/or gems in the grid and
 * the watch on your wrist shows on the right; click it and the watch on your wrist changes.
 */
public class WatchWorkbenchMenu extends AbstractContainerMenu {
    private final CraftingContainer grid = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    /** True when the result is the watch on your wrist (no watch in the grid). */
    private boolean wornMode = false;
    private int pendingStrap = -1;
    private String pendingStrapName = null;
    private int pendingBits = 0;

    /** Client side. */
    public WatchWorkbenchMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public WatchWorkbenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(MorphWatchMod.WORKBENCH_MENU.get(), id);
        this.access = access;
        this.player = inventory.player;
        addSlot(new ResultSlot(124, 35));
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new Slot(grid, x + y * 3, 30 + x * 18, 17 + y * 18));
            }
        }
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new Slot(inventory, x + y * 9 + 9, 8 + x * 18, 84 + y * 18));
            }
        }
        for (int x = 0; x < 9; x++) {
            addSlot(new Slot(inventory, x, 8 + x * 18, 142));
        }
    }

    @Override
    public void slotsChanged(Container container) {
        if (container == grid) result.setItem(0, compute());
    }

    /** What the watch becomes with what's in the grid (empty if it isn't a valid change). */
    private ItemStack compute() {
        ItemStack watch = ItemStack.EMPTY;
        int watches = 0, dyes = 0, addBits = 0;
        float r = 0, g = 0, b = 0;
        String dyeName = null;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(MorphWatchMod.MORPH_WATCH.get())) {
                watches++;
                watch = s;
            } else if (s.getItem() instanceof DyeItem dye) {
                DyeColor colour = dye.getDyeColor();
                float[] c = colour.getTextureDiffuseColors();
                r += c[0];
                g += c[1];
                b += c[2];
                dyes++;
                dyeName = dyeName == null ? colour.getName() : "mixed";
            } else if (s.is(Items.GOLD_INGOT)) {
                addBits |= MorphData.GOLD;
            } else if (s.is(Items.DIAMOND)) {
                addBits |= MorphData.DIAMOND;
            } else if (s.is(Items.EMERALD)) {
                addBits |= MorphData.EMERALD;
            } else {
                return ItemStack.EMPTY;
            }
        }
        wornMode = false;
        if (watches == 0 && MorphData.isWearing(player)) {
            // Change the watch you're wearing
            int oldBits = MorphData.upgrades(player);
            if (dyes == 0 && (oldBits | addBits) == oldBits) return ItemStack.EMPTY;
            CompoundTag root = MorphData.root(player);
            pendingBits = oldBits | addBits;
            pendingStrap = dyes > 0 ? mix(r, g, b, dyes) : MorphData.strapColour(player);
            pendingStrapName = dyes > 0 ? dyeName : (root.contains(MorphData.STRAP_NAME) ? root.getString(MorphData.STRAP_NAME) : null);
            wornMode = true;
            ItemStack preview = MorphWatchItem.makeWatch(pendingBits, pendingStrap, pendingStrapName);
            preview.setHoverName(net.minecraft.network.chat.Component.literal("Your Morph Watch (on your wrist)"));
            return preview;
        }
        if (watches != 1) return ItemStack.EMPTY;
        int oldBits = MorphWatchItem.upgrades(watch);
        if (dyes == 0 && (oldBits | addBits) == oldBits) return ItemStack.EMPTY;
        ItemStack out = watch.copyWithCount(1);
        if (addBits != 0) out.getOrCreateTag().putInt(MorphWatchItem.UPGRADES_TAG, oldBits | addBits);
        if (dyes > 0) {
            MorphWatchItem.setStrap(out, mix(r, g, b, dyes), dyeName);
        }
        return out;
    }

    private static int mix(float r, float g, float b, int dyes) {
        return ((int) (r / dyes * 255) << 16) | ((int) (g / dyes * 255) << 8) | (int) (b / dyes * 255);
    }

    /** Clicking the result while it's the watch on your wrist: change that watch, keep nothing in hand. */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player clicker) {
        if (slotId == 0 && wornMode && !result.getItem(0).isEmpty()) {
            if (clicker instanceof ServerPlayer sp && MorphData.isWearing(sp)) {
                CompoundTag root = MorphData.root(sp);
                root.putInt(MorphData.STRAP, pendingStrap);
                if (pendingStrapName != null) root.putString(MorphData.STRAP_NAME, pendingStrapName);
                MorphData.setUpgrades(sp, pendingBits);
                MorphData.applyHealth(sp, MorphData.getForm(sp));
                MorphData.sync(sp);
            }
            for (int i = 0; i < grid.getContainerSize(); i++) {
                if (!grid.getItem(i).isEmpty()) grid.removeItem(i, 1);
            }
            access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE,
                    SoundSource.BLOCKS, 1.0F, 1.2F));
            slotsChanged(grid);
            broadcastChanges();
            return;
        }
        super.clicked(slotId, button, type, clicker);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index == 0) {
                if (!moveItemStackTo(stack, 10, 46, true)) return ItemStack.EMPTY;
                slot.onQuickCraft(stack, moved);
            } else if (index >= 10) {
                if (!moveItemStackTo(stack, 1, 10, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 10, 46, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            if (stack.getCount() == moved.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
        }
        return moved;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, grid));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, MorphWatchMod.WATCH_WORKBENCH.get());
    }

    /** Taking the changed watch uses up one of everything in the grid. */
    private class ResultSlot extends Slot {
        ResultSlot(int x, int y) {
            super(result, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            for (int i = 0; i < grid.getContainerSize(); i++) {
                if (!grid.getItem(i).isEmpty()) grid.removeItem(i, 1);
            }
            access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE,
                    SoundSource.BLOCKS, 1.0F, 1.2F));
            slotsChanged(grid);
            super.onTake(player, stack);
        }
    }
}

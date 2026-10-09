package com.morphwatch.client;

import com.morphwatch.Abilities;
import com.morphwatch.MorphData;
import com.morphwatch.MorphForm;
import com.morphwatch.MorphWatchMod;
import com.morphwatch.Transformer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Top-left watch panel: current mob, power recharge bars, charge bar. Plus the transform flash. */
public final class HudOverlay implements IGuiOverlay {
    public static final HudOverlay INSTANCE = new HudOverlay();

    private static final int GOLD = 0xFFE0B040;
    private static final int READY = 0xFF50E070;
    private static final int RECHARGE = 0xFFE05040;

    private HudOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // Transform flash
        if (ClientState.flashTicks > 0) {
            float a = (ClientState.flashTicks - partialTick) / ClientState.FLASH_TICKS;
            int alpha = Mth.clamp((int) (a * 200), 0, 255);
            g.fill(0, 0, screenWidth, screenHeight, (alpha << 24) | 0xFFFFFF);
        }

        if (!MorphData.isWearing(player) || mc.options.hideGui) return;
        Font font = mc.font;
        MorphForm form = MorphData.getForm(player);
        CompoundTag data = MorphData.root(player);
        long now = player.level().getGameTime();

        int x = 4, y = 4, w = 112, h = form == MorphForm.NONE ? 34 : 68;
        g.fill(x, y, x + w, y + h, 0x90101010);
        g.fill(x, y, x + w, y + 1, GOLD);
        g.fill(x, y + h - 1, x + w, y + h, GOLD);

        // Icon: the mob's spawn egg, or the watch when human
        ItemStack icon = com.morphwatch.MorphWatchItem.makeWatch(MorphData.upgrades(player), MorphData.strapColour(player), null);
        if (form != MorphForm.NONE) {
            SpawnEggItem egg = SpawnEggItem.byId(form.type());
            if (egg != null) icon = new ItemStack(egg);
        }
        g.renderItem(icon, x + 4, y + 5);

        boolean golden = MorphData.isGolden(player, form);
        String name = (golden ? "Golden " : "") + form.displayName().getString();
        g.drawString(font, name, x + 24, y + 5, golden ? GOLD : 0xFFFFFF);
        String line = "Watch  " + MorphData.unlockedCount(player) + "/" + MorphForm.mobs().size();
        g.drawString(font, line, x + 24, y + 15, 0xA0A0A0);
        // The gems in the watch
        int gx = x + 24 + font.width(line) + 4;
        int[][] gems = {{MorphData.GOLD, 0xFFFFD040}, {MorphData.DIAMOND, 0xFF60F0FF}, {MorphData.EMERALD, 0xFF40F070}};
        for (int[] gem : gems) {
            if (!MorphData.hasUpgrade(player, gem[0])) continue;
            g.fill(gx, y + 15, gx + 6, y + 21, 0xFF000000);
            g.fill(gx + 1, y + 16, gx + 5, y + 20, gem[1]);
            g.fill(gx + 1, y + 16, gx + 3, y + 18, 0xFFFFFFFF);
            gx += 8;
        }

        int barY = y + 28;
        if (form != MorphForm.NONE) {
            String[] keys = {"G", "H", "B", "N"};
            for (int slot = 1; slot <= 4; slot++) {
                bar(g, font, keys[slot - 1], x + 4, barY, w - 8, data.getLong(MorphData.cdKey(slot)),
                        data.getLong(MorphData.cdLenKey(slot)), now, slot >= 3);
                barY += 8;
            }
        }
        // Transform cooldown (the wait between transformations)
        bar(g, font, "X", x + 4, barY, w - 8, data.getLong(Transformer.TRANSFORM_CD),
                data.getLong(Transformer.TRANSFORM_CD_LEN), now, false);

        // Charge bar while holding G
        if (ClientState.charging && ClientState.chargeTicks > 3 && form != MorphForm.NONE) {
            float c = Math.min(1.0F, ClientState.chargeTicks / (float) Abilities.CHARGE_TICKS);
            int cw = 80, cx = screenWidth / 2 - cw / 2, cy = screenHeight / 2 + 12;
            g.fill(cx - 1, cy - 1, cx + cw + 1, cy + 5, 0xA0000000);
            g.fill(cx, cy, cx + (int) (cw * c), cy + 4, c >= 1.0F ? 0xFFFFD040 : 0xFFFFFFFF);
            if (c >= 1.0F) g.drawCenteredString(font, "CHARGED!", screenWidth / 2, cy + 7, GOLD);
        }
    }

    private static void bar(GuiGraphics g, Font font, String key, int x, int y, int w, long readyAt, long length, long now,
                            boolean isSuper) {
        g.drawString(font, key, x, y - 1, isSuper ? GOLD : 0xFFFFFF);
        int bx = x + 10, bw = w - 10;
        g.fill(bx, y, bx + bw, y + 5, 0xFF303030);
        if (now >= readyAt || length <= 0) {
            g.fill(bx, y, bx + bw, y + 5, isSuper ? (0xFF000000 | GOLD) : READY);
        } else {
            float done = 1.0F - (readyAt - now) / (float) length;
            g.fill(bx, y, bx + (int) (bw * Mth.clamp(done, 0.0F, 1.0F)), y + 5, RECHARGE);
        }
    }
}

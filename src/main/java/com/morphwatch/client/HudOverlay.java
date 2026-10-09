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

        int x = 4, y = 4, w = 112, h = form == MorphForm.NONE ? 34 : 52;
        g.fill(x, y, x + w, y + h, 0x90101010);
        g.fill(x, y, x + w, y + 1, GOLD);
        g.fill(x, y + h - 1, x + w, y + h, GOLD);

        // Icon: the mob's spawn egg, or the watch when human
        ItemStack icon = new ItemStack(MorphWatchMod.watchForTier(MorphData.tier(player)));
        if (form != MorphForm.NONE) {
            SpawnEggItem egg = SpawnEggItem.byId(form.type());
            if (egg != null) icon = new ItemStack(egg);
        }
        g.renderItem(icon, x + 4, y + 5);

        boolean golden = MorphData.isGolden(player, form);
        String name = (golden ? "Golden " : "") + form.displayName().getString();
        g.drawString(font, name, x + 24, y + 5, golden ? GOLD : 0xFFFFFF);
        g.drawString(font, MorphWatchMod.tierName(MorphData.tier(player)) + " watch  "
                + MorphData.unlockedCount(player) + "/" + MorphForm.mobs().size(), x + 24, y + 15, 0xA0A0A0);

        int barY = y + 28;
        if (form != MorphForm.NONE) {
            bar(g, font, "R", x + 4, barY, w - 8, data.getLong(MorphData.CD1), data.getLong(MorphData.CD1_LEN), now);
            bar(g, font, "Z", x + 4, barY + 8, w - 8, data.getLong(MorphData.CD2), data.getLong(MorphData.CD2_LEN), now);
            barY += 16;
        }
        // Transform cooldown (the wait between transformations)
        bar(g, font, "C", x + 4, barY, w - 8, data.getLong(Transformer.TRANSFORM_CD),
                data.getLong(Transformer.TRANSFORM_CD_LEN), now);

        // Charge bar while holding R
        if (ClientState.charging && ClientState.chargeTicks > 3 && form != MorphForm.NONE) {
            float c = Math.min(1.0F, ClientState.chargeTicks / (float) Abilities.CHARGE_TICKS);
            int cw = 80, cx = screenWidth / 2 - cw / 2, cy = screenHeight / 2 + 12;
            g.fill(cx - 1, cy - 1, cx + cw + 1, cy + 5, 0xA0000000);
            g.fill(cx, cy, cx + (int) (cw * c), cy + 4, c >= 1.0F ? 0xFFFFD040 : 0xFFFFFFFF);
            if (c >= 1.0F) g.drawCenteredString(font, "CHARGED!", screenWidth / 2, cy + 7, GOLD);
        }

        // The dial: which mob it's on, above the hotbar
        if (ClientState.dialOpen) {
            MorphForm shown = Dial.selected(player);
            if (shown == null) {
                String label = "<   ?   >";
                int ly = screenHeight - 72;
                String hint = "No mobs yet - look at a mob and press G to scan it";
                int lw = font.width(hint) + 16;
                g.fill(screenWidth / 2 - lw / 2, ly - 4, screenWidth / 2 + lw / 2, ly + 22, 0xA0101010);
                g.drawCenteredString(font, label, screenWidth / 2, ly, GOLD);
                g.drawCenteredString(font, hint, screenWidth / 2, ly + 11, 0xA0A0A0);
            } else {
                java.util.List<MorphForm> choices = Dial.choices(player);
                boolean gold = MorphData.isGolden(player, shown);
                String label = "<   " + (gold ? "Golden " : "") + shown.displayName().getString() + "   >";
                int ly = screenHeight - 72;
                int lw = font.width(label) + 16;
                g.fill(screenWidth / 2 - lw / 2, ly - 4, screenWidth / 2 + lw / 2, ly + 22, 0xA0101010);
                g.drawCenteredString(font, label, screenWidth / 2, ly, gold ? GOLD : 0x80E0FF);
                g.drawCenteredString(font, (choices.indexOf(shown) + 1) + "/" + choices.size()
                        + "   scroll to turn  -  C to slam", screenWidth / 2, ly + 11, 0xA0A0A0);
            }
        }
    }

    private static void bar(GuiGraphics g, Font font, String key, int x, int y, int w, long readyAt, long length, long now) {
        g.drawString(font, key, x, y - 1, 0xFFFFFF);
        int bx = x + 10, bw = w - 10;
        g.fill(bx, y, bx + bw, y + 5, 0xFF303030);
        if (now >= readyAt || length <= 0) {
            g.fill(bx, y, bx + bw, y + 5, READY);
        } else {
            float done = 1.0F - (readyAt - now) / (float) length;
            g.fill(bx, y, bx + (int) (bw * Mth.clamp(done, 0.0F, 1.0F)), y + 5, RECHARGE);
        }
    }
}

package com.morphwatch.client;

import com.morphwatch.MorphData;
import com.morphwatch.MorphForm;
import com.morphwatch.network.ModNetwork;
import com.morphwatch.network.WatchActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.player.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The watch's mob menu: every mob, unlocked ones in colour; click one to transform. */
public class MorphMenuScreen extends Screen {
    private static final int COLS = 4;
    private static final int TILE_W = 74;
    private static final int TILE_H = 70;
    private static final int GAP = 4;
    private static final int GOLD = 0xFFE0B040;

    private final Map<MorphForm, LivingEntity> models = new EnumMap<>(MorphForm.class);
    private int left;
    private int top;

    public MorphMenuScreen() {
        super(Component.literal("Morph Watch"));
    }

    @Override
    protected void init() {
        List<MorphForm> mobs = MorphForm.mobs();
        int rows = (mobs.size() + COLS - 1) / COLS;
        int gridW = COLS * TILE_W + (COLS - 1) * GAP;
        int gridH = rows * TILE_H + (rows - 1) * GAP;
        this.left = (this.width - gridW) / 2;
        this.top = Math.max(28, (this.height - gridH) / 2);
        int buttonY = Math.min(this.height - 24, this.top + gridH + 6);
        this.addRenderableWidget(Button.builder(Component.literal("Back to human"), b -> {
            ModNetwork.CHANNEL.sendToServer(new WatchActionPacket(WatchActionPacket.HUMAN, 0));
            this.onClose();
        }).bounds(this.width / 2 - 60, buttonY, 120, 20).build());
    }

    private LivingEntity model(MorphForm form) {
        return models.computeIfAbsent(form, f -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;
            LivingEntity e = f.type().create(mc.level);
            if (e instanceof Bat bat) bat.setResting(false);
            return e;
        });
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        g.drawCenteredString(this.font, "Morph Watch  -  " + MorphData.unlockedCount(player) + "/"
                + MorphForm.mobs().size() + " mobs scanned", this.width / 2, this.top - 18, GOLD);

        MorphForm current = MorphData.getForm(player);
        List<MorphForm> mobs = MorphForm.mobs();
        for (int i = 0; i < mobs.size(); i++) {
            MorphForm form = mobs.get(i);
            int x = tileX(i), y = tileY(i);
            boolean unlocked = MorphData.isUnlocked(player, form);
            boolean golden = MorphData.isGolden(player, form);
            boolean hover = mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H;

            int bg = !unlocked ? 0xC0202020 : hover ? 0xC0405060 : 0xC0303040;
            g.fill(x, y, x + TILE_W, y + TILE_H, bg);
            int border = form == current ? 0xFF50E070 : golden ? GOLD : 0xFF606060;
            outline(g, x, y, TILE_W, TILE_H, border);

            if (unlocked) {
                LivingEntity e = model(form);
                if (e != null) {
                    float size = Math.max(e.getBbHeight(), e.getBbWidth());
                    int scale = (int) Math.max(8, Math.min(30, 34 / size));
                    int cx = x + TILE_W / 2, feetY = y + TILE_H - 20;
                    InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feetY, scale,
                            (float) (cx - mouseX), (float) (feetY - 25 - mouseY), e);
                }
                g.drawCenteredString(this.font, form.displayName().getString(), x + TILE_W / 2, y + TILE_H - 18,
                        golden ? GOLD : 0xFFFFFF);
                String scans = golden ? "GOLDEN" : MorphData.scanCount(player, form) + "/" + MorphData.GOLDEN_SCANS;
                g.drawCenteredString(this.font, scans, x + TILE_W / 2, y + TILE_H - 9, golden ? GOLD : 0x909090);
            } else {
                g.drawCenteredString(this.font, "?", x + TILE_W / 2, y + TILE_H / 2 - 14, 0x707070);
                g.drawCenteredString(this.font, form.displayName().getString(), x + TILE_W / 2, y + TILE_H - 18, 0x707070);
                g.drawCenteredString(this.font, "scan to unlock", x + TILE_W / 2, y + TILE_H - 9, 0x606060);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Player player = Minecraft.getInstance().player;
        if (player != null && button == 0) {
            List<MorphForm> mobs = MorphForm.mobs();
            for (int i = 0; i < mobs.size(); i++) {
                int x = tileX(i), y = tileY(i);
                if (mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H) {
                    MorphForm form = mobs.get(i);
                    if (MorphData.isUnlocked(player, form)) {
                        ModNetwork.CHANNEL.sendToServer(new WatchActionPacket(WatchActionPacket.SELECT, form.ordinal()));
                        this.onClose();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int tileX(int i) {
        return left + (i % COLS) * (TILE_W + GAP);
    }

    private int tileY(int i) {
        return top + (i / COLS) * (TILE_H + GAP);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

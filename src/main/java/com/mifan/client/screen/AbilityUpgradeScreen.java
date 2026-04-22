package com.mifan.client.screen;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalyBookService.SpellSpec;
import com.mifan.anomaly.AnomalySpellRank;
import com.mifan.network.ModNetwork;
import com.mifan.network.serverbound.UpgradeAnomalySpellPacket;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AbilityUpgradeScreen extends Screen {
    private static final int PANEL_W = 400;
    private static final int PANEL_H = 300;
    private static final int ROW_HEIGHT = 26;
    private static final int BTN_W = 64;
    private static final int BTN_H = 20;

    private static final int RANK_B = 0xFF6CB4E4;
    private static final int RANK_A = 0xFFFFAA00;
    private static final int RANK_S = 0xFFFF4455;

    private final Screen parent;

    public AbilityUpgradeScreen(Screen parent) {
        super(Component.translatable("screen.corpse_campus.ability_upgrade.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("screen.corpse_campus.ability_upgrade.btn_close"),
                btn -> onClose())
                .pos(left + PANEL_W - 74, top + PANEL_H - 28)
                .size(64, 20)
                .build());
    }

    private List<Row> collectRows() {
        List<Row> rows = new ArrayList<>();
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return rows;
        }
        ItemStack book = AnomalyBookService.findBookForRead(player);
        if (book.isEmpty()) {
            return rows;
        }
        for (SpellSlot slot : ISpellContainer.getOrCreate(book).getActiveSpells()) {
            ResourceLocation id = slot.getSpell().getSpellResource();
            SpellSpec spec = AnomalyBookService.getSpellSpec(id);
            AnomalySpellRank rank = spec == null ? AnomalySpellRank.B : spec.rank();
            String name = spec == null ? slot.getSpell().getSpellName() : spec.zhName();
            rows.add(new Row(id, name, rank,
                    slot.getLevel(),
                    slot.getSpell().getMaxLevel(),
                    AnomalyBookService.getUpgradeXpCost(rank)));
        }
        return rows;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        drawPanel(g, left, top);
        drawHeader(g, left, top);

        Player player = Minecraft.getInstance().player;
        int xp = player == null ? 0 : player.experienceLevel;
        boolean creative = player != null && player.isCreative();

        List<Row> rows = collectRows();
        int rowTop = top + 54;
        if (rows.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("screen.corpse_campus.ability_upgrade.no_spell"),
                    left + PANEL_W / 2, top + PANEL_H / 2, 0x446688);
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowY = rowTop + i * ROW_HEIGHT;
            drawRow(g, row, left, rowY, mouseX, mouseY, xp, creative);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    private void drawPanel(GuiGraphics g, int left, int top) {
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H + 4, 0xA0020508);
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, 0xFF1A2B3A);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF00B1520);
        g.fill(left + 2, top + 2, left + PANEL_W - 2, top + PANEL_H - 2, 0xEE0F1C28);
        g.fill(left, top, left + PANEL_W, top + 38, 0xFF0D1E2E);
        g.fill(left, top + 38, left + PANEL_W, top + 39, 0xFF3DA7FF);
        g.fill(left, top + PANEL_H - 32, left + PANEL_W, top + PANEL_H, 0xFF0A1620);
    }

    private void drawHeader(GuiGraphics g, int left, int top) {
        g.drawString(font,
                Component.translatable("screen.corpse_campus.ability_upgrade.title"),
                left + 16, top + 9, 0xC8EEFF, false);
        g.drawString(font,
                Component.translatable("screen.corpse_campus.ability_upgrade.subtitle"),
                left + 16, top + 22, 0x5591BB, false);
        Player player = Minecraft.getInstance().player;
        int xp = player == null ? 0 : player.experienceLevel;
        String xpStr = Component.translatable("screen.corpse_campus.ability_upgrade.current_xp", xp).getString();
        g.drawString(font, xpStr, left + PANEL_W - font.width(xpStr) - 16, top + 9, 0xCCF0D070, false);
    }

    private void drawRow(GuiGraphics g, Row row, int left, int rowY,
            int mouseX, int mouseY, int xp, boolean creative) {
        g.fill(left + 10, rowY - 2, left + PANEL_W - 10, rowY + ROW_HEIGHT - 4, 0x18FFFFFF);

        ResourceLocation iconRL = ResourceLocation.fromNamespaceAndPath(
                row.spellId.getNamespace(),
                "textures/gui/spell_icons/" + row.spellId.getPath() + ".png");
        g.blit(iconRL, left + 16, rowY, 0, 0, 16, 16, 16, 16);

        int rankColor = switch (row.rank) {
            case S -> RANK_S;
            case A -> RANK_A;
            default -> RANK_B;
        };
        g.drawString(font, row.zhName, left + 38, rowY + 1, 0xBBDDFF, false);
        String badge = "[" + row.rank.name() + "]";
        g.drawString(font, badge, left + 38 + font.width(row.zhName) + 4, rowY + 1, rankColor, false);

        boolean atMax = row.currentLevel >= row.maxLevel;
        String lvlStr = atMax
                ? String.format(Locale.ROOT, "Lv.%d / %d", row.currentLevel, row.maxLevel)
                : String.format(Locale.ROOT, "Lv.%d → Lv.%d  (上限 %d)",
                        row.currentLevel, row.currentLevel + 1, row.maxLevel);
        g.drawString(font, lvlStr, left + 38, rowY + 12, 0x6688AA, false);

        String costStr;
        int costColor;
        if (atMax) {
            costStr = Component.translatable("screen.corpse_campus.ability_upgrade.max_reached").getString();
            costColor = 0xFF667788;
        } else {
            costStr = Component.translatable("screen.corpse_campus.ability_upgrade.cost", row.cost).getString();
            boolean ok = xp >= row.cost || creative;
            costColor = ok ? 0xFFDDDD77 : 0xFFAA4455;
        }
        int costX = left + PANEL_W - BTN_W - 14 - font.width(costStr);
        g.drawString(font, costStr, costX, rowY + 6, costColor, false);

        int btnX = left + PANEL_W - BTN_W - 12;
        int btnY = rowY - 2;
        boolean canClick = !atMax && (xp >= row.cost || creative);
        boolean hovered = mouseX >= btnX && mouseX < btnX + BTN_W
                && mouseY >= btnY && mouseY < btnY + BTN_H;
        drawButton(g, btnX, btnY, BTN_W, BTN_H,
                atMax ? Component.translatable("screen.corpse_campus.ability_upgrade.btn_max")
                      : Component.translatable("screen.corpse_campus.ability_upgrade.btn_upgrade"),
                canClick, hovered);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label,
            boolean enabled, boolean hovered) {
        int bg = !enabled ? 0xFF223040 : (hovered ? 0xFF3A7FB0 : 0xFF2A5080);
        int border = !enabled ? 0xFF334455 : 0xFF5FB0E0;
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        int textColor = enabled ? 0xFFE6F2FF : 0xFF556677;
        int tx = x + (w - font.width(label)) / 2;
        int ty = y + (h - 8) / 2;
        g.drawString(font, label, tx, ty, textColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Player player = Minecraft.getInstance().player;
            int xp = player == null ? 0 : player.experienceLevel;
            boolean creative = player != null && player.isCreative();

            List<Row> rows = collectRows();
            int left = (this.width - PANEL_W) / 2;
            int top = (this.height - PANEL_H) / 2;
            int rowTop = top + 54;

            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int rowY = rowTop + i * ROW_HEIGHT;
                int btnX = left + PANEL_W - BTN_W - 12;
                int btnY = rowY - 2;
                boolean inBtn = mouseX >= btnX && mouseX < btnX + BTN_W
                        && mouseY >= btnY && mouseY < btnY + BTN_H;
                if (inBtn) {
                    boolean atMax = row.currentLevel >= row.maxLevel;
                    boolean canClick = !atMax && (xp >= row.cost || creative);
                    if (canClick) {
                        ModNetwork.CHANNEL.sendToServer(new UpgradeAnomalySpellPacket(row.spellId));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Row(ResourceLocation spellId, String zhName, AnomalySpellRank rank,
                       int currentLevel, int maxLevel, int cost) {}
}

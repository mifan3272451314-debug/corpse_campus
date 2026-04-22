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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AbilityUpgradeScreen extends Screen {
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 320;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 36;
    private static final int LIST_PAD_X = 12;
    private static final int LIST_PAD_Y = 8;
    private static final int ROW_HEIGHT = 30;
    private static final int BTN_W = 72;
    private static final int BTN_H = 22;
    private static final int SCROLLBAR_W = 4;

    private static final int RANK_B = 0xFF6CB4E4;
    private static final int RANK_A = 0xFFFFAA00;
    private static final int RANK_S = 0xFFFF4455;

    private static final int COLOR_PANEL_GLOW = 0xA0020508;
    private static final int COLOR_PANEL_BORDER = 0xFF1A2B3A;
    private static final int COLOR_PANEL_BG = 0xF00B1520;
    private static final int COLOR_PANEL_INNER = 0xEE0F1C28;
    private static final int COLOR_HEADER = 0xFF0D1E2E;
    private static final int COLOR_NEON = 0xFF3DA7FF;
    private static final int COLOR_FOOTER = 0xFF0A1620;
    private static final int COLOR_ROW_BG = 0x18FFFFFF;
    private static final int COLOR_ROW_BG_HOVER = 0x30FFFFFF;
    private static final int COLOR_ROW_BG_CONFIRM = 0x40FFAA00;

    private final Screen parent;

    private int scrollOffset;
    private int maxScroll;

    private ResourceLocation pendingConfirmId;

    public AbilityUpgradeScreen(Screen parent) {
        super(Component.translatable("screen.corpse_campus.ability_upgrade.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 自绘返回按钮（不再用 vanilla Button）
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
            ResourceLocation schoolId = spec == null ? null : spec.schoolId();
            rows.add(new Row(id, name, rank, schoolId,
                    slot.getLevel(),
                    slot.getSpell().getMaxLevel(),
                    AnomalyBookService.getUpgradeXpCost(rank)));
        }
        // 排序：可升级 > 满级；组内按阶位 S→A→B、等级从低到高
        rows.sort((a, b) -> {
            boolean aMax = a.currentLevel >= a.maxLevel;
            boolean bMax = b.currentLevel >= b.maxLevel;
            if (aMax != bMax) {
                return aMax ? 1 : -1;
            }
            int rank = b.rank.ordinal() - a.rank.ordinal();
            if (rank != 0) {
                return rank;
            }
            return a.currentLevel - b.currentLevel;
        });
        return rows;
    }

    private int listInnerTop(int top) {
        return top + HEADER_H + LIST_PAD_Y;
    }

    private int listInnerBottom(int top) {
        return top + PANEL_H - FOOTER_H - LIST_PAD_Y;
    }

    private int visibleRowCount(int top) {
        return Math.max(1, (listInnerBottom(top) - listInnerTop(top)) / ROW_HEIGHT);
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
        boolean shift = hasShiftDown();

        List<Row> rows = collectRows();
        int visible = visibleRowCount(top);
        this.maxScroll = Math.max(0, rows.size() - visible);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll);

        // 若 pendingConfirm 对应的 spell 已不在列表（被移除），清空
        if (pendingConfirmId != null && rows.stream().noneMatch(r -> r.spellId.equals(pendingConfirmId))) {
            pendingConfirmId = null;
        }

        int innerTop = listInnerTop(top);
        int innerBottom = listInnerBottom(top);
        g.enableScissor(left + LIST_PAD_X, innerTop, left + PANEL_W - LIST_PAD_X - SCROLLBAR_W - 2, innerBottom);

        if (rows.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("screen.corpse_campus.ability_upgrade.no_spell"),
                    left + PANEL_W / 2, innerTop + (innerBottom - innerTop) / 2 - 4, 0xFF446688);
        }

        List<Component> pendingTooltip = null;
        int tooltipMouseX = mouseX;
        int tooltipMouseY = mouseY;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowY = innerTop + (i - scrollOffset) * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT <= innerTop || rowY >= innerBottom) {
                continue;
            }
            boolean rowHovered = mouseX >= left + LIST_PAD_X
                    && mouseX < left + PANEL_W - LIST_PAD_X - SCROLLBAR_W - 2
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
            drawRow(g, row, left, rowY, mouseX, mouseY, xp, creative, rowHovered, shift);
            if (rowHovered) {
                pendingTooltip = buildTooltip(row, xp, creative, shift);
            }
        }

        g.disableScissor();

        drawScrollbar(g, left, innerTop, innerBottom, rows.size(), visible);
        drawFooter(g, left, top, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partial);

        if (pendingTooltip != null && !pendingTooltip.isEmpty()) {
            g.renderComponentTooltip(font, pendingTooltip, tooltipMouseX, tooltipMouseY);
        }
    }

    private void drawPanel(GuiGraphics g, int left, int top) {
        g.fill(left - 6, top - 6, left + PANEL_W + 6, top + PANEL_H + 6, COLOR_PANEL_GLOW);
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, COLOR_PANEL_BORDER);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, COLOR_PANEL_BG);
        g.fill(left + 2, top + 2, left + PANEL_W - 2, top + PANEL_H - 2, COLOR_PANEL_INNER);
        // 头部
        g.fill(left, top, left + PANEL_W, top + HEADER_H - 2, COLOR_HEADER);
        g.fill(left, top + HEADER_H - 2, left + PANEL_W, top + HEADER_H - 1, COLOR_NEON);
        // 尾部
        g.fill(left, top + PANEL_H - FOOTER_H, left + PANEL_W, top + PANEL_H - FOOTER_H + 1, COLOR_NEON);
        g.fill(left, top + PANEL_H - FOOTER_H + 1, left + PANEL_W, top + PANEL_H, COLOR_FOOTER);
        // 角落四点装饰
        for (int[] c : new int[][]{{0, 0}, {PANEL_W - 6, 0}, {0, PANEL_H - 6}, {PANEL_W - 6, PANEL_H - 6}}) {
            g.fill(left + c[0], top + c[1], left + c[0] + 6, top + c[1] + 1, COLOR_NEON);
            g.fill(left + c[0], top + c[1], left + c[0] + 1, top + c[1] + 6, COLOR_NEON);
        }
    }

    private void drawHeader(GuiGraphics g, int left, int top) {
        g.drawString(font,
                Component.translatable("screen.corpse_campus.ability_upgrade.title"),
                left + 16, top + 10, 0xC8EEFF, false);
        g.drawString(font,
                Component.translatable("screen.corpse_campus.ability_upgrade.subtitle"),
                left + 16, top + 24, 0x5591BB, false);
        Player player = Minecraft.getInstance().player;
        int xp = player == null ? 0 : player.experienceLevel;
        Component xpComp = Component.translatable("screen.corpse_campus.ability_upgrade.current_xp", xp);
        int xpWidth = font.width(xpComp);
        // 徽章背景
        int badgeX = left + PANEL_W - xpWidth - 28;
        int badgeY = top + 12;
        g.fill(badgeX, badgeY, badgeX + xpWidth + 18, badgeY + 18, 0xFF132638);
        g.fill(badgeX, badgeY, badgeX + xpWidth + 18, badgeY + 1, COLOR_NEON);
        g.fill(badgeX, badgeY + 17, badgeX + xpWidth + 18, badgeY + 18, COLOR_NEON);
        g.drawString(font, xpComp, badgeX + 9, badgeY + 5, 0xFFF0D070, false);
    }

    private void drawFooter(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        int btnX = left + PANEL_W - 86;
        int btnY = top + PANEL_H - FOOTER_H + 7;
        int btnW = 72;
        int btnH = 22;
        boolean hovered = mouseX >= btnX && mouseX < btnX + btnW
                && mouseY >= btnY && mouseY < btnY + btnH;
        drawButton(g, btnX, btnY, btnW, btnH,
                Component.translatable("screen.corpse_campus.ability_upgrade.btn_close"),
                true, hovered, false);
    }

    private void drawRow(GuiGraphics g, Row row, int left, int rowY,
            int mouseX, int mouseY, int xp, boolean creative,
            boolean rowHovered, boolean shift) {
        boolean atMax = row.currentLevel >= row.maxLevel;
        boolean awaitingConfirm = row.spellId.equals(pendingConfirmId);
        int bg = awaitingConfirm ? COLOR_ROW_BG_CONFIRM : (rowHovered ? COLOR_ROW_BG_HOVER : COLOR_ROW_BG);
        g.fill(left + LIST_PAD_X, rowY, left + PANEL_W - LIST_PAD_X - SCROLLBAR_W - 2, rowY + ROW_HEIGHT - 3, bg);
        // 行左侧的阶位色条
        int rankColor = switch (row.rank) {
            case S -> RANK_S;
            case A -> RANK_A;
            default -> RANK_B;
        };
        g.fill(left + LIST_PAD_X, rowY, left + LIST_PAD_X + 2, rowY + ROW_HEIGHT - 3, rankColor);

        // 图标
        ResourceLocation iconRL = ResourceLocation.fromNamespaceAndPath(
                row.spellId.getNamespace(),
                "textures/gui/spell_icons/" + row.spellId.getPath() + ".png");
        g.blit(iconRL, left + LIST_PAD_X + 8, rowY + 4, 0, 0, 18, 18, 18, 18);

        // 名字 + 阶位徽章
        int textX = left + LIST_PAD_X + 34;
        g.drawString(font, row.zhName, textX, rowY + 4, 0xFFBBDDFF, false);
        String badge = "[" + row.rank.name() + "]";
        g.drawString(font, badge, textX + font.width(row.zhName) + 4, rowY + 4, rankColor, false);

        // 等级
        String lvlStr = atMax
                ? String.format(Locale.ROOT, "Lv.%d / %d", row.currentLevel, row.maxLevel)
                : String.format(Locale.ROOT, "Lv.%d → Lv.%d  (上限 %d)",
                        row.currentLevel, row.currentLevel + 1, row.maxLevel);
        g.drawString(font, lvlStr, textX, rowY + 16, 0xFF7090B0, false);

        // 消耗
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
        int costX = left + PANEL_W - BTN_W - SCROLLBAR_W - 18 - font.width(costStr);
        g.drawString(font, costStr, costX, rowY + 10, costColor, false);

        // 按钮
        int btnX = left + PANEL_W - BTN_W - SCROLLBAR_W - 14;
        int btnY = rowY + 3;
        boolean canClick = !atMax && (xp >= row.cost || creative);
        boolean hovered = mouseX >= btnX && mouseX < btnX + BTN_W
                && mouseY >= btnY && mouseY < btnY + BTN_H;
        Component label = atMax
                ? Component.translatable("screen.corpse_campus.ability_upgrade.btn_max")
                : awaitingConfirm
                    ? Component.translatable("screen.corpse_campus.ability_upgrade.btn_confirm")
                    : Component.translatable("screen.corpse_campus.ability_upgrade.btn_upgrade");
        drawButton(g, btnX, btnY, BTN_W, BTN_H, label, canClick, hovered, awaitingConfirm);
    }

    private void drawScrollbar(GuiGraphics g, int left, int innerTop, int innerBottom,
            int totalRows, int visibleRows) {
        int trackX = left + PANEL_W - LIST_PAD_X - SCROLLBAR_W;
        int trackTop = innerTop;
        int trackBottom = innerBottom;
        int trackH = trackBottom - trackTop;
        g.fill(trackX, trackTop, trackX + SCROLLBAR_W, trackBottom, 0x301A3250);
        if (totalRows <= visibleRows) {
            return;
        }
        int thumbH = Math.max(16, trackH * visibleRows / totalRows);
        int thumbY = trackTop + (trackH - thumbH) * scrollOffset / Math.max(1, maxScroll);
        g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, COLOR_NEON);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label,
            boolean enabled, boolean hovered, boolean confirm) {
        int bg;
        int border;
        if (confirm) {
            bg = hovered ? 0xFFC47A20 : 0xFFA86418;
            border = 0xFFFFC85F;
        } else if (!enabled) {
            bg = 0xFF223040;
            border = 0xFF334455;
        } else {
            bg = hovered ? 0xFF3A7FB0 : 0xFF2A5080;
            border = 0xFF5FB0E0;
        }
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        int textColor = (!enabled && !confirm) ? 0xFF556677 : 0xFFE6F2FF;
        int tx = x + (w - font.width(label)) / 2;
        int ty = y + (h - 8) / 2;
        g.drawString(font, label, tx, ty, textColor, false);
    }

    private List<Component> buildTooltip(Row row, int xp, boolean creative, boolean shift) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("spell." + row.spellId.getNamespace() + "." + row.spellId.getPath())
                .copy().withStyle(style -> style.withColor(0xBBDDFF)));
        if (row.schoolId != null) {
            lines.add(Component.translatable("screen.corpse_campus.ability_upgrade.tooltip.school",
                    Component.translatable("school." + row.schoolId.getNamespace() + "." + row.schoolId.getPath())));
        }
        lines.add(Component.translatable("screen.corpse_campus.ability_upgrade.tooltip.rank", row.rank.name()));
        lines.add(Component.translatable("screen.corpse_campus.ability_upgrade.tooltip.level",
                row.currentLevel, row.maxLevel));

        boolean atMax = row.currentLevel >= row.maxLevel;
        if (atMax) {
            lines.add(Component.translatable("screen.corpse_campus.ability_upgrade.tooltip.maxed"));
        } else if (!creative && xp < row.cost) {
            lines.add(Component.translatable("screen.corpse_campus.ability_upgrade.tooltip.not_enough",
                    row.cost - xp));
        } else {
            lines.add(Component.translatable("screen.corpse_campus.ability_upgrade.tooltip.cost", row.cost));
            boolean awaitingConfirm = row.spellId.equals(pendingConfirmId);
            if (awaitingConfirm) {
                lines.add(Component.translatable("screen.corpse_campus.ability_upgrade.tooltip.confirm_hint"));
            } else if (!shift) {
                lines.add(Component.translatable("screen.corpse_campus.ability_upgrade.tooltip.shift_hint"));
            }
        }
        return lines;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        // 返回按钮
        int backX = left + PANEL_W - 86;
        int backY = top + PANEL_H - FOOTER_H + 7;
        if (mouseX >= backX && mouseX < backX + 72 && mouseY >= backY && mouseY < backY + 22) {
            onClose();
            return true;
        }

        Player player = Minecraft.getInstance().player;
        int xp = player == null ? 0 : player.experienceLevel;
        boolean creative = player != null && player.isCreative();
        boolean shift = hasShiftDown();

        List<Row> rows = collectRows();
        int innerTop = listInnerTop(top);
        int innerBottom = listInnerBottom(top);
        boolean clickedAnyRow = false;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowY = innerTop + (i - scrollOffset) * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT <= innerTop || rowY >= innerBottom) {
                continue;
            }
            int btnX = left + PANEL_W - BTN_W - SCROLLBAR_W - 14;
            int btnY = rowY + 3;
            boolean inBtn = mouseX >= btnX && mouseX < btnX + BTN_W
                    && mouseY >= btnY && mouseY < btnY + BTN_H;
            if (inBtn) {
                clickedAnyRow = true;
                boolean atMax = row.currentLevel >= row.maxLevel;
                boolean canClick = !atMax && (xp >= row.cost || creative);
                if (!canClick) {
                    return true;
                }
                boolean awaitingConfirm = row.spellId.equals(pendingConfirmId);
                if (shift || awaitingConfirm) {
                    pendingConfirmId = null;
                    ModNetwork.CHANNEL.sendToServer(new UpgradeAnomalySpellPacket(row.spellId));
                } else {
                    pendingConfirmId = row.spellId;
                }
                return true;
            }
        }

        if (!clickedAnyRow) {
            pendingConfirmId = null;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
                       ResourceLocation schoolId,
                       int currentLevel, int maxLevel, int cost) {}
}

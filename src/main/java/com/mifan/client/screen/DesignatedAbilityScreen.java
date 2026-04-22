package com.mifan.client.screen;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalyBookService.SpellSpec;
import com.mifan.network.ModNetwork;
import com.mifan.network.serverbound.ConfigureDesignatedAbilityPacket;
import com.mifan.registry.ModSchools;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 指定异能配置 GUI（仅"指定异能"物品未配置态触发）。
 * 三栏：流派 / 法术（按流派过滤）/ 等级（按法术 MaxLevel 决定）。
 * 底部确定按钮发 {@link ConfigureDesignatedAbilityPacket}。
 */
public class DesignatedAbilityScreen extends Screen {
    private static final int PANEL_W = 460;
    private static final int PANEL_H = 320;
    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 36;
    private static final int COL_GAP = 8;
    private static final int LIST_PAD = 10;
    private static final int ROW_HEIGHT = 22;

    private static final int COLOR_PANEL_BORDER = 0xFF1A2B3A;
    private static final int COLOR_PANEL_BG = 0xF00B1520;
    private static final int COLOR_PANEL_INNER = 0xEE0F1C28;
    private static final int COLOR_HEADER = 0xFF0D1E2E;
    private static final int COLOR_NEON = 0xFF3DA7FF;
    private static final int COLOR_FOOTER = 0xFF0A1620;
    private static final int COLOR_ROW_BG = 0x18FFFFFF;
    private static final int COLOR_ROW_BG_HOVER = 0x30FFFFFF;
    private static final int COLOR_ROW_BG_SELECTED = 0x60FFAA00;
    private static final int RANK_B = 0xFF6CB4E4;
    private static final int RANK_A = 0xFFFFAA00;
    private static final int RANK_S = 0xFFFF4455;

    private static final List<ResourceLocation> SCHOOL_ORDER = List.of(
            ModSchools.XUJING_RESOURCE,
            ModSchools.RIZHAO_RESOURCE,
            ModSchools.DONGYUE_RESOURCE,
            ModSchools.YUZHE_RESOURCE,
            ModSchools.SHENGQI_RESOURCE);

    private final Map<ResourceLocation, List<SpellSpec>> spellsBySchool = new LinkedHashMap<>();

    private ResourceLocation selectedSchool;
    private ResourceLocation selectedSpell;
    private int selectedLevel = 1;

    private int spellScroll;
    private int levelScroll;

    public DesignatedAbilityScreen() {
        super(Component.translatable("screen.corpse_campus.designated_ability.title"));
        Collection<SpellSpec> all = AnomalyBookService.getAllSpellSpecs();
        for (ResourceLocation school : SCHOOL_ORDER) {
            spellsBySchool.put(school, new ArrayList<>());
        }
        for (SpellSpec spec : all) {
            spellsBySchool.computeIfAbsent(spec.schoolId(), k -> new ArrayList<>()).add(spec);
        }
        // 各流派内按 B → A → S 排序，便于查找
        for (List<SpellSpec> list : spellsBySchool.values()) {
            list.sort((a, b) -> {
                int r = a.rank().ordinal() - b.rank().ordinal();
                if (r != 0) return r;
                return a.zhName().compareTo(b.zhName());
            });
        }
    }

    private int colWidth() {
        return (PANEL_W - LIST_PAD * 2 - COL_GAP * 2) / 3;
    }

    private int listInnerTop(int top) {
        return top + HEADER_H + 24;
    }

    private int listInnerBottom(int top) {
        return top + PANEL_H - FOOTER_H - 6;
    }

    private int visibleRows(int top) {
        return Math.max(1, (listInnerBottom(top) - listInnerTop(top)) / ROW_HEIGHT);
    }

    private int maxLevelForSelected() {
        if (selectedSpell == null) {
            return 1;
        }
        AbstractSpell spell = AnomalyBookService.getRegisteredSpell(selectedSpell);
        return spell == null ? 1 : Math.max(1, spell.getMaxLevel());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        drawPanel(g, left, top);
        drawHeader(g, left, top);
        drawColumns(g, left, top, mouseX, mouseY);
        drawFooter(g, left, top, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partial);
    }

    private void drawPanel(GuiGraphics g, int left, int top) {
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, COLOR_PANEL_BORDER);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, COLOR_PANEL_BG);
        g.fill(left + 2, top + 2, left + PANEL_W - 2, top + PANEL_H - 2, COLOR_PANEL_INNER);
        g.fill(left, top, left + PANEL_W, top + HEADER_H - 2, COLOR_HEADER);
        g.fill(left, top + HEADER_H - 2, left + PANEL_W, top + HEADER_H - 1, COLOR_NEON);
        g.fill(left, top + PANEL_H - FOOTER_H, left + PANEL_W, top + PANEL_H - FOOTER_H + 1, COLOR_NEON);
        g.fill(left, top + PANEL_H - FOOTER_H + 1, left + PANEL_W, top + PANEL_H, COLOR_FOOTER);
    }

    private void drawHeader(GuiGraphics g, int left, int top) {
        g.drawString(font,
                Component.translatable("screen.corpse_campus.designated_ability.title"),
                left + 14, top + 10, 0xC8EEFF, false);
        g.drawString(font,
                Component.translatable("screen.corpse_campus.designated_ability.subtitle"),
                left + 14, top + 24, 0x5591BB, false);
    }

    private void drawColumns(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        int cw = colWidth();
        int innerTop = listInnerTop(top);
        int innerBottom = listInnerBottom(top);
        int visible = visibleRows(top);

        // 列 1：流派
        int col1X = left + LIST_PAD;
        g.drawString(font,
                Component.translatable("screen.corpse_campus.designated_ability.school_column"),
                col1X, innerTop - 14, 0xFFAACCEE, false);
        for (int i = 0; i < SCHOOL_ORDER.size(); i++) {
            ResourceLocation sid = SCHOOL_ORDER.get(i);
            int rowY = innerTop + i * ROW_HEIGHT;
            if (rowY >= innerBottom) break;
            boolean hovered = inRect(mouseX, mouseY, col1X, rowY, cw, ROW_HEIGHT - 2);
            boolean selected = sid.equals(selectedSchool);
            int bg = selected ? COLOR_ROW_BG_SELECTED : (hovered ? COLOR_ROW_BG_HOVER : COLOR_ROW_BG);
            g.fill(col1X, rowY, col1X + cw, rowY + ROW_HEIGHT - 2, bg);
            g.drawString(font,
                    Component.translatable("school." + sid.getNamespace() + "." + sid.getPath()),
                    col1X + 6, rowY + 7, 0xFFE6F2FF, false);
        }

        // 列 2：法术（按选中流派过滤）
        int col2X = col1X + cw + COL_GAP;
        g.drawString(font,
                Component.translatable("screen.corpse_campus.designated_ability.spell_column"),
                col2X, innerTop - 14, 0xFFAACCEE, false);
        if (selectedSchool != null) {
            List<SpellSpec> spells = spellsBySchool.getOrDefault(selectedSchool, List.of());
            int maxScroll = Math.max(0, spells.size() - visible);
            spellScroll = Mth.clamp(spellScroll, 0, maxScroll);
            for (int i = 0; i < spells.size(); i++) {
                int rowY = innerTop + (i - spellScroll) * ROW_HEIGHT;
                if (rowY < innerTop || rowY + ROW_HEIGHT > innerBottom + 2) continue;
                SpellSpec spec = spells.get(i);
                boolean hovered = inRect(mouseX, mouseY, col2X, rowY, cw, ROW_HEIGHT - 2);
                boolean selected = spec.spellId().equals(selectedSpell);
                int bg = selected ? COLOR_ROW_BG_SELECTED : (hovered ? COLOR_ROW_BG_HOVER : COLOR_ROW_BG);
                g.fill(col2X, rowY, col2X + cw, rowY + ROW_HEIGHT - 2, bg);
                int rankColor = switch (spec.rank()) {
                    case S -> RANK_S;
                    case A -> RANK_A;
                    default -> RANK_B;
                };
                g.fill(col2X, rowY, col2X + 2, rowY + ROW_HEIGHT - 2, rankColor);
                g.drawString(font, "[" + spec.rank().name() + "]",
                        col2X + 6, rowY + 7, rankColor, false);
                g.drawString(font, spec.zhName(),
                        col2X + 32, rowY + 7, 0xFFE6F2FF, false);
            }
        }

        // 列 3：等级
        int col3X = col2X + cw + COL_GAP;
        g.drawString(font,
                Component.translatable("screen.corpse_campus.designated_ability.level_column"),
                col3X, innerTop - 14, 0xFFAACCEE, false);
        if (selectedSpell != null) {
            int max = maxLevelForSelected();
            int maxScroll = Math.max(0, max - visible);
            levelScroll = Mth.clamp(levelScroll, 0, maxScroll);
            for (int lvl = 1; lvl <= max; lvl++) {
                int rowY = innerTop + (lvl - 1 - levelScroll) * ROW_HEIGHT;
                if (rowY < innerTop || rowY + ROW_HEIGHT > innerBottom + 2) continue;
                boolean hovered = inRect(mouseX, mouseY, col3X, rowY, cw, ROW_HEIGHT - 2);
                boolean selected = lvl == selectedLevel;
                int bg = selected ? COLOR_ROW_BG_SELECTED : (hovered ? COLOR_ROW_BG_HOVER : COLOR_ROW_BG);
                g.fill(col3X, rowY, col3X + cw, rowY + ROW_HEIGHT - 2, bg);
                g.drawString(font, String.format(Locale.ROOT, "Lv.%d / %d", lvl, max),
                        col3X + 6, rowY + 7, 0xFFE6F2FF, false);
            }
        }
    }

    private void drawFooter(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        int btnW = 90;
        int btnH = 22;
        int gap = 12;
        int totalW = btnW * 2 + gap;
        int btnY = top + PANEL_H - FOOTER_H + 7;
        int confirmX = left + (PANEL_W - totalW) / 2;
        int cancelX = confirmX + btnW + gap;

        boolean canConfirm = selectedSchool != null && selectedSpell != null && selectedLevel >= 1;
        boolean confirmHovered = inRect(mouseX, mouseY, confirmX, btnY, btnW, btnH);
        boolean cancelHovered = inRect(mouseX, mouseY, cancelX, btnY, btnW, btnH);

        drawButton(g, confirmX, btnY, btnW, btnH,
                Component.translatable("screen.corpse_campus.designated_ability.btn_confirm"),
                canConfirm, confirmHovered);
        drawButton(g, cancelX, btnY, btnW, btnH,
                Component.translatable("screen.corpse_campus.designated_ability.btn_cancel"),
                true, cancelHovered);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label,
            boolean enabled, boolean hovered) {
        int bg;
        int border;
        if (!enabled) {
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
        int textColor = enabled ? 0xFFE6F2FF : 0xFF556677;
        int tx = x + (w - font.width(label)) / 2;
        int ty = y + (h - 8) / 2;
        g.drawString(font, label, tx, ty, textColor, false);
    }

    private static boolean inRect(int x, int y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int cw = colWidth();
        int col2X = left + LIST_PAD + cw + COL_GAP;
        int col3X = col2X + cw + COL_GAP;
        int innerTop = listInnerTop(top);
        int innerBottom = listInnerBottom(top);
        int visible = visibleRows(top);

        if (mouseY >= innerTop && mouseY < innerBottom) {
            if (mouseX >= col2X && mouseX < col2X + cw && selectedSchool != null) {
                int total = spellsBySchool.getOrDefault(selectedSchool, List.of()).size();
                int maxScroll = Math.max(0, total - visible);
                spellScroll = Mth.clamp(spellScroll - (int) Math.signum(delta), 0, maxScroll);
                return true;
            }
            if (mouseX >= col3X && mouseX < col3X + cw && selectedSpell != null) {
                int total = maxLevelForSelected();
                int maxScroll = Math.max(0, total - visible);
                levelScroll = Mth.clamp(levelScroll - (int) Math.signum(delta), 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int cw = colWidth();
        int col1X = left + LIST_PAD;
        int col2X = col1X + cw + COL_GAP;
        int col3X = col2X + cw + COL_GAP;
        int innerTop = listInnerTop(top);
        int innerBottom = listInnerBottom(top);

        // 流派列
        for (int i = 0; i < SCHOOL_ORDER.size(); i++) {
            int rowY = innerTop + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT > innerBottom) break;
            if (inRect((int) mouseX, (int) mouseY, col1X, rowY, cw, ROW_HEIGHT - 2)) {
                ResourceLocation clicked = SCHOOL_ORDER.get(i);
                if (!clicked.equals(selectedSchool)) {
                    selectedSchool = clicked;
                    selectedSpell = null;
                    selectedLevel = 1;
                    spellScroll = 0;
                    levelScroll = 0;
                }
                return true;
            }
        }

        // 法术列
        if (selectedSchool != null) {
            List<SpellSpec> spells = spellsBySchool.getOrDefault(selectedSchool, List.of());
            for (int i = 0; i < spells.size(); i++) {
                int rowY = innerTop + (i - spellScroll) * ROW_HEIGHT;
                if (rowY < innerTop || rowY + ROW_HEIGHT > innerBottom + 2) continue;
                if (inRect((int) mouseX, (int) mouseY, col2X, rowY, cw, ROW_HEIGHT - 2)) {
                    SpellSpec spec = spells.get(i);
                    if (!spec.spellId().equals(selectedSpell)) {
                        selectedSpell = spec.spellId();
                        selectedLevel = 1;
                        levelScroll = 0;
                    }
                    return true;
                }
            }
        }

        // 等级列
        if (selectedSpell != null) {
            int max = maxLevelForSelected();
            for (int lvl = 1; lvl <= max; lvl++) {
                int rowY = innerTop + (lvl - 1 - levelScroll) * ROW_HEIGHT;
                if (rowY < innerTop || rowY + ROW_HEIGHT > innerBottom + 2) continue;
                if (inRect((int) mouseX, (int) mouseY, col3X, rowY, cw, ROW_HEIGHT - 2)) {
                    selectedLevel = lvl;
                    return true;
                }
            }
        }

        // 底部按钮
        int btnW = 90;
        int btnH = 22;
        int gap = 12;
        int totalW = btnW * 2 + gap;
        int btnY = top + PANEL_H - FOOTER_H + 7;
        int confirmX = left + (PANEL_W - totalW) / 2;
        int cancelX = confirmX + btnW + gap;

        if (inRect((int) mouseX, (int) mouseY, confirmX, btnY, btnW, btnH)) {
            if (selectedSchool != null && selectedSpell != null && selectedLevel >= 1) {
                ModNetwork.CHANNEL.sendToServer(
                        new ConfigureDesignatedAbilityPacket(selectedSpell, selectedLevel));
                onClose();
            }
            return true;
        }
        if (inRect((int) mouseX, (int) mouseY, cancelX, btnY, btnW, btnH)) {
            onClose();
            return true;
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
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

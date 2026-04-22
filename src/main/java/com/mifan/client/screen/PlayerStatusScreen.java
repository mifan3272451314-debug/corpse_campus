package com.mifan.client.screen;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalySpellRank;
import com.mifan.registry.ModItems;
import com.mifan.registry.ModSchools;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class PlayerStatusScreen extends Screen {

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 300;

    // Per-school accent colors (same order as ResourceLocation[] below)
    private static final int[] SCHOOL_TEXT_COLORS  = { 0xFF7ECFFF, 0xFFFFD07D, 0xFF8EE888, 0xFFD888F0, 0xFFFFADC8 };
    private static final int[] SCHOOL_BAR_COLORS   = { 0xFF3A9FCC, 0xFFCC9A00, 0xFF339933, 0xFF9933CC, 0xFFCC3377 };
    private static final int[] SCHOOL_DOT_COLORS   = { 0xCC3DA7E0, 0xCCAA7700, 0xCC229922, 0xCC8822BB, 0xBBCC2266 };

    private static final int RANK_B = 0xFF6CB4E4;
    private static final int RANK_A = 0xFFFFAA00;
    private static final int RANK_S = 0xFFFF4455;

    // Abilities scroll area layout constants
    private static final int ROW_HEIGHT = 22;
    private static final int ABILITY_COLS = 3;

    private static final int UPGRADE_BTN_W = 84;
    private static final int UPGRADE_BTN_H = 18;

    private int scrollOffset;
    private boolean draggingScrollbar;

    public PlayerStatusScreen() {
        super(Component.translatable("screen.corpse_campus.player_status.title"));
    }

    @Override
    protected void init() {
        // 升级按钮改为自绘（drawUpgradeButton），在 render 里根据主序列动态变色；
        // 点击路由在 mouseClicked 里处理。
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        int left = (this.width - PANEL_W) / 2;
        int top  = (this.height - PANEL_H) / 2;
        float tick = player.tickCount + partial;

        drawPanel(g, left, top, tick);

        ItemStack book = findAnomalyBook(player);
        List<SpellSlot> spells = collectSpells(book);

        drawHeader(g, player, left, top);
        drawIdentityBand(g, player, book, left + 14, top + 44, PANEL_W - 28);
        drawVitals(g, player, book, left + 14, top + 98, 170);
        drawSchools(g, book, left + 200, top + 98, 186);
        drawAbilities(g, spells, left + 14, top + 184, PANEL_W - 28, 92, mouseX, mouseY);

        g.drawString(font,
                Component.translatable("screen.corpse_campus.player_status.hint"),
                left + 14, top + PANEL_H - 14, 0x445577, false);

        drawUpgradeButton(g, book,
                left + PANEL_W - UPGRADE_BTN_W - 10,
                top + PANEL_H - UPGRADE_BTN_H - 3,
                mouseX, mouseY, tick);

        super.render(g, mouseX, mouseY, partial);
    }

    private void drawUpgradeButton(GuiGraphics g, ItemStack book, int x, int y,
            int mouseX, int mouseY, float tick) {
        boolean hovered = mouseX >= x && mouseX < x + UPGRADE_BTN_W
                && mouseY >= y && mouseY < y + UPGRADE_BTN_H;

        int accent = sequenceAccent(book);
        int accentRgb = accent & 0x00FFFFFF;
        float pulse = (float) (Math.sin(tick * 0.08) * 0.5 + 0.5);

        // 底色：深蓝 → 悬停时混入一点主序列色
        int bg = hovered
                ? mixColor(0xFF0D1E2E, accent, 0.28f)
                : 0xEE0A1828;
        g.fill(x, y, x + UPGRADE_BTN_W, y + UPGRADE_BTN_H, bg);
        // 头尾 neon 发光条（上下各 1px）：始终是主序列色，悬停时加粗一点
        int neonTop = accentRgb | 0xFF000000;
        int neonBot = accentRgb | (hovered ? 0xFF000000 : 0xCC000000);
        g.fill(x, y, x + UPGRADE_BTN_W, y + 1, neonTop);
        g.fill(x, y + UPGRADE_BTN_H - 1, x + UPGRADE_BTN_W, y + UPGRADE_BTN_H, neonBot);
        // 左右细边：更暗的主序列色
        int sideBorder = accentRgb | 0x80000000;
        g.fill(x, y, x + 1, y + UPGRADE_BTN_H, sideBorder);
        g.fill(x + UPGRADE_BTN_W - 1, y, x + UPGRADE_BTN_W, y + UPGRADE_BTN_H, sideBorder);
        // 四角 L 型高光（与面板角落括号风格呼应）
        int corner = accentRgb | 0xFF000000;
        drawCornerAngle(g, x, y, 4, corner, true, true);
        drawCornerAngle(g, x + UPGRADE_BTN_W - 1, y, 4, corner, false, true);
        drawCornerAngle(g, x, y + UPGRADE_BTN_H - 1, 4, corner, true, false);
        drawCornerAngle(g, x + UPGRADE_BTN_W - 1, y + UPGRADE_BTN_H - 1, 4, corner, false, false);
        // 悬停时叠加一道脉冲扫描线
        if (hovered) {
            int scanAlpha = 0x30 + (int) (pulse * 0x40);
            int scan = (accentRgb | (scanAlpha << 24));
            int scanY = y + 1 + (int) (pulse * (UPGRADE_BTN_H - 3));
            g.fill(x + 2, scanY, x + UPGRADE_BTN_W - 2, scanY + 1, scan);
        }

        Component label = Component.translatable("screen.corpse_campus.player_status.btn_upgrade");
        int labelColor = hovered ? 0xFFF6FBFF : (0xFFE0E0E0 & 0x00FFFFFF) | 0xFFE0E0E0;
        // 悬停时文字改成主序列色（亮化）
        if (hovered) {
            labelColor = brighten(accent, 0.35f);
        }
        int tx = x + (UPGRADE_BTN_W - font.width(label)) / 2;
        int ty = y + (UPGRADE_BTN_H - 8) / 2;
        g.drawString(font, label, tx, ty, labelColor, false);
    }

    private void drawCornerAngle(GuiGraphics g, int cx, int cy, int len, int color,
            boolean rightward, boolean downward) {
        int dx = rightward ? 1 : -1;
        int dy = downward ? 1 : -1;
        g.fill(Math.min(cx, cx + dx * len), cy, Math.max(cx + 1, cx + dx * len + 1), cy + 1, color);
        g.fill(cx, Math.min(cy, cy + dy * len), cx + 1, Math.max(cy + 1, cy + dy * len + 1), color);
    }

    private static int mixColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Mth.clamp((int) (ar + (br - ar) * t), 0, 255);
        int gg = Mth.clamp((int) (ag + (bg - ag) * t), 0, 255);
        int bbb = Mth.clamp((int) (ab + (bb - ab) * t), 0, 255);
        return (aa << 24) | (r << 16) | (gg << 8) | bbb;
    }

    private static int brighten(int color, float t) {
        int r = Mth.clamp((int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * t), 0, 255);
        int g = Mth.clamp((int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * t), 0, 255);
        int b = Mth.clamp((int) ((color & 0xFF) + (255 - (color & 0xFF)) * t), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ── Background panel ────────────────────────────────────────────────────

    private void drawPanel(GuiGraphics g, int left, int top, float tick) {
        float pulse = (float) (Math.sin(tick * 0.05) * 0.5 + 0.5);
        int accentAlpha = 0x70 + (int) (pulse * 0x50);

        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H + 4, 0xA0020508);
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, 0xFF1A2B3A);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF00B1520);
        g.fill(left + 2, top + 2, left + PANEL_W - 2, top + PANEL_H - 2, 0xEE0F1C28);

        // Header band
        g.fill(left, top, left + PANEL_W, top + 38, 0xFF0D1E2E);
        g.fill(left, top + 38, left + PANEL_W, top + 39, (accentAlpha << 24) | 0x3DA7FF);
        g.fill(left, top, left + PANEL_W, top + 1, 0xCC2A6090);

        // Divider lines
        g.fill(left + 10, top + 94, left + PANEL_W - 10, top + 95, 0x40243E58);
        g.fill(left + 10, top + 180, left + PANEL_W - 10, top + 181, 0x40243E58);
        g.fill(left + 196, top + 98, left + 197, top + 180, 0x30243E58);

        // Footer
        g.fill(left, top + PANEL_H - 22, left + PANEL_W, top + PANEL_H, 0xFF0A1620);
        g.fill(left, top + PANEL_H - 22, left + PANEL_W, top + PANEL_H - 21, 0x28243E58);

        // Corner brackets
        drawBracket(g, left + 6,            top + 6,             22, 22,  0xCC4ABCEE, true,  true);
        drawBracket(g, left + PANEL_W - 6,  top + 6,             22, 22,  0xCC4ABCEE, false, true);
        drawBracket(g, left + 6,            top + PANEL_H - 6,   22, 22,  0x803A9ABB, true,  false);
        drawBracket(g, left + PANEL_W - 6,  top + PANEL_H - 6,   22, 22,  0x803A9ABB, false, false);
    }

    private void drawBracket(GuiGraphics g, int x, int y, int wx, int wy, int color,
            boolean rightward, boolean downward) {
        int dx = rightward ? 1 : -1;
        int dy = downward  ? 1 : -1;
        g.fill(Math.min(x, x + dx * wx), y, Math.max(x, x + dx * wx), y + dy, color);
        g.fill(x, Math.min(y, y + dy * wy), x + dx, Math.max(y, y + dy * wy), color);
    }

    // ── Header ──────────────────────────────────────────────────────────────

    private void drawHeader(GuiGraphics g, Player player, int left, int top) {
        g.drawString(font,
                Component.translatable("screen.corpse_campus.player_status.title"),
                left + 16, top + 9, 0xC8EEFF, false);
        g.drawString(font,
                Component.translatable("screen.corpse_campus.player_status.subtitle"),
                left + 16, top + 22, 0x5591BB, false);

        String name = player.getName().getString();
        int nameX = left + PANEL_W - this.font.width(name) - 16;
        g.drawString(font, name, nameX, top + 9, 0x99C8E8, false);

        String idStr = "No." + String.format("%04d", Math.abs(player.getUUID().hashCode() % 10000));
        int idX = left + PANEL_W - this.font.width(idStr) - 16;
        g.drawString(font, idStr, idX, top + 22, 0x3A5D7A, false);
    }

    // ── Identity band: face + sequence/rank/loaded/awakened ─────────────────

    private void drawIdentityBand(GuiGraphics g, Player player, ItemStack book, int left, int top, int width) {
        // Face frame
        int faceSize = 44;
        int faceX = left;
        int faceY = top;

        int accent = sequenceAccent(book);
        // Outer glow
        g.fill(faceX - 2, faceY - 2, faceX + faceSize + 2, faceY + faceSize + 2, (accent & 0x00FFFFFF) | 0x60000000);
        g.fill(faceX - 1, faceY - 1, faceX + faceSize + 1, faceY + faceSize + 1, 0xFF0D1E2E);

        try {
            if (player instanceof AbstractClientPlayer acp) {
                ResourceLocation skin = acp.getSkinTextureLocation();
                PlayerFaceRenderer.draw(g, skin, faceX, faceY, faceSize);
            }
        } catch (Throwable ignored) {
            g.fill(faceX, faceY, faceX + faceSize, faceY + faceSize, 0xFF1A2B3A);
        }

        // Text column
        int tx = faceX + faceSize + 10;
        int ty = top + 1;

        boolean awakened = !book.isEmpty() && AnomalyBookService.isAwakened(book);
        ResourceLocation mainSeq = book.isEmpty() ? null : AnomalyBookService.getMainSequenceId(book);
        AnomalySpellRank rank = book.isEmpty() ? null : AnomalyBookService.getHighestRank(book);
        int loadedCount = book.isEmpty() ? 0 : ISpellContainer.getOrCreate(book).getActiveSpells().size();

        // Line 1: main sequence
        g.drawString(font, Component.translatable("screen.corpse_campus.player_status.main_sequence"),
                tx, ty, 0x80BBDD, false);
        Component seqText;
        int seqColor;
        if (awakened && mainSeq != null) {
            seqText = Component.translatable("school." + mainSeq.getNamespace() + "." + mainSeq.getPath());
            seqColor = schoolAccentColor(mainSeq);
        } else {
            seqText = Component.translatable("screen.corpse_campus.player_status.main_sequence_none");
            seqColor = 0xFF667788;
        }
        g.drawString(font, seqText, tx + 48, ty, seqColor, false);

        // Line 2: rank badge
        ty += 12;
        g.drawString(font, Component.translatable("screen.corpse_campus.player_status.rank"),
                tx, ty, 0x80BBDD, false);
        String badge = rank == null ? "[--]" : "[" + rank.name() + "]";
        int badgeColor = switch (rank == null ? "" : rank.name()) {
            case "S" -> RANK_S;
            case "A" -> RANK_A;
            case "B" -> RANK_B;
            default  -> 0xFF667788;
        };
        g.drawString(font, badge, tx + 48, ty, badgeColor, false);

        // Line 3: loaded count + progress bar
        ty += 12;
        g.drawString(font,
                Component.translatable("screen.corpse_campus.player_status.loaded_count", loadedCount),
                tx, ty, 0xBBDDFF, false);

        int barX = tx + 110;
        int barY = ty + 2;
        int barW = Math.max(40, width - (barX - left) - 80);
        g.fill(barX, barY, barX + barW, barY + 5, 0x35182838);
        int fill = Mth.clamp(Math.round(loadedCount / (float) AnomalyBookService.MAX_SPELL_SLOTS * barW),
                0, barW);
        if (fill > 0) {
            g.fill(barX, barY, barX + fill, barY + 5, accent | 0xCC000000);
        }

        // Line 4: awakened status
        ty += 14;
        g.drawString(font, Component.translatable("screen.corpse_campus.player_status.awakened"),
                tx, ty, 0x80BBDD, false);
        Component statusText = awakened
                ? Component.translatable("screen.corpse_campus.player_status.awakened_yes")
                : Component.translatable("screen.corpse_campus.player_status.awakened_no");
        int statusColor = awakened ? 0xFF66EE88 : 0xFFDD4455;
        g.drawString(font, statusText, tx + 48, ty, statusColor, false);
    }

    private int sequenceAccent(ItemStack book) {
        if (book.isEmpty() || !AnomalyBookService.isAwakened(book)) {
            return 0xFF3A9FDD;
        }
        ResourceLocation seq = AnomalyBookService.getMainSequenceId(book);
        return seq == null ? 0xFF3A9FDD : schoolAccentColor(seq);
    }

    private int schoolAccentColor(ResourceLocation schoolId) {
        String path = schoolId.getPath();
        return switch (path) {
            case "xujing"  -> SCHOOL_TEXT_COLORS[0];
            case "rizhao"  -> SCHOOL_TEXT_COLORS[1];
            case "dongyue" -> SCHOOL_TEXT_COLORS[2];
            case "yuzhe"   -> SCHOOL_TEXT_COLORS[3];
            case "shengqi" -> SCHOOL_TEXT_COLORS[4];
            default        -> 0xFF3A9FDD;
        };
    }

    // ── Vitals ──────────────────────────────────────────────────────────────

    private void drawVitals(GuiGraphics g, Player player, ItemStack book, int left, int top, int width) {
        drawSectionLabel(g, Component.translatable("screen.corpse_campus.player_status.section_core"), left, top);

        float hp    = player.getHealth();
        float maxHp = player.getMaxHealth();
        MagicData md = MagicData.getPlayerMagicData(player);
        float mana    = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        float curMana = md == null ? 0f : Math.min(mana, md.getMana());
        int manaBonus = book.isEmpty() ? 0 : AnomalyBookService.getStoredManaBonus(book);

        drawStatBar(g,
                Component.translatable("screen.corpse_campus.player_status.health"),
                String.format(Locale.ROOT, "%.0f / %.0f", hp, maxHp),
                left, top + 16, width,
                maxHp <= 0f ? 0f : Mth.clamp(hp / maxHp, 0f, 1f),
                0xBB250E1A, 0xFFD05080);

        drawStatBar(g,
                Component.translatable("screen.corpse_campus.player_status.mana"),
                String.format(Locale.ROOT, "%.0f / %.0f  +%d", curMana, mana, manaBonus),
                left, top + 46, width,
                mana <= 0f ? 0f : Mth.clamp(curMana / mana, 0f, 1f),
                0xBB0E1E38, 0xFF3A9FDD);
    }

    private void drawStatBar(GuiGraphics g, Component label, String value,
            int left, int top, int width, float progress, int bgColor, int fillColor) {
        g.drawString(font, label, left, top, 0x80BBDD, false);
        g.drawString(font, value, left + 2, top + 10, 0xCCE8FF, false);
        int bTop = top + 21;
        g.fill(left, bTop, left + width, bTop + 6, bgColor);
        int fillW = Math.max(2, (int) (width * progress));
        g.fill(left, bTop, left + fillW, bTop + 6, fillColor);
        if (progress > 0.02f && progress < 0.98f) {
            g.fill(left + fillW - 1, bTop, left + fillW + 1, bTop + 6,
                    (fillColor & 0x00FFFFFF) | 0xAA000000);
        }
        g.fill(left, bTop + 6, left + width, bTop + 7, 0x50182840);
    }

    // ── Schools ─────────────────────────────────────────────────────────────

    private void drawSchools(GuiGraphics g, ItemStack book, int left, int top, int width) {
        drawSectionLabel(g, Component.translatable("screen.corpse_campus.player_status.section_school"), left, top);

        ResourceLocation[] schools = {
            ModSchools.XUJING_RESOURCE,
            ModSchools.RIZHAO_RESOURCE,
            ModSchools.DONGYUE_RESOURCE,
            ModSchools.YUZHE_RESOURCE,
            ModSchools.SHENGQI_RESOURCE
        };

        for (int i = 0; i < schools.length; i++) {
            double bonus = book.isEmpty() ? 0.0 : AnomalyBookService.getStoredSchoolBonusPercent(book, schools[i]);
            String schoolName = Component.translatable(
                    "school." + schools[i].getNamespace() + "." + schools[i].getPath()).getString();
            int y = top + 16 + i * 16;
            boolean active = bonus > 0.0;

            int dotColor = active ? SCHOOL_DOT_COLORS[i] : 0x223A5060;
            g.fill(left, y + 4, left + 3, y + 7, dotColor);

            g.drawString(font, schoolName, left + 7, y, active ? SCHOOL_TEXT_COLORS[i] : 0x3A6080, false);

            String bonusStr = String.format(Locale.ROOT, "+%.0f%%", bonus);
            g.drawString(font, bonusStr,
                    left + width - this.font.width(bonusStr), y,
                    active ? 0xBBF0FFCC : 0x304A6070, false);

            int barY  = y + 10;
            int barW  = width - 2;
            g.fill(left, barY, left + barW, barY + 2, 0x35182838);
            int fill = Mth.clamp((int) Math.round(bonus * barW / 100.0), 0, barW);
            if (fill > 0) {
                g.fill(left, barY, left + fill, barY + 2, SCHOOL_BAR_COLORS[i] | 0xCC000000);
            }
        }
    }

    // ── Abilities with scrollbar ────────────────────────────────────────────

    private void drawAbilities(GuiGraphics g, List<SpellSlot> spells, int left, int top, int width, int height,
            int mouseX, int mouseY) {
        drawSectionLabel(g, Component.translatable("screen.corpse_campus.player_status.section_abilities"), left, top);

        int listTop   = top + 14;
        int listLeft  = left + 2;
        int listWidth = width - 14;
        int scrollbarX = left + width - 6;
        int visibleRows = Math.max(1, height / ROW_HEIGHT);
        int totalRows   = (spells.size() + ABILITY_COLS - 1) / ABILITY_COLS;
        int maxOff      = Math.max(0, totalRows - visibleRows);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxOff);

        g.fill(listLeft, listTop, listLeft + listWidth, listTop + height, 0x380C1A28);

        if (spells.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("screen.corpse_campus.player_status.no_ability"),
                    listLeft + listWidth / 2, listTop + height / 2 - 4, 0x446688);
        } else {
            int colW = (listWidth - 6) / ABILITY_COLS;
            for (int row = 0; row < visibleRows; row++) {
                for (int col = 0; col < ABILITY_COLS; col++) {
                    int idx = (scrollOffset + row) * ABILITY_COLS + col;
                    if (idx >= spells.size()) {
                        break;
                    }
                    SpellSlot slot = spells.get(idx);
                    drawAbilityCell(g, slot, listLeft + 2 + col * colW, listTop + 2 + row * ROW_HEIGHT,
                            colW - 2, row);
                }
            }
        }

        drawScrollbar(g, scrollbarX, listTop, height, totalRows, visibleRows);
    }

    private void drawAbilityCell(GuiGraphics g, SpellSlot slot, int x, int y, int w, int row) {
        if (row % 2 == 0) {
            g.fill(x - 2, y - 1, x + w, y + ROW_HEIGHT - 2, 0x18FFFFFF);
        }

        ResourceLocation spellRL = slot.getSpell().getSpellResource();
        var spec = AnomalyBookService.getSpellSpec(spellRL);
        String spellName;
        if (spec != null) {
            spellName = spec.zhName();
        } else {
            String key = "spell." + spellRL.getNamespace() + "." + spellRL.getPath();
            String translated = Component.translatable(key).getString();
            spellName = (!translated.equals(key) && !translated.isEmpty())
                    ? translated
                    : slot.getSpell().getSpellName();
        }
        String rankStr = spec == null ? "B" : spec.rank().name();

        ResourceLocation iconRL  = ResourceLocation.fromNamespaceAndPath(spellRL.getNamespace(),
                "textures/gui/spell_icons/" + spellRL.getPath() + ".png");
        int iconSize = 16;
        g.blit(iconRL, x, y, 0, 0, iconSize, iconSize, iconSize, iconSize);

        g.drawString(font, spellName, x + iconSize + 4, y + 1, 0xBBDDFF, false);
        String lvlStr = "Lv." + slot.getLevel();
        g.drawString(font, lvlStr, x + iconSize + 4, y + 10, 0x6688AA, false);

        int rankColor = switch (rankStr.toUpperCase(Locale.ROOT)) {
            case "S" -> RANK_S;
            case "A" -> RANK_A;
            default  -> RANK_B;
        };
        String badge = "[" + rankStr + "]";
        int badgeX = x + w - this.font.width(badge) - 2;
        g.drawString(font, badge, badgeX, y + 1, rankColor, false);
    }

    private void drawScrollbar(GuiGraphics g, int x, int top, int height, int totalRows, int visibleRows) {
        g.fill(x, top, x + 4, top + height, 0x551A2B3A);
        if (totalRows <= visibleRows) {
            return;
        }
        int thumbH = Math.max(10, height * visibleRows / totalRows);
        int maxOff = totalRows - visibleRows;
        int travel = height - thumbH;
        int thumbY = top + (maxOff == 0 ? 0 : travel * scrollOffset / maxOff);
        g.fill(x, thumbY, x + 4, thumbY + thumbH, 0xFF3DA7E0);
        g.fill(x, thumbY, x + 4, thumbY + 1, 0xFF7ECFFF);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void drawSectionLabel(GuiGraphics g, Component label, int left, int top) {
        g.fill(left, top + 1, left + 2, top + 8, 0xBB3A8AB0);
        g.drawString(font, label, left + 5, top, 0x77BBDD, false);
    }

    private List<SpellSlot> collectSpells(ItemStack book) {
        if (book.isEmpty()) {
            return List.of();
        }
        List<SpellSlot> list = new ArrayList<>(ISpellContainer.getOrCreate(book).getActiveSpells());
        list.sort(Comparator
                .comparingInt((SpellSlot s) -> {
                    var spec = AnomalyBookService.getSpellSpec(s.getSpell().getSpellResource());
                    if (spec == null) {
                        return 999;
                    }
                    return schoolOrder(spec.schoolId());
                })
                .thenComparingInt((SpellSlot s) -> {
                    var spec = AnomalyBookService.getSpellSpec(s.getSpell().getSpellResource());
                    return spec == null ? 9 : spec.rank().ordinal();
                })
                .thenComparing(s -> {
                    var spec = AnomalyBookService.getSpellSpec(s.getSpell().getSpellResource());
                    return spec == null ? "zzzz" : spec.zhName();
                }, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private int schoolOrder(ResourceLocation schoolId) {
        return switch (schoolId.getPath()) {
            case "xujing"  -> 0;
            case "rizhao"  -> 1;
            case "dongyue" -> 2;
            case "yuzhe"   -> 3;
            case "shengqi" -> 4;
            default        -> 9;
        };
    }

    private ItemStack findAnomalyBook(Player player) {
        Optional<ICurioStacksHandler> handler = CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inv -> inv.getStacksHandler("spellbook"));
        if (handler.isPresent() && handler.get().getStacks().getSlots() > 0) {
            ItemStack slotStack = handler.get().getStacks().getStackInSlot(0);
            if (slotStack.is(ModItems.ANOMALY_TRAIT_SPELLBOOK.get())) {
                return slotStack;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ANOMALY_TRAIT_SPELLBOOK.get())) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ModItems.ANOMALY_TRAIT_SPELLBOOK.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        ItemStack book = findAnomalyBook(player);
        int count = book.isEmpty() ? 0 : ISpellContainer.getOrCreate(book).getActiveSpells().size();
        int totalRows = (count + ABILITY_COLS - 1) / ABILITY_COLS;
        int visibleRows = Math.max(1, 92 / ROW_HEIGHT);
        int maxOff = Math.max(0, totalRows - visibleRows);
        if (maxOff <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int next = Mth.clamp(scrollOffset + (delta < 0 ? 1 : -1), 0, maxOff);
        if (next != scrollOffset) {
            scrollOffset = next;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = (this.width - PANEL_W) / 2;
            int top  = (this.height - PANEL_H) / 2;

            // 升级按钮（自绘）
            int btnX = left + PANEL_W - UPGRADE_BTN_W - 10;
            int btnY = top + PANEL_H - UPGRADE_BTN_H - 3;
            if (mouseX >= btnX && mouseX < btnX + UPGRADE_BTN_W
                    && mouseY >= btnY && mouseY < btnY + UPGRADE_BTN_H) {
                Minecraft.getInstance().setScreen(new AbilityUpgradeScreen(this));
                return true;
            }

            int scrollbarX = left + 14 + (PANEL_W - 28) - 6;
            int listTop = top + 184 + 14;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + 4
                    && mouseY >= listTop && mouseY <= listTop + 92) {
                draggingScrollbar = true;
                applyScrollbarDrag(mouseY, listTop, 92);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingScrollbar) {
            int top  = (this.height - PANEL_H) / 2;
            int listTop = top + 184 + 14;
            applyScrollbarDrag(mouseY, listTop, 92);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    private void applyScrollbarDrag(double mouseY, int listTop, int listHeight) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack book = findAnomalyBook(player);
        int count = book.isEmpty() ? 0 : ISpellContainer.getOrCreate(book).getActiveSpells().size();
        int totalRows = (count + ABILITY_COLS - 1) / ABILITY_COLS;
        int visibleRows = Math.max(1, listHeight / ROW_HEIGHT);
        int maxOff = Math.max(0, totalRows - visibleRows);
        if (maxOff <= 0) {
            scrollOffset = 0;
            return;
        }
        double rel = (mouseY - listTop) / (double) listHeight;
        rel = Mth.clamp(rel, 0.0, 1.0);
        scrollOffset = (int) Math.round(rel * maxOff);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

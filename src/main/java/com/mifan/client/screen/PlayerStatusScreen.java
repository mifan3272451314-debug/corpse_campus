package com.mifan.client.screen;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.registry.ModItems;
import com.mifan.registry.ModSchools;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
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
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class PlayerStatusScreen extends Screen {
    private int scrollOffset;

    private static final int PANEL_W = 364;
    private static final int PANEL_H = 272;

    // Per-school accent colors
    private static final int[] SCHOOL_TEXT_COLORS  = { 0xFF7ECFFF, 0xFFFFD07D, 0xFF8EE888, 0xFFD888F0, 0xFFFFADC8 };
    private static final int[] SCHOOL_BAR_COLORS   = { 0xFF3A9FCC, 0xFFCC9A00, 0xFF339933, 0xFF9933CC, 0xFFCC3377 };
    private static final int[] SCHOOL_DOT_COLORS   = { 0xCC3DA7E0, 0xCCAA7700, 0xCC229922, 0xCC8822BB, 0xBBCC2266 };

    private static final int RANK_B = 0xFF6CB4E4;
    private static final int RANK_A = 0xFFFFAA00;
    private static final int RANK_S = 0xFFFF4455;

    public PlayerStatusScreen() {
        super(Component.translatable("screen.corpse_campus.player_status.title"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Player player = Minecraft.getInstance().player;
        if (player == null) { super.render(g, mouseX, mouseY, partial); return; }

        int left = (this.width - PANEL_W) / 2;
        int top  = (this.height - PANEL_H) / 2;
        float tick = player.tickCount + partial;

        drawPanel(g, left, top, tick);

        ItemStack book = findAnomalyBook(player);
        List<SpellSlot> spells = collectSpells(book);

        drawHeader(g, player, left, top);
        drawVitals(g, player, book, left + 14, top + 58, 162);
        drawSchools(g, book, left + 192, top + 58, 156);
        drawAbilities(g, spells, left + 14, top + 172, PANEL_W - 28, 72);
        g.drawString(font,
                Component.translatable("screen.corpse_campus.player_status.hint"),
                left + 14, top + PANEL_H - 14, 0x445577, false);

        super.render(g, mouseX, mouseY, partial);
    }

    // ── Background ──────────────────────────────────────────────────────────

    private void drawPanel(GuiGraphics g, int left, int top, float tick) {
        float pulse = (float) (Math.sin(tick * 0.05) * 0.5 + 0.5);
        int accentAlpha = 0x70 + (int) (pulse * 0x50);

        // Drop shadow
        g.fill(left - 4, top - 4, left + PANEL_W + 4, top + PANEL_H + 4, 0xA0020508);
        // Outer border
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, 0xFF1A2B3A);
        // Main fill
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF00B1520);
        // Inner surface
        g.fill(left + 2, top + 2, left + PANEL_W - 2, top + PANEL_H - 2, 0xEE0F1C28);

        // ── Header bar ──────────────────────────────────────────────────
        g.fill(left, top, left + PANEL_W, top + 38, 0xFF0D1E2E);
        // Breathing accent stripe
        g.fill(left, top + 38, left + PANEL_W, top + 39, (accentAlpha << 24) | 0x3DA7FF);
        // Thin top chrome line
        g.fill(left, top, left + PANEL_W, top + 1, 0xCC2A6090);

        // ── Section dividers ────────────────────────────────────────────
        // Horizontal rule above abilities
        g.fill(left + 10, top + 168, left + PANEL_W - 10, top + 169, 0x40243E58);
        // Vertical rule between vitals and schools
        g.fill(left + 186, top + 54, left + 187, top + 168, 0x30243E58);
        // Footer bar
        g.fill(left, top + PANEL_H - 22, left + PANEL_W, top + PANEL_H, 0xFF0A1620);
        g.fill(left, top + PANEL_H - 22, left + PANEL_W, top + PANEL_H - 21, 0x28243E58);

        // ── Corner brackets ─────────────────────────────────────────────
        drawBracket(g, left + 6,            top + 6,             22, 22,  0xCC4ABCEE, true,  true);
        drawBracket(g, left + PANEL_W - 6,  top + 6,             22, 22,  0xCC4ABCEE, false, true);
        drawBracket(g, left + 6,            top + PANEL_H - 6,   22, 22,  0x803A9ABB, true,  false);
        drawBracket(g, left + PANEL_W - 6,  top + PANEL_H - 6,   22, 22,  0x803A9ABB, false, false);

        // Small corner dots
        g.fill(left + 7,            top + 7,            left + 10,           top + 10,           0x774ABCEE);
        g.fill(left + PANEL_W - 10, top + 7,            left + PANEL_W - 7,  top + 10,           0x774ABCEE);

        // Subtle grid in abilities area
        for (int gx = left + 14; gx < left + PANEL_W - 14; gx += 44) {
            g.fill(gx, top + 172, gx + 1, top + 244, 0x12243E55);
        }
    }

    private void drawBracket(GuiGraphics g, int x, int y, int wx, int wy, int color,
            boolean rightward, boolean downward) {
        int dx = rightward ? 1 : -1;
        int dy = downward  ? 1 : -1;
        g.fill(Math.min(x, x + dx * wx), y, Math.max(x, x + dx * wx), y + dy, color);
        g.fill(x, Math.min(y, y + dy * wy), x + dx, Math.max(y, y + dy * wy), color);
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private void drawHeader(GuiGraphics g, Player player, int left, int top) {
        // Main title
        g.drawString(font,
                Component.translatable("screen.corpse_campus.player_status.title"),
                left + 16, top + 9, 0xC8EEFF, false);
        // Subtitle
        g.drawString(font,
                Component.translatable("screen.corpse_campus.player_status.subtitle"),
                left + 16, top + 22, 0x5591BB, false);

        // Player name (right-aligned)
        String name = player.getName().getString();
        int nameX = left + PANEL_W - this.font.width(name) - 16;
        g.drawString(font, name, nameX, top + 9, 0x99C8E8, false);

        // Unique ID tag based on UUID
        String idStr = "No." + String.format("%04d", Math.abs(player.getUUID().hashCode() % 10000));
        int idX = left + PANEL_W - this.font.width(idStr) - 16;
        g.drawString(font, idStr, idX, top + 22, 0x3A5D7A, false);
    }

    // ── Vitals ───────────────────────────────────────────────────────────────

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
                left, top + 49, width,
                mana <= 0f ? 0f : Mth.clamp(curMana / mana, 0f, 1f),
                0xBB0E1E38, 0xFF3A9FDD);

        // Current anomaly label + box
        g.drawString(font,
                Component.translatable("screen.corpse_campus.player_status.anomaly"),
                left, top + 83, 0x6699CC, false);
        g.fill(left, top + 94, left + width, top + 106 + 12, 0x380D1E30);
        String abilityText = book.isEmpty()
                ? Component.translatable("screen.corpse_campus.player_status.no_ability").getString()
                : activeAbilitySummary(book);
        g.drawWordWrap(font, Component.literal(abilityText), left + 4, top + 97, width - 8, 0xBBD8F0FF);
    }

    private void drawStatBar(GuiGraphics g, Component label, String value,
            int left, int top, int width, float progress, int bgColor, int fillColor) {
        g.drawString(font, label, left, top, 0x80BBDD, false);
        g.drawString(font, value, left + 2, top + 10, 0xCCE8FF, false);
        int bTop = top + 21;
        g.fill(left, bTop, left + width, bTop + 6, bgColor);
        int fillW = Math.max(2, (int) (width * progress));
        g.fill(left, bTop, left + fillW, bTop + 6, fillColor);
        // Bright leading edge
        if (progress > 0.02f && progress < 0.98f) {
            g.fill(left + fillW - 1, bTop, left + fillW + 1, bTop + 6,
                    (fillColor & 0x00FFFFFF) | 0xAA000000);
        }
        g.fill(left, bTop + 6, left + width, bTop + 7, 0x50182840);
    }

    // ── Schools ──────────────────────────────────────────────────────────────

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
            int y = top + 16 + i * 22;
            boolean active = bonus > 0.0;

            // Dot indicator
            int dotColor = active ? SCHOOL_DOT_COLORS[i] : 0x223A5060;
            g.fill(left, y + 4, left + 3, y + 7, dotColor);

            // School name
            g.drawString(font, schoolName, left + 7, y, active ? SCHOOL_TEXT_COLORS[i] : 0x3A6080, false);

            // Bonus value
            String bonusStr = String.format(Locale.ROOT, "+%.0f%%", bonus);
            g.drawString(font, bonusStr,
                    left + width - this.font.width(bonusStr), y,
                    active ? 0xBBF0FFCC : 0x304A6070, false);

            // Progress bar (cap display at 100 for visual)
            int barY  = y + 12;
            int barW  = width - 2;
            g.fill(left, barY, left + barW, barY + 3, 0x35182838);
            int fill = Mth.clamp((int) Math.round(bonus * barW / 100.0), 0, barW);
            if (fill > 0) {
                g.fill(left, barY, left + fill, barY + 3, SCHOOL_BAR_COLORS[i] | 0xCC000000);
            }
        }
    }

    // ── Abilities ────────────────────────────────────────────────────────────

    private void drawAbilities(GuiGraphics g, List<SpellSlot> spells, int left, int top, int width, int height) {
        drawSectionLabel(g, Component.translatable("screen.corpse_campus.player_status.section_abilities"), left, top);

        int listTop  = top + 16;
        int iconSize = 16;
        int colW     = (width - 6) / 2;
        int rowH     = iconSize + 6;
        int visRows  = Math.max(1, height / rowH);
        int maxOff   = Math.max(0, (spells.size() + 1) / 2 - visRows);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxOff);

        g.fill(left, listTop, left + width, listTop + height, 0x380C1A28);

        if (spells.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("screen.corpse_campus.player_status.no_ability"),
                    left + width / 2, listTop + height / 2 - 4, 0x446688);
            return;
        }

        for (int row = 0; row < visRows; row++) {
            for (int col = 0; col < 2; col++) {
                int idx = (scrollOffset + row) * 2 + col;
                if (idx >= spells.size()) break;

                SpellSlot slot = spells.get(idx);
                var spec = AnomalyBookService.getSpellSpec(slot.getSpell().getSpellResource());
                String spellName = spec == null ? slot.getSpell().getSpellName() : spec.zhName();
                String rankStr   = spec == null ? "B" : spec.rank().name();

                int cellX = left + 4 + col * (colW + 2);
                int cellY = listTop + 4 + row * rowH;

                // Row highlight on even rows for readability
                if ((scrollOffset + row) % 2 == 0) {
                    g.fill(cellX - 2, cellY - 1, cellX + colW, cellY + rowH - 2, 0x18FFFFFF);
                }

                // Spell icon
                ResourceLocation spellRL = slot.getSpell().getSpellResource();
                ResourceLocation iconRL  = new ResourceLocation(spellRL.getNamespace(),
                        "textures/gui/spell_icons/" + spellRL.getPath() + ".png");
                g.blit(iconRL, cellX, cellY, 0, 0, iconSize, iconSize, iconSize, iconSize);

                // Name
                g.drawString(font, spellName, cellX + iconSize + 4, cellY + 1, 0xBBDDFF, false);

                // Level text
                String lvlStr = "Lv." + slot.getLevel();
                g.drawString(font, lvlStr, cellX + iconSize + 4, cellY + 9, 0x6688AA, false);

                // Rank badge (right-aligned within column)
                int rankColor = switch (rankStr.toUpperCase(Locale.ROOT)) {
                    case "S" -> RANK_S;
                    case "A" -> RANK_A;
                    default  -> RANK_B;
                };
                String badge = "[" + rankStr + "]";
                int badgeX = cellX + colW - this.font.width(badge) - 2;
                g.drawString(font, badge, badgeX, cellY + 1, rankColor, false);
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void drawSectionLabel(GuiGraphics g, Component label, int left, int top) {
        // Accent mark
        g.fill(left, top + 1, left + 2, top + 8, 0xBB3A8AB0);
        g.drawString(font, label, left + 5, top, 0x77BBDD, false);
    }

    private List<SpellSlot> collectSpells(ItemStack book) {
        if (book.isEmpty()) return List.of();
        return ISpellContainer.getOrCreate(book).getActiveSpells().stream()
                .sorted(Comparator.comparing(slot -> {
                    var spec = AnomalyBookService.getSpellSpec(slot.getSpell().getSpellResource());
                    return spec == null ? "zzzz" : spec.zhName();
                }, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String activeAbilitySummary(ItemStack book) {
        List<String> names = new ArrayList<>();
        for (SpellSlot slot : ISpellContainer.getOrCreate(book).getActiveSpells()) {
            var spec = AnomalyBookService.getSpellSpec(slot.getSpell().getSpellResource());
            names.add(spec == null ? slot.getSpell().getSpellName() : spec.zhName());
            if (names.size() >= 3) break;
        }
        if (names.isEmpty()) return Component.translatable("screen.corpse_campus.player_status.no_ability").getString();
        return String.join(" / ", names);
    }

    private ItemStack findAnomalyBook(Player player) {
        Optional<ICurioStacksHandler> handler = CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inv -> inv.getStacksHandler("spellbook"));
        if (handler.isPresent() && handler.get().getStacks().getSlots() > 0) {
            ItemStack slotStack = handler.get().getStacks().getStackInSlot(0);
            if (slotStack.is(ModItems.ANOMALY_TRAIT_SPELLBOOK.get())) return slotStack;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ANOMALY_TRAIT_SPELLBOOK.get())) return stack;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ModItems.ANOMALY_TRAIT_SPELLBOOK.get())) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return super.mouseScrolled(mouseX, mouseY, delta);

        ItemStack book = findAnomalyBook(player);
        int count = book.isEmpty() ? 0 : ISpellContainer.getOrCreate(book).getActiveSpells().size();
        int visRows = Math.max(1, 72 / (16 + 6));
        int maxOff  = Math.max(0, (count + 1) / 2 - visRows);
        if (maxOff <= 0) return super.mouseScrolled(mouseX, mouseY, delta);

        int next = Mth.clamp(scrollOffset + (delta < 0 ? 1 : -1), 0, maxOff);
        if (next != scrollOffset) { scrollOffset = next; return true; }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

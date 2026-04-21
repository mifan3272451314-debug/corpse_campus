package com.mifan.client.screen;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.registry.ModItems;
import com.mifan.registry.ModSchools;
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

    public PlayerStatusScreen() {
        super(Component.translatable("screen.corpse_campus.player_status.title"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        int panelWidth = 320;
        int panelHeight = 236;
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;

        drawTechBackground(guiGraphics, left, top, panelWidth, panelHeight, player.tickCount + partialTick);

        ItemStack anomalyBook = findAnomalyBook(player);
        List<SpellSlot> activeSpells = anomalyBook.isEmpty()
                ? List.of()
                : ISpellContainer.getOrCreate(anomalyBook).getActiveSpells().stream()
                        .sorted(Comparator.comparing(slot -> {
                            var spec = AnomalyBookService.getSpellSpec(slot.getSpell().getSpellResource());
                            return spec == null ? "zzzz" : spec.zhName();
                        }, String.CASE_INSENSITIVE_ORDER))
                        .toList();

        guiGraphics.drawString(this.font,
                Component.translatable("screen.corpse_campus.player_status.title"),
                left + 18,
                top + 14,
                0xCFF6FF,
                false);
        guiGraphics.drawString(this.font,
                Component.translatable("screen.corpse_campus.player_status.subtitle"),
                left + 18,
                top + 32,
                0x7BCBFF,
                false);

        drawVitals(guiGraphics, player, anomalyBook, left + 18, top + 58, 126);
        drawSchoolBonuses(guiGraphics, anomalyBook, left + 168, top + 58, 134);
        drawAbilities(guiGraphics, activeSpells, left + 18, top + 152, panelWidth - 36, 60);

        guiGraphics.drawString(this.font,
                Component.translatable("screen.corpse_campus.player_status.hint"),
                left + 18,
                top + panelHeight - 16,
                0x73A9C7,
                false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawTechBackground(GuiGraphics guiGraphics, int left, int top, int width, int height, float tick) {
        int pulse = 18 + (int) ((Math.sin(tick * 0.08F) + 1.0F) * 8.0F);
        guiGraphics.fill(left - 2, top - 2, left + width + 2, top + height + 2, 0xD0101824);
        guiGraphics.fill(left, top, left + width, top + height, 0xE10A1320);
        guiGraphics.fill(left + 4, top + 4, left + width - 4, top + height - 4, 0xD4132233);
        guiGraphics.fill(left, top, left + width, top + 4, ((60 + pulse) << 24) | 0x3DA7FF);
        guiGraphics.fill(left, top + height - 4, left + width, top + height, ((28 + pulse / 2) << 24) | 0x163A54);

        for (int i = 0; i < 8; i++) {
            int x = left + 14 + i * 38;
            guiGraphics.fill(x, top + 10, x + 1, top + height - 10, 0x302A5276);
        }
        for (int i = 0; i < 6; i++) {
            int y = top + 18 + i * 32;
            guiGraphics.fill(left + 10, y, left + width - 10, y + 1, 0x242A5276);
        }

        drawBracket(guiGraphics, left + 8, top + 8, 18, 18, 0xB04FC7FF);
        drawBracket(guiGraphics, left + width - 8, top + 8, -18, 18, 0xB04FC7FF);
        drawBracket(guiGraphics, left + 8, top + height - 8, 18, -18, 0x8039A5DB);
        drawBracket(guiGraphics, left + width - 8, top + height - 8, -18, -18, 0x8039A5DB);
    }

    private void drawBracket(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(Math.min(x, x + width), y, Math.max(x, x + width), y + Integer.signum(height), color);
        guiGraphics.fill(x, Math.min(y, y + height), x + Integer.signum(width), Math.max(y, y + height), color);
    }

    private void drawVitals(GuiGraphics guiGraphics, Player player, ItemStack book, int left, int top, int width) {
        guiGraphics.drawString(this.font,
                Component.translatable("screen.corpse_campus.player_status.section_core"),
                left,
                top,
                0x94E5FF,
                false);

        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float mana = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        float currentMana = Math.min(mana, player.getPersistentData().getFloat("irons_spellbooks_mana"));
        int anomalyManaBonus = book.isEmpty() ? 0 : AnomalyBookService.getStoredManaBonus(book);

        drawLabeledBar(guiGraphics, Component.translatable("screen.corpse_campus.player_status.health"),
                String.format(Locale.ROOT, "%.1f / %.1f", health, maxHealth),
                left,
                top + 18,
                width,
                maxHealth <= 0.0F ? 0.0F : Mth.clamp(health / maxHealth, 0.0F, 1.0F),
                0xD64F7B90,
                0xFF86E3FF);
        drawLabeledBar(guiGraphics, Component.translatable("screen.corpse_campus.player_status.mana"),
                String.format(Locale.ROOT, "%.0f / %.0f  (+%d)", currentMana, mana, anomalyManaBonus),
                left,
                top + 52,
                width,
                mana <= 0.0F ? 0.0F : Mth.clamp(currentMana / mana, 0.0F, 1.0F),
                0xD6233958,
                0xFF58C7FF);

        String anomalyText = book.isEmpty()
                ? Component.translatable("screen.corpse_campus.player_status.no_ability").getString()
                : activeAbilitySummary(book);
        guiGraphics.drawString(this.font,
                Component.translatable("screen.corpse_campus.player_status.anomaly"),
                left,
                top + 86,
                0x7FCBFF,
                false);
        guiGraphics.fill(left, top + 98, left + width, top + 132, 0x45132437);
        guiGraphics.drawWordWrap(this.font,
                Component.literal(anomalyText),
                left + 4,
                top + 102,
                width - 8,
                0xD7F4FF);
    }

    private void drawLabeledBar(GuiGraphics guiGraphics, Component label, String value, int left, int top,
            int width, float progress, int backColor, int fillColor) {
        guiGraphics.drawString(this.font, label, left, top, 0x7FCBFF, false);
        guiGraphics.drawString(this.font, value, left + 2, top + 10, 0xE2FAFF, false);
        int barTop = top + 21;
        guiGraphics.fill(left, barTop, left + width, barTop + 7, backColor);
        guiGraphics.fill(left, barTop, left + Math.max(1, (int) (width * progress)), barTop + 7, fillColor);
        guiGraphics.fill(left, barTop + 7, left + width, barTop + 8, 0x701A334A);
    }

    private void drawSchoolBonuses(GuiGraphics guiGraphics, ItemStack book, int left, int top, int width) {
        guiGraphics.drawString(this.font,
                Component.translatable("screen.corpse_campus.player_status.section_school"),
                left,
                top,
                0x94E5FF,
                false);

        List<ResourceLocation> schools = List.of(
                ModSchools.XUJING_RESOURCE,
                ModSchools.RIZHAO_RESOURCE,
                ModSchools.DONGYUE_RESOURCE,
                ModSchools.YUZHE_RESOURCE,
                ModSchools.SHENGQI_RESOURCE);

        for (int i = 0; i < schools.size(); i++) {
            ResourceLocation school = schools.get(i);
            double bonus = book.isEmpty() ? 0.0D : AnomalyBookService.getStoredSchoolBonusPercent(book, school);
            String schoolName = Component.translatable("school." + school.getNamespace() + "." + school.getPath()).getString();
            int y = top + 20 + i * 28;
            guiGraphics.drawString(this.font, schoolName, left, y, 0x9DD7FF, false);
            guiGraphics.drawString(this.font,
                    String.format(Locale.ROOT, "+%.0f%%", bonus),
                    left + width - this.font.width(String.format(Locale.ROOT, "+%.0f%%", bonus)),
                    y,
                    bonus > 0.0D ? 0xCFFBFF : 0x6C97B2,
                    false);
            guiGraphics.fill(left, y + 13, left + width, y + 17, 0x40213A56);
            guiGraphics.fill(left, y + 13, left + Math.min(width, (int) Math.round(bonus)), y + 17, 0xFF4BC6FF);
        }
    }

    private void drawAbilities(GuiGraphics guiGraphics, List<SpellSlot> activeSpells, int left, int top, int width,
            int height) {
        guiGraphics.drawString(this.font,
                Component.translatable("screen.corpse_campus.player_status.section_abilities"),
                left,
                top,
                0x94E5FF,
                false);

        int listTop = top + 16;
        int visibleRows = Math.max(1, height / 14);
        int maxOffset = Math.max(0, activeSpells.size() - visibleRows);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxOffset);

        guiGraphics.fill(left, listTop, left + width, listTop + height, 0x55122134);

        if (activeSpells.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.corpse_campus.player_status.no_ability"),
                    left + width / 2,
                    listTop + height / 2 - 4,
                    0x7FA9C6);
            return;
        }

        for (int row = 0; row < visibleRows && scrollOffset + row < activeSpells.size(); row++) {
            SpellSlot slot = activeSpells.get(scrollOffset + row);
            var spec = AnomalyBookService.getSpellSpec(slot.getSpell().getSpellResource());
            String spellName = spec == null ? slot.getSpell().getSpellName() : spec.zhName();
            String rank = spec == null ? "?" : spec.rank().name();
            int y = listTop + 5 + row * 14;
            guiGraphics.drawString(this.font,
                    "• " + spellName,
                    left + 6,
                    y,
                    0xD7F6FF,
                    false);
            String right = "Lv." + slot.getLevel() + " [" + rank + "]";
            guiGraphics.drawString(this.font,
                    right,
                    left + width - 6 - this.font.width(right),
                    y,
                    0x75C9FF,
                    false);
        }
    }

    private String activeAbilitySummary(ItemStack book) {
        List<String> names = new ArrayList<>();
        for (SpellSlot slot : ISpellContainer.getOrCreate(book).getActiveSpells()) {
            var spec = AnomalyBookService.getSpellSpec(slot.getSpell().getSpellResource());
            names.add(spec == null ? slot.getSpell().getSpellName() : spec.zhName());
            if (names.size() >= 3) {
                break;
            }
        }
        if (names.isEmpty()) {
            return Component.translatable("screen.corpse_campus.player_status.no_ability").getString();
        }
        return String.join(" / ", names);
    }

    private ItemStack findAnomalyBook(Player player) {
        Optional<ICurioStacksHandler> handler = CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler("spellbook"));
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

        ItemStack anomalyBook = findAnomalyBook(player);
        int activeCount = anomalyBook.isEmpty() ? 0 : ISpellContainer.getOrCreate(anomalyBook).getActiveSpells().size();
        int visibleRows = Math.max(1, 60 / 14);
        int maxOffset = Math.max(0, activeCount - visibleRows);
        if (maxOffset <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        int nextOffset = scrollOffset + (delta < 0 ? 1 : -1);
        nextOffset = Mth.clamp(nextOffset, 0, maxOffset);
        if (nextOffset != scrollOffset) {
            scrollOffset = nextOffset;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

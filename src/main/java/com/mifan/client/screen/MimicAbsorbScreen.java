package com.mifan.client.screen;

import com.mifan.network.ModNetwork;
import com.mifan.network.serverbound.MimicAbsorbPacket;
import com.mifan.spell.runtime.MimicRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class MimicAbsorbScreen extends Screen {
    private static final int MAX_VISIBLE = 8;

    private final UUID targetPlayerId;
    private final List<String> spellIds;
    private int scrollOffset;

    public MimicAbsorbScreen(UUID targetPlayerId, String targetPlayerName, List<String> spellIds) {
        super(Component.translatable("screen.corpse_campus.mimic_absorb.title", targetPlayerName));
        this.targetPlayerId = targetPlayerId;
        this.spellIds = spellIds;
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();

        int width = 200;
        int left = (this.width - width) / 2;
        int top = Math.max(36, this.height / 2 - 84);

        int visible = Math.min(MAX_VISIBLE, Math.max(0, spellIds.size() - scrollOffset));
        for (int i = 0; i < visible; i++) {
            String id = spellIds.get(scrollOffset + i);
            Component label = Component.literal(MimicRuntime.displayName(id));
            addRenderableWidget(Button.builder(label, button -> {
                ModNetwork.CHANNEL.sendToServer(new MimicAbsorbPacket(targetPlayerId, id));
                onClose();
            }).pos(left, top + i * 22).size(width, 20).build());
        }

        if (scrollOffset > 0) {
            addRenderableWidget(Button.builder(Component.literal("▲"), button -> {
                scrollOffset = Math.max(0, scrollOffset - 1);
                rebuildButtons();
            }).pos(left + width + 6, top).size(20, 20).build());
        }

        if (scrollOffset + MAX_VISIBLE < spellIds.size()) {
            addRenderableWidget(Button.builder(Component.literal("▼"), button -> {
                int max = Math.max(0, spellIds.size() - MAX_VISIBLE);
                scrollOffset = Math.min(max, scrollOffset + 1);
                rebuildButtons();
            }).pos(left + width + 6, top + Math.max(0, visible - 1) * 22).size(20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .pos(left, top + MAX_VISIBLE * 22 + 8)
                .size(width, 20)
                .build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, spellIds.size() - MAX_VISIBLE);
        if (max <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int next = scrollOffset + (delta < 0 ? 1 : -1);
        next = Math.max(0, Math.min(max, next));
        if (next != scrollOffset) {
            scrollOffset = next;
            rebuildButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int top = Math.max(18, this.height / 2 - 112);
        guiGraphics.drawCenteredString(this.font, this.title, centerX, top, 0xC8E6FF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.corpse_campus.mimic_absorb.subtitle"),
                centerX,
                top + 14,
                0xA0B8D2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

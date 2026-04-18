package com.mifan.client.screen;

import com.mifan.network.ModNetwork;
import com.mifan.network.serverbound.SetDominanceTargetPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DominanceTargetScreen extends Screen {
    private static final int MAX_VISIBLE = 8;
    private int scrollOffset;

    public DominanceTargetScreen() {
        super(Component.translatable("screen.corpse_campus.dominance.title"));
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        List<Player> players = new ArrayList<>(minecraft.level.players());
        players.remove(minecraft.player);
        players.sort(Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER));

        int width = 180;
        int left = (this.width - width) / 2;
        int top = Math.max(36, this.height / 2 - 84);

        int visible = Math.min(MAX_VISIBLE, Math.max(0, players.size() - scrollOffset));
        for (int i = 0; i < visible; i++) {
            Player target = players.get(scrollOffset + i);
            addRenderableWidget(Button.builder(target.getDisplayName(), button -> {
                ModNetwork.CHANNEL.sendToServer(new SetDominanceTargetPacket(target.getUUID()));
                onClose();
            }).pos(left, top + i * 22).size(width, 20).build());
        }

        if (scrollOffset > 0) {
            addRenderableWidget(Button.builder(Component.literal("▲"), button -> {
                scrollOffset = Math.max(0, scrollOffset - 1);
                rebuildButtons();
            }).pos(left + width + 6, top).size(20, 20).build());
        }

        if (scrollOffset + MAX_VISIBLE < players.size()) {
            addRenderableWidget(Button.builder(Component.literal("▼"), button -> {
                scrollOffset = Math.min(Math.max(0, players.size() - MAX_VISIBLE), scrollOffset + 1);
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        int otherPlayers = Math.max(0, mc.level.players().size() - 1);
        int maxOffset = Math.max(0, otherPlayers - MAX_VISIBLE);
        if (maxOffset <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        int nextOffset = scrollOffset + (delta < 0 ? 1 : -1);
        nextOffset = Math.max(0, Math.min(maxOffset, nextOffset));
        if (nextOffset != scrollOffset) {
            scrollOffset = nextOffset;
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
        guiGraphics.drawCenteredString(this.font, this.title, centerX, top, 0xE7D7FF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.corpse_campus.dominance.subtitle"),
                centerX,
                top + 14,
                0xB8A8D2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

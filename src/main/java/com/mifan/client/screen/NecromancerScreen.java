package com.mifan.client.screen;

import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.OpenNecromancerScreenPacket;
import com.mifan.network.clientbound.UpdateNecromancerScreenPacket;
import com.mifan.network.serverbound.CloseNecromancerScreenPacket;
import com.mifan.network.serverbound.SummonNecromancerMinionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NecromancerScreen extends Screen {
    private static final int MAX_VISIBLE = 6;
    private static final int ROW_HEIGHT = 26;
    private static final int ROW_WIDTH = 260;

    private List<OpenNecromancerScreenPacket.SoulEntry> souls;
    private float currentMana;
    private int enhanceCost;
    private boolean canCastNormal;
    private boolean canCastEnhanced;
    private int scrollOffset;
    private boolean closeSent;

    public NecromancerScreen(OpenNecromancerScreenPacket packet) {
        super(Component.translatable("screen.corpse_campus.necromancer.title"));
        applySnapshot(packet.getSouls(), packet.getCurrentMana(), packet.getEnhanceCost(),
                packet.canCastNormal(), packet.canCastEnhanced());
    }

    public void applyUpdate(UpdateNecromancerScreenPacket packet) {
        applySnapshot(packet.getSouls(), packet.getCurrentMana(), packet.getEnhanceCost(),
                packet.canCastNormal(), packet.canCastEnhanced());
        int maxOffset = Math.max(0, souls.size() - MAX_VISIBLE);
        if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
        if (this.minecraft != null) {
            rebuildButtons();
        }
    }

    private void applySnapshot(List<OpenNecromancerScreenPacket.SoulEntry> incoming, float mana, int enhance,
            boolean canCastNormal, boolean canCastEnhanced) {
        List<OpenNecromancerScreenPacket.SoulEntry> copy = new ArrayList<>(incoming);
        copy.sort(Comparator.comparingInt(OpenNecromancerScreenPacket.SoulEntry::count).reversed());
        this.souls = copy;
        this.currentMana = mana;
        this.enhanceCost = enhance;
        this.canCastNormal = canCastNormal;
        this.canCastEnhanced = canCastEnhanced;
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int top = Math.max(48, this.height / 2 - 96);
        int left = (this.width - ROW_WIDTH) / 2;

        int visible = Math.min(MAX_VISIBLE, Math.max(0, souls.size() - scrollOffset));
        for (int i = 0; i < visible; i++) {
            OpenNecromancerScreenPacket.SoulEntry entry = souls.get(scrollOffset + i);
            int rowY = top + i * ROW_HEIGHT;
            String typeIdString = entry.typeId();

            Button summonBtn = Button.builder(
                    Component.translatable("screen.corpse_campus.necromancer.btn_summon"),
                    button -> ModNetwork.CHANNEL
                            .sendToServer(new SummonNecromancerMinionPacket(typeIdString, false)))
                    .pos(left + 140, rowY)
                    .size(56, 20)
                    .build();
            summonBtn.active = canCastNormal && entry.count() > 0;
            addRenderableWidget(summonBtn);

            Button enhancedBtn = Button.builder(
                    Component.translatable("screen.corpse_campus.necromancer.btn_enhance", enhanceCost),
                    button -> ModNetwork.CHANNEL
                            .sendToServer(new SummonNecromancerMinionPacket(typeIdString, true)))
                    .pos(left + 200, rowY)
                    .size(60, 20)
                    .build();
            enhancedBtn.active = canCastEnhanced && entry.count() > 0;
            addRenderableWidget(enhancedBtn);
        }

        if (scrollOffset > 0) {
            addRenderableWidget(Button.builder(Component.literal("▲"), button -> {
                scrollOffset = Math.max(0, scrollOffset - 1);
                rebuildButtons();
            }).pos(left - 28, top).size(20, 20).build());
        }

        if (scrollOffset + MAX_VISIBLE < souls.size()) {
            addRenderableWidget(Button.builder(Component.literal("▼"), button -> {
                scrollOffset = Math.min(Math.max(0, souls.size() - MAX_VISIBLE), scrollOffset + 1);
                rebuildButtons();
            }).pos(left - 28, top + (MAX_VISIBLE - 1) * ROW_HEIGHT).size(20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.corpse_campus.necromancer.btn_done"),
                button -> onClose())
                .pos(left + ROW_WIDTH / 2 - 40, top + MAX_VISIBLE * ROW_HEIGHT + 16)
                .size(80, 20)
                .build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxOffset = Math.max(0, souls.size() - MAX_VISIBLE);
        if (maxOffset <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int next = scrollOffset + (delta < 0 ? 1 : -1);
        next = Math.max(0, Math.min(maxOffset, next));
        if (next != scrollOffset) {
            scrollOffset = next;
            rebuildButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (!closeSent) {
            closeSent = true;
            ModNetwork.CHANNEL.sendToServer(new CloseNecromancerScreenPacket());
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int top = Math.max(22, this.height / 2 - 120);
        guiGraphics.drawCenteredString(this.font, this.title, centerX, top, 0xE7C6FF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.corpse_campus.necromancer.subtitle",
                        String.format("%.0f", currentMana)),
                centerX,
                top + 14,
                0xA4B8D2);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.corpse_campus.necromancer.hint"),
                centerX,
                top + 26,
                0x7F8AA1);

        int rowTop = Math.max(48, this.height / 2 - 96);
        int left = (this.width - ROW_WIDTH) / 2;

        if (souls.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.corpse_campus.necromancer.empty"),
                    centerX,
                    rowTop + 16,
                    0xC8AE96);
            return;
        }

        int visible = Math.min(MAX_VISIBLE, Math.max(0, souls.size() - scrollOffset));
        for (int i = 0; i < visible; i++) {
            OpenNecromancerScreenPacket.SoulEntry entry = souls.get(scrollOffset + i);
            int rowY = rowTop + i * ROW_HEIGHT;
            ResourceLocation rl = ResourceLocation.tryParse(entry.typeId());
            Component label;
            if (rl != null) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
                label = type != null ? type.getDescription() : Component.literal(entry.typeId());
            } else {
                label = Component.literal(entry.typeId());
            }
            guiGraphics.drawString(this.font, label, left, rowY + 6, 0xE6E6E6, false);
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.corpse_campus.necromancer.count", entry.count()),
                    left + 100, rowY + 6, 0x9FC2A8, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

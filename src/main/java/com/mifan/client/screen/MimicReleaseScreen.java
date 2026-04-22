package com.mifan.client.screen;

import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.OpenMimicReleaseScreenPacket;
import com.mifan.network.serverbound.MimicReleasePacket;
import com.mifan.spell.runtime.MimicRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class MimicReleaseScreen extends Screen {
    private final List<OpenMimicReleaseScreenPacket.SlotEntry> entries;

    public MimicReleaseScreen(List<OpenMimicReleaseScreenPacket.SlotEntry> entries) {
        super(Component.translatable("screen.corpse_campus.mimic_release.title"));
        this.entries = entries;
    }

    @Override
    protected void init() {
        clearWidgets();

        int width = 200;
        int left = (this.width - width) / 2;
        int top = Math.max(40, this.height / 2 - 60);

        for (int i = 0; i < entries.size(); i++) {
            OpenMimicReleaseScreenPacket.SlotEntry e = entries.get(i);
            Component label = Component.translatable(
                    "screen.corpse_campus.mimic_release.entry",
                    e.slot() + 1,
                    MimicRuntime.displayName(e.spellId()));
            int slot = e.slot();
            addRenderableWidget(Button.builder(label, button -> {
                ModNetwork.CHANNEL.sendToServer(new MimicReleasePacket(slot));
                onClose();
            }).pos(left, top + i * 24).size(width, 22).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .pos(left, top + entries.size() * 24 + 8)
                .size(width, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int top = Math.max(18, this.height / 2 - 96);
        guiGraphics.drawCenteredString(this.font, this.title, centerX, top, 0xFFD7C8);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.corpse_campus.mimic_release.subtitle"),
                centerX,
                top + 14,
                0xC8B0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

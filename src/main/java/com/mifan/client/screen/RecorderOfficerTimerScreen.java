package com.mifan.client.screen;

import com.mifan.network.ModNetwork;
import com.mifan.network.serverbound.SetRecorderOfficerTimerPacket;
import com.mifan.spell.AbilityRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RecorderOfficerTimerScreen extends Screen {
    private final int spellLevel;
    private final int targetEntityId;
    private final String targetName;
    private final int minSeconds;
    private final int maxSeconds;
    private int timerSeconds;

    public RecorderOfficerTimerScreen(int spellLevel, int targetEntityId, String targetName,
            int defaultSeconds, int minSeconds, int maxSeconds) {
        super(Component.translatable("screen.corpse_campus.recorder_officer.title"));
        this.spellLevel = spellLevel;
        this.targetEntityId = targetEntityId;
        this.targetName = targetName;
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
        this.timerSeconds = AbilityRuntime.clampRecorderOfficerSeconds(defaultSeconds);
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 100;
        int top = this.height / 2 - 34;

        addRenderableWidget(new TimerSlider(left, top, 200, 20));
        addRenderableWidget(Button.builder(Component.translatable("screen.corpse_campus.recorder_officer.confirm"), button -> {
            ModNetwork.CHANNEL.sendToServer(new SetRecorderOfficerTimerPacket(spellLevel, targetEntityId, timerSeconds));
            onClose();
        }).pos(left, top + 34).size(96, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .pos(left + 104, top + 34)
                .size(96, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int titleY = this.height / 2 - 58;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, titleY, 0xD7E9FF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.corpse_campus.recorder_officer.target", targetName),
                centerX,
                titleY + 14,
                0xC5DFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.corpse_campus.recorder_officer.seconds", timerSeconds),
                centerX,
                titleY + 30,
                0xF6FAFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class TimerSlider extends AbstractSliderButton {
        protected TimerSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), RecorderOfficerTimerScreen.this.maxSeconds <= RecorderOfficerTimerScreen.this.minSeconds
                    ? 0.0D
                    : (double) (RecorderOfficerTimerScreen.this.timerSeconds - RecorderOfficerTimerScreen.this.minSeconds)
                            / (double) (RecorderOfficerTimerScreen.this.maxSeconds - RecorderOfficerTimerScreen.this.minSeconds));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.corpse_campus.recorder_officer.slider", timerSeconds));
        }

        @Override
        protected void applyValue() {
            timerSeconds = minSeconds + (int) Math.round(this.value * (maxSeconds - minSeconds));
            timerSeconds = AbilityRuntime.clampRecorderOfficerSeconds(timerSeconds);
            updateMessage();
        }
    }
}

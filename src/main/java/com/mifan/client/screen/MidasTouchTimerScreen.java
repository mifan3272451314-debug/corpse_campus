package com.mifan.client.screen;

import com.mifan.network.ModNetwork;
import com.mifan.network.serverbound.SetMidasTouchTimerPacket;
import com.mifan.spell.MidasBombRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MidasTouchTimerScreen extends Screen {
    private final int spellLevel;
    private final int minSeconds;
    private final int maxSeconds;
    private int timerSeconds;
    private TimerSlider slider;

    public MidasTouchTimerScreen(int spellLevel, int defaultSeconds, int minSeconds, int maxSeconds) {
        super(Component.translatable("screen.corpse_campus.midas_touch.title"));
        this.spellLevel = spellLevel;
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
        this.timerSeconds = MidasBombRuntime.clampSeconds(defaultSeconds);
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 100;
        int top = this.height / 2 - 30;

        this.slider = addRenderableWidget(new TimerSlider(left, top, 200, 20));
        addRenderableWidget(Button.builder(Component.translatable("screen.corpse_campus.midas_touch.confirm"), button -> {
            ModNetwork.CHANNEL.sendToServer(new SetMidasTouchTimerPacket(spellLevel, timerSeconds));
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
        int titleY = this.height / 2 - 62;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, titleY, 0xF8DE8A);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.corpse_campus.midas_touch.subtitle"),
                centerX,
                titleY + 14,
                0xE8D2A1);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.corpse_campus.midas_touch.seconds", timerSeconds),
                centerX,
                titleY + 34,
                0xFFF6D9);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class TimerSlider extends AbstractSliderButton {
        protected TimerSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), MidasTouchTimerScreen.this.maxSeconds <= MidasTouchTimerScreen.this.minSeconds
                    ? 0.0D
                    : (double) (MidasTouchTimerScreen.this.timerSeconds - MidasTouchTimerScreen.this.minSeconds)
                            / (double) (MidasTouchTimerScreen.this.maxSeconds - MidasTouchTimerScreen.this.minSeconds));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.corpse_campus.midas_touch.slider", timerSeconds));
        }

        @Override
        protected void applyValue() {
            timerSeconds = fromValue(this.value);
            updateMessage();
        }

        private double toValue(int seconds) {
            if (maxSeconds <= minSeconds) {
                return 0.0D;
            }
            return (double) (seconds - minSeconds) / (double) (maxSeconds - minSeconds);
        }

        private int fromValue(double value) {
            return minSeconds + (int) Math.round(value * (maxSeconds - minSeconds));
        }
    }
}

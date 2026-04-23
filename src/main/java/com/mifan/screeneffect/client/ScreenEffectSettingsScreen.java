package com.mifan.screeneffect.client;

import com.mifan.screeneffect.config.ScreenEffectConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Consumer;

/**
 * 屏幕特效不透明度设置页。提供 6 个滑动条:
 *   1) 全局不透明度（乘在所有特效上）
 *   2-6) 5 个终阶法术各自的独立系数
 * 拖动立即写回 ForgeConfigSpec 并由 Forge 异步持久化到 corpse_campus-screen_effect.toml。
 */
public class ScreenEffectSettingsScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 260;
    private static final int SLIDER_W = 260;
    private static final int SLIDER_H = 18;
    private static final int ROW_GAP = 26;

    public ScreenEffectSettingsScreen() {
        super(Component.translatable("screen.corpse_campus.effect_settings.title"));
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int sliderX = left + (PANEL_W - SLIDER_W) / 2;
        int y = top + 36;

        addSlider(sliderX, y, Component.translatable("screen.corpse_campus.effect_settings.global"),
                ScreenEffectConfig.ALPHA_MULTIPLIER);
        y += ROW_GAP;
        addSlider(sliderX, y, Component.translatable("spell.corpse_campus.endless_life"),
                ScreenEffectConfig.ALPHA_ENDLESS_LIFE);
        y += ROW_GAP;
        addSlider(sliderX, y, Component.translatable("spell.corpse_campus.golden_crow_sun"),
                ScreenEffectConfig.ALPHA_GOLDEN_CROW_SUN);
        y += ROW_GAP;
        addSlider(sliderX, y, Component.translatable("spell.corpse_campus.great_necromancer"),
                ScreenEffectConfig.ALPHA_GREAT_NECROMANCER);
        y += ROW_GAP;
        addSlider(sliderX, y, Component.translatable("spell.corpse_campus.authority_grasp"),
                ScreenEffectConfig.ALPHA_AUTHORITY_GRASP);
        y += ROW_GAP;
        addSlider(sliderX, y, Component.translatable("spell.corpse_campus.rewind_worm"),
                ScreenEffectConfig.ALPHA_REWIND_WORM);
        y += ROW_GAP + 8;

        int btnW = 80;
        int btnX = left + (PANEL_W - btnW) / 2;
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.corpse_campus.effect_settings.close"),
                b -> this.onClose()).bounds(btnX, y, btnW, 20).build());
    }

    private void addSlider(int x, int y, Component label, ForgeConfigSpec.DoubleValue configValue) {
        this.addRenderableWidget(new AlphaSlider(x, y, label, configValue));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);

        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        // 面板背景
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xDD101014);
        g.fill(left, top, left + PANEL_W, top + 1, 0xFF7A6BE0);
        g.fill(left, top + PANEL_H - 1, left + PANEL_W, top + PANEL_H, 0xFF7A6BE0);
        g.fill(left, top, left + 1, top + PANEL_H, 0xFF7A6BE0);
        g.fill(left + PANEL_W - 1, top, left + PANEL_W, top + PANEL_H, 0xFF7A6BE0);

        // 标题
        g.drawCenteredString(this.font, this.title, left + PANEL_W / 2, top + 12, 0xFFE7C6FF);

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 拖动即时写回 ForgeConfigSpec 的滑条 */
    private static final class AlphaSlider extends AbstractSliderButton {
        private final Component label;
        private final ForgeConfigSpec.DoubleValue configValue;
        private final Consumer<Double> onChange;

        AlphaSlider(int x, int y, Component label, ForgeConfigSpec.DoubleValue configValue) {
            super(x, y, SLIDER_W, SLIDER_H, label, configValue.get());
            this.label = label;
            this.configValue = configValue;
            this.onChange = configValue::set;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int pct = (int) Math.round(this.value * 100D);
            this.setMessage(Component.translatable("screen.corpse_campus.effect_settings.slider_format",
                    this.label, pct));
        }

        @Override
        protected void applyValue() {
            onChange.accept(this.value);
        }
    }
}

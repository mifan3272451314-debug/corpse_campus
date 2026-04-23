package com.mifan.screeneffect.api;

import net.minecraft.client.gui.GuiGraphics;

public record EffectContext(
        GuiGraphics graphics,
        int screenWidth,
        int screenHeight,
        long gameTime,
        float partialTick,
        float progress,
        float alpha,
        EffectIntensity intensity) {
}

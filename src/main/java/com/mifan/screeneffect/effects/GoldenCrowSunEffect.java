package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 日轮金乌：柔和太阳光晕 + 暖色维涅特 + 稀疏金色浮尘。克制、低 alpha。4s。 */
public class GoldenCrowSunEffect extends SpellScreenEffect {

    private static final int GOLD = 0xFFD040;
    private static final int HOT_WHITE = 0xFFF8E6;
    private static final int RED = 0xE0342C;

    public GoldenCrowSunEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "golden_crow_sun"),
                80, 10, 34);
    }

    @Override
    protected SoundEvent getStartSound() {
        return SoundEvents.LIGHTNING_BOLT_THUNDER;
    }

    @Override
    protected SoundEvent getImpactSound() {
        return SoundEvents.BLAZE_AMBIENT;
    }

    @Override
    protected int getImpactTickOffset() {
        return 10;
    }

    @Override
    protected SoundEvent getEndSound() {
        return SoundEvents.FIRE_AMBIENT;
    }

    @Override
    protected float getSoundPitch() {
        return 0.75F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    @Override
    public void renderOverlay(EffectContext ctx) {
        GuiGraphics g = ctx.graphics();
        int w = ctx.screenWidth();
        int h = ctx.screenHeight();
        // 全局降 alpha 到 40%
        float alpha = ctx.alpha() * 0.40F;
        float progress = ctx.progress();
        EffectIntensity intensity = ctx.intensity();
        float time = ctx.gameTime() + ctx.partialTick();
        int cx = w / 2;
        int cy = h / 2;

        // 非常淡的暖色调罩层
        if (intensity.uiDistortion) {
            fillFullscreen(g, w, h, argb(GOLD, alpha * 0.04F));
        }

        if (intensity.midground) {
            // 柔和光晕：多层同心方形 alpha 平方衰减
            float pulse = 0.92F + 0.08F * (float) Math.sin(time * 0.18D);
            int maxR = (int) (Math.min(w, h) / 3 * pulse);
            int step = Math.max(3, maxR / 22);
            for (int r = maxR; r > 0; r -= step) {
                float t = r / (float) maxR;
                float falloff = (1F - t) * (1F - t);
                float aa = alpha * falloff * 0.22F;
                int color = (t < 0.28F) ? HOT_WHITE : GOLD;
                drawCenterSquare(g, cx, cy, r, argb(color, aa));
            }
        }

        if (intensity.edgeGlow) {
            // 暖色维涅特 - 低 alpha
            fillVignetteVertical(g, w, h, RED, alpha * 0.16F, 90);
        }

        if (intensity.particles) {
            // 稀疏浮尘：6~10 个缓慢上升的金点
            int count = Math.max(6, (int) (10 * intensity.particleDensity));
            for (int i = 0; i < count; i++) {
                float seed = i * 13.7F;
                float fx = (float) ((Math.sin(time * 0.018D + seed) * 0.5D + 0.5D) * w);
                float fy = (float) ((h + seed * 9F - time * 0.45F) % h + h) % h;
                int color = (i % 4 == 0) ? HOT_WHITE : GOLD;
                float aa = alpha * 0.45F;
                int px = (int) fx;
                int py = (int) fy;
                g.fill(px, py, px + 2, py + 2, argb(color, aa));
            }
        }

        // 入场/出场淡入淡出 — progress 尾部做一点点柔化
        // (基类的 alpha 已经算入 fadeIn/fadeOut，这里不再额外处理)
        if (progress < 0.0F) { /* no-op */ }
    }
}

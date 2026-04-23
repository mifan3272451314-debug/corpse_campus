package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 日轮金乌：过曝白闪 → 中央日盘 + 十字 + 对角光点 + 朱红维涅特。4s 爆发。 */
public class GoldenCrowSunEffect extends SpellScreenEffect {

    private static final int GOLD = 0xFFD040;
    private static final int HOT_WHITE = 0xFFF8E6;
    private static final int RED = 0xE0342C;

    public GoldenCrowSunEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "golden_crow_sun"),
                80, 8, 30);
    }

    @Override
    protected SoundEvent getStartSound() {
        return SoundEvents.LIGHTNING_BOLT_THUNDER;
    }

    @Override
    protected SoundEvent getImpactSound() {
        return SoundEvents.GENERIC_EXPLODE;
    }

    @Override
    protected int getImpactTickOffset() {
        return 6;
    }

    @Override
    protected SoundEvent getEndSound() {
        return SoundEvents.BLAZE_AMBIENT;
    }

    @Override
    protected float getSoundPitch() {
        return 0.75F;
    }

    @Override
    public void renderOverlay(EffectContext ctx) {
        GuiGraphics g = ctx.graphics();
        int w = ctx.screenWidth();
        int h = ctx.screenHeight();
        float alpha = ctx.alpha();
        float progress = ctx.progress();
        EffectIntensity intensity = ctx.intensity();
        float time = ctx.gameTime() + ctx.partialTick();
        int cx = w / 2;
        int cy = h / 2;

        // ---- 白闪（前 15% 进度）----
        if (progress < 0.15F) {
            float flashT = 1F - (progress / 0.15F);
            fillFullscreen(g, w, h, argb(HOT_WHITE, alpha * flashT));
        }

        // ---- 暖色色温叠加（整段持续）----
        if (intensity.uiDistortion) {
            fillFullscreen(g, w, h, argb(RED, alpha * 0.06F));
        }

        if (intensity.midground) {
            // 十字光柱
            int beamW = 4;
            g.fill(0, cy - beamW, w, cy + beamW, argb(GOLD, alpha * 0.28F));
            g.fill(cx - beamW, 0, cx + beamW, h, argb(GOLD, alpha * 0.28F));

            // 对角光点阵
            drawDiagonalDots(g, cx, cy, 14, 28, 3, argb(GOLD, alpha * 0.45F));

            // 中心日盘（多层正方形）
            int base = 56;
            for (int r = base; r > 0; r -= 4) {
                float t = r / (float) base;
                float coreAlpha = alpha * (1F - t) * 0.55F;
                int color = (r < base * 0.35F) ? HOT_WHITE : GOLD;
                drawCenterSquare(g, cx, cy, r, argb(color, coreAlpha));
            }

            // 外环朱红印泥
            float ringPulse = 0.5F + 0.5F * (float) Math.sin(time * 0.25D);
            int outer = base + 20 + (int) (ringPulse * 6);
            drawHollowSquare(g, cx, cy, outer, 3, argb(RED, alpha * 0.35F));
        }

        if (intensity.edgeGlow) {
            fillVignetteVertical(g, w, h, RED, alpha * 0.30F, 80);
            int edgeA = argb(GOLD, alpha * 0.25F);
            fillEdges(g, w, h, 3, edgeA);
        }
    }
}

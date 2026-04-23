package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 回溯之虫：倒带扫描线 + 中央沙漏虫群 + RGB 色差 + 边缘虫爬纹。5s。 */
public class RewindWormEffect extends SpellScreenEffect {

    private static final int DEEP_PURPLE = 0x4A148C;
    private static final int SILVER = 0xE0E0F0;
    private static final int INK_PURPLE = 0x1A0A2A;
    private static final int WORM_PURPLE = 0x9E37C4;
    private static final int GHOST_R = 0xFF5A5A;
    private static final int GHOST_B = 0x5AA6FF;

    public RewindWormEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "rewind_worm"),
                100, 12, 32);
    }

    @Override
    protected SoundEvent getStartSound() {
        return SoundEvents.PORTAL_TRIGGER;
    }

    @Override
    protected SoundEvent getImpactSound() {
        return SoundEvents.PORTAL_TRAVEL;
    }

    @Override
    protected int getImpactTickOffset() {
        return 14;
    }

    @Override
    protected SoundEvent getEndSound() {
        return SoundEvents.ENDER_EYE_DEATH;
    }

    @Override
    protected float getSoundPitch() {
        return 1.4F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public void renderOverlay(EffectContext ctx) {
        GuiGraphics g = ctx.graphics();
        int w = ctx.screenWidth();
        int h = ctx.screenHeight();
        float alpha = ctx.alpha();
        EffectIntensity intensity = ctx.intensity();
        float time = ctx.gameTime() + ctx.partialTick();
        int cx = w / 2;
        int cy = h / 2;

        // 整屏深紫底调
        if (intensity.uiDistortion) {
            fillFullscreen(g, w, h, argb(INK_PURPLE, alpha * 0.20F));
        }

        // ---- 倒带扫描线（从底往上滚）----
        if (intensity.edgeGlow) {
            int scanSpacing = 14;
            int scanOffset = (int) ((-time * 2.5F) % scanSpacing + scanSpacing) % scanSpacing;
            for (int y = scanOffset; y < h; y += scanSpacing) {
                g.fill(0, y, w, y + 1, argb(SILVER, alpha * 0.22F));
            }
            // 一条主扫描粗线
            int heavyY = (int) ((-time * 1.2F) % h + h) % h;
            g.fill(0, heavyY, w, heavyY + 2, argb(SILVER, alpha * 0.55F));
        }

        // ---- RGB 色差（中央三色重影方框）----
        if (intensity.uiDistortion) {
            int offset = 2 + (int) (Math.sin(time * 0.3D) * 1.5D);
            drawHollowSquare(g, cx - offset, cy, 60, 1, argb(GHOST_R, alpha * 0.45F));
            drawHollowSquare(g, cx + offset, cy, 60, 1, argb(GHOST_B, alpha * 0.45F));
            drawHollowSquare(g, cx, cy, 60, 1, argb(SILVER, alpha * 0.55F));
        }

        // ---- 沙漏图形（两个相对三角，用上下梯形近似）----
        if (intensity.midground) {
            int hourglassH = 50;
            int maxW = 28;
            // 上半：从宽到窄
            for (int y = 0; y < hourglassH / 2; y++) {
                int t = (hourglassH / 2) - y;
                int hw = (maxW * t) / (hourglassH / 2);
                int yy = cy - hourglassH / 2 + y;
                g.fill(cx - hw, yy, cx + hw, yy + 1, argb(WORM_PURPLE, alpha * 0.65F));
            }
            // 下半：从窄到宽
            for (int y = 0; y < hourglassH / 2; y++) {
                int hw = (maxW * y) / (hourglassH / 2);
                int yy = cy + y;
                g.fill(cx - hw, yy, cx + hw, yy + 1, argb(WORM_PURPLE, alpha * 0.65F));
            }
            // 沙漏框
            g.fill(cx - maxW - 2, cy - hourglassH / 2 - 2, cx + maxW + 2, cy - hourglassH / 2,
                    argb(SILVER, alpha * 0.70F));
            g.fill(cx - maxW - 2, cy + hourglassH / 2, cx + maxW + 2, cy + hourglassH / 2 + 2,
                    argb(SILVER, alpha * 0.70F));

            // 沙漏内"蠕虫"：随机小紫点
            int wormCount = 6 + (int) (4 * intensity.particleDensity);
            for (int i = 0; i < wormCount; i++) {
                float wt = (time * 0.05F + i * 1.7F) % 1F;
                int wy = cy - hourglassH / 2 + (int) (hourglassH * wt);
                int wx = cx + (int) (Math.sin(time * 0.2D + i) * 10);
                g.fill(wx, wy, wx + 2, wy + 2, argb(DEEP_PURPLE, alpha * 0.85F));
            }
        }

        // ---- 边缘虫爬纹（上下边缘小紫点流）----
        if (intensity.particles) {
            int segCount = Math.max(12, (int) (20 * intensity.particleDensity));
            for (int i = 0; i < segCount; i++) {
                float phase = (time * 0.6F + i * 5.1F) % w;
                int px = (int) phase;
                g.fill(px, 3, px + 3, 5, argb(WORM_PURPLE, alpha * 0.6F));
                g.fill(w - px, h - 5, w - px + 3, h - 3, argb(WORM_PURPLE, alpha * 0.6F));
            }
        }
    }
}

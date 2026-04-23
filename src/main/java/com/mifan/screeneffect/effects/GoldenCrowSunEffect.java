package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 日轮金乌："日食冕"风格 — 深色底 + 水平金色扫描线 + 中心负空间日蚀环(RGB 色差) + 金色胶片小点。4s。 */
public class GoldenCrowSunEffect extends SpellScreenEffect {

    private static final int DARK_VOID = 0x150605;
    private static final int DEEP_RED = 0x2A0A0A;
    private static final int DIM_GOLD = 0x8F6314;
    private static final int HOT_GOLD = 0xD4A428;
    private static final int SILVER = 0xF0EAD2;
    private static final int CRIMSON = 0xA02020;
    private static final int GHOST_R = 0xD04040;
    private static final int GHOST_B = 0xE0C860;

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
        float alpha = ctx.alpha();
        EffectIntensity intensity = ctx.intensity();
        float time = ctx.gameTime() + ctx.partialTick();
        int cx = w / 2;
        int cy = h / 2;

        // 1) 深色底调
        if (intensity.uiDistortion) {
            fillFullscreen(g, w, h, argb(DEEP_RED, alpha * 0.18F));
            fillFullscreen(g, w, h, argb(DARK_VOID, alpha * 0.10F));
        }

        // 2) 水平金色扫描线（从上向下滚）
        if (intensity.edgeGlow) {
            int spacing = 18;
            int offset = (int) ((time * 2.2F) % spacing + spacing) % spacing;
            for (int y = offset; y < h; y += spacing) {
                g.fill(0, y, w, y + 1, argb(DIM_GOLD, alpha * 0.15F));
            }
            int heavyY = (int) ((time * 1.1F) % h + h) % h;
            g.fill(0, heavyY, w, heavyY + 2, argb(HOT_GOLD, alpha * 0.45F));
            g.fill(0, heavyY - 1, w, heavyY, argb(SILVER, alpha * 0.22F));
        }

        // 3) 中心"日食环" + RGB 色差
        if (intensity.midground) {
            int maxR = Math.min(w, h) / 3;
            int ringOffset = 2 + (int) (Math.sin(time * 0.3D) * 1.5D);
            drawHollowSquare(g, cx - ringOffset, cy, maxR, 1, argb(GHOST_R, alpha * 0.40F));
            drawHollowSquare(g, cx + ringOffset, cy, maxR, 1, argb(GHOST_B, alpha * 0.40F));
            drawHollowSquare(g, cx, cy, maxR, 2, argb(HOT_GOLD, alpha * 0.70F));

            int step = 8;
            for (int r = maxR - step; r > 0; r -= step) {
                float t = r / (float) maxR;
                float aa = alpha * 0.20F * t;
                int color = (t > 0.6F) ? DIM_GOLD : ((t > 0.35F) ? CRIMSON : DARK_VOID);
                drawHollowSquare(g, cx, cy, r, 1, argb(color, aa));
            }

            // 日蚀黑心
            drawCenterSquare(g, cx, cy, 4, argb(DARK_VOID, alpha * 0.85F));
        }

        // 4) 竖向金色胶片小点（自上而下）
        if (intensity.particles) {
            int count = Math.max(8, (int) (14 * intensity.particleDensity));
            for (int i = 0; i < count; i++) {
                float seed = i * 17.91F;
                float fx = (float) ((Math.sin(seed * 3.7D) * 0.5D + 0.5D) * w);
                float fy = (float) ((time * 1.4F + seed * 5F) % (h + 20)) - 10;
                int px = (int) fx;
                int py = (int) fy;
                float aa = alpha * 0.55F;
                int color = (i % 4 == 0) ? SILVER : HOT_GOLD;
                g.fill(px, py, px + 1, py + 4, argb(color, aa));
            }
        }

        // 5) 顶底断续金色印文扫描短条
        if (intensity.edgeGlow) {
            int segCount = Math.max(10, (int) (18 * intensity.particleDensity));
            for (int i = 0; i < segCount; i++) {
                float phase = (time * 0.7F + i * 7.3F) % w;
                int px = (int) phase;
                g.fill(px, 4, px + 5, 5, argb(HOT_GOLD, alpha * 0.50F));
                g.fill(w - px, h - 5, w - px + 5, h - 4, argb(HOT_GOLD, alpha * 0.50F));
            }
        }
    }
}

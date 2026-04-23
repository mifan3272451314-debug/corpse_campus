package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 万权一手：巨掌压顶 → 白闪 → 金色放射刻纹 + 四角赤红玺印。3s 爆发。 */
public class AuthorityGraspEffect extends SpellScreenEffect {

    private static final int ROYAL_GOLD = 0xFFB528;
    private static final int WHITE = 0xFFFFFF;
    private static final int CRIMSON = 0xDF2018;
    private static final int DEEP_GOLD = 0xA67510;

    public AuthorityGraspEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "authority_grasp"),
                60, 6, 24);
    }

    @Override
    protected SoundEvent getStartSound() {
        return SoundEvents.WARDEN_EMERGE;
    }

    @Override
    protected SoundEvent getImpactSound() {
        return SoundEvents.ANVIL_LAND;
    }

    @Override
    protected int getImpactTickOffset() {
        return 14;
    }

    @Override
    protected SoundEvent getEndSound() {
        return SoundEvents.WARDEN_SONIC_BOOM;
    }

    @Override
    protected float getSoundPitch() {
        return 0.7F;
    }

    @Override
    public void renderOverlay(EffectContext ctx) {
        GuiGraphics g = ctx.graphics();
        int w = ctx.screenWidth();
        int h = ctx.screenHeight();
        float alpha = ctx.alpha();
        float progress = ctx.progress();
        EffectIntensity intensity = ctx.intensity();
        int cx = w / 2;
        int cy = h / 2;

        // ---- 第一段 (0~25%): 巨手剪影从上缓降 ----
        if (progress < 0.25F) {
            float t = progress / 0.25F;
            int handH = (int) (h * 0.7F);
            int handW = handH / 2;
            int hx = cx - handW / 2;
            int hy = -handH + (int) (handH * t * 1.05F);
            if (intensity.midground) {
                g.fill(hx, hy, hx + handW, hy + handH, argb(DEEP_GOLD, alpha * 0.55F));
                // 五指（5 条竖条）
                for (int i = 0; i < 5; i++) {
                    int fw = handW / 6;
                    int fx = hx + i * (handW / 5) + fw / 2;
                    int fh = handH / 3;
                    g.fill(fx, hy - fh, fx + fw, hy, argb(DEEP_GOLD, alpha * 0.55F));
                }
            }
        }

        // ---- 第二段 (25%~35%): 全屏白闪 ----
        if (progress >= 0.23F && progress < 0.38F) {
            float flashT;
            if (progress < 0.28F) {
                flashT = (progress - 0.23F) / 0.05F;
            } else {
                flashT = 1F - (progress - 0.28F) / 0.10F;
            }
            flashT = Math.max(0F, Math.min(1F, flashT));
            fillFullscreen(g, w, h, argb(WHITE, alpha * flashT));
        }

        // ---- 第三段 (35%~100%): 放射刻纹 + 玺印方框 ----
        if (progress >= 0.35F) {
            float t3 = (progress - 0.35F) / 0.65F;
            if (intensity.midground) {
                int beamW = 3;
                g.fill(0, cy - beamW, w, cy + beamW, argb(ROYAL_GOLD, alpha * 0.40F));
                g.fill(cx - beamW, 0, cx + beamW, h, argb(ROYAL_GOLD, alpha * 0.40F));

                // 放射刻纹（对角金点）
                drawDiagonalDots(g, cx, cy, 10, 40, 3, argb(ROYAL_GOLD, alpha * 0.55F));

                // 中心玉玺核心 - 空心方框
                int coreR = 18 + (int) (t3 * 4);
                drawHollowSquare(g, cx, cy, coreR, 2, argb(CRIMSON, alpha * 0.80F));
                drawHollowSquare(g, cx, cy, coreR + 6, 1, argb(ROYAL_GOLD, alpha * 0.50F));
            }

            if (intensity.edgeGlow) {
                // 四角玺印方框
                int size = 36;
                int pad = 18;
                drawHollowSquare(g, pad + size / 2, pad + size / 2, size / 2, 2,
                        argb(CRIMSON, alpha * 0.75F));
                drawHollowSquare(g, w - pad - size / 2, pad + size / 2, size / 2, 2,
                        argb(CRIMSON, alpha * 0.75F));
                drawHollowSquare(g, pad + size / 2, h - pad - size / 2, size / 2, 2,
                        argb(CRIMSON, alpha * 0.75F));
                drawHollowSquare(g, w - pad - size / 2, h - pad - size / 2, size / 2, 2,
                        argb(CRIMSON, alpha * 0.75F));

                fillEdges(g, w, h, 2, argb(ROYAL_GOLD, alpha * 0.35F));
            }
        }

        if (intensity.uiDistortion) {
            // 整屏偏黄暖色
            fillFullscreen(g, w, h, argb(ROYAL_GOLD, alpha * 0.05F));
        }
    }
}

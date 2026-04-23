package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 生生不息：四边绿/黄藤蔓条纹向中心蔓延生长 + 底部嫩金柔光 + 上升花瓣粒子（无中央光柱）。8s。 */
public class EndlessLifeEffect extends SpellScreenEffect {

    private static final int GREEN = 0x4BD169;
    private static final int DEEP_GREEN = 0x2E8B47;
    private static final int GOLD = 0xFFE07A;
    private static final int AMBER = 0xFFC847;
    private static final int PALE_GREEN = 0xA8F0C0;

    public EndlessLifeEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "endless_life"),
                160, 20, 40);
    }

    @Override
    protected SoundEvent getStartSound() {
        return SoundEvents.BEACON_POWER_SELECT;
    }

    @Override
    protected SoundEvent getImpactSound() {
        return SoundEvents.BEACON_ACTIVATE;
    }

    @Override
    protected int getImpactTickOffset() {
        return 24;
    }

    @Override
    protected SoundEvent getEndSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
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
        float pulse = 0.5F + 0.5F * (float) Math.sin(time * 0.08D);

        // 1) 边框呼吸光晕（保留但降权重）
        if (intensity.edgeGlow) {
            fillEdges(g, w, h, 3, argb(GREEN, alpha * (0.22F + 0.15F * pulse)));
        }

        // 2) 四边藤蔓条纹向中心蔓延 —— 核心主视觉
        if (intensity.midground) {
            drawVines(g, w, h, progress, time, alpha);
        }

        // 3) 底部嫩金柔光（生命力从地面涌起）
        if (intensity.midground) {
            g.fillGradient(0, h - 80, w, h,
                    argb(GOLD, 0F),
                    argb(GOLD, alpha * 0.12F * (0.6F + 0.4F * pulse)));
        }

        // 4) 上升花瓣粒子（绿/黄/浅绿交替）
        if (intensity.particles) {
            int count = Math.max(8, (int) (20 * intensity.particleDensity));
            for (int i = 0; i < count; i++) {
                float seed = i * 17.37F;
                float fx = (float) ((Math.sin(time * 0.015D + seed) * 0.5D + 0.5D) * w);
                float fy = (float) ((h + seed * 7.13D - time * 0.85D) % h + h) % h;
                int color = (i % 3 == 0) ? GOLD : (i % 3 == 1 ? PALE_GREEN : GREEN);
                float aa = alpha * (0.30F + 0.30F * pulse);
                int px = (int) fx;
                int py = (int) fy;
                int size = (i % 4 == 0) ? 3 : 2;
                g.fill(px, py, px + size, py + size, argb(color, aa));
            }
        }
    }

    /** 四边各 N 根藤蔓短条，长度 = maxLen * (progress 的渐进生长 + 每根独立波动) */
    private static void drawVines(GuiGraphics g, int w, int h, float progress, float time, float alpha) {
        int barsPerSide = 14;
        int maxLenH = (int) (w * 0.28F);    // 左右方向最大长度
        int maxLenV = (int) (h * 0.30F);    // 上下方向最大长度
        float baseAlpha = alpha * 0.55F;

        // 生长曲线：progress 前 40% 快速蔓延，之后保持并微摆动
        float grow = Math.min(1F, progress / 0.40F);

        // 顶边 & 底边：竖直方向藤蔓
        int xStep = w / barsPerSide;
        for (int i = 0; i < barsPerSide; i++) {
            float seed = i * 3.7F;
            float wobble = 0.85F + 0.15F * (float) Math.sin(time * 0.08D + seed);
            int len = (int) (maxLenV * grow * wobble);
            int xTop = xStep * i + xStep / 2;

            int colorTop = pickVineColor(i);
            int colorBot = pickVineColor(i + 7);

            // 主干 2px 粗
            g.fill(xTop - 1, 0, xTop + 1, len, argb(colorTop, baseAlpha));
            // 分叉小芽（每第 3 根长一个小分叉）
            if (i % 3 == 0 && len > 20) {
                int branchY = len - 8;
                int branchLen = 8;
                g.fill(xTop - 1, branchY, xTop + branchLen, branchY + 1,
                        argb(AMBER, baseAlpha * 0.7F));
            }

            // 底边对称
            g.fill(xTop - 1, h - len, xTop + 1, h, argb(colorBot, baseAlpha));
            if (i % 3 == 1 && len > 20) {
                int branchY = h - len + 8;
                int branchLen = 8;
                g.fill(xTop - branchLen, branchY, xTop + 1, branchY + 1,
                        argb(AMBER, baseAlpha * 0.7F));
            }
        }

        // 左边 & 右边：水平方向藤蔓
        int yStep = h / barsPerSide;
        for (int i = 0; i < barsPerSide; i++) {
            float seed = i * 2.9F + 1.3F;
            float wobble = 0.85F + 0.15F * (float) Math.sin(time * 0.09D + seed);
            int len = (int) (maxLenH * grow * wobble);
            int yLeft = yStep * i + yStep / 2;

            int colorL = pickVineColor(i + 3);
            int colorR = pickVineColor(i + 10);

            // 左边向右生长
            g.fill(0, yLeft - 1, len, yLeft + 1, argb(colorL, baseAlpha));
            if (i % 3 == 2 && len > 20) {
                int branchX = len - 8;
                g.fill(branchX, yLeft - 8, branchX + 1, yLeft + 1,
                        argb(AMBER, baseAlpha * 0.7F));
            }

            // 右边向左生长
            g.fill(w - len, yLeft - 1, w, yLeft + 1, argb(colorR, baseAlpha));
            if (i % 3 == 0 && len > 20) {
                int branchX = w - len + 8;
                g.fill(branchX, yLeft - 1, branchX + 1, yLeft + 9,
                        argb(AMBER, baseAlpha * 0.7F));
            }
        }
    }

    private static int pickVineColor(int i) {
        return switch (i % 5) {
            case 0, 3 -> GREEN;
            case 1 -> AMBER;
            case 2 -> GOLD;
            default -> DEEP_GREEN;
        };
    }
}

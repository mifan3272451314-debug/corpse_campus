package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 万权一手：玉玺盖章 — 空心方框从大变小落下、中心停留微脉动、淡出；暖金边框。克制、低 alpha。3s。 */
public class AuthorityGraspEffect extends SpellScreenEffect {

    private static final int ROYAL_GOLD = 0xFFB528;
    private static final int CRIMSON = 0xDF2018;

    public AuthorityGraspEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "authority_grasp"),
                60, 8, 24);
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
        return 12;
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
    protected float getSoundVolume() {
        return 0.75F;
    }

    @Override
    public void renderOverlay(EffectContext ctx) {
        GuiGraphics g = ctx.graphics();
        int w = ctx.screenWidth();
        int h = ctx.screenHeight();
        // 全局降 alpha 到 50%
        float alpha = ctx.alpha() * 0.50F;
        float progress = ctx.progress();
        EffectIntensity intensity = ctx.intensity();
        int cx = w / 2;
        int cy = h / 2;

        // 极淡暖金罩层
        if (intensity.uiDistortion) {
            fillFullscreen(g, w, h, argb(ROYAL_GOLD, alpha * 0.03F));
        }

        if (intensity.midground) {
            // 玉玺盖章动画分 3 段：
            // 0~40%  从 size=120 缩到 70，同时 alpha 从 0 渐入（"印章落下"）
            // 40~80% 停留在 size≈70，有微小脉动
            // 80~100% 淡出
            int baseSize;
            float stampAlpha;
            if (progress < 0.40F) {
                float k = progress / 0.40F;
                baseSize = (int) (120 - 50 * k);
                stampAlpha = alpha * k * 0.95F;
            } else if (progress < 0.80F) {
                float k = (progress - 0.40F) / 0.40F;
                float pulse = 0.96F + 0.04F * (float) Math.sin(k * Math.PI * 3.5D);
                baseSize = (int) (70 * pulse);
                stampAlpha = alpha * 0.95F;
            } else {
                float k = (progress - 0.80F) / 0.20F;
                baseSize = 70;
                stampAlpha = alpha * 0.95F * (1F - k);
            }

            // 玺印外框：赤红双重空心方
            drawHollowSquare(g, cx, cy, baseSize, 3, argb(CRIMSON, stampAlpha));
            drawHollowSquare(g, cx, cy, baseSize - 7, 1, argb(ROYAL_GOLD, stampAlpha * 0.65F));

            // 印心：简化的十字"权"字纹（只有横竖两笔）
            int innerR = baseSize - 18;
            if (innerR > 0) {
                int t2 = 2;
                g.fill(cx - innerR, cy - t2, cx + innerR, cy + t2, argb(CRIMSON, stampAlpha * 0.55F));
                g.fill(cx - t2, cy - innerR, cx + t2, cy + innerR, argb(CRIMSON, stampAlpha * 0.55F));
            }
        }

        if (intensity.edgeGlow) {
            // 边框极细暖金线
            fillEdges(g, w, h, 2, argb(ROYAL_GOLD, alpha * 0.22F));
            // 顶底很淡的金色维涅特
            fillVignetteVertical(g, w, h, ROYAL_GOLD, alpha * 0.12F, 60);
        }
    }
}

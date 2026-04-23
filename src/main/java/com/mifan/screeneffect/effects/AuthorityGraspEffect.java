package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 万权一手：高频极简版 —— 一声起手音 + 紫色方框快速扩散淡出。1.25s。
 *  紫银配色保持,去除扫描线/色差/盖章/束权/印泥飞溅。 */
public class AuthorityGraspEffect extends SpellScreenEffect {

    private static final int ROYAL_PURPLE = 0x3B0066;
    private static final int BRIGHT_PURPLE = 0x7D4FC4;
    private static final int SILVER = 0xE0E0F0;

    public AuthorityGraspEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "authority_grasp"),
                25, 3, 10);
    }

    @Override
    protected SoundEvent getStartSound() {
        return SoundEvents.WARDEN_EMERGE;
    }

    @Override
    protected float getSoundPitch() {
        return 0.8F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.65F;
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

        // 中央紫银方框快速扩散:20px -> 80px,顶峰在 40% 进度,然后淡出
        if (intensity.midground) {
            int size = 20 + (int) (60 * progress);
            float a;
            if (progress < 0.40F) {
                a = progress / 0.40F;
            } else {
                a = 1F - (progress - 0.40F) / 0.60F;
            }
            a = Math.max(0F, Math.min(1F, a));

            drawHollowSquare(g, cx, cy, size, 2, argb(ROYAL_PURPLE, alpha * a * 0.85F));
            drawHollowSquare(g, cx, cy, size + 4, 1, argb(BRIGHT_PURPLE, alpha * a * 0.55F));
            drawHollowSquare(g, cx, cy, size + 8, 1, argb(SILVER, alpha * a * 0.30F));
        }

        // 极淡边框暖紫
        if (intensity.edgeGlow) {
            fillEdges(g, w, h, 1, argb(ROYAL_PURPLE, alpha * 0.15F));
        }
    }
}

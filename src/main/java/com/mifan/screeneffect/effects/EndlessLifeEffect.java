package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 生生不息：翠绿呼吸光晕 + 嫩金柔光 + 上升花瓣粒子。8s 持续。 */
public class EndlessLifeEffect extends SpellScreenEffect {

    private static final int GREEN = 0x4BD169;
    private static final int GOLD = 0xFFE07A;
    private static final int LIGHT_GREEN = 0xA8F0C0;

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
        EffectIntensity intensity = ctx.intensity();
        float time = ctx.gameTime() + ctx.partialTick();
        float pulse = 0.5F + 0.5F * (float) Math.sin(time * 0.08D);

        if (intensity.edgeGlow) {
            fillEdges(g, w, h, 5, argb(GREEN, alpha * (0.30F + 0.25F * pulse)));
            g.fillGradient(0, 0, w, 24, argb(GREEN, alpha * 0.20F), argb(GREEN, 0F));
            g.fillGradient(0, h - 24, w, h, argb(GREEN, 0F), argb(GREEN, alpha * 0.20F));
        }

        if (intensity.midground) {
            g.fillGradient(0, h - 90, w, h, argb(GOLD, 0F), argb(GOLD, alpha * 0.14F * (0.6F + 0.4F * pulse)));
            int cx = w / 2;
            int beamW = 2 + (int) (pulse * 3);
            g.fillGradient(cx - beamW, 0, cx + beamW, h,
                    argb(GOLD, alpha * 0.06F), argb(GOLD, alpha * 0.16F));
        }

        if (intensity.particles) {
            int count = Math.max(8, (int) (20 * intensity.particleDensity));
            for (int i = 0; i < count; i++) {
                float seed = i * 17.37F;
                float fx = (float) ((Math.sin(time * 0.015D + seed) * 0.5D + 0.5D) * w);
                float fy = (float) ((h + seed * 7.13D - time * 0.85D) % h + h) % h;
                int color = (i % 3 == 0) ? GOLD : (i % 3 == 1 ? LIGHT_GREEN : GREEN);
                float aa = alpha * (0.35F + 0.35F * pulse);
                int px = (int) fx;
                int py = (int) fy;
                int size = (i % 4 == 0) ? 3 : 2;
                g.fill(px, py, px + size, py + size, argb(color, aa));
            }
        }
    }
}

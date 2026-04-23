package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 大冥鬼师：四角黑雾侵蚀 + 紫符箓边框 + 心跳红闪 + 菱形骷髅剪影 + 鬼火粒子。7s。 */
public class GreatNecromancerEffect extends SpellScreenEffect {

    private static final int PURPLE = 0x6B1FAA;
    private static final int DARK = 0x0A0512;
    private static final int JADE = 0x5ABB9E;
    private static final int BLOOD = 0x8F1A1A;
    private static final int GHOST_FIRE = 0x9AF5C0;

    public GreatNecromancerEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "great_necromancer"),
                140, 20, 40);
    }

    @Override
    protected SoundEvent getStartSound() {
        return SoundEvents.SOUL_ESCAPE;
    }

    @Override
    protected SoundEvent getImpactSound() {
        return SoundEvents.WITHER_AMBIENT;
    }

    @Override
    protected int getImpactTickOffset() {
        return 22;
    }

    @Override
    protected SoundEvent getEndSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected float getSoundPitch() {
        return 0.6F;
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

        // 去饱和偏冷底色
        if (intensity.uiDistortion) {
            fillFullscreen(g, w, h, argb(DARK, alpha * 0.20F));
        }

        // 心跳红闪（每 40 tick 一次，持续 8 tick）
        int heartbeat = (int) (time % 40);
        if (heartbeat < 8) {
            float pulse = 1F - heartbeat / 8F;
            fillFullscreen(g, w, h, argb(BLOOD, alpha * 0.10F * pulse));
        }

        if (intensity.edgeGlow) {
            // 四角向中心的黑雾侵蚀（上下浓，左右淡）
            fillVignetteVertical(g, w, h, DARK, alpha * 0.70F, 90);

            // 紫色符箓边框（粗 + 细）
            fillEdges(g, w, h, 3, argb(PURPLE, alpha * 0.55F));
            drawHollowSquare(g, cx, cy, Math.min(w, h) / 2 - 12, 1, argb(PURPLE, alpha * 0.30F));
        }

        if (intensity.midground) {
            // 中心骷髅剪影（用菱形 + 下颌两方块模拟）
            int s = 40;
            for (int i = s; i > 0; i -= 3) {
                float t = i / (float) s;
                int color = (i < s * 0.4F) ? JADE : PURPLE;
                drawCenterSquare(g, cx, cy, i, argb(color, alpha * (1F - t) * 0.22F));
            }
            // 眼窝（两个深黑方块）
            int eyeY = cy - 6;
            g.fill(cx - 12, eyeY, cx - 4, eyeY + 8, argb(0x000000, alpha * 0.85F));
            g.fill(cx + 4, eyeY, cx + 12, eyeY + 8, argb(0x000000, alpha * 0.85F));
            // 眼火（幽绿）
            float flicker = 0.5F + 0.5F * (float) Math.sin(time * 0.8D);
            g.fill(cx - 10, eyeY + 2, cx - 6, eyeY + 6, argb(GHOST_FIRE, alpha * 0.75F * flicker));
            g.fill(cx + 6, eyeY + 2, cx + 10, eyeY + 6, argb(GHOST_FIRE, alpha * 0.75F * flicker));
        }

        if (intensity.particles) {
            // 鬼火 + 飘符：左右两侧漂浮
            int count = Math.max(10, (int) (24 * intensity.particleDensity));
            for (int i = 0; i < count; i++) {
                float seed = i * 11.91F;
                float fy = (float) ((h + seed * 5.3D - time * 0.45D) % h + h) % h;
                float sx = (float) (Math.sin(time * 0.03D + seed) * 0.5D + 0.5D);
                float fx = sx * w;
                int color = (i % 4 == 0) ? BLOOD : ((i % 4 == 1) ? 0xE3D28A : GHOST_FIRE);
                int size = (i % 5 == 0) ? 3 : 2;
                float aa = alpha * 0.55F;
                int px = (int) fx;
                int py = (int) fy;
                g.fill(px, py, px + size, py + size, argb(color, aa));
            }
        }
    }
}

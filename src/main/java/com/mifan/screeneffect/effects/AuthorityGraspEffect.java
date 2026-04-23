package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 万权一手(诡秘侍者)：一只"灵之虫"从屏幕左外爬到右外,身后留短暗紫拖尾。1.25s 高频极简。 */
public class AuthorityGraspEffect extends SpellScreenEffect {

    private static final int DARK_PURPLE = 0x1B0033;
    private static final int ROYAL_PURPLE = 0x3B0066;
    private static final int BRIGHT_PURPLE = 0x7D4FC4;
    private static final int SILVER = 0xE0E0F0;

    public AuthorityGraspEffect() {
        super(ResourceLocation.fromNamespaceAndPath("corpse_campus", "authority_grasp"),
                25, 3, 10);
    }

    @Override
    protected SoundEvent getStartSound() {
        return SoundEvents.SOUL_ESCAPE;
    }

    @Override
    protected float getSoundPitch() {
        return 1.2F;
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
        float progress = ctx.progress();
        EffectIntensity intensity = ctx.intensity();
        float time = ctx.gameTime() + ctx.partialTick();

        // 虫子头部 x:从 -60 爬到 w+60
        int wormX = (int) (-60 + (w + 120) * progress);
        int baseY = h / 2;

        if (intensity.midground) {
            // 身体 5 节,index 0 = 尾(最细最暗),index 4 = 头(最粗最亮)
            int[] offsets = {28, 21, 14, 7, 0};
            int[] sizes = {3, 4, 5, 6, 7};
            int[] colors = {DARK_PURPLE, ROYAL_PURPLE, ROYAL_PURPLE, BRIGHT_PURPLE, BRIGHT_PURPLE};
            float[] alphas = {0.45F, 0.60F, 0.75F, 0.88F, 0.95F};

            for (int i = 0; i < 5; i++) {
                int sx = wormX - offsets[i];
                // 每节随时间+索引波动,产生蠕动感
                int sy = baseY + (int) (Math.sin(time * 0.55D + i * 0.9D) * 3.0D);
                int s = sizes[i];
                g.fill(sx - s / 2, sy - s / 2, sx - s / 2 + s, sy - s / 2 + s,
                        argb(colors[i], alpha * alphas[i]));
            }

            // 头部银色眼点
            if (wormX >= 0 && wormX < w) {
                int eyeY = baseY + (int) (Math.sin(time * 0.55D + 4 * 0.9D) * 3.0D);
                g.fill(wormX - 1, eyeY - 2, wormX + 1, eyeY - 1, argb(SILVER, alpha * 0.95F));
            }

            // 身后拖尾(从虫尾往回一小段暗紫横线)
            int trailEnd = Math.max(0, Math.min(w, wormX - 35));
            int trailStart = Math.max(0, Math.min(w, wormX - 85));
            if (trailEnd > trailStart) {
                g.fill(trailStart, baseY - 1, trailEnd, baseY + 1,
                        argb(DARK_PURPLE, alpha * 0.30F));
            }
        }

        // 极淡边框暖紫
        if (intensity.edgeGlow) {
            fillEdges(g, w, h, 1, argb(ROYAL_PURPLE, alpha * 0.12F));
        }
    }
}

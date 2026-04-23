package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 日轮金乌：中央发光的太阳 + 太阳上方扇动翅膀的乌鸦（金乌本意）。4s。 */
public class GoldenCrowSunEffect extends SpellScreenEffect {

    private static final int DIM_GOLD = 0x8F6314;
    private static final int GOLD = 0xFFD040;
    private static final int HOT_YELLOW = 0xFFE870;
    private static final int HOT_WHITE = 0xFFF8E6;
    private static final int RED = 0xE0342C;
    private static final int BIRD_BLACK = 0x0A0505;

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

        // 极淡暖色调底
        if (intensity.uiDistortion) {
            fillFullscreen(g, w, h, argb(DIM_GOLD, alpha * 0.04F));
        }

        // ─── 中央太阳 ───
        if (intensity.midground) {
            drawGlowingSun(g, cx, cy, time, alpha);
        }

        // ─── 太阳上方扇翅的乌鸦 ───
        if (intensity.midground) {
            drawFlappingCrow(g, cx, cy - 62, time, alpha);
        }

        // ─── 边缘暖色维涅特 ───
        if (intensity.edgeGlow) {
            fillVignetteVertical(g, w, h, RED, alpha * 0.14F, 70);
        }
    }

    private static void drawGlowingSun(GuiGraphics g, int cx, int cy, float time, float alpha) {
        // 呼吸脉动缩放
        float pulse = 0.90F + 0.10F * (float) Math.sin(time * 0.12D);
        int coreR = (int) (38 * pulse);

        // 外光晕：4 层同心方形向外柔和扩散
        int haloMax = coreR + 40;
        for (int r = haloMax; r > coreR; r -= 5) {
            float t = (r - coreR) / 40F;
            float aa = alpha * (1F - t) * (1F - t) * 0.22F;
            drawCenterSquare(g, cx, cy, r, argb(GOLD, aa));
        }

        // 太阳本体：外到内 暗金 -> 金 -> 亮黄 -> 炽白
        for (int r = coreR; r > 0; r -= 3) {
            float t = r / (float) coreR;
            int color;
            if (t > 0.70F) color = DIM_GOLD;
            else if (t > 0.42F) color = GOLD;
            else if (t > 0.20F) color = HOT_YELLOW;
            else color = HOT_WHITE;
            float aa = alpha * (0.35F + 0.65F * (1F - t)) * 0.88F;
            drawCenterSquare(g, cx, cy, r, argb(color, aa));
        }
    }

    private static void drawFlappingCrow(GuiGraphics g, int cx, int cy, float time, float alpha) {
        // 扇翅频率：sin phase -1~+1
        float phase = (float) Math.sin(time * 0.65D);

        // 身体：4x5 椭圆近似
        g.fill(cx - 2, cy, cx + 2, cy + 5, argb(BIRD_BLACK, alpha * 0.95F));
        // 头部：上方一个 2x2 小点
        g.fill(cx - 1, cy - 2, cx + 1, cy, argb(BIRD_BLACK, alpha * 0.95F));
        // 嘴喙：头顶尖一点 1x1
        g.fill(cx, cy - 3, cx + 1, cy - 2, argb(DIM_GOLD, alpha * 0.90F));

        // 翅膀：每侧 3 节,距离身体越远摆动幅度越大
        int wingNodes = 3;
        for (int i = 1; i <= wingNodes; i++) {
            int dy = (int) (-phase * i * 2.5F);  // 负号:phase=+1 时翅膀上举
            // 左翅
            int lx = cx - 2 - i * 2;
            int ly = cy + 1 + dy;
            g.fill(lx - 1, ly, lx + 1, ly + 2, argb(BIRD_BLACK, alpha * 0.90F));
            // 右翅
            int rx = cx + 2 + i * 2;
            int ry = cy + 1 + dy;
            g.fill(rx - 1, ry, rx + 1, ry + 2, argb(BIRD_BLACK, alpha * 0.90F));
        }

        // 翅尖：最外节再加一小羽尾
        int tipOffset = (int) (-phase * (wingNodes + 1) * 2.5F);
        int ltipX = cx - 2 - (wingNodes + 1) * 2;
        int rtipX = cx + 2 + (wingNodes + 1) * 2;
        int tipY = cy + 1 + tipOffset;
        g.fill(ltipX, tipY, ltipX + 1, tipY + 1, argb(BIRD_BLACK, alpha * 0.75F));
        g.fill(rtipX, tipY, rtipX + 1, tipY + 1, argb(BIRD_BLACK, alpha * 0.75F));
    }
}

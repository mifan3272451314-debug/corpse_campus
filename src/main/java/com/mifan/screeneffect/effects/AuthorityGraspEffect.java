package com.mifan.screeneffect.effects;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 万权一手："符契锁印"风格 — 深邃冥紫底 + 水平银色扫描线 + 中央不闭合紫银权印环(RGB 色差) + 放射紫色印泥飞溅。3s。 */
public class AuthorityGraspEffect extends SpellScreenEffect {

    private static final int NIGHT_PURPLE = 0x180033;   // 近黑底紫
    private static final int ROYAL_PURPLE = 0x3B0066;   // 深邃冥紫（主色）
    private static final int BRIGHT_PURPLE = 0x7D4FC4;  // 亮紫高光
    private static final int SILVER = 0xE0E0F0;         // 银白
    private static final int SILVER_BRIGHT = 0xF5F5FF;  // 银白高光
    private static final int INK_PURPLE = 0x4B148C;     // 印泥紫
    private static final int GHOST_PURPLE = 0x9B5FD4;   // 色差-紫通道
    private static final int GHOST_SILVER = 0xC8C8E8;   // 色差-银通道

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
        return 0.65F;
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
        float alpha = ctx.alpha();
        float progress = ctx.progress();
        EffectIntensity intensity = ctx.intensity();
        float time = ctx.gameTime() + ctx.partialTick();
        int cx = w / 2;
        int cy = h / 2;

        // 1) 深紫底调（双层）
        if (intensity.uiDistortion) {
            fillFullscreen(g, w, h, argb(NIGHT_PURPLE, alpha * 0.22F));
            fillFullscreen(g, w, h, argb(ROYAL_PURPLE, alpha * 0.08F));
        }

        // 2) 水平银色扫描线（从上向下，稀疏）
        if (intensity.edgeGlow) {
            int spacing = 22;
            int offset = (int) ((time * 1.8F) % spacing + spacing) % spacing;
            for (int y = offset; y < h; y += spacing) {
                g.fill(0, y, w, y + 1, argb(SILVER, alpha * 0.10F));
            }
            int heavyY = (int) ((time * 0.9F) % h + h) % h;
            g.fill(0, heavyY, w, heavyY + 1, argb(SILVER_BRIGHT, alpha * 0.40F));
        }

        // 3) 中央"权印环" — 不闭合方框 + RGB 色差（紫-银-紫）
        if (intensity.midground) {
            int r = 58;
            // 盖章动画：前 30% 印章从远放大，30-85% 停留微脉动，85-100% 淡出
            float stampAlpha;
            int scaleR = r;
            if (progress < 0.30F) {
                float k = progress / 0.30F;
                scaleR = (int) (r * (1.6F - 0.6F * k));  // 从 1.6x 缩到 1x
                stampAlpha = alpha * k * 0.95F;
            } else if (progress < 0.85F) {
                float k = (progress - 0.30F) / 0.55F;
                float pulse = 0.96F + 0.04F * (float) Math.sin(k * Math.PI * 3.5D);
                scaleR = (int) (r * pulse);
                stampAlpha = alpha * 0.95F;
            } else {
                float k = (progress - 0.85F) / 0.15F;
                stampAlpha = alpha * 0.95F * (1F - k);
            }

            // RGB 色差重影（紫偏左、银偏右、主线居中）
            int ghostOffset = 2 + (int) (Math.sin(time * 0.25D) * 1.2D);
            drawBrokenSquareFrame(g, cx - ghostOffset, cy, scaleR, argb(GHOST_PURPLE, stampAlpha * 0.55F));
            drawBrokenSquareFrame(g, cx + ghostOffset, cy, scaleR, argb(GHOST_SILVER, stampAlpha * 0.55F));
            drawBrokenSquareFrame(g, cx, cy, scaleR, argb(SILVER_BRIGHT, stampAlpha * 0.90F));

            // 内层亮紫符契线（十字权纹，每笔留缺口）
            int inner = scaleR - 14;
            if (inner > 0) {
                int gap = 6;
                // 横：left 段 + right 段（中间 gap 空出）
                g.fill(cx - inner, cy - 1, cx - gap, cy + 1, argb(BRIGHT_PURPLE, stampAlpha * 0.70F));
                g.fill(cx + gap, cy - 1, cx + inner, cy + 1, argb(BRIGHT_PURPLE, stampAlpha * 0.70F));
                // 竖：top 段 + bottom 段
                g.fill(cx - 1, cy - inner, cx + 1, cy - gap, argb(BRIGHT_PURPLE, stampAlpha * 0.70F));
                g.fill(cx - 1, cy + gap, cx + 1, cy + inner, argb(BRIGHT_PURPLE, stampAlpha * 0.70F));
                // 中心点
                g.fill(cx - 1, cy - 1, cx + 1, cy + 1, argb(SILVER_BRIGHT, stampAlpha));
            }
        }

        // 4) 四角向中心收拢的短线 — "四方束权"
        if (intensity.midground && progress > 0.15F) {
            float tighten = Math.min(1F, (progress - 0.15F) / 0.40F);
            int lineLen = (int) (Math.min(w, h) * 0.12F * (1F - tighten * 0.5F));
            int pad = 20 + (int) (tighten * 40);
            int aaTiny = argb(BRIGHT_PURPLE, alpha * 0.45F);
            // 左上→右下 对角
            for (int s = 0; s < lineLen; s += 2) {
                g.fill(pad + s, pad + s, pad + s + 2, pad + s + 2, aaTiny);
                g.fill(w - pad - s, pad + s, w - pad - s + 2, pad + s + 2, aaTiny);
                g.fill(pad + s, h - pad - s, pad + s + 2, h - pad - s + 2, aaTiny);
                g.fill(w - pad - s, h - pad - s, w - pad - s + 2, h - pad - s + 2, aaTiny);
            }
        }

        // 5) 印泥紫点从中心向外飞溅
        if (intensity.particles) {
            int count = Math.max(10, (int) (18 * intensity.particleDensity));
            for (int i = 0; i < count; i++) {
                float seed = i * 11.31F;
                float dirX = (float) Math.cos(seed * 3.7D);
                float dirY = (float) Math.sin(seed * 3.7D);
                float dist = ((time * 2.8F + seed * 13F) % 220F);
                float px = cx + dirX * dist;
                float py = cy + dirY * dist;
                if (px < 0 || px >= w || py < 0 || py >= h) {
                    continue;
                }
                float fadeOutWithDist = 1F - (dist / 220F);
                int color = (i % 3 == 0) ? INK_PURPLE : BRIGHT_PURPLE;
                g.fill((int) px, (int) py, (int) px + 2, (int) py + 2,
                        argb(color, alpha * 0.60F * fadeOutWithDist));
            }
        }
    }

    /** 画一个"不闭合方框"：每边中间留 1/3 gap，符契感 */
    private static void drawBrokenSquareFrame(GuiGraphics g, int cx, int cy, int r, int argb) {
        int seg = (int) (r * 0.55F);
        int t = 2;
        // top: left seg + right seg
        g.fill(cx - r, cy - r, cx - r + seg, cy - r + t, argb);
        g.fill(cx + r - seg, cy - r, cx + r, cy - r + t, argb);
        // bottom
        g.fill(cx - r, cy + r - t, cx - r + seg, cy + r, argb);
        g.fill(cx + r - seg, cy + r - t, cx + r, cy + r, argb);
        // left
        g.fill(cx - r, cy - r, cx - r + t, cy - r + seg, argb);
        g.fill(cx - r, cy + r - seg, cx - r + t, cy + r, argb);
        // right
        g.fill(cx + r - t, cy - r, cx + r, cy - r + seg, argb);
        g.fill(cx + r - t, cy + r - seg, cx + r, cy + r, argb);
    }
}

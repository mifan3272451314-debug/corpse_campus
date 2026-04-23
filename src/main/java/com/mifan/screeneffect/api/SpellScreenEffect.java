package com.mifan.screeneffect.api;

import com.mifan.screeneffect.sound.ClientOnlySound;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public abstract class SpellScreenEffect {
    private final ResourceLocation spellId;
    private final int durationTicks;
    private final int fadeInTicks;
    private final int fadeOutTicks;

    protected SpellScreenEffect(ResourceLocation spellId, int durationTicks,
            int fadeInTicks, int fadeOutTicks) {
        this.spellId = spellId;
        this.durationTicks = durationTicks;
        this.fadeInTicks = fadeInTicks;
        this.fadeOutTicks = fadeOutTicks;
    }

    public final ResourceLocation getSpellId() {
        return spellId;
    }

    public final int getDurationTicks() {
        return durationTicks;
    }

    public final int getFadeInTicks() {
        return fadeInTicks;
    }

    public final int getFadeOutTicks() {
        return fadeOutTicks;
    }

    public abstract void renderOverlay(EffectContext ctx);

    // ---- 音效三段：起手 / 爆发 / 余韵 ----
    protected SoundEvent getStartSound() {
        return null;
    }

    protected SoundEvent getImpactSound() {
        return null;
    }

    /** 爆发音相对起始的 tick 偏移 */
    protected int getImpactTickOffset() {
        return 0;
    }

    protected SoundEvent getEndSound() {
        return null;
    }

    protected float getSoundVolume() {
        return 1.0F;
    }

    protected float getSoundPitch() {
        return 1.0F;
    }

    public void onStart(LocalPlayer player) {
        SoundEvent s = getStartSound();
        if (s != null) {
            ClientOnlySound.play(s, getSoundVolume(), getSoundPitch());
        }
    }

    public void onTick(LocalPlayer player, int elapsedTicks, float progress) {
        SoundEvent impact = getImpactSound();
        if (impact != null && elapsedTicks == getImpactTickOffset()) {
            ClientOnlySound.play(impact, getSoundVolume(), getSoundPitch());
        }
    }

    public void onEnd(LocalPlayer player) {
        SoundEvent s = getEndSound();
        if (s != null) {
            ClientOnlySound.play(s, getSoundVolume() * 0.6F, getSoundPitch());
        }
    }

    // ---- 绘制 helper（protected static，所有子类共享）----

    protected static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255F)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    protected static void fillFullscreen(GuiGraphics g, int w, int h, int argb) {
        g.fill(0, 0, w, h, argb);
    }

    protected static void fillEdges(GuiGraphics g, int w, int h, int thickness, int argb) {
        g.fill(0, 0, w, thickness, argb);
        g.fill(0, h - thickness, w, h, argb);
        g.fill(0, thickness, thickness, h - thickness, argb);
        g.fill(w - thickness, thickness, w, h - thickness, argb);
    }

    /** 上下渐变 vignette（顶向下淡、底向上淡）*/
    protected static void fillVignetteVertical(GuiGraphics g, int w, int h, int rgb, float alpha, int depth) {
        int argb = argb(rgb, alpha);
        int clear = argb(rgb, 0F);
        g.fillGradient(0, 0, w, depth, argb, clear);
        g.fillGradient(0, h - depth, w, h, clear, argb);
    }

    protected static void drawCenterSquare(GuiGraphics g, int cx, int cy, int radius, int argb) {
        g.fill(cx - radius, cy - radius, cx + radius, cy + radius, argb);
    }

    protected static void drawHollowSquare(GuiGraphics g, int cx, int cy, int r, int thickness, int argb) {
        g.fill(cx - r, cy - r, cx + r, cy - r + thickness, argb);
        g.fill(cx - r, cy + r - thickness, cx + r, cy + r, argb);
        g.fill(cx - r, cy - r + thickness, cx - r + thickness, cy + r - thickness, argb);
        g.fill(cx + r - thickness, cy - r + thickness, cx + r, cy + r - thickness, argb);
    }

    protected static void drawDiagonalDots(GuiGraphics g, int cx, int cy, int step, int count,
            int size, int argb) {
        for (int i = 1; i <= count; i++) {
            int d = i * step;
            g.fill(cx + d, cy + d, cx + d + size, cy + d + size, argb);
            g.fill(cx - d, cy + d, cx - d + size, cy + d + size, argb);
            g.fill(cx + d, cy - d, cx + d + size, cy - d + size, argb);
            g.fill(cx - d, cy - d, cx - d + size, cy - d + size, argb);
        }
    }

    /** 从 (cx,cy) 向外画一条方向上的粗线（dx,dy 为单位方向的整数缩放）*/
    protected static void drawBeamFromCenter(GuiGraphics g, int cx, int cy, int dxUnit, int dyUnit,
            int length, int thickness, int argb) {
        for (int i = 0; i < length; i++) {
            int x = cx + dxUnit * i;
            int y = cy + dyUnit * i;
            g.fill(x, y, x + thickness, y + thickness, argb);
        }
    }
}

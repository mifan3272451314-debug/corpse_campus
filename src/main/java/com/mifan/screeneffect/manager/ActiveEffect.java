package com.mifan.screeneffect.manager;

import com.mifan.screeneffect.api.SpellScreenEffect;

public final class ActiveEffect {
    public final SpellScreenEffect effect;
    public final long startGameTime;
    public int elapsedTicks;
    public long requestedFadeOutAt = -1L;

    public ActiveEffect(SpellScreenEffect effect, long startGameTime) {
        this.effect = effect;
        this.startGameTime = startGameTime;
    }

    public float computeProgress(long gameTime, float partialTick) {
        float t = (gameTime - startGameTime) + partialTick;
        float duration = Math.max(1F, effect.getDurationTicks());
        return clamp01(t / duration);
    }

    public float computeAlpha(long gameTime, float partialTick) {
        float t = (gameTime - startGameTime) + partialTick;
        float fadeIn = effect.getFadeInTicks();
        float fadeOut = effect.getFadeOutTicks();
        float duration = effect.getDurationTicks();

        float a = 1F;
        if (fadeIn > 0F && t < fadeIn) {
            a = t / fadeIn;
        }

        if (requestedFadeOutAt >= 0L) {
            float since = (gameTime - requestedFadeOutAt) + partialTick;
            float f = fadeOut <= 0F ? 1F : since / fadeOut;
            a = Math.min(a, 1F - f);
        } else if (fadeOut > 0F && t > duration - fadeOut) {
            float since = t - (duration - fadeOut);
            float f = since / fadeOut;
            a = Math.min(a, 1F - f);
        }

        return clamp01(a);
    }

    public boolean isFinished(long gameTime) {
        if (requestedFadeOutAt >= 0L) {
            return (gameTime - requestedFadeOutAt) >= effect.getFadeOutTicks();
        }
        return (gameTime - startGameTime) >= effect.getDurationTicks();
    }

    public void requestFadeOut(long gameTime) {
        if (requestedFadeOutAt < 0L) {
            requestedFadeOutAt = gameTime;
        }
    }

    private static float clamp01(float v) {
        return v < 0F ? 0F : (v > 1F ? 1F : v);
    }
}

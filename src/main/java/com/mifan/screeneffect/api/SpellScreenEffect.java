package com.mifan.screeneffect.api;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

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

    public void onStart(LocalPlayer player) {
    }

    public void onTick(LocalPlayer player, int elapsedTicks, float progress) {
    }

    public void onEnd(LocalPlayer player) {
    }
}

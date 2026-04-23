package com.mifan.screeneffect.manager;

import com.mifan.screeneffect.api.EffectContext;
import com.mifan.screeneffect.api.EffectIntensity;
import com.mifan.screeneffect.api.SpellScreenEffect;
import com.mifan.screeneffect.config.ScreenEffectConfig;
import com.mifan.screeneffect.registry.ModScreenEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ScreenEffectManager {
    private static final List<ActiveEffect> ACTIVE = new ArrayList<>();

    private ScreenEffectManager() {
    }

    public static void trigger(ResourceLocation spellId) {
        if (!ScreenEffectConfig.ENABLED.get()) {
            return;
        }
        if (ScreenEffectConfig.INTENSITY.get() == EffectIntensity.OFF) {
            return;
        }
        SpellScreenEffect effect = ModScreenEffects.get(spellId);
        if (effect == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!effect.shouldTrigger(player)) {
            return;
        }
        long gameTime = player.level().getGameTime();
        ActiveEffect active = new ActiveEffect(effect, gameTime);
        ACTIVE.add(active);
        effect.onStart(player);
    }

    public static void fadeOutAll() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        long gameTime = player.level().getGameTime();
        for (ActiveEffect a : ACTIVE) {
            a.requestFadeOut(gameTime);
        }
    }

    public static void fadeOut(ResourceLocation spellId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        long gameTime = player.level().getGameTime();
        for (ActiveEffect a : ACTIVE) {
            if (a.effect.getSpellId().equals(spellId)) {
                a.requestFadeOut(gameTime);
            }
        }
    }

    public static void clear() {
        LocalPlayer player = Minecraft.getInstance().player;
        for (ActiveEffect a : ACTIVE) {
            if (player != null) {
                a.effect.onEnd(player);
            }
        }
        ACTIVE.clear();
        for (SpellScreenEffect e : ModScreenEffects.all().values()) {
            e.onClearSession();
        }
    }

    public static void tick(Player player, long gameTime) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        if (player == null || player.isRemoved() || player.isDeadOrDying()) {
            fadeOutAll();
        }
        LocalPlayer local = Minecraft.getInstance().player;
        Iterator<ActiveEffect> it = ACTIVE.iterator();
        while (it.hasNext()) {
            ActiveEffect a = it.next();
            a.elapsedTicks++;
            if (local != null) {
                float p = a.computeProgress(gameTime, 0F);
                a.effect.onTick(local, a.elapsedTicks, p);
            }
            if (a.isFinished(gameTime)) {
                if (local != null) {
                    a.effect.onEnd(local);
                }
                it.remove();
            }
        }
    }

    public static void renderOverlay(GuiGraphics graphics, Player player,
            int width, int height, long gameTime, float partialTick) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        if (!ScreenEffectConfig.ENABLED.get()) {
            return;
        }
        EffectIntensity intensity = ScreenEffectConfig.INTENSITY.get();
        if (intensity == EffectIntensity.OFF) {
            return;
        }
        float combatAlphaMult = ScreenEffectConfig.COMBAT_ALPHA_MULT.get().floatValue();
        float combatT = CombatStateTracker.combatIntensity(gameTime);
        float combatFactor = 1F - combatT * (1F - combatAlphaMult);
        float globalAlphaMult = ScreenEffectConfig.ALPHA_MULTIPLIER.get().floatValue();

        for (ActiveEffect a : ACTIVE) {
            float perSpell = ScreenEffectConfig.getPerSpellAlpha(a.effect.getSpellId().getPath());
            float alpha = a.computeAlpha(gameTime, partialTick) * combatFactor
                    * globalAlphaMult * perSpell;
            if (alpha <= 0.001F) {
                continue;
            }
            float progress = a.computeProgress(gameTime, partialTick);
            EffectContext ctx = new EffectContext(graphics, width, height, gameTime,
                    partialTick, progress, alpha, intensity);
            a.effect.renderOverlay(ctx);
        }
    }

    public static boolean hasActive() {
        return !ACTIVE.isEmpty();
    }
}

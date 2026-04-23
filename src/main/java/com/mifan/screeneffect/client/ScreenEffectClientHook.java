package com.mifan.screeneffect.client;

import com.mifan.screeneffect.manager.ScreenEffectManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * 客户端专用入口。Spell.onCast 用 {@code if (level.isClientSide)} 守卫调用这里，
 * 避免服务端 class-load 时 resolve 到 net.minecraft.client.Minecraft 导致缺类。
 */
public final class ScreenEffectClientHook {

    private ScreenEffectClientHook() {
    }

    public static void triggerIfLocalPlayer(LivingEntity entity, ResourceLocation spellId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || entity != mc.player) {
            return;
        }
        ScreenEffectManager.trigger(spellId);
    }
}

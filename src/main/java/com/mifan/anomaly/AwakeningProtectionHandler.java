package com.mifan.anomaly;

import com.mifan.corpsecampus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 自然觉醒后的"3 秒完全无敌"窗口拦截器。
 *
 * 触发口径（用户 2026-04-23 确认）：
 *   - 仅自然觉醒通道（吞噬 B / 指定异能核心都不挂保护）
 *   - 完全无敌：含 /kill、虚空、火、掉血等任何来源（含 LivingAttackEvent + LivingHurtEvent + LivingDeathEvent 三层兜底）
 *   - 仅在觉醒真正成功时由 NaturalAwakeningService 写 NBT；3 秒后自动失效
 *
 * 拦截优先级：HIGHEST + receiveCanceled = false。在所有伤害结算前最早 cancel，避免与其他模组的伤害修饰器冲突。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class AwakeningProtectionHandler {

    private AwakeningProtectionHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (AnomalyBookService.isUnderAwakeningProtection(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (AnomalyBookService.isUnderAwakeningProtection(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!AnomalyBookService.isUnderAwakeningProtection(player)) {
            return;
        }
        event.setCanceled(true);
        // 死亡兜底被触发说明前两层都漏了（极少见，例如某 mod 直接 setHealth(0) 绕过 hurt）。
        // 把玩家血量回满 + 给一条提示，避免玩家立刻又被秒。
        if (player instanceof ServerPlayer sp) {
            sp.setHealth(sp.getMaxHealth());
            sp.displayClientMessage(
                    Component.translatable("message.corpse_campus.awakening_invuln_save")
                            .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** 玩家退出登录时清掉保护，避免下次上线还残留绝对 tick 比对错乱（理论上 getGameTime 跨重启连续，但保险起见）。 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        AnomalyBookService.clearAwakeningProtection(event.getEntity());
    }
}

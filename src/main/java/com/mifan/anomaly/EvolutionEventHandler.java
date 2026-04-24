package com.mifan.anomaly;

import com.mifan.corpsecampus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 进化觉醒事件路由。仅承担 Phase 1 基础设施：
 *   - PlayerInteractEvent.RightClickBlock：玩家 shift+右键祭坛中心方块 → {@link EvolutionRitualService#attemptFusion}
 *   - PlayerEvent.Clone：复制 NBT（复用 AnomalyEventHandler 已有逻辑，不重复处理）
 *   - LivingDeathEvent：仅清进度 NBT，物品不动（rule #6）
 *
 * 具体条件触发（施法广播、伤害免死、tick 扫描等）由 Phase 2-6 逐流派接入；
 * 本类只提供分发入口，不包含每条进化的判定代码。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class EvolutionEventHandler {

    private EvolutionEventHandler() {
    }

    /**
     * 玩家右键祭坛中心方块时尝试融合。要求：shift+右键（避免与原版附魔台/信标 UI 冲突）。
     * 主手拿着胚胎也允许；空手 / 任意物品都不影响融合本身（配方只看祭坛上的 ItemEntity）。
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        EvolutionAltarStructure altar = EvolutionAltarStructure.fromCenterBlock(
                serverLevel.getBlockState(event.getPos()));
        if (altar == null) {
            return;
        }
        boolean handled = EvolutionRitualService.attemptFusion(serverLevel, event.getPos(), player);
        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * 玩家死亡 → 清空进化进度（rule #6：胚胎/核心物品本身不清，按普通物品掉落规则）。
     * 钩在独立的 LivingDeathEvent；与 AnomalyEventHandler.onPlayerDeath 并列，不破坏其既有死亡语义。
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        EvolutionAwakeningService.clearProgress(player);
    }

    /**
     * Player clone：NBT 由 AnomalyEventHandler.onPlayerClone 整体复制 PERSISTED_NBT_TAG，
     * 进化进度 NBT 同在该路径下，无需额外处理。此处预留钩子以备未来分离。
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // 无操作：PERSISTED_NBT_TAG 复制已覆盖
    }

    /**
     * Tick 占位：Phase 2-6 各流派的连续条件（祈光人/无常僧等）通过自己的服务代码挂接进来，
     * 不在本基础事件处理器里硬编码；此方法保留空壳供将来使用。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (!(event.player instanceof ServerPlayer)) {
            return;
        }
        // Phase 2-6 会通过向本类 addTickHandler 或直接写独立 @EventBusSubscriber 方式接入，
        // Phase 1 不做 tick 级判定。
    }

    // 参数未使用警告抑制
    @SuppressWarnings("unused")
    private static void noopPlayer(Player player) {
    }
}

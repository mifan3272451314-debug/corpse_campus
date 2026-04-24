package com.mifan.anomaly.evolution;

import com.mifan.anomaly.EvolutionAwakeningService;
import com.mifan.corpsecampus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 东岳进化条件触发层：无常僧 / 刽子手。
 *
 * <h3>无常僧（impermanence_monk）</h3>
 * 背包持无常僧胚胎时，半径 20 格内 ≥ 3 名"其他途径已 B 级觉醒者"同时处于蹲伏状态**连续** 30 秒
 * （600 tick）即转化。每 20 tick 扫一次；当次扫描不满足则 continuous_crouch_tick 归零。
 *
 * <h3>刽子手（executioner）</h3>
 * 背包持刽子手胚胎时，由该玩家击杀的"玩家实体"累计达到 2 → 转化。按 LivingDeathEvent 的
 * source.getEntity() == player && victim instanceof Player 计数。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class DongyueEvolutionTrigger {

    public static final ResourceLocation IMPERMANENCE_MONK =
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "impermanence_monk");
    public static final ResourceLocation EXECUTIONER =
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "executioner");

    public static final double IMPERMANENCE_RADIUS = 20.0D;
    public static final long IMPERMANENCE_REQUIRED_TICKS = 600L;       // 30 秒
    public static final int IMPERMANENCE_REQUIRED_CROUCHERS = 3;
    private static final String IMPERMANENCE_TICK_KEY = "continuous_crouch_tick";
    private static final int IMPERMANENCE_CHECK_INTERVAL = 20;

    public static final int EXECUTIONER_REQUIRED_KILLS = 2;
    private static final String EXECUTIONER_KILL_KEY = "player_kill_count";

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % IMPERMANENCE_CHECK_INTERVAL != 0) {
            return;
        }
        if (!XujingEvolutionTrigger.hasEmbryoFor(player, IMPERMANENCE_MONK)) {
            return;
        }

        List<ServerPlayer> nearby = player.serverLevel().getEntitiesOfClass(
                ServerPlayer.class,
                new AABB(player.blockPosition()).inflate(IMPERMANENCE_RADIUS),
                p -> p != player);
        int crouchingAwakeners = 0;
        for (ServerPlayer other : nearby) {
            if (!other.isCrouching()) continue;
            if (!EvolutionAwakeningService.isOtherPathAwakener(player, other)) continue;
            crouchingAwakeners++;
        }

        var progress = EvolutionAwakeningService.getOrCreateProgress(player, IMPERMANENCE_MONK);
        long accumulated = progress.getLong(IMPERMANENCE_TICK_KEY);
        if (crouchingAwakeners >= IMPERMANENCE_REQUIRED_CROUCHERS) {
            accumulated += IMPERMANENCE_CHECK_INTERVAL;
            progress.putLong(IMPERMANENCE_TICK_KEY, accumulated);
            if (accumulated >= IMPERMANENCE_REQUIRED_TICKS) {
                EvolutionAwakeningService.transformEmbryoToCore(player, IMPERMANENCE_MONK);
            }
        } else if (accumulated > 0) {
            progress.putLong(IMPERMANENCE_TICK_KEY, 0L);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        if (killer.level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim) || victim == killer) {
            return;
        }
        if (!XujingEvolutionTrigger.hasEmbryoFor(killer, EXECUTIONER)) {
            return;
        }

        var progress = EvolutionAwakeningService.getOrCreateProgress(killer, EXECUTIONER);
        int kills = progress.getInt(EXECUTIONER_KILL_KEY) + 1;
        progress.putInt(EXECUTIONER_KILL_KEY, kills);
        if (kills >= EXECUTIONER_REQUIRED_KILLS) {
            EvolutionAwakeningService.transformEmbryoToCore(killer, EXECUTIONER);
        }
    }

    private DongyueEvolutionTrigger() {
    }
}

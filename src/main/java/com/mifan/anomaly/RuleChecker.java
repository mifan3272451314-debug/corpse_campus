package com.mifan.anomaly;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * 核心吞噬规则统一拦截门面。
 *
 * 三条可配置规则（见 {@link AnomalyConfig}）：
 *   R1. 是否受 40 人觉醒上限约束（按渠道独立）
 *   R2. 位阶前置依赖（吃 A 需已有 B；吃 S 需已有 A）
 *   R3. 同位阶或更低位阶拒吞（已有 >= 核心位阶就拒绝）
 *
 * 由 {@link AnomalyBookService#applyScrollSpell} / {@link AnomalyBookService#applyDirectAwakening}
 * 以及 {@link com.mifan.item.RankBlessingItem#use} 在落地写数据前调用。
 */
public final class RuleChecker {

    public enum Channel {
        /** {@link com.mifan.item.SpellScrollItem} —— 普通玩家吞异常异能核心 */
        SCROLL,
        /** {@link com.mifan.item.DesignatedAbilityItem} —— 管理员指定异能核心 */
        DESIGNATED,
        /** {@link com.mifan.item.RankBlessingItem} —— 管理员 B/A/S 位阶核心 */
        RANK_BLESSING
    }

    private RuleChecker() {
    }

    /**
     * @param player     尝试吞噬的玩家
     * @param book       玩家异常书（已由调用方 ensureBookPresent）
     * @param coreRank   核心的位阶
     * @param channel    渠道
     * @return Optional.empty() 放行；Optional.of(component) 拒绝理由（已本地化，调用方直接回显）
     */
    public static Optional<Component> check(ServerPlayer player, ItemStack book,
            AnomalySpellRank coreRank, Channel channel) {
        // R1: cap 拦截（仅当渠道配置要求受限、且玩家当前不在觉醒名单时触发）
        if (respectsCap(channel)) {
            MinecraftServer server = player.getServer();
            if (server != null
                    && AnomalyConfig.globalCapEnabled
                    && AnomalyLimitService.get(server).isCapReached()
                    && !AnomalyLimitService.get(server).isAwakened(player.getUUID())) {
                return Optional.of(Component.translatable("message.corpse_campus.anomaly_cap_reached"));
            }
        }

        AnomalySpellRank current = AnomalyBookService.getHighestRank(book);

        // R3: 已有位阶 >= 核心位阶则拒绝
        if (AnomalyConfig.rejectSameOrLowerRank
                && current != null
                && current.ordinal() >= coreRank.ordinal()) {
            return Optional.of(Component.translatable(
                    "message.corpse_campus.rules.already_ge_rank",
                    current.name(), coreRank.name()));
        }

        // R2: 前置位阶
        if (coreRank == AnomalySpellRank.A
                && AnomalyConfig.requireBForA
                && (current == null || current.ordinal() < AnomalySpellRank.B.ordinal())) {
            return Optional.of(Component.translatable("message.corpse_campus.rules.need_rank_b"));
        }
        if (coreRank == AnomalySpellRank.S
                && AnomalyConfig.requireAForS
                && (current == null || current.ordinal() < AnomalySpellRank.A.ordinal())) {
            return Optional.of(Component.translatable("message.corpse_campus.rules.need_rank_a"));
        }

        return Optional.empty();
    }

    private static boolean respectsCap(Channel channel) {
        return switch (channel) {
            case SCROLL -> AnomalyConfig.scrollRespectCap;
            case DESIGNATED -> AnomalyConfig.directAwakenRespectCap;
            case RANK_BLESSING -> AnomalyConfig.rankBlessingRespectCap;
        };
    }
}

package com.mifan.anomaly.evolution;

import com.mifan.anomaly.EvolutionAwakeningService;
import com.mifan.corpsecampus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * 愚者进化条件触发层：模仿者 / 盗命客。
 *
 * <h3>模仿者（mimic）</h3>
 * 背包持模仿者胚胎时，半径 20 格内任一"其他途径已 B 级觉醒者"死亡即转化。
 * 挂 LivingDeathEvent：victim 是其他途径觉醒者 → 扫描半径 20 的观察者胚胎持有者 → 触发转化。
 *
 * <h3>盗命客（life_thief）</h3>
 * 背包持盗命客胚胎时，5 秒（100 tick）滑窗内被 ≥ 2 名独立的"其他途径已 B 级觉醒者"攻击即转化。
 * 挂 LivingAttackEvent：victim == player + attacker == other player + isOtherPathAwakener。
 * 使用 pushUniqueActorAndCount 按 attacker UUID 去重。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class YuzheEvolutionTrigger {

    public static final ResourceLocation MIMIC =
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "mimic");
    public static final ResourceLocation LIFE_THIEF =
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "life_thief");

    public static final double MIMIC_RADIUS = 20.0D;

    public static final long LIFE_THIEF_WINDOW_TICKS = 100L;           // 5 秒
    public static final int LIFE_THIEF_REQUIRED_UNIQUE_ATTACKERS = 2;
    private static final String LIFE_THIEF_TS = "attack_ts";
    private static final String LIFE_THIEF_MOST = "attack_actor_most";
    private static final String LIFE_THIEF_LEAST = "attack_actor_least";

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.level().isClientSide) {
            return;
        }
        // 只关心"其他途径已 B 级觉醒者的死亡"，故 victim 必须已觉醒（不论 MainSequence）
        // 对每个半径 20 内的模仿者胚胎持有者判定 isOtherPathAwakener(observer, victim)
        List<ServerPlayer> nearby = victim.serverLevel().getEntitiesOfClass(
                ServerPlayer.class,
                new AABB(victim.blockPosition()).inflate(MIMIC_RADIUS),
                p -> p != victim);
        for (ServerPlayer observer : nearby) {
            if (!XujingEvolutionTrigger.hasEmbryoFor(observer, MIMIC)) continue;
            if (!EvolutionAwakeningService.isOtherPathAwakener(observer, victim)) continue;
            EvolutionAwakeningService.transformEmbryoToCore(observer, MIMIC);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        if (!XujingEvolutionTrigger.hasEmbryoFor(victim, LIFE_THIEF)) {
            return;
        }
        if (!EvolutionAwakeningService.isOtherPathAwakener(victim, attacker)) {
            return;
        }
        long now = victim.level().getGameTime();
        UUID attackerId = attacker.getUUID();
        int unique = EvolutionAwakeningService.pushUniqueActorAndCount(
                victim, LIFE_THIEF,
                LIFE_THIEF_TS, LIFE_THIEF_MOST, LIFE_THIEF_LEAST,
                now, LIFE_THIEF_WINDOW_TICKS, attackerId);
        if (unique >= LIFE_THIEF_REQUIRED_UNIQUE_ATTACKERS) {
            EvolutionAwakeningService.transformEmbryoToCore(victim, LIFE_THIEF);
        }
    }

    private YuzheEvolutionTrigger() {
    }
}

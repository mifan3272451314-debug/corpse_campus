package com.mifan.anomaly.evolution;

import com.mifan.anomaly.EvolutionAwakeningService;
import com.mifan.corpsecampus;
import com.mifan.registry.ModSchools;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 虚境进化条件触发层：记录官 / 元素使 的独有逻辑。
 *
 * <h3>记录官（recorder_officer）</h3>
 * 背包带记录官胚胎 → 半径 20 格内 10 秒（200 tick）滑窗内有 ≥ 2 名"其他途径已 B 级觉醒者"施展能力。
 * 由 {@link #onAnySpellCast(ServerPlayer)} 在任意法术成功施展后广播；每个施法者在 20 格内广播 1 次，
 * 广播时为 spellId=recorder_officer 的 progress 追加（actor, tick）条目，去重后计数 ≥ 2 即触发。
 *
 * <h3>元素使（elementalist）</h3>
 * 背包带元素使胚胎 → 遭受火烧 / 水淹 / 雷击三种伤害（{@link #ELEMENTAL_HURT_TAGS}）。
 * 每种伤害首次发生时记录 flag，**且** 把该次伤害衰减到 {@code health - 1}（免死保护，避免条件达成前就被伤害致死）。
 * 三个 flag 齐全 → 下一次 tick 触发 {@code transformEmbryoToCore}（在 hurt 分支内立即转化亦可）。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class XujingEvolutionTrigger {

    public static final ResourceLocation RECORDER_OFFICER =
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "recorder_officer");
    public static final ResourceLocation ELEMENTALIST =
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "elementalist");

    // 记录官滑窗
    public static final long RECORDER_WINDOW_TICKS = 200L;         // 10 秒
    public static final double RECORDER_RADIUS = 20.0D;
    public static final int RECORDER_REQUIRED_UNIQUE_ACTORS = 2;

    private static final String RECORDER_TS = "cast_ts";
    private static final String RECORDER_MOST = "cast_actor_most";
    private static final String RECORDER_LEAST = "cast_actor_least";

    // 元素使 flag NBT 键
    private static final String ELEM_FLAME = "flame_seen";
    private static final String ELEM_DROWN = "drown_seen";
    private static final String ELEM_LIGHTNING = "lightning_seen";

    /** ISS / 其他施法者在服务端完成施法后调用一次；本类按半径广播给持胚胎玩家。 */
    public static void onAnySpellCast(ServerPlayer caster) {
        if (caster == null || caster.level().isClientSide) {
            return;
        }
        // caster 必须是其他途径已 B 级觉醒者；广播给半径 20 内所有持记录官胚胎的观察者
        List<ServerPlayer> nearby = caster.serverLevel().getEntitiesOfClass(
                ServerPlayer.class,
                new AABB(caster.blockPosition()).inflate(RECORDER_RADIUS),
                p -> p != caster);
        long now = caster.level().getGameTime();
        for (ServerPlayer observer : nearby) {
            if (!EvolutionAwakeningService.isOtherPathAwakener(observer, caster)) {
                continue;
            }
            if (!hasEmbryoFor(observer, RECORDER_OFFICER)) {
                continue;
            }
            int unique = EvolutionAwakeningService.pushUniqueActorAndCount(
                    observer, RECORDER_OFFICER,
                    RECORDER_TS, RECORDER_MOST, RECORDER_LEAST,
                    now, RECORDER_WINDOW_TICKS, caster.getUUID());
            if (unique >= RECORDER_REQUIRED_UNIQUE_ACTORS) {
                EvolutionAwakeningService.transformEmbryoToCore(observer, RECORDER_OFFICER);
            }
        }
    }

    /** ISS 服务端完成施法后会触发；把事件拆包转发到 onAnySpellCast 处理记录官广播。 */
    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer caster) || caster.level().isClientSide) {
            return;
        }
        onAnySpellCast(caster);
    }

    /** 元素使伤害拦截 + flag 记录 + 免死衰减。 */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        if (!hasEmbryoFor(player, ELEMENTALIST)) {
            return;
        }
        DamageSource src = event.getSource();
        String flagKey = classifyElementalDamage(src);
        if (flagKey == null) {
            return;
        }
        var progress = EvolutionAwakeningService.getOrCreateProgress(player, ELEMENTALIST);
        progress.putBoolean(flagKey, true);

        // 免死：把本次伤害限制到不致死（保留 1 HP）
        float current = player.getHealth();
        float incoming = event.getAmount();
        if (incoming >= current) {
            float clamped = Math.max(0.0F, current - 1.0F);
            event.setAmount(clamped);
        }

        // 三 flag 齐全 → 立即转化
        if (progress.getBoolean(ELEM_FLAME)
                && progress.getBoolean(ELEM_DROWN)
                && progress.getBoolean(ELEM_LIGHTNING)) {
            EvolutionAwakeningService.transformEmbryoToCore(player, ELEMENTALIST);
        }
    }

    private static String classifyElementalDamage(DamageSource src) {
        if (src.is(DamageTypes.IN_FIRE) || src.is(DamageTypes.ON_FIRE) || src.is(DamageTypes.LAVA)) {
            return ELEM_FLAME;
        }
        if (src.is(DamageTypes.DROWN)) {
            return ELEM_DROWN;
        }
        if (src.is(DamageTypes.LIGHTNING_BOLT)) {
            return ELEM_LIGHTNING;
        }
        return null;
    }

    /** 通用工具：玩家是否持有对应 spellId 的胚胎（任意数量）。 */
    public static boolean hasEmbryoFor(ServerPlayer player, ResourceLocation spellId) {
        for (EvolutionAwakeningService.EmbryoRef ref : EvolutionAwakeningService.listEmbryos(player)) {
            if (ref.spellId().equals(spellId)) {
                return true;
            }
        }
        return false;
    }

    // 仅供 schoolId 对齐使用（linter 占位，避免 ModSchools 未使用警告）
    @SuppressWarnings("unused")
    private static final ResourceLocation SCHOOL = ModSchools.XUJING_RESOURCE;

    private XujingEvolutionTrigger() {
    }
}

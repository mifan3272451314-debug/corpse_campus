package com.mifan.anomaly.evolution;

import com.mifan.anomaly.EvolutionAwakeningService;
import com.mifan.corpsecampus;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 日兆进化条件触发层：祈光人 / 点金客。
 *
 * <h3>祈光人（light_prayer）</h3>
 * 背包带祈光人胚胎 → 处于光照值 ≥ 15 的方块位置**连续** 5 分钟（6000 tick）即转化。
 * 每 tick 扫光照值（max(sky, block) ≥ 15）；满足则 continuous_light_tick += 1，否则归零。
 *
 * <h3>点金客（midas_touch）</h3>
 * 背包带点金客胚胎 → 受到爆炸伤害时立即触发；伤害被免死（衰减到 health - 1）。
 * 覆盖 vanilla EXPLOSION / PLAYER_EXPLOSION 两类伤害源。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class RizhaoEvolutionTrigger {

    public static final ResourceLocation LIGHT_PRAYER =
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "light_prayer");
    public static final ResourceLocation MIDAS_TOUCH =
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "midas_touch");

    public static final long LIGHT_PRAYER_REQUIRED_TICKS = 6000L;      // 5 分钟
    public static final int LIGHT_PRAYER_MIN_LIGHT = 15;
    private static final String LIGHT_PRAYER_TICK_KEY = "continuous_light_tick";
    /** Tick 节流：避免每 tick 都更新 NBT（光照条件不需要这么密） */
    private static final int LIGHT_PRAYER_CHECK_INTERVAL = 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % LIGHT_PRAYER_CHECK_INTERVAL != 0) {
            return;
        }
        if (!XujingEvolutionTrigger.hasEmbryoFor(player, LIGHT_PRAYER)) {
            return;
        }

        BlockPos pos = player.blockPosition();
        int sky = player.level().getBrightness(LightLayer.SKY, pos);
        int block = player.level().getBrightness(LightLayer.BLOCK, pos);
        int effective = Math.max(sky, block);

        var progress = EvolutionAwakeningService.getOrCreateProgress(player, LIGHT_PRAYER);
        long accumulated = progress.getLong(LIGHT_PRAYER_TICK_KEY);
        if (effective >= LIGHT_PRAYER_MIN_LIGHT) {
            accumulated += LIGHT_PRAYER_CHECK_INTERVAL;
            progress.putLong(LIGHT_PRAYER_TICK_KEY, accumulated);
            if (accumulated >= LIGHT_PRAYER_REQUIRED_TICKS) {
                EvolutionAwakeningService.transformEmbryoToCore(player, LIGHT_PRAYER);
            }
        } else if (accumulated > 0) {
            // 中断即清零（连续 5 分钟，非累计）
            progress.putLong(LIGHT_PRAYER_TICK_KEY, 0L);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        if (!XujingEvolutionTrigger.hasEmbryoFor(player, MIDAS_TOUCH)) {
            return;
        }
        DamageSource src = event.getSource();
        if (!src.is(DamageTypes.EXPLOSION) && !src.is(DamageTypes.PLAYER_EXPLOSION)) {
            return;
        }

        // 免死：把本次爆炸伤害限制到 health - 1
        float current = player.getHealth();
        float incoming = event.getAmount();
        if (incoming >= current) {
            event.setAmount(Math.max(0.0F, current - 1.0F));
        }

        EvolutionAwakeningService.transformEmbryoToCore(player, MIDAS_TOUCH);
    }

    private RizhaoEvolutionTrigger() {
    }
}

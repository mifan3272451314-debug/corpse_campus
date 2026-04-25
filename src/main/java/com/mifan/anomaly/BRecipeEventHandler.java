package com.mifan.anomaly;

import com.mifan.corpsecampus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "扔齐就自动合成" tick 调度层，统一处理 3 条通道：
 *
 * <ol>
 *   <li><b>6 条无祭坛 B 级配方</b>：在玩家附近 ItemEntity 锚点周围 1.5 格扫描材料，命中即出 B 级核心</li>
 *   <li><b>B→A 祭坛仪式（3×3）</b>：若 ItemEntity 下方一格是某流派祭坛中心方块，调
 *     {@link EvolutionRitualService#attemptFusionAuto} 静默融合；命中产出 A 级胚胎</li>
 *   <li><b>A→S 祭坛仪式（5×5）</b>：同上，先尝试 5×5 静默版；命中产出 S 级胚胎</li>
 * </ol>
 *
 * <p>实现策略：服务端每 {@value #SCAN_INTERVAL_TICKS} tick（1 秒）对每个玩家扫描 8 格内 ItemEntity，
 * 每个 anchor 依次尝试 (祭坛 5×5 → 祭坛 3×3 → 无祭坛 B 级)。同一 anchor 一旦命中其中一条即 break，
 * 避免一秒内重复触发。
 *
 * <p>性能：anchor 路径只读 1 个 BlockState（{@code anchor.below()}），开销极小；扫描范围限定为玩家附近。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class BRecipeEventHandler {

    private static final int SCAN_INTERVAL_TICKS = 20;

    private BRecipeEventHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        Set<Integer> processed = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            AABB box = player.getBoundingBox().inflate(8.0D);
            List<ItemEntity> nearItems = level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive);
            for (ItemEntity anchor : nearItems) {
                if (!processed.add(anchor.getId())) {
                    continue;
                }
                if (tryAltarFusion(level, anchor, player)) {
                    break;
                }
                if (BRecipeFusionService.tryFuseAround(level, anchor)) {
                    break;
                }
            }
        }
    }

    /**
     * 尝试把 anchor 下方/同方块当作祭坛中心方块进行融合。
     *
     * <p>判定路径：先看 anchor.below() 是否是某流派中心方块（玩家把物品丢在中心方块上方时
     * ItemEntity 通常落在 above() 层），若是则先试 5×5 再试 3×3 静默融合。
     *
     * @return true = 命中并融合；false = 没命中
     */
    private static boolean tryAltarFusion(ServerLevel level, ItemEntity anchor, ServerPlayer player) {
        BlockPos belowPos = anchor.blockPosition().below();
        BlockState belowState = level.getBlockState(belowPos);
        if (EvolutionAltarStructure.fromCenterBlock(belowState) == null) {
            return false;
        }
        if (EvolutionRitualService.attemptFusionSAuto(level, belowPos, player)) {
            return true;
        }
        return EvolutionRitualService.attemptFusionAuto(level, belowPos, player);
    }
}

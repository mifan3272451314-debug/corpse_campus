package com.mifan.anomaly;

import com.mifan.registry.ModSchools;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * 五流派仪式祭坛结构配置 + 校验。
 *
 * 坐标约定：center 为中心方块所在 BlockPos。3×3 底座位于 center 同一 Y 层的 8 格（含 center 自身）。
 * 四角 corner 位于 center.above() 层的四个对角（±1, +1, ±1）。中心装饰 pillar 位于 center.above()。
 *
 * 视觉拓扑：
 * <pre>
 *          [corner]  [ ]  [corner]
 *          [ ]   [pillar]   [ ]
 *          [corner]  [ ]  [corner]
 *          (center.above() 层)
 *
 *          [floor][floor][floor]
 *          [floor][center][floor]
 *          [floor][floor][floor]
 *          (center 层)
 * </pre>
 *
 * "中心方块" 是唯一必须右键的交互方块，也是 floor 的一部分；因此 center 自身 BlockState 必须同时
 * 是 {@code centerBlock}。floor 的其余 8 格只检查 {@code floorBlock}。
 */
public enum EvolutionAltarStructure {
    XUJING(ModSchools.XUJING_RESOURCE,
            Blocks.GILDED_BLACKSTONE, Blocks.ENCHANTING_TABLE, Blocks.BLACK_CANDLE),
    RIZHAO(ModSchools.RIZHAO_RESOURCE,
            Blocks.MOSS_BLOCK, Blocks.FLOWERING_AZALEA, Blocks.SUNFLOWER),
    DONGYUE(ModSchools.DONGYUE_RESOURCE,
            Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.SOUL_LANTERN),
    YUZHE(ModSchools.YUZHE_RESOURCE,
            Blocks.AMETHYST_BLOCK, Blocks.LODESTONE, Blocks.SKELETON_SKULL),
    SHENGQI(ModSchools.SHENGQI_RESOURCE,
            Blocks.SCULK, Blocks.BEACON, Blocks.SCULK_SENSOR);

    private final ResourceLocation schoolId;
    private final Block floorBlock;
    private final Block centerBlock;
    private final Block cornerBlock;

    EvolutionAltarStructure(ResourceLocation schoolId, Block floorBlock, Block centerBlock, Block cornerBlock) {
        this.schoolId = schoolId;
        this.floorBlock = floorBlock;
        this.centerBlock = centerBlock;
        this.cornerBlock = cornerBlock;
    }

    public ResourceLocation schoolId() {
        return schoolId;
    }

    public Block floorBlock() {
        return floorBlock;
    }

    public Block centerBlock() {
        return centerBlock;
    }

    public Block cornerBlock() {
        return cornerBlock;
    }

    /**
     * 检查给定中心坐标是否满足本流派祭坛结构。
     * @param level 世界
     * @param center 被右键的中心方块（必须是 centerBlock）
     */
    public boolean matches(Level level, BlockPos center) {
        BlockState centerState = level.getBlockState(center);
        if (!centerState.is(centerBlock)) {
            return false;
        }
        // 3x3 底座：center 同层 8 格全部 floorBlock（中心方块本身不算 floor 严格比对）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos pos = center.offset(dx, 0, dz);
                if (!level.getBlockState(pos).is(floorBlock)) {
                    return false;
                }
            }
        }
        // 四角装饰：center.above() 的四个对角
        BlockPos above = center.above();
        int[][] corners = { {-1, -1}, {-1, 1}, {1, -1}, {1, 1} };
        for (int[] offset : corners) {
            BlockPos pos = above.offset(offset[0], 0, offset[1]);
            if (!level.getBlockState(pos).is(cornerBlock)) {
                return false;
            }
        }
        return true;
    }

    /** 从被右键的 BlockState 反查对应的流派祭坛类型；可能返回 null（非任何祭坛中心方块）。 */
    public static EvolutionAltarStructure fromCenterBlock(BlockState state) {
        for (EvolutionAltarStructure altar : values()) {
            if (state.is(altar.centerBlock)) {
                return altar;
            }
        }
        return null;
    }

    /**
     * 快速映射：schoolId → altar。用于根据目标 spell 的流派定位需要哪种祭坛。
     */
    public static EvolutionAltarStructure forSchool(ResourceLocation schoolId) {
        for (EvolutionAltarStructure altar : values()) {
            if (altar.schoolId.equals(schoolId)) {
                return altar;
            }
        }
        return null;
    }

    /** 供外部静态初始化复用：5 个流派的官方顺序。 */
    public static final Map<ResourceLocation, EvolutionAltarStructure> BY_SCHOOL = Map.of(
            ModSchools.XUJING_RESOURCE, XUJING,
            ModSchools.RIZHAO_RESOURCE, RIZHAO,
            ModSchools.DONGYUE_RESOURCE, DONGYUE,
            ModSchools.YUZHE_RESOURCE, YUZHE,
            ModSchools.SHENGQI_RESOURCE, SHENGQI);
}

package com.mifan.spell.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 负责"回溯之虫"镜像维度的分片异步扫描与状态查询。
 * <ul>
 *   <li>镜像维度由 datapack 预注册：{@code corpse_campus:rewind_mirror}（flat void）。</li>
 *   <li>管理员用 {@code /magic rewind backup create <玩家> [半径]} 启动扫描；
 *       {@link #tick(MinecraftServer)} 按阶段处理 chunk，避免卡服。</li>
 *   <li>扫描分为三个串行阶段：
 *     <ol>
 *       <li>{@link RewindBackupState.Phase#SCAN_BLOCKS}：复制方块态（每 tick {@link #CHUNKS_PER_TICK_BLOCKS} 个 chunk）</li>
 *       <li>{@link RewindBackupState.Phase#SCAN_BLOCK_ENTITIES}：复制方块实体（箱子/告示牌/漏斗 NBT；每 tick {@link #CHUNKS_PER_TICK_BE} 个 chunk）</li>
 *       <li>{@link RewindBackupState.Phase#SCAN_ENTITIES}：复制"持久型"生物实体（每 tick {@link #CHUNKS_PER_TICK_ENTITIES} 个 chunk）</li>
 *     </ol>
 *   </li>
 *   <li>每个被复制的实体在 PersistentData 里打 {@link #TAG_ENTITY_REWIND_COPIED} 标志，
 *       用于区分"自然刷怪"与"备份复制"。</li>
 * </ul>
 */
public final class RewindBackupService {
    public static final ResourceKey<Level> MIRROR_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("corpse_campus", "rewind_mirror"));

    public static final int DEFAULT_RADIUS_BLOCKS = 3000;
    public static final int CHUNKS_PER_TICK_BLOCKS = 2;
    public static final int CHUNKS_PER_TICK_BE = 4;
    public static final int CHUNKS_PER_TICK_ENTITIES = 4;

    /** 打在镜像维度里"从备份复制进来"的实体上。用于让 EntityJoinLevelEvent 拦截器放行。 */
    public static final String TAG_ENTITY_REWIND_COPIED = "corpse_campus_rewind_copied";

    private RewindBackupService() {
    }

    public static RewindBackupState getState(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                RewindBackupState::load, RewindBackupState::new, RewindBackupState.FILE_ID);
    }

    @Nullable
    public static ServerLevel getMirrorLevel(MinecraftServer server) {
        return server.getLevel(MIRROR_DIMENSION);
    }

    public static boolean isReady(MinecraftServer server) {
        return getState(server).phase == RewindBackupState.Phase.READY;
    }

    public enum StartResult {
        STARTED,
        BUSY,
        MIRROR_MISSING,
        SOURCE_IS_MIRROR
    }

    /** 从 {@code center} 为中心，水平半径 {@code radiusBlocks} 启动异步备份扫描。 */
    public static StartResult startBackup(MinecraftServer server, ServerLevel sourceLevel,
                                          BlockPos center, int radiusBlocks) {
        if (sourceLevel.dimension().equals(MIRROR_DIMENSION)) {
            return StartResult.SOURCE_IS_MIRROR;
        }
        RewindBackupState state = getState(server);
        if (isScanning(state)) {
            return StartResult.BUSY;
        }
        ServerLevel mirror = getMirrorLevel(server);
        if (mirror == null) {
            return StartResult.MIRROR_MISSING;
        }

        int radiusChunks = Math.max(1, (radiusBlocks + 15) >> 4);
        int centerCX = center.getX() >> 4;
        int centerCZ = center.getZ() >> 4;

        state.phase = RewindBackupState.Phase.SCAN_BLOCKS;
        state.sourceDim = sourceLevel.dimension().location().toString();
        state.centerChunkX = centerCX;
        state.centerChunkZ = centerCZ;
        state.radiusChunks = radiusChunks;
        state.scannedChunks = 0;
        state.blocksScanned = 0;
        state.blockEntitiesScanned = 0;
        state.entitiesScanned = 0;
        state.startTick = server.getTickCount();
        state.lastUpdateTick = server.getTickCount();

        fillQueueFromCenter(state, centerCX, centerCZ, radiusChunks);
        state.totalChunks = state.queue.size();
        state.setDirty();
        return StartResult.STARTED;
    }

    public static void cancel(MinecraftServer server) {
        RewindBackupState state = getState(server);
        state.phase = RewindBackupState.Phase.IDLE;
        state.queue.clear();
        state.scannedChunks = 0;
        state.totalChunks = 0;
        state.blocksScanned = 0;
        state.blockEntitiesScanned = 0;
        state.entitiesScanned = 0;
        state.sourceDim = "";
        state.setDirty();
    }

    /** 由 ServerTick 驱动的分片扫描。按当前 phase 调度对应的 copy 函数。 */
    public static void tick(MinecraftServer server) {
        RewindBackupState state = getState(server);
        if (!isScanning(state)) {
            return;
        }
        ServerLevel mirror = getMirrorLevel(server);
        if (mirror == null) {
            return;
        }
        ResourceLocation sourceId = ResourceLocation.tryParse(state.sourceDim);
        if (sourceId == null) {
            cancel(server);
            return;
        }
        ServerLevel source = server.getLevel(ResourceKey.create(Registries.DIMENSION, sourceId));
        if (source == null) {
            return;
        }

        int budget = switch (state.phase) {
            case SCAN_BLOCKS -> CHUNKS_PER_TICK_BLOCKS;
            case SCAN_BLOCK_ENTITIES -> CHUNKS_PER_TICK_BE;
            case SCAN_ENTITIES -> CHUNKS_PER_TICK_ENTITIES;
            default -> 0;
        };

        int processed = 0;
        while (processed < budget && !state.queue.isEmpty()) {
            long packed = state.queue.pollFirst();
            int cx = RewindBackupState.unpackX(packed);
            int cz = RewindBackupState.unpackZ(packed);
            switch (state.phase) {
                case SCAN_BLOCKS -> {
                    copyChunkBlocks(source, mirror, cx, cz);
                    state.blocksScanned++;
                }
                case SCAN_BLOCK_ENTITIES -> {
                    copyChunkBlockEntities(source, mirror, cx, cz);
                    state.blockEntitiesScanned++;
                }
                case SCAN_ENTITIES -> {
                    copyChunkEntities(source, mirror, cx, cz);
                    state.entitiesScanned++;
                }
                default -> {
                }
            }
            state.scannedChunks++;
            processed++;
        }
        state.lastUpdateTick = server.getTickCount();
        state.setDirty();

        if (state.queue.isEmpty()) {
            advancePhase(state);
        }
    }

    /** 当前 phase 的 chunk queue 跑空后进入下一阶段，或结束。 */
    private static void advancePhase(RewindBackupState state) {
        switch (state.phase) {
            case SCAN_BLOCKS -> {
                state.phase = RewindBackupState.Phase.SCAN_BLOCK_ENTITIES;
                state.scannedChunks = 0;
                fillQueueFromCenter(state, state.centerChunkX, state.centerChunkZ, state.radiusChunks);
                state.totalChunks = state.queue.size();
            }
            case SCAN_BLOCK_ENTITIES -> {
                state.phase = RewindBackupState.Phase.SCAN_ENTITIES;
                state.scannedChunks = 0;
                fillQueueFromCenter(state, state.centerChunkX, state.centerChunkZ, state.radiusChunks);
                state.totalChunks = state.queue.size();
            }
            case SCAN_ENTITIES -> {
                state.phase = RewindBackupState.Phase.READY;
                state.queue.clear();
            }
            default -> {
            }
        }
        state.setDirty();
    }

    private static boolean isScanning(RewindBackupState state) {
        return state.phase == RewindBackupState.Phase.SCAN_BLOCKS
                || state.phase == RewindBackupState.Phase.SCAN_BLOCK_ENTITIES
                || state.phase == RewindBackupState.Phase.SCAN_ENTITIES;
    }

    /** 同心方环顺序入队：中心 → 半径 1 → 半径 2 → ... → 半径 R。 */
    private static void fillQueueFromCenter(RewindBackupState state, int centerCX, int centerCZ, int radiusChunks) {
        state.queue.clear();
        state.queue.add(RewindBackupState.packChunk(centerCX, centerCZ));
        for (int r = 1; r <= radiusChunks; r++) {
            for (int dx = -r; dx <= r; dx++) {
                state.queue.add(RewindBackupState.packChunk(centerCX + dx, centerCZ - r));
                state.queue.add(RewindBackupState.packChunk(centerCX + dx, centerCZ + r));
            }
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                state.queue.add(RewindBackupState.packChunk(centerCX - r, centerCZ + dz));
                state.queue.add(RewindBackupState.packChunk(centerCX + r, centerCZ + dz));
            }
        }
    }

    /** Phase 1：只复制方块态。 */
    private static void copyChunkBlocks(ServerLevel source, ServerLevel mirror, int cx, int cz) {
        LevelChunk sourceChunk = source.getChunk(cx, cz);
        int lowY = Math.max(source.getMinBuildHeight(), mirror.getMinBuildHeight());
        int highY = Math.min(source.getMaxBuildHeight(), mirror.getMaxBuildHeight());
        int baseX = cx << 4;
        int baseZ = cz << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = lowY; y < highY; y++) {
                    pos.set(baseX + dx, y, baseZ + dz);
                    BlockState state = sourceChunk.getBlockState(pos);
                    if (!state.isAir()) {
                        // flag 2 = 仅更新客户端，不触发方块更新，避免连锁事件
                        mirror.setBlock(pos, state, 2);
                    }
                }
            }
        }
    }

    /** Phase 2：复制方块实体（箱子内容 / 告示牌 / 漏斗 / 熔炉 / 命令方块 等 NBT）。 */
    private static void copyChunkBlockEntities(ServerLevel source, ServerLevel mirror, int cx, int cz) {
        LevelChunk sourceChunk = source.getChunk(cx, cz);
        // getBlockEntities() 返回的是视图，不会抛 ConcurrentModification
        for (Map.Entry<BlockPos, BlockEntity> entry : sourceChunk.getBlockEntities().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockEntity sourceBe = entry.getValue();
            if (sourceBe == null || sourceBe.isRemoved()) {
                continue;
            }
            CompoundTag beTag;
            try {
                beTag = sourceBe.saveWithFullMetadata();
            } catch (Throwable ignored) {
                continue;
            }

            // 镜像侧 blockstate 必须先在 Phase 1 写入；这里不再 setBlock，避免把玩家对镜像的改动覆盖
            BlockState mirrorState = mirror.getBlockState(pos);
            if (mirrorState.isAir()) {
                // Phase 1 漏了某些方块（例如方块没 tick 到），这里补写一次
                mirrorState = sourceBe.getBlockState();
                mirror.setBlock(pos, mirrorState, 2);
            }
            BlockEntity newBe = BlockEntity.loadStatic(pos, mirrorState, beTag);
            if (newBe == null) {
                continue;
            }
            try {
                mirror.getChunk(cx, cz).setBlockEntity(newBe);
                newBe.setChanged();
            } catch (Throwable ignored) {
                // 个别方块实体（特别是 mod 扩展）可能在载入时抛异常，不因单点失败中断整体
            }
        }
    }

    /** Phase 3：复制"持久型"生物实体。 */
    private static void copyChunkEntities(ServerLevel source, ServerLevel mirror, int cx, int cz) {
        int baseX = cx << 4;
        int baseZ = cz << 4;
        AABB aabb = new AABB(
                baseX, source.getMinBuildHeight() - 1, baseZ,
                baseX + 16, source.getMaxBuildHeight() + 1, baseZ + 16);

        List<Entity> sourceEntities = source.getEntities(
                (Entity) null, aabb,
                e -> shouldCopyEntity(e) && !e.isPassenger());

        for (Entity src : sourceEntities) {
            CompoundTag tag = new CompoundTag();
            if (!src.save(tag)) {
                continue;
            }
            try {
                Entity copy = EntityType.loadEntityRecursive(tag, mirror, e -> {
                    e.moveTo(src.getX(), src.getY(), src.getZ(), src.getYRot(), src.getXRot());
                    markEntityCopiedRecursive(e);
                    return e;
                });
                if (copy != null) {
                    mirror.addFreshEntity(copy);
                }
            } catch (Throwable ignored) {
                // 单个实体加载失败（mod 扩展实体 NBT 校验不过）不中断整体
            }
        }
    }

    /** 递归给实体及其骑乘 passenger 打"从备份复制来"标记，避免被刷怪拦截器误伤。 */
    private static void markEntityCopiedRecursive(Entity e) {
        e.getPersistentData().putBoolean(TAG_ENTITY_REWIND_COPIED, true);
        for (Entity passenger : e.getPassengers()) {
            markEntityCopiedRecursive(passenger);
        }
    }

    /** 是否属于"持久型"实体。玩家、物品掉落、经验球、投射物、下落方块、TNT、烟花、区域效果云全部跳过。 */
    private static boolean shouldCopyEntity(Entity e) {
        if (e == null || !e.isAlive()) return false;
        if (e instanceof Player) return false;
        if (e instanceof ItemEntity) return false;
        if (e instanceof ExperienceOrb) return false;
        if (e instanceof Projectile) return false;
        if (e instanceof FallingBlockEntity) return false;
        if (e instanceof PrimedTnt) return false;
        if (e instanceof AreaEffectCloud) return false;
        if (e instanceof LightningBolt) return false;
        return true;
    }

    public static List<Component> buildStatusLines(MinecraftServer server) {
        RewindBackupState state = getState(server);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6§l══ 回溯之虫 · 镜像维度备份状态 ══"));
        lines.add(Component.literal("§f阶段：§e" + localizePhase(state.phase)));
        if (state.phase != RewindBackupState.Phase.IDLE) {
            lines.add(Component.literal("§f源维度：§7" + state.sourceDim));
            lines.add(Component.literal("§f中心 chunk：§7(" + state.centerChunkX + ", " + state.centerChunkZ + ")"));
            lines.add(Component.literal("§f扫描半径：§7" + state.radiusChunks
                    + " chunks (~ " + (state.radiusChunks * 16) + " 格)"));
            lines.add(Component.literal("§a § Phase 1 方块态：§f" + state.blocksScanned
                    + " §7chunks 已扫描"));
            lines.add(Component.literal("§a § Phase 2 方块实体：§f" + state.blockEntitiesScanned
                    + " §7chunks 已扫描"));
            lines.add(Component.literal("§a § Phase 3 生物实体：§f" + state.entitiesScanned
                    + " §7chunks 已扫描"));
            if (isScanning(state)) {
                int budget = switch (state.phase) {
                    case SCAN_BLOCKS -> CHUNKS_PER_TICK_BLOCKS;
                    case SCAN_BLOCK_ENTITIES -> CHUNKS_PER_TICK_BE;
                    case SCAN_ENTITIES -> CHUNKS_PER_TICK_ENTITIES;
                    default -> 1;
                };
                int percent = state.totalChunks <= 0
                        ? 100
                        : (int) (100L * state.scannedChunks / state.totalChunks);
                lines.add(Component.literal("§f当前阶段进度：§a" + state.scannedChunks + "§7/§a"
                        + state.totalChunks + " chunks §7(" + percent + "%)"));
                long remaining = state.totalChunks - state.scannedChunks;
                long estSeconds = remaining / Math.max(1, budget) / 20L;
                long estMinutes = estSeconds / 60L;
                lines.add(Component.literal("§f本阶段预计剩余：§7约 " + estMinutes + " 分钟 §8(每 tick "
                        + budget + " chunks)"));
            }
        }
        ServerLevel mirror = getMirrorLevel(server);
        lines.add(Component.literal("§f镜像维度：" + (mirror == null
                ? "§c不存在（datapack 维度未加载，检查 data/corpse_campus/dimension/rewind_mirror.json）"
                : "§a已加载")));
        return lines;
    }

    private static String localizePhase(RewindBackupState.Phase phase) {
        return switch (phase) {
            case IDLE -> "空闲（未启动备份）";
            case SCAN_BLOCKS -> "Phase 1 · 扫描方块态";
            case SCAN_BLOCK_ENTITIES -> "Phase 2 · 扫描方块实体（箱子/告示牌 NBT）";
            case SCAN_ENTITIES -> "Phase 3 · 扫描生物实体";
            case READY -> "READY（可供 rewind_worm 施放进入）";
        };
    }
}

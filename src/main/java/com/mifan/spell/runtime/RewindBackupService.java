package com.mifan.spell.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责"回溯之虫"镜像维度的分片异步扫描与状态查询。
 * <ul>
 *   <li>镜像维度由 datapack 预注册：{@code corpse_campus:rewind_mirror}（flat void）。</li>
 *   <li>管理员用 {@code /magic rewind backup create <玩家> [半径]} 启动扫描；
 *       每 tick 仅复制 {@link #CHUNKS_PER_TICK} 个 chunk，避免卡服。</li>
 *   <li>仅复制方块态，不迁移方块实体（TileEntity）和实体；
 *       镜像维度作为"时间静止的避难所"使用。</li>
 * </ul>
 */
public final class RewindBackupService {
    public static final ResourceKey<Level> MIRROR_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("corpse_campus", "rewind_mirror"));

    public static final int DEFAULT_RADIUS_BLOCKS = 3000;
    public static final int CHUNKS_PER_TICK = 2;

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
        if (state.phase == RewindBackupState.Phase.SCANNING) {
            return StartResult.BUSY;
        }
        ServerLevel mirror = getMirrorLevel(server);
        if (mirror == null) {
            return StartResult.MIRROR_MISSING;
        }

        int radiusChunks = Math.max(1, (radiusBlocks + 15) >> 4);
        int centerCX = center.getX() >> 4;
        int centerCZ = center.getZ() >> 4;

        state.phase = RewindBackupState.Phase.SCANNING;
        state.sourceDim = sourceLevel.dimension().location().toString();
        state.centerChunkX = centerCX;
        state.centerChunkZ = centerCZ;
        state.radiusChunks = radiusChunks;
        state.scannedChunks = 0;
        state.queue.clear();
        state.startTick = server.getTickCount();
        state.lastUpdateTick = server.getTickCount();

        // 从中心向外按同心方环顺序入队，就近优先
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
        state.sourceDim = "";
        state.setDirty();
    }

    /** 由 ServerTick 驱动的分片扫描。每 tick 仅处理 {@link #CHUNKS_PER_TICK} 个 chunk。 */
    public static void tick(MinecraftServer server) {
        RewindBackupState state = getState(server);
        if (state.phase != RewindBackupState.Phase.SCANNING) {
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

        int processed = 0;
        while (processed < CHUNKS_PER_TICK && !state.queue.isEmpty()) {
            long packed = state.queue.pollFirst();
            int cx = RewindBackupState.unpackX(packed);
            int cz = RewindBackupState.unpackZ(packed);
            copyChunkBlocks(source, mirror, cx, cz);
            state.scannedChunks++;
            processed++;
        }
        state.lastUpdateTick = server.getTickCount();
        state.setDirty();

        if (state.queue.isEmpty()) {
            state.phase = RewindBackupState.Phase.READY;
            state.setDirty();
        }
    }

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

    public static List<Component> buildStatusLines(MinecraftServer server) {
        RewindBackupState state = getState(server);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6§l══ 回溯之虫 · 镜像维度备份状态 ══"));
        lines.add(Component.literal("§f阶段：§e" + state.phase.name()));
        if (state.phase != RewindBackupState.Phase.IDLE) {
            lines.add(Component.literal("§f源维度：§7" + state.sourceDim));
            lines.add(Component.literal("§f中心 chunk：§7(" + state.centerChunkX + ", " + state.centerChunkZ + ")"));
            lines.add(Component.literal("§f扫描半径：§7" + state.radiusChunks
                    + " chunks (~ " + (state.radiusChunks * 16) + " 格)"));
            int percent = state.totalChunks <= 0 ? 100 : (int) (100L * state.scannedChunks / state.totalChunks);
            lines.add(Component.literal("§f进度：§a" + state.scannedChunks + "§7/§a" + state.totalChunks
                    + " chunks §7(" + percent + "%)"));
            if (state.phase == RewindBackupState.Phase.SCANNING) {
                long remaining = state.totalChunks - state.scannedChunks;
                long estTicks = remaining * 20L / Math.max(1, CHUNKS_PER_TICK * 20);
                long estMinutes = estTicks / 60L;
                lines.add(Component.literal("§f预计剩余时间：§7约 " + estMinutes + " 分钟 §8(每 tick "
                        + CHUNKS_PER_TICK + " chunks)"));
            }
        }
        ServerLevel mirror = getMirrorLevel(server);
        lines.add(Component.literal("§f镜像维度：" + (mirror == null
                ? "§c不存在（datapack 维度未加载，检查 data/corpse_campus/dimension/rewind_mirror.json）"
                : "§a已加载")));
        return lines;
    }
}

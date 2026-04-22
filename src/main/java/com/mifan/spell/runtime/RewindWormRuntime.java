package com.mifan.spell.runtime;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * "回溯之虫"(rewind_worm) 法术的玩家侧数据与传送逻辑。
 * <ul>
 *   <li>锚点 NBT：记录玩家进入镜像维度之前的所在维度与坐标；回程时读取并清除。</li>
 *   <li>30 分钟共享 CD：去/回共用一个 {@link #COOLDOWN_TICKS}。</li>
 *   <li>镜像未就绪时直接拒绝施法，避免把玩家传送到一个空维度里。</li>
 *   <li>死亡时保留 CD、清除 in_mirror 标记，避免重生后错误滞留。</li>
 * </ul>
 */
public final class RewindWormRuntime {
    public static final ResourceLocation REWIND_WORM_ID =
            ResourceLocation.fromNamespaceAndPath("corpse_campus", "rewind_worm");

    public static final int COOLDOWN_TICKS = 20 * 60 * 30; // 30 min

    public static final String TAG_ANCHOR_DIM = "corpse_campus_rewind_anchor_dim";
    public static final String TAG_ANCHOR_X = "corpse_campus_rewind_anchor_x";
    public static final String TAG_ANCHOR_Y = "corpse_campus_rewind_anchor_y";
    public static final String TAG_ANCHOR_Z = "corpse_campus_rewind_anchor_z";
    public static final String TAG_ANCHOR_YAW = "corpse_campus_rewind_anchor_yaw";
    public static final String TAG_ANCHOR_PITCH = "corpse_campus_rewind_anchor_pitch";
    public static final String TAG_IN_MIRROR = "corpse_campus_rewind_in_mirror";
    public static final String TAG_LAST_USE_TICK = "corpse_campus_rewind_last_use_tick";

    private RewindWormRuntime() {
    }

    public enum CastOutcome {
        MIRROR_NOT_READY,
        MIRROR_LEVEL_MISSING,
        ON_COOLDOWN,
        INTO_MIRROR,
        OUT_OF_MIRROR,
        ANCHOR_LOST
    }

    public record CastResult(CastOutcome outcome, long remainingCooldownTicks) {
    }

    public static CastResult cast(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!RewindBackupService.isReady(server)) {
            return new CastResult(CastOutcome.MIRROR_NOT_READY, 0L);
        }
        ServerLevel mirror = RewindBackupService.getMirrorLevel(server);
        if (mirror == null) {
            return new CastResult(CastOutcome.MIRROR_LEVEL_MISSING, 0L);
        }

        CompoundTag data = player.getPersistentData();
        long now = server.getTickCount();
        if (data.contains(TAG_LAST_USE_TICK)) {
            long last = data.getLong(TAG_LAST_USE_TICK);
            long remaining = COOLDOWN_TICKS - (now - last);
            if (remaining > 0L) {
                return new CastResult(CastOutcome.ON_COOLDOWN, remaining);
            }
        }

        boolean inMirror = data.getBoolean(TAG_IN_MIRROR);
        if (!inMirror) {
            data.putString(TAG_ANCHOR_DIM, player.level().dimension().location().toString());
            data.putDouble(TAG_ANCHOR_X, player.getX());
            data.putDouble(TAG_ANCHOR_Y, player.getY());
            data.putDouble(TAG_ANCHOR_Z, player.getZ());
            data.putFloat(TAG_ANCHOR_YAW, player.getYRot());
            data.putFloat(TAG_ANCHOR_PITCH, player.getXRot());
            data.putBoolean(TAG_IN_MIRROR, true);
            data.putLong(TAG_LAST_USE_TICK, now);

            player.teleportTo(mirror, player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());
            return new CastResult(CastOutcome.INTO_MIRROR, 0L);
        }

        if (!data.contains(TAG_ANCHOR_DIM)) {
            return new CastResult(CastOutcome.ANCHOR_LOST, 0L);
        }
        ResourceLocation anchorDimId = ResourceLocation.tryParse(data.getString(TAG_ANCHOR_DIM));
        if (anchorDimId == null) {
            return new CastResult(CastOutcome.ANCHOR_LOST, 0L);
        }
        ServerLevel anchorLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, anchorDimId));
        if (anchorLevel == null) {
            return new CastResult(CastOutcome.ANCHOR_LOST, 0L);
        }
        double x = data.getDouble(TAG_ANCHOR_X);
        double y = data.getDouble(TAG_ANCHOR_Y);
        double z = data.getDouble(TAG_ANCHOR_Z);
        float yaw = data.getFloat(TAG_ANCHOR_YAW);
        float pitch = data.getFloat(TAG_ANCHOR_PITCH);

        data.putBoolean(TAG_IN_MIRROR, false);
        data.putLong(TAG_LAST_USE_TICK, now);
        data.remove(TAG_ANCHOR_DIM);
        data.remove(TAG_ANCHOR_X);
        data.remove(TAG_ANCHOR_Y);
        data.remove(TAG_ANCHOR_Z);
        data.remove(TAG_ANCHOR_YAW);
        data.remove(TAG_ANCHOR_PITCH);

        player.teleportTo(anchorLevel, x, y, z, yaw, pitch);
        return new CastResult(CastOutcome.OUT_OF_MIRROR, 0L);
    }

    /**
     * 死亡/克隆时：保留 CD，清除 in_mirror 标志，避免重生后卡死在"我在镜像内"的假状态。
     * 锚点不再复制（死亡后老锚点已无意义）。
     */
    public static void onDeath(CompoundTag oldData, CompoundTag newData) {
        if (oldData.contains(TAG_LAST_USE_TICK)) {
            newData.putLong(TAG_LAST_USE_TICK, oldData.getLong(TAG_LAST_USE_TICK));
        }
        newData.putBoolean(TAG_IN_MIRROR, false);
    }

    public static Component formatRemaining(long ticks) {
        long seconds = Math.max(0L, ticks) / 20L;
        long minutes = seconds / 60L;
        long remSeconds = seconds % 60L;
        return Component.literal(minutes + " 分 " + remSeconds + " 秒");
    }
}

package com.mifan.spell.runtime;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * "回溯之虫"(rewind_worm) 法术的玩家侧数据与传送逻辑。
 * <ul>
 *   <li>锚点 NBT：记录玩家进入镜像维度之前的所在维度与坐标；回程时读取并清除。</li>
 *   <li>冷却完全交由 ISS 法术系统的 {@code defaultConfig.setCooldownSeconds(...)} 管理；
 *       本类不再维护任何 NBT 冷却字段。</li>
 *   <li>镜像未就绪时直接拒绝施法，避免把玩家传送到一个空维度里。</li>
 *   <li>死亡时清除 in_mirror 标记，避免重生后错误滞留。</li>
 * </ul>
 */
public final class RewindWormRuntime {
    public static final ResourceLocation REWIND_WORM_ID =
            ResourceLocation.fromNamespaceAndPath("corpse_campus", "rewind_worm");

    public static final String TAG_ANCHOR_DIM = "corpse_campus_rewind_anchor_dim";
    public static final String TAG_ANCHOR_X = "corpse_campus_rewind_anchor_x";
    public static final String TAG_ANCHOR_Y = "corpse_campus_rewind_anchor_y";
    public static final String TAG_ANCHOR_Z = "corpse_campus_rewind_anchor_z";
    public static final String TAG_ANCHOR_YAW = "corpse_campus_rewind_anchor_yaw";
    public static final String TAG_ANCHOR_PITCH = "corpse_campus_rewind_anchor_pitch";
    public static final String TAG_IN_MIRROR = "corpse_campus_rewind_in_mirror";

    private RewindWormRuntime() {
    }

    public enum CastOutcome {
        MIRROR_NOT_READY,
        MIRROR_LEVEL_MISSING,
        INTO_MIRROR,
        OUT_OF_MIRROR,
        ANCHOR_LOST
    }

    public record CastResult(CastOutcome outcome) {
    }

    public static CastResult cast(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!RewindBackupService.isReady(server)) {
            return new CastResult(CastOutcome.MIRROR_NOT_READY);
        }
        ServerLevel mirror = RewindBackupService.getMirrorLevel(server);
        if (mirror == null) {
            return new CastResult(CastOutcome.MIRROR_LEVEL_MISSING);
        }

        CompoundTag data = player.getPersistentData();
        boolean inMirror = data.getBoolean(TAG_IN_MIRROR);
        if (!inMirror) {
            data.putString(TAG_ANCHOR_DIM, player.level().dimension().location().toString());
            data.putDouble(TAG_ANCHOR_X, player.getX());
            data.putDouble(TAG_ANCHOR_Y, player.getY());
            data.putDouble(TAG_ANCHOR_Z, player.getZ());
            data.putFloat(TAG_ANCHOR_YAW, player.getYRot());
            data.putFloat(TAG_ANCHOR_PITCH, player.getXRot());
            data.putBoolean(TAG_IN_MIRROR, true);

            player.teleportTo(mirror, player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());
            return new CastResult(CastOutcome.INTO_MIRROR);
        }

        if (!data.contains(TAG_ANCHOR_DIM)) {
            return new CastResult(CastOutcome.ANCHOR_LOST);
        }
        ResourceLocation anchorDimId = ResourceLocation.tryParse(data.getString(TAG_ANCHOR_DIM));
        if (anchorDimId == null) {
            return new CastResult(CastOutcome.ANCHOR_LOST);
        }
        ServerLevel anchorLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, anchorDimId));
        if (anchorLevel == null) {
            return new CastResult(CastOutcome.ANCHOR_LOST);
        }
        double x = data.getDouble(TAG_ANCHOR_X);
        double y = data.getDouble(TAG_ANCHOR_Y);
        double z = data.getDouble(TAG_ANCHOR_Z);
        float yaw = data.getFloat(TAG_ANCHOR_YAW);
        float pitch = data.getFloat(TAG_ANCHOR_PITCH);

        data.putBoolean(TAG_IN_MIRROR, false);
        data.remove(TAG_ANCHOR_DIM);
        data.remove(TAG_ANCHOR_X);
        data.remove(TAG_ANCHOR_Y);
        data.remove(TAG_ANCHOR_Z);
        data.remove(TAG_ANCHOR_YAW);
        data.remove(TAG_ANCHOR_PITCH);

        player.teleportTo(anchorLevel, x, y, z, yaw, pitch);
        return new CastResult(CastOutcome.OUT_OF_MIRROR);
    }

    /**
     * 死亡/克隆时：清除 in_mirror 标志，避免重生后卡死在"我在镜像内"的假状态。
     * 锚点不再复制（死亡后老锚点已无意义）。
     */
    public static void onDeath(CompoundTag oldData, CompoundTag newData) {
        newData.putBoolean(TAG_IN_MIRROR, false);
    }
}

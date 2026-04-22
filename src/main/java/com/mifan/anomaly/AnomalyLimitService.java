package com.mifan.anomaly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端持久化数据：记录已觉醒玩家 UUID 集合，
 * 以及运行时可调的上限数值与开关。
 *
 * capValue / capEnabled 以 SavedData 为权威来源，
 * 仅在首次创建（无存档）时从 AnomalyConfig 读取默认值。
 * 此后通过 /magic limit set|enable|disable 修改，持久化到 .dat 文件中。
 */
public final class AnomalyLimitService extends SavedData {

    private static final String DATA_NAME = "corpse_campus_anomaly_limit";
    private static final String TAG_UUIDS = "AwakenedUUIDs";
    private static final String TAG_CAP_VALUE = "CapValue";
    private static final String TAG_CAP_ENABLED = "CapEnabled";

    private final Set<UUID> awakenedPlayers = new HashSet<>();
    private int capValue;
    private boolean capEnabled;

    /** 首次创建（无存档）时从配置读取默认值 */
    private AnomalyLimitService() {
        this.capValue = AnomalyConfig.globalCapValue;
        this.capEnabled = AnomalyConfig.globalCapEnabled;
    }

    /** 从已有存档恢复 */
    private AnomalyLimitService(CompoundTag tag) {
        // 上限参数：优先读存档，不存在则降级到配置默认值
        this.capValue = tag.contains(TAG_CAP_VALUE) ? tag.getInt(TAG_CAP_VALUE) : AnomalyConfig.globalCapValue;
        this.capEnabled = tag.contains(TAG_CAP_ENABLED) ? tag.getBoolean(TAG_CAP_ENABLED) : AnomalyConfig.globalCapEnabled;

        ListTag list = tag.getList(TAG_UUIDS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                awakenedPlayers.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(TAG_CAP_VALUE, capValue);
        tag.putBoolean(TAG_CAP_ENABLED, capEnabled);
        ListTag list = new ListTag();
        for (UUID uuid : awakenedPlayers) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put(TAG_UUIDS, list);
        return tag;
    }

    public static AnomalyLimitService get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(AnomalyLimitService::new, AnomalyLimitService::new, DATA_NAME);
    }

    // ─── 上限设置（运行时可调） ────────────────────────────────────────

    public int getCapValue() {
        return capValue;
    }

    public boolean isCapEnabled() {
        return capEnabled;
    }

    /** 设置上限数值并持久化，返回旧值。 */
    public int setCapValue(int newValue) {
        int old = this.capValue;
        this.capValue = Math.max(1, newValue);
        setDirty();
        return old;
    }

    /** 开启或关闭上限并持久化，返回变更前的状态。 */
    public boolean setCapEnabled(boolean enabled) {
        boolean old = this.capEnabled;
        this.capEnabled = enabled;
        setDirty();
        return old;
    }

    // ─── 觉醒统计 ─────────────────────────────────────────────────────

    public int getAnomalyCount() {
        return awakenedPlayers.size();
    }

    public boolean isCapReached() {
        if (!capEnabled || !AnomalyConfig.countAwakenedPlayers) {
            return false;
        }
        return awakenedPlayers.size() >= capValue;
    }

    public boolean isAwakened(UUID uuid) {
        return awakenedPlayers.contains(uuid);
    }

    /** 将玩家标记为已觉醒，首次加入返回 true。 */
    public boolean markAwakened(UUID uuid) {
        if (awakenedPlayers.add(uuid)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** 将玩家从已觉醒集合移除，首次移除返回 true。用于死亡后清除觉醒状态。 */
    public boolean clearAwakened(UUID uuid) {
        if (awakenedPlayers.remove(uuid)) {
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * 重新扫描在线玩家，更新觉醒集合；离线玩家记录保留。
     */
    public int recountFromServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (AnomalyBookService.hasLoadedSpells(player)) {
                awakenedPlayers.add(player.getUUID());
            } else {
                awakenedPlayers.remove(player.getUUID());
            }
        }
        setDirty();
        return awakenedPlayers.size();
    }

    public List<Component> buildInfoLines() {
        List<Component> lines = new ArrayList<>();
        int count = awakenedPlayers.size();
        String capStr = capEnabled ? String.valueOf(capValue) : "已关闭";
        String statusStr = capEnabled && AnomalyConfig.countAwakenedPlayers
                ? (isCapReached() ? "§c已满额，B 级特性掉落已禁用§r" : "§a未满额§r")
                : "§7（上限已关闭）§r";

        lines.add(Component.literal("[异常上限统计]"));
        lines.add(Component.literal("- 当前已觉醒: " + count + " 人"));
        lines.add(Component.literal("- 上限: " + capStr
                + (capEnabled ? "  §8（可用 /magic limit set <数值> 调整）§r" : "")));
        lines.add(Component.literal("- 状态: " + statusStr));
        return lines;
    }
}

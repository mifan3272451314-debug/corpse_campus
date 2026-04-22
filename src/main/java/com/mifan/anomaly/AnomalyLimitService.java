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
 * 服务端持久化数据：记录已觉醒（书内持有法术）的玩家 UUID 集合，
 * 用于全服异常者数量上限的统计与校验。
 */
public final class AnomalyLimitService extends SavedData {

    private static final String DATA_NAME = "corpse_campus_anomaly_limit";
    private static final String TAG_UUIDS = "AwakenedUUIDs";

    private final Set<UUID> awakenedPlayers = new HashSet<>();

    private AnomalyLimitService() {}

    private AnomalyLimitService(CompoundTag tag) {
        ListTag list = tag.getList(TAG_UUIDS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                awakenedPlayers.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID uuid : awakenedPlayers) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put(TAG_UUIDS, list);
        return tag;
    }

    public static AnomalyLimitService get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        // Forge 1.20.1 (47.x) uses the older computeIfAbsent(deserializer, constructor, key) API
        return storage.computeIfAbsent(AnomalyLimitService::new, AnomalyLimitService::new, DATA_NAME);
    }

    public int getAnomalyCount() {
        return awakenedPlayers.size();
    }

    public boolean isCapReached() {
        if (!AnomalyConfig.globalCapEnabled || !AnomalyConfig.countAwakenedPlayers) {
            return false;
        }
        return awakenedPlayers.size() >= AnomalyConfig.globalCapValue;
    }

    public boolean isAwakened(UUID uuid) {
        return awakenedPlayers.contains(uuid);
    }

    /**
     * 将玩家标记为已觉醒。返回 true 表示这是首次标记（计数新增）。
     */
    public boolean markAwakened(UUID uuid) {
        if (awakenedPlayers.add(uuid)) {
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * 重新扫描：对在线玩家根据当前书状态更新觉醒集合；
     * 离线玩家的原有记录原样保留（无法验证）。
     */
    public int recountFromServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (AnomalyBookService.hasLoadedSpells(player)) {
                awakenedPlayers.add(player.getUUID());
            } else {
                // 在线玩家当前无法术说明已被清空，移出计数
                awakenedPlayers.remove(player.getUUID());
            }
        }
        setDirty();
        return awakenedPlayers.size();
    }

    public List<Component> buildInfoLines() {
        List<Component> lines = new ArrayList<>();
        int count = awakenedPlayers.size();
        boolean capEnabled = AnomalyConfig.globalCapEnabled && AnomalyConfig.countAwakenedPlayers;
        String capStr = capEnabled ? String.valueOf(AnomalyConfig.globalCapValue) : "已关闭";
        String statusStr = capEnabled
                ? (isCapReached() ? "§c已满额，B 级特性掉落已禁用§r" : "§a未满额§r")
                : "§7（上限已关闭）§r";

        lines.add(Component.literal("[异常上限统计]"));
        lines.add(Component.literal("- 当前已觉醒: " + count + " 人"));
        lines.add(Component.literal("- 上限: " + capStr));
        lines.add(Component.literal("- 状态: " + statusStr));
        return lines;
    }
}

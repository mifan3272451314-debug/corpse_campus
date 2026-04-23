package com.mifan.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 维度级 SavedData：记录每个战利品箱在"首次被看到时"的 LootTable + Seed，
 * 供 LootRefreshItem 恢复已被打开过的箱子。
 *
 * 存档文件位于 <world>/<dim>/data/corpse_campus_loot_backup.dat。
 */
public class LootBackupData extends SavedData {

    public static final String DATA_NAME = "corpse_campus_loot_backup";

    private final Map<BlockPos, Entry> backups = new HashMap<>();

    public record Entry(ResourceLocation lootTable, long seed) {
    }

    public static LootBackupData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                LootBackupData::load,
                LootBackupData::new,
                DATA_NAME);
    }

    public LootBackupData() {
    }

    public static LootBackupData load(CompoundTag tag) {
        LootBackupData data = new LootBackupData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            BlockPos pos = new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z"));
            ResourceLocation lt = ResourceLocation.tryParse(e.getString("loot_table"));
            long seed = e.getLong("seed");
            if (lt != null) {
                data.backups.put(pos, new Entry(lt, seed));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        backups.forEach((pos, entry) -> {
            CompoundTag e = new CompoundTag();
            e.putInt("x", pos.getX());
            e.putInt("y", pos.getY());
            e.putInt("z", pos.getZ());
            e.putString("loot_table", entry.lootTable().toString());
            e.putLong("seed", entry.seed());
            list.add(e);
        });
        tag.put("entries", list);
        return tag;
    }

    public void putIfAbsent(BlockPos pos, ResourceLocation lootTable, long seed) {
        BlockPos immutable = pos.immutable();
        if (!backups.containsKey(immutable)) {
            backups.put(immutable, new Entry(lootTable, seed));
            setDirty();
        }
    }

    public Map<BlockPos, Entry> getBackups() {
        return backups;
    }
}

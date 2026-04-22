package com.mifan.spell.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.Deque;

public final class RewindBackupState extends SavedData {
    public static final String FILE_ID = "corpse_campus_rewind_backup";

    public enum Phase { IDLE, SCANNING, READY }

    public Phase phase = Phase.IDLE;
    public String sourceDim = "";
    public int centerChunkX = 0;
    public int centerChunkZ = 0;
    public int radiusChunks = 0;
    public int scannedChunks = 0;
    public int totalChunks = 0;
    public long startTick = 0L;
    public long lastUpdateTick = 0L;
    public final Deque<Long> queue = new ArrayDeque<>();

    public RewindBackupState() {
    }

    public static RewindBackupState load(CompoundTag tag) {
        RewindBackupState s = new RewindBackupState();
        s.phase = readPhase(tag.getString("phase"));
        s.sourceDim = tag.getString("sourceDim");
        s.centerChunkX = tag.getInt("cx");
        s.centerChunkZ = tag.getInt("cz");
        s.radiusChunks = tag.getInt("radius");
        s.scannedChunks = tag.getInt("scanned");
        s.totalChunks = tag.getInt("total");
        s.startTick = tag.getLong("startTick");
        s.lastUpdateTick = tag.getLong("lastUpdateTick");
        ListTag list = tag.getList("queue", Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            Tag entry = list.get(i);
            if (entry instanceof LongTag lt) {
                s.queue.add(lt.getAsLong());
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("phase", phase.name());
        tag.putString("sourceDim", sourceDim);
        tag.putInt("cx", centerChunkX);
        tag.putInt("cz", centerChunkZ);
        tag.putInt("radius", radiusChunks);
        tag.putInt("scanned", scannedChunks);
        tag.putInt("total", totalChunks);
        tag.putLong("startTick", startTick);
        tag.putLong("lastUpdateTick", lastUpdateTick);
        ListTag list = new ListTag();
        for (Long packed : queue) {
            list.add(LongTag.valueOf(packed));
        }
        tag.put("queue", list);
        return tag;
    }

    private static Phase readPhase(String name) {
        if (name == null || name.isEmpty()) return Phase.IDLE;
        try {
            return Phase.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return Phase.IDLE;
        }
    }

    public static long packChunk(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    public static int unpackX(long packed) {
        return ChunkPos.getX(packed);
    }

    public static int unpackZ(long packed) {
        return ChunkPos.getZ(packed);
    }
}

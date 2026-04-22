package com.mifan.spell.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 全局"每日限定"法术的刷新代数。
 * <p>
 * 设计目标：每次使用一次"每日限定"法术（目前只有日轮金乌）后，玩家 NBT 写入当时的 {@code generation}；
 * 管理员通过 {@code /magic refresh all} 调用 {@link #bumpGeneration(MinecraftServer)} 让全局 generation++，
 * 于是所有玩家（无论在线或离线）下一次施法时比较 {@code 玩家记录的 gen < 当前 gen} → 视为已刷新。
 * 无需遍历玩家，也无需维护日期/游戏日逻辑。
 */
public final class DailyAbilityRefreshState extends SavedData {
    private static final String DATA_NAME = "corpse_campus_daily_refresh";
    private static final String KEY_GENERATION = "Generation";

    private long generation;

    private DailyAbilityRefreshState() {
        this.generation = 0L;
    }

    private DailyAbilityRefreshState(long generation) {
        this.generation = generation;
    }

    private static DailyAbilityRefreshState load(CompoundTag tag) {
        return new DailyAbilityRefreshState(tag.getLong(KEY_GENERATION));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong(KEY_GENERATION, generation);
        return tag;
    }

    public long getGeneration() {
        return generation;
    }

    public long bumpGeneration() {
        generation++;
        setDirty();
        return generation;
    }

    public static DailyAbilityRefreshState get(MinecraftServer server) {
        var overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new DailyAbilityRefreshState();
        }
        return overworld.getDataStorage().computeIfAbsent(
                DailyAbilityRefreshState::load,
                DailyAbilityRefreshState::new,
                DATA_NAME);
    }

    public static long currentGeneration(MinecraftServer server) {
        return get(server).getGeneration();
    }

    public static long bumpGeneration(MinecraftServer server) {
        return get(server).bumpGeneration();
    }
}

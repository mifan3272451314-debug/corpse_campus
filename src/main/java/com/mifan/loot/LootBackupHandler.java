package com.mifan.loot;

import com.mifan.corpsecampus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 被动备份：当 chunk 加载或玩家右键交互时，如果遇到未打开过的战利品容器，
 * 把它的 LootTable + Seed 写入维度级 {@link LootBackupData}，供 LootRefreshItem 恢复。
 */
@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class LootBackupHandler {

    private LootBackupHandler() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        ChunkAccess chunkAccess = event.getChunk();
        if (!(chunkAccess instanceof LevelChunk chunk)) {
            return;
        }
        LootBackupData data = LootBackupData.get(serverLevel);
        chunk.getBlockEntities().forEach((pos, be) -> tryBackup(data, pos, be));
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockEntity be = serverLevel.getBlockEntity(event.getPos());
        if (be != null) {
            tryBackup(LootBackupData.get(serverLevel), event.getPos(), be);
        }
    }

    private static void tryBackup(LootBackupData data, BlockPos pos, BlockEntity be) {
        if (!(be instanceof RandomizableContainerBlockEntity)) {
            return;
        }
        CompoundTag tag = be.saveWithoutMetadata();
        if (!tag.contains("LootTable", Tag.TAG_STRING)) {
            return;
        }
        ResourceLocation lootTable = ResourceLocation.tryParse(tag.getString("LootTable"));
        if (lootTable == null) {
            return;
        }
        long seed = tag.contains("LootTableSeed", Tag.TAG_LONG) ? tag.getLong("LootTableSeed") : 0L;
        data.putIfAbsent(pos, lootTable, seed);
    }
}

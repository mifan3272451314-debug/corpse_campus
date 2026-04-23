package com.mifan.item;

import com.mifan.loot.LootBackupData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 管理员调试物品：右键刷新已被打开过的战利品箱。
 *
 * 识别机制：依赖 {@link com.mifan.loot.LootBackupHandler} 在 chunk 加载 / 玩家右键时
 * 备份的 LootTable + Seed 数据（维度级 SavedData）。
 *
 * 使用方式：
 *   - 普通右键：刷新玩家所在维度已加载区块内的所有战利品箱
 *   - Shift + 右键：刷新所有维度已加载区块内的所有战利品箱
 *
 * 刷新动作 = 清空当前箱内物品 + 重新赋予原 LootTable + 新随机 seed，
 * 下次打开时 MC 会重新 roll 一次战利品。
 *
 * 未加载区块的备份条目会被跳过（不强制加载，避免卡服）。
 * mod 装入前就被打开过的箱子没有备份数据，无法恢复（tooltip 有说明）。
 */
public class LootRefreshItem extends Item {

    public LootRefreshItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }

        boolean allDimensions = sp.isShiftKeyDown();
        MinecraftServer server = sp.server;

        int refreshed = 0;
        int skipped = 0;
        int dims = 0;

        for (ServerLevel serverLevel : server.getAllLevels()) {
            if (!allDimensions && serverLevel != sp.serverLevel()) {
                continue;
            }
            dims++;
            int[] result = refreshLevel(serverLevel);
            refreshed += result[0];
            skipped += result[1];
        }

        sp.displayClientMessage(
                Component.translatable("message.corpse_campus.loot_refreshed",
                        refreshed, skipped, dims).withStyle(ChatFormatting.AQUA),
                false);

        return InteractionResultHolder.consume(stack);
    }

    private static int[] refreshLevel(ServerLevel level) {
        LootBackupData data = LootBackupData.get(level);
        int refreshed = 0;
        int skipped = 0;

        for (Map.Entry<BlockPos, LootBackupData.Entry> entry : data.getBackups().entrySet()) {
            BlockPos pos = entry.getKey();
            LootBackupData.Entry backup = entry.getValue();

            if (!level.isLoaded(pos)) {
                skipped++;
                continue;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof RandomizableContainerBlockEntity rcbe)) {
                continue;
            }
            rcbe.clearContent();
            rcbe.setLootTable(backup.lootTable(), level.random.nextLong());
            rcbe.setChanged();
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 3);
            refreshed++;
        }
        return new int[] { refreshed, skipped };
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.corpse_campus.loot_refresh_hint")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.corpse_campus.loot_refresh_shift")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.corpse_campus.loot_refresh_warning")
                .withStyle(ChatFormatting.RED));
    }
}

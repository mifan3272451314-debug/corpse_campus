package com.mifan.item;

import com.mifan.anomaly.AnomalyBookService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;

/**
 * 管理员调试物品：右键直接把玩家完全重置回"白板"状态。
 *
 * 清理范围（用户 2026-04-23 口径）：
 *   1) 异常法术 / 序列绑定 / 位阶字段（{@link AnomalyBookService#unawakenPlayer}）
 *   2) 自然觉醒进度 NBT（NaturalAwakeningProgress / NaturalAwakeningDone）
 *   3) 经验等级 + 经验进度
 *   4) 主物品栏 + 装备槽 + 副手（{@link Player#getInventory()}.clearContent）
 *   5) 全部 Curios 槽位
 *   6) 全部状态效果
 *   7) 当前血量回满 + 饥饿回满
 *   8) 血量上限自然恢复（书已清空，AnomalyBookService 实时扫描自动归零加成）
 *
 * 不做权限校验：放在创造标签页里，由用户自己控制分发。
 */
public class AbilityClearItem extends Item {

    private static final String PERSISTED_ROOT = "AnomalyP0";
    private static final String NATURAL_PROGRESS = "NaturalAwakeningProgress";
    private static final String NATURAL_DONE = "NaturalAwakeningDone";

    public AbilityClearItem() {
        super(new Item.Properties().stacksTo(16));
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

        // 1) 法术 + 序列 + 位阶 + LimitService 觉醒计数
        AnomalyBookService.unawakenPlayer(sp);

        // 1.5) 觉醒后无敌窗口标记
        AnomalyBookService.clearAwakeningProtection(sp);

        // 2) 自然觉醒进度
        clearNaturalAwakeningProgress(sp);

        // 3) 经验：等级 → 0、进度 → 0、总经验 → 0
        //   - giveExperienceLevels(-level) 会触发 ServerPlayer 的同步逻辑；
        //   - experienceProgress / totalExperience 改动后由 ServerPlayer.doTick 自动同步给客户端。
        sp.giveExperienceLevels(-sp.experienceLevel);
        sp.experienceProgress = 0f;
        sp.totalExperience = 0;

        // 4) 主物品栏 + 装备槽 + 副手（注意：清掉物品栏会同时把"清除核心"本身一起清掉，这是预期行为）
        sp.getInventory().clearContent();

        // 5) Curios 全部槽位
        clearAllCurios(sp);

        // 6) 状态效果
        sp.removeAllEffects();

        // 7) 血量 + 饥饿回满
        sp.setHealth(sp.getMaxHealth());
        sp.getFoodData().setFoodLevel(20);
        sp.getFoodData().setSaturation(5.0F);

        // 8) 强制重算异常书数值（书已被清空，加成自动归零）
        AnomalyBookService.forceRecalc(sp);

        sp.displayClientMessage(
                Component.translatable("message.corpse_campus.ability_cleared")
                        .withStyle(ChatFormatting.AQUA), false);
        return InteractionResultHolder.consume(stack);
    }

    private static void clearNaturalAwakeningProgress(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag persisted = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(PERSISTED_ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag anomalyData = persisted.getCompound(PERSISTED_ROOT);
        anomalyData.remove(NATURAL_PROGRESS);
        anomalyData.remove(NATURAL_DONE);
    }

    private static void clearAllCurios(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(inv ->
                inv.getCurios().forEach((slotId, handler) -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        stacks.setStackInSlot(i, ItemStack.EMPTY);
                    }
                    IDynamicStackHandler cosmetics = handler.getCosmeticStacks();
                    for (int i = 0; i < cosmetics.getSlots(); i++) {
                        cosmetics.setStackInSlot(i, ItemStack.EMPTY);
                    }
                    handler.update();
                }));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.corpse_campus.ability_clear_hint")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.corpse_campus.ability_clear_warning")
                .withStyle(ChatFormatting.RED));
    }
}

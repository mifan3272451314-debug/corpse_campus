package com.mifan.item;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalySpellRank;
import com.mifan.anomaly.RuleChecker;
import net.minecraft.ChatFormatting;
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

import java.util.List;

/**
 * 管理员调试物品：右键直接把玩家异常书的"最高位阶"字段设为指定 rank。
 *
 * 注意：本物品只改 BOOK_HIGHEST_RANK 字段，不发法术。位阶字段的实际血量/法力加成
 * 仍按 {@link AnomalyBookService#computeLoadedHighestRank} 实时扫书内法术决定，
 * 单纯改字段不会让玩家凭空获得 +HP / +Mana。要拿到加成，仍需让玩家书内搭载对应位阶的法术。
 */
public class RankBlessingItem extends Item {

    private final AnomalySpellRank rank;

    public RankBlessingItem(AnomalySpellRank rank) {
        super(new Item.Properties().stacksTo(16));
        this.rank = rank;
    }

    public AnomalySpellRank getRank() {
        return rank;
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

        var ruleBlock = AnomalyBookService.checkRule(sp, rank, RuleChecker.Channel.RANK_BLESSING);
        if (ruleBlock.isPresent()) {
            sp.displayClientMessage(ruleBlock.get().copy().withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        boolean ok = AnomalyBookService.setHighestRank(sp, rank);
        if (!ok) {
            sp.displayClientMessage(
                    Component.translatable("message.corpse_campus.absorb_no_book")
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        sp.displayClientMessage(
                Component.translatable("message.corpse_campus.rank_core_applied", rank.name())
                        .withStyle(ChatFormatting.AQUA), false);
        sp.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.corpse_campus.rank_core_hint", rank.name())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.corpse_campus.rank_core_warning")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

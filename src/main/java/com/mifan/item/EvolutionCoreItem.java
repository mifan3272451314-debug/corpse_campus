package com.mifan.item;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalyBookService.AbsorbResult;
import com.mifan.anomaly.AnomalyBookService.SpellSpec;
import com.mifan.anomaly.AnomalySpellRank;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 进化觉醒核心：胚胎在觉醒条件达成后原位转化成的可消耗物品。
 *
 * 与 {@link SpellScrollItem} 复用同一套"NBT 绑 spellId → applyScrollSpell 装书"通道，但做语义隔离：
 *   - 核心只承载 A 级 spell（胚胎进化的终点是 A 级）
 *   - 右键消耗 1 枚，由 {@link AnomalyBookService#applyScrollSpell} 走已觉醒 + 主序列匹配 + 按需升阶
 *   - 外观强化：A 级 foil + 稀有度 RARE，S 级保留（理论上用不到，兼容 Direct/Admin 路径）
 *
 * 与胚胎 / 卷轴的身份分离：
 *   - 胚胎 → 被动凭证，不可消耗
 *   - 核心 → 可消耗凭证，只能通过"进化条件达成后胚胎原位转核心"得到
 *   - 卷轴 → 管理员赠送 / 掉落（通用通道，可 B/A/S 任意阶位）
 */
public class EvolutionCoreItem extends Item {

    public static final String TAG_CORE_SPELL_ID = "EvolutionCoreSpellId";

    public EvolutionCoreItem() {
        super(new Item.Properties().stacksTo(16));
    }

    /** 给定目标 A 级法术 id 产出 1 个核心（用于胚胎→核心转化与创造栏）。 */
    public static ItemStack createFor(ResourceLocation spellId) {
        ItemStack stack = new ItemStack(com.mifan.registry.ModItems.EVOLUTION_CORE.get());
        stack.getOrCreateTag().putString(TAG_CORE_SPELL_ID, spellId.toString());
        return stack;
    }

    @Nullable
    public static ResourceLocation getSpellId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_CORE_SPELL_ID)) {
            return null;
        }
        String raw = tag.getString(TAG_CORE_SPELL_ID);
        return raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
    }

    @Nullable
    public static AnomalySpellRank getRank(ItemStack stack) {
        ResourceLocation spellId = getSpellId(stack);
        if (spellId == null) {
            return null;
        }
        SpellSpec spec = AnomalyBookService.getSpellSpec(spellId);
        return spec == null ? null : spec.rank();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        ResourceLocation spellId = getSpellId(stack);
        if (spellId == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.corpse_campus.evolution_core_invalid")
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        AbsorbResult result = AnomalyBookService.applyScrollSpell(serverPlayer, spellId);
        serverPlayer.displayClientMessage(result.message(), false);
        if (result.success()) {
            stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        AnomalySpellRank rank = getRank(stack);
        if (rank == AnomalySpellRank.S) return Rarity.EPIC;
        return Rarity.RARE;
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation spellId = getSpellId(stack);
        if (spellId == null) {
            return super.getName(stack);
        }
        SpellSpec spec = AnomalyBookService.getSpellSpec(spellId);
        if (spec == null) {
            return super.getName(stack);
        }
        Component schoolName = Component.translatable(
                "school." + spec.schoolId().getNamespace() + "." + spec.schoolId().getPath());
        return Component.translatable("item.corpse_campus.evolution_core.configured",
                schoolName, spec.zhName(), rankLabel(spec.rank()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation spellId = getSpellId(stack);
        if (spellId == null) {
            tooltip.add(Component.translatable("tooltip.corpse_campus.evolution_core_unconfigured")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        SpellSpec spec = AnomalyBookService.getSpellSpec(spellId);
        if (spec == null) {
            tooltip.add(Component.translatable("tooltip.corpse_campus.scroll_missing_spec",
                    spellId.toString()).withStyle(ChatFormatting.RED));
            return;
        }

        Component schoolName = Component.translatable(
                "school." + spec.schoolId().getNamespace() + "." + spec.schoolId().getPath());

        tooltip.add(Component.translatable("tooltip.corpse_campus.evolution_core_school", schoolName)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.corpse_campus.evolution_core_spell", spec.zhName())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.corpse_campus.evolution_core_rank", rankLabel(spec.rank()))
                .withStyle(rankColor(spec.rank())));
        tooltip.add(Component.translatable("tooltip.corpse_campus.evolution_core_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent rankLabel(AnomalySpellRank rank) {
        return Component.literal(rank.name() + "级");
    }

    private static ChatFormatting rankColor(AnomalySpellRank rank) {
        return switch (rank) {
            case B -> ChatFormatting.GREEN;
            case A -> ChatFormatting.LIGHT_PURPLE;
            case S -> ChatFormatting.GOLD;
        };
    }
}

package com.mifan.item;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalyBookService.SpellSpec;
import com.mifan.anomaly.AnomalySpellRank;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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
 * 异能胚胎:纯标识物品,无任何行为。
 *
 * 设计与 {@link SpellScrollItem} 同构——单 Item 类 + NBT 存 spellId,创造栏按法术批量生成。
 * 与"核心"(SpellScroll)的关键差异:
 *   - {@link #use} 返回 pass,不触发觉醒、不装法术、不改位阶;
 *   - 按法术位阶给出 B/A/S 视觉差异:
 *       · B 级:纯文本,无附魔发光
 *       · A 级:{@link #isFoil} 返回 true,显示附魔闪光
 *       · S 级:foil + Rarity.EPIC(金色物品名),进一步视觉强化
 *   - 保留为"判断用":其它代码可 instanceof SpellEmbryoItem 识别。
 *
 * 贴图机制和卷轴一致:客户端通过 ItemProperties "corpse_campus:school" 读 NBT 里的 spellId 映射到 0-4,
 * {@code spell_embryo.json} 的 overrides 指向 5 张 embryo_<school>.png。
 */
public class SpellEmbryoItem extends Item {

    public static final String TAG_EMBRYO_SPELL_ID = "EmbryoSpellId";

    public SpellEmbryoItem() {
        super(new Item.Properties().stacksTo(16));
    }

    /** 产出一枚绑定了该法术的胚胎 ItemStack(创造栏/指令/配方产出的唯一入口)。 */
    public static ItemStack createFor(ResourceLocation spellId) {
        ItemStack stack = new ItemStack(com.mifan.registry.ModItems.SPELL_EMBRYO.get());
        stack.getOrCreateTag().putString(TAG_EMBRYO_SPELL_ID, spellId.toString());
        return stack;
    }

    @Nullable
    public static ResourceLocation getSpellId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_EMBRYO_SPELL_ID)) {
            return null;
        }
        String raw = tag.getString(TAG_EMBRYO_SPELL_ID);
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
        // 框架阶段:右键无任何效果
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /** A/S 级胚胎带附魔发光(enchantment glint)。 */
    @Override
    public boolean isFoil(ItemStack stack) {
        AnomalySpellRank rank = getRank(stack);
        return rank == AnomalySpellRank.A || rank == AnomalySpellRank.S;
    }

    /** S 级胚胎物品名显示为金色(Rarity.EPIC),强化"手持特殊渲染"观感。 */
    @Override
    public Rarity getRarity(ItemStack stack) {
        AnomalySpellRank rank = getRank(stack);
        if (rank == AnomalySpellRank.S) return Rarity.EPIC;
        if (rank == AnomalySpellRank.A) return Rarity.RARE;
        return Rarity.COMMON;
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
        // "虚境·音波·B级胚胎"
        return Component.translatable("item.corpse_campus.spell_embryo.configured",
                schoolName, spec.zhName(), rankLabel(spec.rank()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation spellId = getSpellId(stack);
        if (spellId == null) {
            tooltip.add(Component.translatable("tooltip.corpse_campus.embryo_unconfigured")
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

        tooltip.add(Component.translatable("tooltip.corpse_campus.embryo_school", schoolName)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.corpse_campus.embryo_spell", spec.zhName())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.corpse_campus.embryo_rank", rankLabel(spec.rank()))
                .withStyle(rankColor(spec.rank())));
        tooltip.add(Component.translatable("tooltip.corpse_campus.embryo_hint")
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

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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 异常法术卷轴：单一 Item 类 + NBT 存 spellId，实时从 AnomalyBookService.SPELL_SPECS 读法术信息。
 *
 * 避免"每法术一 Item"的重复注册。参考 ISS Scroll 架构（io.redspace.ironsspellbooks.item.Scroll）。
 * 同文件内的 DesignatedAbilityItem 用了同样的 NBT 单例模式。
 *
 * 右键行为（分支见 {@link AnomalyBookService#applyScrollSpell}）：
 *   - 未觉醒 + B 级卷轴：走 applyAwakening 通道（受 40 上限拦截）
 *   - 未觉醒 + A/S 级卷轴：拒绝，要求先 B 觉醒
 *   - 已觉醒 + 主序列匹配 + 未拥有该法术：装法术 + 按需上调 highest_rank
 *   - 已觉醒 + 主序列不匹配：拒绝（序列已锁）
 *   - 已觉醒 + 已拥有该法术：拒绝不消耗
 *
 * 客户端贴图由 {@code ItemProperties.register(... "school", ...)} 读 NBT 给出 0-4 索引，
 * {@code spell_scroll.json} 的 overrides 把索引映射到 5 张 scroll_<school>.png。
 */
public class SpellScrollItem extends Item {

    public static final String TAG_SCROLL_SPELL_ID = "ScrollSpellId";

    public SpellScrollItem() {
        super(new Item.Properties().stacksTo(16));
    }

    /** 产出一张绑定了该法术的卷轴 ItemStack（创造栏 / 指令 / 配方合成输出都用这个入口）。 */
    public static ItemStack createFor(ResourceLocation spellId) {
        ItemStack stack = new ItemStack(com.mifan.registry.ModItems.SPELL_SCROLL.get());
        stack.getOrCreateTag().putString(TAG_SCROLL_SPELL_ID, spellId.toString());
        return stack;
    }

    @Nullable
    public static ResourceLocation getSpellId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SCROLL_SPELL_ID)) {
            return null;
        }
        String raw = tag.getString(TAG_SCROLL_SPELL_ID);
        return raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
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

        ResourceLocation spellId = getSpellId(stack);
        if (spellId == null) {
            sp.displayClientMessage(
                    Component.translatable("message.corpse_campus.scroll_unconfigured")
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        AbsorbResult result = AnomalyBookService.applyScrollSpell(sp, spellId);
        sp.displayClientMessage(result.message(), false);
        if (result.success()) {
            if (!sp.isCreative()) {
                stack.shrink(1);
            }
            sp.getCooldowns().addCooldown(this, 20);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
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
        // 复用 §tooltip.scroll_title 做统一格式: "虚境·音波·B级卷轴"
        return Component.translatable("item.corpse_campus.spell_scroll.configured",
                schoolName, spec.zhName(), rankLabel(spec.rank()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation spellId = getSpellId(stack);
        if (spellId == null) {
            tooltip.add(Component.translatable("tooltip.corpse_campus.scroll_unconfigured")
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

        tooltip.add(Component.translatable("tooltip.corpse_campus.scroll_school", schoolName)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.corpse_campus.scroll_spell", spec.zhName())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.corpse_campus.scroll_rank", rankLabel(spec.rank()))
                .withStyle(rankColor(spec.rank())));
        tooltip.add(Component.translatable("tooltip.corpse_campus.scroll_use_hint")
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

package com.mifan.item;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalyBookService.AbsorbResult;
import com.mifan.anomaly.AnomalyBookService.SpellSpec;
import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.OpenDesignatedAbilityScreenPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * 管理员调试物品：
 *   - 未配置时（无 NBT）：右键 → 打开 GUI 选择 (流派, 法术, 等级)，确定后把选择写到 NBT
 *   - 已配置时（有 NBT）：物品名变 "XX核心"，未觉醒玩家右键 → 直接定向觉醒；已觉醒玩家被拒绝
 *
 * 单一 Item 靠 NBT 区分两种形态，避免每法术 × 每等级 N 个 Item 注册。
 */
public class DesignatedAbilityItem extends Item {
    public static final String TAG_SPELL_ID = "DesignatedSpell";
    public static final String TAG_SPELL_LEVEL = "DesignatedLevel";
    public static final String TAG_SCHOOL_PATH = "DesignatedSchool";

    public DesignatedAbilityItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static boolean isConfigured(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_SPELL_ID) && tag.contains(TAG_SCHOOL_PATH);
    }

    @Nullable
    public static ResourceLocation getDesignatedSpellId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SPELL_ID)) {
            return null;
        }
        return ResourceLocation.tryParse(tag.getString(TAG_SPELL_ID));
    }

    public static int getDesignatedLevel(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 1 : Math.max(1, tag.getInt(TAG_SPELL_LEVEL));
    }

    @Nullable
    public static ResourceLocation getDesignatedSchoolId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SCHOOL_PATH)) {
            return null;
        }
        String path = tag.getString(TAG_SCHOOL_PATH);
        if (path.isEmpty()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath("corpse_campus", path);
    }

    /** 由 ConfigureDesignatedAbilityPacket 在服务端调用。 */
    public static void applyConfiguration(ItemStack stack, ResourceLocation spellId, int level,
            ResourceLocation schoolId) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_SPELL_ID, spellId.toString());
        tag.putInt(TAG_SPELL_LEVEL, level);
        tag.putString(TAG_SCHOOL_PATH, schoolId.getPath());
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

        if (!isConfigured(stack)) {
            // 未配置：开 GUI
            ModNetwork.sendToPlayer(new OpenDesignatedAbilityScreenPacket(), sp);
            return InteractionResultHolder.success(stack);
        }

        // 已配置：执行定向觉醒
        ResourceLocation spellId = getDesignatedSpellId(stack);
        ResourceLocation schoolId = getDesignatedSchoolId(stack);
        int spellLevel = getDesignatedLevel(stack);
        if (spellId == null || schoolId == null) {
            sp.displayClientMessage(
                    Component.translatable("message.corpse_campus.designated_ability.bad_nbt")
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        AbsorbResult result = AnomalyBookService.applyDirectAwakening(sp, schoolId, spellId, spellLevel);
        sp.displayClientMessage(result.message(), false);
        if (result.success()) {
            stack.shrink(1);
            sp.getCooldowns().addCooldown(this, 20);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (!isConfigured(stack)) {
            return super.getName(stack);
        }
        ResourceLocation spellId = getDesignatedSpellId(stack);
        if (spellId == null) {
            return super.getName(stack);
        }
        SpellSpec spec = AnomalyBookService.getSpellSpec(spellId);
        String spellName = spec != null ? spec.zhName()
                : Component.translatable("spell." + spellId.getNamespace() + "." + spellId.getPath()).getString();
        return Component.translatable("item.corpse_campus.designated_ability.configured", spellName);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!isConfigured(stack)) {
            tooltip.add(Component.translatable("tooltip.corpse_campus.designated_ability_unconfigured")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        ResourceLocation spellId = getDesignatedSpellId(stack);
        ResourceLocation schoolId = getDesignatedSchoolId(stack);
        int spellLevel = getDesignatedLevel(stack);
        if (spellId == null || schoolId == null) {
            tooltip.add(Component.translatable("message.corpse_campus.designated_ability.bad_nbt")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        SpellSpec spec = AnomalyBookService.getSpellSpec(spellId);
        String spellName = spec != null ? spec.zhName()
                : Component.translatable("spell." + spellId.getNamespace() + "." + spellId.getPath()).getString();
        Component schoolName = Component.translatable("school." + schoolId.getNamespace() + "." + schoolId.getPath());

        tooltip.add(Component.translatable("tooltip.corpse_campus.designated_ability_school", schoolName)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.corpse_campus.designated_ability_spell", spellName)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.corpse_campus.designated_ability_level", spellLevel)
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.corpse_campus.designated_ability_use_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

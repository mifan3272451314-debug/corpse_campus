package com.mifan.item;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalySpellRank;
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

public class AnomalyTraitItem extends Item {
    public static final String TAG_OWNER_NAME = "TraitOwnerName";
    public static final String TAG_STAGE_LABEL = "TraitStageLabel";
    public static final String TAG_STAGE_ABILITIES = "TraitStageAbilities";

    private final ResourceLocation schoolId;
    private final AnomalySpellRank rank;

    public AnomalyTraitItem(ResourceLocation schoolId, AnomalySpellRank rank) {
        super(new Item.Properties().stacksTo(16));
        this.schoolId = schoolId;
        this.rank = rank;
    }

    public ResourceLocation getSchoolId() {
        return schoolId;
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

        if (rank != AnomalySpellRank.B) {
            sp.displayClientMessage(
                    Component.translatable("message.corpse_campus.trait_only_b_supported"), true);
            return InteractionResultHolder.pass(stack);
        }

        AnomalyBookService.AbsorbResult result = AnomalyBookService.tryAbsorbBTrait(sp, schoolId);
        sp.displayClientMessage(result.message(), false);
        if (result.success()) {
            stack.shrink(1);
            sp.getCooldowns().addCooldown(this, 20);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            if (tag.contains(TAG_OWNER_NAME)) {
                tooltip.add(Component.translatable("tooltip.corpse_campus.trait_former_owner",
                        tag.getString(TAG_OWNER_NAME)).withStyle(ChatFormatting.GRAY));
            }
            if (tag.contains(TAG_STAGE_LABEL)) {
                tooltip.add(Component.translatable("tooltip.corpse_campus.trait_stage",
                        tag.getString(TAG_STAGE_LABEL)).withStyle(ChatFormatting.AQUA));
            }
            if (tag.contains(TAG_STAGE_ABILITIES)) {
                tooltip.add(Component.translatable("tooltip.corpse_campus.trait_stage_abilities",
                        tag.getString(TAG_STAGE_ABILITIES)).withStyle(ChatFormatting.BLUE));
            }
        }

        if (rank == AnomalySpellRank.B) {
            tooltip.add(Component.translatable("tooltip.corpse_campus.trait_b_hint")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.corpse_campus.trait_non_b_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

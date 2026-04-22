package com.mifan.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

    public AnomalyTraitItem() {
        super(new Item.Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }

        if (tag.contains(TAG_OWNER_NAME)) {
            tooltip.add(Component.translatable("tooltip.corpse_campus.trait_former_owner", tag.getString(TAG_OWNER_NAME))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (tag.contains(TAG_STAGE_LABEL)) {
            tooltip.add(Component.translatable("tooltip.corpse_campus.trait_stage", tag.getString(TAG_STAGE_LABEL))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (tag.contains(TAG_STAGE_ABILITIES)) {
            tooltip.add(Component.translatable("tooltip.corpse_campus.trait_stage_abilities",
                    tag.getString(TAG_STAGE_ABILITIES)).withStyle(ChatFormatting.BLUE));
        }
    }
}

package com.mifan.anomaly;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mifan.corpsecampus;
import com.mifan.registry.ModAttributes;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import top.theillusivec4.curios.api.SlotContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class AnomalySpellBookItem extends SpellBook {
    private static final UUID MAX_MANA_UUID = uuid("anomaly_spellbook_max_mana");
    private static final UUID MAX_HEALTH_UUID = uuid("anomaly_spellbook_max_health");

    public AnomalySpellBookItem() {
        super(AnomalyBookService.MAX_SPELL_SLOTS, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        UUID ownerUuid = AnomalyBookService.getOwnerUuid(stack);
        return ownerUuid == null || ownerUuid.equals(slotContext.entity().getUUID());
    }

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return false;
    }

    @Override
    public @NotNull Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid,
            ItemStack stack) {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(super.getAttributeModifiers(slotContext, uuid, stack));

        if (!AnomalyBookService.SPELLBOOK_SLOT.equals(slotContext.identifier())) {
            return builder.build();
        }

        int manaBonus = AnomalyBookService.getStoredManaBonus(stack);
        if (manaBonus > 0) {
            builder.put(AttributeRegistry.MAX_MANA.get(), new AttributeModifier(MAX_MANA_UUID,
                    corpsecampus.MODID + ":anomaly_spellbook_max_mana", manaBonus, AttributeModifier.Operation.ADDITION));
        }

        AnomalySpellRank highestRank = AnomalyBookService.getHighestRank(stack);
        if (highestRank != null && highestRank.getHealthBonus() > 0.0D) {
            builder.put(Attributes.MAX_HEALTH, new AttributeModifier(MAX_HEALTH_UUID,
                    corpsecampus.MODID + ":anomaly_spellbook_max_health",
                    highestRank.getHealthBonus(), AttributeModifier.Operation.ADDITION));
        }

        for (var schoolId : AnomalyBookService.getTrackedSchoolIds()) {
            Attribute attribute = ModAttributes.getSpellPowerAttribute(schoolId);
            double schoolBonusPercent = AnomalyBookService.getStoredSchoolBonusPercent(stack, schoolId);
            if (attribute != null && schoolBonusPercent > 0.0D) {
                builder.put(attribute,
                        new AttributeModifier(uuid("anomaly_spellbook_" + schoolId.getPath() + "_power"),
                                corpsecampus.MODID + ":anomaly_spellbook_" + schoolId.getPath() + "_power",
                                schoolBonusPercent / 100.0D,
                                AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }

        return builder.build();
    }

    private static UUID uuid(String key) {
        return UUID.nameUUIDFromBytes((corpsecampus.MODID + ":" + key).getBytes(StandardCharsets.UTF_8));
    }
}

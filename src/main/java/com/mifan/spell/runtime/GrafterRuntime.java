package com.mifan.spell.runtime;

import com.mifan.anomaly.AnomalyBookService;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class GrafterRuntime {
    public static final ResourceLocation GRAFTER_ID = ResourceLocation.fromNamespaceAndPath("corpse_campus", "grafter");
    public static final ResourceLocation ENDLESS_LIFE_ID = ResourceLocation.fromNamespaceAndPath("corpse_campus", "endless_life");

    public record EligibleSpell(ResourceLocation id, int level) {
    }

    private GrafterRuntime() {
    }

    public static List<EligibleSpell> collectTransferableSpells(ServerPlayer player) {
        List<EligibleSpell> list = new ArrayList<>();
        for (SpellSlot slot : AnomalyBookService.getPlayerLoadedSpellSlots(player)) {
            ResourceLocation id = slot.getSpell().getSpellResource();
            if (isForbidden(id)) {
                continue;
            }
            list.add(new EligibleSpell(id, slot.getLevel()));
        }
        return list;
    }

    public static boolean isForbidden(ResourceLocation id) {
        return GRAFTER_ID.equals(id) || ENDLESS_LIFE_ID.equals(id);
    }

    public static boolean absorb(ServerPlayer caster, ServerPlayer target) {
        List<EligibleSpell> candidates = collectTransferableSpells(target);
        if (candidates.isEmpty()) {
            return false;
        }
        EligibleSpell chosen = candidates.get(caster.getRandom().nextInt(candidates.size()));
        return writeSpellIntoBook(caster, chosen);
    }

    public static boolean graft(ServerPlayer caster, ServerPlayer target) {
        List<EligibleSpell> candidates = collectTransferableSpells(caster);
        if (candidates.isEmpty()) {
            return false;
        }
        EligibleSpell chosen = candidates.get(caster.getRandom().nextInt(candidates.size()));
        return writeSpellIntoBook(target, chosen);
    }

    public static boolean isGraftTargetEligible(ServerPlayer target) {
        // 非异能者：未搭载任何异常法术
        return !AnomalyBookService.hasLoadedSpells(target);
    }

    private static boolean writeSpellIntoBook(ServerPlayer player, EligibleSpell spell) {
        AbstractSpell abstractSpell = SpellRegistry.getSpell(spell.id());
        if (abstractSpell == null) {
            return false;
        }
        ItemStack book = AnomalyBookService.ensureBookPresent(player);
        if (book.isEmpty()) {
            return false;
        }
        return AnomalyBookService.addSpell(player, book, abstractSpell, spell.level(), 1);
    }
}

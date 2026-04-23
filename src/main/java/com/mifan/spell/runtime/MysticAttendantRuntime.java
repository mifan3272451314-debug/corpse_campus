package com.mifan.spell.runtime;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalySpellRank;
import com.mifan.entity.SpiritWormEntity;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class MysticAttendantRuntime {
    public static final String SPELL_PATH = "authority_grasp";

    private MysticAttendantRuntime() {
    }

    public static void onWormTouchPlayer(SpiritWormEntity worm, ServerPlayer victim) {
        UUID ownerUUID = worm.getOwnerUUID();
        if (ownerUUID == null) {
            return;
        }
        MinecraftServer server = victim.getServer();
        if (server == null) {
            return;
        }
        ServerPlayer caster = server.getPlayerList().getPlayer(ownerUUID);
        if (caster == null || caster == victim) {
            return;
        }

        List<SpellSlot> victimSlots = AnomalyBookService.getPlayerLoadedSpellSlots(victim);
        if (victimSlots.isEmpty()) {
            notifyCaster(caster, "message.corpse_campus.mystic_attendant_victim_empty",
                    victim.getDisplayName().getString());
            return;
        }

        List<SpellSlot> candidates = new ArrayList<>();
        for (SpellSlot slot : victimSlots) {
            AnomalyBookService.SpellSpec spec = AnomalyBookService.getSpellSpec(
                    slot.getSpell().getSpellResource());
            if (spec == null) {
                continue;
            }
            if (spec.rank() == AnomalySpellRank.S) {
                continue;
            }
            candidates.add(slot);
        }
        if (candidates.isEmpty()) {
            notifyCaster(caster, "message.corpse_campus.mystic_attendant_no_candidate",
                    victim.getDisplayName().getString());
            return;
        }

        candidates.sort(Comparator
                .comparingInt((SpellSlot s) -> {
                    AnomalyBookService.SpellSpec spec = AnomalyBookService.getSpellSpec(
                            s.getSpell().getSpellResource());
                    return spec == null ? -1 : spec.rank().ordinal();
                })
                .thenComparingInt(SpellSlot::getLevel)
                .reversed());

        ItemStack casterBook = AnomalyBookService.getOwnedBook(caster);
        if (casterBook.isEmpty()) {
            notifyCaster(caster, "message.corpse_campus.mystic_attendant_no_book");
            return;
        }

        SpellSlot chosen = null;
        for (SpellSlot cand : candidates) {
            if (!bookContainsSpell(casterBook, cand.getSpell().getSpellResource())) {
                chosen = cand;
                break;
            }
        }
        if (chosen == null) {
            notifyCaster(caster, "message.corpse_campus.mystic_attendant_all_mastered",
                    victim.getDisplayName().getString());
            return;
        }

        AbstractSpell chosenSpell = chosen.getSpell();
        int wantedLevel = Math.max(1, chosen.getLevel());
        boolean added = AnomalyBookService.addSpell(caster, casterBook, chosenSpell, wantedLevel, 1);
        if (added) {
            AnomalyBookService.SpellSpec spec = AnomalyBookService.getSpellSpec(
                    chosenSpell.getSpellResource());
            String zhName = spec != null ? spec.zhName() : chosenSpell.getSpellResource().getPath();
            notifyCaster(caster, "message.corpse_campus.mystic_attendant_acquired",
                    zhName, victim.getDisplayName().getString());
            if (worm.level() instanceof ServerLevel sl) {
                sl.playSound(null, worm.blockPosition(), SoundEvents.SOUL_ESCAPE,
                        SoundSource.PLAYERS, 1.0F, 0.8F);
                sl.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 1.0D, victim.getZ(),
                        12, 0.3D, 0.3D, 0.3D, 0.05D);
            }
        }
    }

    public static void onScrollDevour(ServerPlayer caster, ItemStack scroll) {
        scroll.shrink(1);

        List<SpellSlot> slots = AnomalyBookService.getPlayerLoadedSpellSlots(caster);
        int upgraded = 0;
        for (SpellSlot slot : slots) {
            ResourceLocation id = slot.getSpell().getSpellResource();
            AnomalyBookService.UpgradeOutcome outcome = AnomalyBookService.upgradeLoadedSpell(caster, id);
            if (outcome == AnomalyBookService.UpgradeOutcome.SUCCESS) {
                upgraded++;
            }
        }

        if (caster.level() instanceof ServerLevel sl) {
            sl.playSound(null, caster.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.PLAYERS, 1.0F, 1.2F);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    caster.getX(), caster.getY() + 1.0D, caster.getZ(),
                    16, 0.3D, 0.5D, 0.3D, 0.03D);
        }
        caster.displayClientMessage(
                Component.translatable("message.corpse_campus.mystic_attendant_devoured", upgraded),
                true);
    }

    public static boolean casterKnowsMysticAttendant(ServerPlayer caster) {
        ItemStack book = AnomalyBookService.getOwnedBook(caster);
        if (book.isEmpty()) {
            return false;
        }
        return bookContainsSpell(book, new ResourceLocation("corpse_campus", SPELL_PATH));
    }

    private static boolean bookContainsSpell(ItemStack book, ResourceLocation spellId) {
        for (SpellSlot slot : ISpellContainer.getOrCreate(book).getActiveSpells()) {
            if (slot.getSpell().getSpellResource().equals(spellId)) {
                return true;
            }
        }
        return false;
    }

    private static void notifyCaster(ServerPlayer caster, String key, Object... args) {
        caster.displayClientMessage(Component.translatable(key, args), true);
    }
}

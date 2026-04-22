package com.mifan.spell.runtime;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.spell.AbilityRuntime;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class MimicRuntime {
    private static final String SLOT_ID = "id";
    private static final String SLOT_LEVEL = "lvl";

    private MimicRuntime() {
    }

    public static int getActiveSlot(CompoundTag data) {
        return Mth.clamp(data.getInt(AbilityRuntime.TAG_MIMIC_ACTIVE_SLOT), 0, AbilityRuntime.MIMIC_MAX_SLOTS - 1);
    }

    public static void cycleActiveSlot(CompoundTag data) {
        int next = (getActiveSlot(data) + 1) % AbilityRuntime.MIMIC_MAX_SLOTS;
        data.putInt(AbilityRuntime.TAG_MIMIC_ACTIVE_SLOT, next);
    }

    public static String getSlotId(CompoundTag data, int slot) {
        ListTag list = getOrCreateSlotList(data);
        if (slot >= list.size()) {
            return "";
        }
        return list.getCompound(slot).getString(SLOT_ID);
    }

    public static int getSlotLevel(CompoundTag data, int slot) {
        ListTag list = getOrCreateSlotList(data);
        if (slot >= list.size()) {
            return 1;
        }
        return Math.max(1, list.getCompound(slot).getInt(SLOT_LEVEL));
    }

    public static boolean hasSlotContent(CompoundTag data, int slot) {
        return !getSlotId(data, slot).isEmpty();
    }

    public static boolean setSlot(CompoundTag data, int slot, String spellId, int level) {
        if (slot < 0 || slot >= AbilityRuntime.MIMIC_MAX_SLOTS) {
            return false;
        }
        ListTag list = getOrCreateSlotList(data);
        while (list.size() < AbilityRuntime.MIMIC_MAX_SLOTS) {
            CompoundTag empty = new CompoundTag();
            empty.putString(SLOT_ID, "");
            empty.putInt(SLOT_LEVEL, 0);
            list.add(empty);
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(SLOT_ID, spellId);
        tag.putInt(SLOT_LEVEL, level);
        list.set(slot, tag);
        data.put(AbilityRuntime.TAG_MIMIC_SLOTS, list);
        return true;
    }

    public static int findNextEmptySlot(CompoundTag data) {
        for (int i = 0; i < AbilityRuntime.MIMIC_MAX_SLOTS; i++) {
            if (getSlotId(data, i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static void clearAllSlots(CompoundTag data) {
        data.remove(AbilityRuntime.TAG_MIMIC_SLOTS);
        data.remove(AbilityRuntime.TAG_MIMIC_ACTIVE_SLOT);
    }

    public static List<ResourceLocation> getTargetBRankSpells(ServerPlayer target) {
        return AnomalyBookService.getPlayerBRankSpellIds(target);
    }

    public static AbstractSpell resolveSlotSpell(CompoundTag data, int slot) {
        String id = getSlotId(data, slot);
        if (id.isEmpty()) {
            return null;
        }
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) {
            return null;
        }
        AbstractSpell spell = SpellRegistry.getSpell(loc);
        return spell;
    }

    public static String buildSlotsStatus(CompoundTag data) {
        StringBuilder sb = new StringBuilder();
        int active = getActiveSlot(data);
        for (int i = 0; i < AbilityRuntime.MIMIC_MAX_SLOTS; i++) {
            String id = getSlotId(data, i);
            String name = id.isEmpty() ? "空" : getDisplayName(id);
            sb.append(i == active ? "[" : " ").append(i + 1).append(":").append(name).append(i == active ? "]" : " ");
            if (i < AbilityRuntime.MIMIC_MAX_SLOTS - 1) {
                sb.append("  ");
            }
        }
        return sb.toString();
    }

    private static String getDisplayName(String spellId) {
        ResourceLocation loc = ResourceLocation.tryParse(spellId);
        if (loc == null) {
            return spellId;
        }
        AbstractSpell spell = SpellRegistry.getSpell(loc);
        return spell != null ? spell.getSpellName() : loc.getPath();
    }

    private static ListTag getOrCreateSlotList(CompoundTag data) {
        if (data.contains(AbilityRuntime.TAG_MIMIC_SLOTS, Tag.TAG_LIST)) {
            return data.getList(AbilityRuntime.TAG_MIMIC_SLOTS, Tag.TAG_COMPOUND);
        }
        return new ListTag();
    }
}

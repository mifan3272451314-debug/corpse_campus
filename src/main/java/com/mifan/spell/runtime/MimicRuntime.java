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

    public static final ResourceLocation MIMIC_ID = ResourceLocation.fromNamespaceAndPath("corpse_campus", "mimic");
    public static final ResourceLocation GRAFTER_ID = ResourceLocation.fromNamespaceAndPath("corpse_campus", "grafter");
    public static final ResourceLocation ENDLESS_LIFE_ID = ResourceLocation.fromNamespaceAndPath("corpse_campus", "endless_life");

    public record SlotEntry(int slot, String spellId, int level) {
    }

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
        ListTag list = ensureFullList(data);
        CompoundTag tag = new CompoundTag();
        tag.putString(SLOT_ID, spellId);
        tag.putInt(SLOT_LEVEL, level);
        list.set(slot, tag);
        data.put(AbilityRuntime.TAG_MIMIC_SLOTS, list);
        return true;
    }

    public static boolean clearSlot(CompoundTag data, int slot) {
        if (slot < 0 || slot >= AbilityRuntime.MIMIC_MAX_SLOTS) {
            return false;
        }
        ListTag list = ensureFullList(data);
        CompoundTag empty = new CompoundTag();
        empty.putString(SLOT_ID, "");
        empty.putInt(SLOT_LEVEL, 0);
        list.set(slot, empty);
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

    public static int countFilledSlots(CompoundTag data) {
        int n = 0;
        for (int i = 0; i < AbilityRuntime.MIMIC_MAX_SLOTS; i++) {
            if (!getSlotId(data, i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public static boolean isFull(CompoundTag data) {
        return findNextEmptySlot(data) < 0;
    }

    public static boolean isAlreadyCopied(CompoundTag data, String spellId) {
        if (spellId == null || spellId.isEmpty()) {
            return false;
        }
        for (int i = 0; i < AbilityRuntime.MIMIC_MAX_SLOTS; i++) {
            if (spellId.equals(getSlotId(data, i))) {
                return true;
            }
        }
        return false;
    }

    public static List<SlotEntry> listFilledSlots(CompoundTag data) {
        List<SlotEntry> out = new ArrayList<>();
        for (int i = 0; i < AbilityRuntime.MIMIC_MAX_SLOTS; i++) {
            String id = getSlotId(data, i);
            if (!id.isEmpty()) {
                out.add(new SlotEntry(i, id, getSlotLevel(data, i)));
            }
        }
        return out;
    }

    public static void clearAllSlots(CompoundTag data) {
        data.remove(AbilityRuntime.TAG_MIMIC_SLOTS);
        data.remove(AbilityRuntime.TAG_MIMIC_ACTIVE_SLOT);
    }

    /**
     * 取目标玩家身上"可被模仿者复制"的一阶能力候选：
     * - 必须是 B 阶（一阶）
     * - 排除模仿者自身、嫁接师、生生不息
     * - 排除调用方已复制过的（避免重复）
     */
    public static List<ResourceLocation> getCopyCandidates(ServerPlayer caster, ServerPlayer target) {
        CompoundTag casterData = caster.getPersistentData();
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : AnomalyBookService.getPlayerBRankSpellIds(target)) {
            if (isForbiddenForCopy(id)) {
                continue;
            }
            if (isAlreadyCopied(casterData, id.toString())) {
                continue;
            }
            out.add(id);
        }
        return out;
    }

    public static boolean isForbiddenForCopy(ResourceLocation id) {
        return MIMIC_ID.equals(id) || GRAFTER_ID.equals(id) || ENDLESS_LIFE_ID.equals(id);
    }

    /** 兼容老调用：返回 target 全部 B 阶，不做过滤。 */
    public static List<ResourceLocation> getTargetBRankSpells(ServerPlayer target) {
        return AnomalyBookService.getPlayerBRankSpellIds(target);
    }

    public static AbstractSpell resolveSlotSpell(CompoundTag data, int slot) {
        String id = getSlotId(data, slot);
        if (id.isEmpty()) {
            return null;
        }
        return resolveSpell(id);
    }

    public static AbstractSpell resolveSpell(String spellId) {
        ResourceLocation loc = ResourceLocation.tryParse(spellId);
        if (loc == null) {
            return null;
        }
        return SpellRegistry.getSpell(loc);
    }

    /**
     * 用于 server 端 actionbar/chat 显示槽位状态。直接拼中文名，避免 ISS 的
     * AbstractSpell.getSpellName() 返回 raw path 的问题。
     */
    public static String buildSlotsStatus(CompoundTag data) {
        StringBuilder sb = new StringBuilder();
        int active = getActiveSlot(data);
        for (int i = 0; i < AbilityRuntime.MIMIC_MAX_SLOTS; i++) {
            String id = getSlotId(data, i);
            String name = id.isEmpty() ? "空" : displayName(id);
            sb.append(i == active ? "[" : " ").append(i + 1).append(":").append(name).append(i == active ? "]" : " ");
            if (i < AbilityRuntime.MIMIC_MAX_SLOTS - 1) {
                sb.append("  ");
            }
        }
        return sb.toString();
    }

    /** 优先取 SpellSpec.zhName；没有则回退到 spell path。 */
    public static String displayName(String spellId) {
        ResourceLocation loc = ResourceLocation.tryParse(spellId);
        if (loc == null) {
            return spellId;
        }
        return displayName(loc);
    }

    public static String displayName(ResourceLocation loc) {
        AnomalyBookService.SpellSpec spec = AnomalyBookService.getSpellSpec(loc);
        if (spec != null) {
            return spec.zhName();
        }
        AbstractSpell spell = SpellRegistry.getSpell(loc);
        return spell != null ? spell.getSpellName() : loc.getPath();
    }

    private static ListTag ensureFullList(CompoundTag data) {
        ListTag list = getOrCreateSlotList(data);
        while (list.size() < AbilityRuntime.MIMIC_MAX_SLOTS) {
            CompoundTag empty = new CompoundTag();
            empty.putString(SLOT_ID, "");
            empty.putInt(SLOT_LEVEL, 0);
            list.add(empty);
        }
        return list;
    }

    private static ListTag getOrCreateSlotList(CompoundTag data) {
        if (data.contains(AbilityRuntime.TAG_MIMIC_SLOTS, Tag.TAG_LIST)) {
            return data.getList(AbilityRuntime.TAG_MIMIC_SLOTS, Tag.TAG_COMPOUND);
        }
        return new ListTag();
    }
}

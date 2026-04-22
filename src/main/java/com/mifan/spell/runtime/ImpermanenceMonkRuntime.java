package com.mifan.spell.runtime;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.spell.AbilityRuntime;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ImpermanenceMonkRuntime {

    public record InfectionResult(boolean success, String grantedSpellName) {
    }

    private ImpermanenceMonkRuntime() {
    }

    public static InfectionResult infectTarget(ServerPlayer caster, ServerPlayer target) {
        if (caster == target) {
            return new InfectionResult(false, null);
        }
        CompoundTag casterData = caster.getPersistentData();
        List<UUID> infected = getInfectedUuids(casterData);
        if (infected.size() >= AbilityRuntime.IMPERMANENCE_MAX_TARGETS) {
            return new InfectionResult(false, null);
        }
        UUID targetUuid = target.getUUID();
        if (infected.contains(targetUuid)) {
            return new InfectionResult(false, null);
        }

        // 给予被感染者施法者的一阶（B 级）技能
        List<ResourceLocation> casterBSpells = AnomalyBookService.getPlayerBRankSpellIds(caster);
        ResourceLocation granted = null;
        String grantedName = null;
        if (!casterBSpells.isEmpty()) {
            ResourceLocation candidate = casterBSpells.get(caster.getRandom().nextInt(casterBSpells.size()));
            AbstractSpell spell = SpellRegistry.getSpell(candidate);
            if (spell != null) {
                ItemStack victimBook = AnomalyBookService.ensureBookPresent(target);
                if (!victimBook.isEmpty()
                        && AnomalyBookService.addSpell(target, victimBook, spell, 1, 1)) {
                    granted = candidate;
                    grantedName = spell.getSpellName();
                }
            }
        }

        infected.add(targetUuid);
        saveInfectedUuids(casterData, infected);

        CompoundTag targetData = target.getPersistentData();
        targetData.putUUID(AbilityRuntime.TAG_IMPERMANENCE_INFECTOR_UUID, caster.getUUID());
        if (granted != null) {
            targetData.putString(AbilityRuntime.TAG_IMPERMANENCE_GRANTED_SPELL, granted.toString());
            targetData.putInt(AbilityRuntime.TAG_IMPERMANENCE_GRANTED_LEVEL, 1);
        } else {
            targetData.remove(AbilityRuntime.TAG_IMPERMANENCE_GRANTED_SPELL);
            targetData.remove(AbilityRuntime.TAG_IMPERMANENCE_GRANTED_LEVEL);
        }

        return new InfectionResult(true, grantedName);
    }

    public static boolean releaseTarget(Player caster, Player target) {
        CompoundTag casterData = caster.getPersistentData();
        List<UUID> infected = getInfectedUuids(casterData);
        boolean removed = infected.remove(target.getUUID());
        if (!removed) {
            return false;
        }
        saveInfectedUuids(casterData, infected);
        revokeGrantedSpell(target);
        target.getPersistentData().remove(AbilityRuntime.TAG_IMPERMANENCE_INFECTOR_UUID);
        return true;
    }

    public static void clearAllInfections(Player caster) {
        CompoundTag casterData = caster.getPersistentData();
        List<UUID> infected = getInfectedUuids(casterData);
        if (caster.level() instanceof ServerLevel serverLevel) {
            for (UUID uuid : infected) {
                ServerPlayer victim = serverLevel.getServer().getPlayerList().getPlayer(uuid);
                if (victim != null) {
                    revokeGrantedSpell(victim);
                    victim.getPersistentData().remove(AbilityRuntime.TAG_IMPERMANENCE_INFECTOR_UUID);
                }
            }
        }
        casterData.remove(AbilityRuntime.TAG_IMPERMANENCE_INFECTED_LIST);
    }

    private static void revokeGrantedSpell(Player target) {
        if (!(target instanceof ServerPlayer serverTarget)) {
            return;
        }
        CompoundTag data = serverTarget.getPersistentData();
        if (!data.contains(AbilityRuntime.TAG_IMPERMANENCE_GRANTED_SPELL)) {
            return;
        }
        String spellIdRaw = data.getString(AbilityRuntime.TAG_IMPERMANENCE_GRANTED_SPELL);
        data.remove(AbilityRuntime.TAG_IMPERMANENCE_GRANTED_SPELL);
        data.remove(AbilityRuntime.TAG_IMPERMANENCE_GRANTED_LEVEL);
        ResourceLocation loc = ResourceLocation.tryParse(spellIdRaw);
        if (loc == null) {
            return;
        }
        AbstractSpell spell = SpellRegistry.getSpell(loc);
        if (spell == null) {
            return;
        }
        ItemStack book = AnomalyBookService.getOwnedBook(serverTarget);
        if (book.isEmpty()) {
            return;
        }
        AnomalyBookService.clearSpell(serverTarget, book, spell);
    }

    public static int getInfectedCount(Player caster) {
        return getInfectedUuids(caster.getPersistentData()).size();
    }

    public static boolean isInfectedBy(LivingEntity entity, UUID casterUuid) {
        if (!(entity instanceof Player)) {
            return false;
        }
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(AbilityRuntime.TAG_IMPERMANENCE_INFECTOR_UUID)
                && data.getUUID(AbilityRuntime.TAG_IMPERMANENCE_INFECTOR_UUID).equals(casterUuid);
    }

    public static UUID getInfectorUuid(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(AbilityRuntime.TAG_IMPERMANENCE_INFECTOR_UUID)
                ? data.getUUID(AbilityRuntime.TAG_IMPERMANENCE_INFECTOR_UUID)
                : null;
    }

    private static List<UUID> getInfectedUuids(CompoundTag data) {
        List<UUID> result = new ArrayList<>();
        if (!data.contains(AbilityRuntime.TAG_IMPERMANENCE_INFECTED_LIST, Tag.TAG_LIST)) {
            return result;
        }
        ListTag list = data.getList(AbilityRuntime.TAG_IMPERMANENCE_INFECTED_LIST, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                result.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private static void saveInfectedUuids(CompoundTag data, List<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        data.put(AbilityRuntime.TAG_IMPERMANENCE_INFECTED_LIST, list);
    }
}

package com.mifan.spell.runtime;

import com.mifan.spell.AbilityRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ImpermanenceMonkRuntime {

    private ImpermanenceMonkRuntime() {
    }

    public static boolean infectTarget(Player caster, Player target) {
        if (caster == target) {
            return false;
        }
        CompoundTag casterData = caster.getPersistentData();
        List<UUID> infected = getInfectedUuids(casterData);
        if (infected.size() >= AbilityRuntime.IMPERMANENCE_MAX_TARGETS) {
            return false;
        }
        UUID targetUuid = target.getUUID();
        if (infected.contains(targetUuid)) {
            return false;
        }
        infected.add(targetUuid);
        saveInfectedUuids(casterData, infected);

        CompoundTag targetData = target.getPersistentData();
        targetData.putUUID(AbilityRuntime.TAG_IMPERMANENCE_INFECTOR_UUID, caster.getUUID());
        return true;
    }

    public static boolean releaseTarget(Player caster, Player target) {
        CompoundTag casterData = caster.getPersistentData();
        List<UUID> infected = getInfectedUuids(casterData);
        boolean removed = infected.remove(target.getUUID());
        if (removed) {
            saveInfectedUuids(casterData, infected);
            target.getPersistentData().remove(AbilityRuntime.TAG_IMPERMANENCE_INFECTOR_UUID);
        }
        return removed;
    }

    public static void clearAllInfections(Player caster) {
        CompoundTag casterData = caster.getPersistentData();
        List<UUID> infected = getInfectedUuids(casterData);
        if (caster.level() instanceof ServerLevel serverLevel) {
            for (UUID uuid : infected) {
                ServerPlayer victim = serverLevel.getServer().getPlayerList().getPlayer(uuid);
                if (victim != null) {
                    victim.getPersistentData().remove(AbilityRuntime.TAG_IMPERMANENCE_INFECTOR_UUID);
                }
            }
        }
        casterData.remove(AbilityRuntime.TAG_IMPERMANENCE_INFECTED_LIST);
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

package com.mifan.spell.runtime;

import com.mifan.spell.AbilityRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DominanceRuntime {
    private DominanceRuntime() {
    }

    public static Mob findMobTarget(LivingEntity caster, double range, double minDot) {
        LivingEntity target = AbilityRuntime.findTargetInSight(caster, range, minDot);
        return target instanceof Mob mob ? mob : null;
    }

    public static boolean addDominatedMob(LivingEntity caster, Mob mob, int spellLevel, int maxControlled) {
        if (mob.getMaxHealth() > AbilityRuntime.DOMINANCE_MAX_HEALTH_LIMIT) {
            return false;
        }

        CompoundTag data = caster.getPersistentData();
        ListTag list = getDominatedMobList(data);
        UUID mobId = mob.getUUID();
        if (AbilityRuntime.containsUuid(list, mobId)) {
            data.putBoolean(AbilityRuntime.TAG_DOMINANCE_LINK_ACTIVE, true);
            tagDominatedMob(mob, caster.getUUID(), spellLevel);
            return true;
        }

        if (list.size() >= maxControlled) {
            return false;
        }

        list.add(StringTag.valueOf(mobId.toString()));
        data.put(AbilityRuntime.TAG_DOMINANCE_MOBS, list);
        data.putBoolean(AbilityRuntime.TAG_DOMINANCE_LINK_ACTIVE, true);
        tagDominatedMob(mob, caster.getUUID(), spellLevel);
        return true;
    }

    public static void setTargetPlayer(ServerPlayer caster, UUID targetPlayerId) {
        if (targetPlayerId.equals(caster.getUUID())) {
            return;
        }

        Player target = caster.serverLevel().getPlayerByUUID(targetPlayerId);
        if (target == null) {
            return;
        }

        caster.getPersistentData().putUUID(AbilityRuntime.TAG_DOMINANCE_TARGET_PLAYER, targetPlayerId);
        retargetDominatedMobs(caster, target);
        caster.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.corpse_campus.dominance_target_set", target.getDisplayName()), true);
    }

    public static void tick(Player player) {
        CompoundTag data = player.getPersistentData();
        List<Mob> dominatedMobs = getDominatedMobs(player);

        if (dominatedMobs.isEmpty()) {
            AbilityRuntime.clear(data, AbilityRuntime.TAG_DOMINANCE_MOBS, AbilityRuntime.TAG_DOMINANCE_TARGET_PLAYER);
            if (data.getBoolean(AbilityRuntime.TAG_DOMINANCE_LINK_ACTIVE) && player.isAlive()) {
                data.remove(AbilityRuntime.TAG_DOMINANCE_LINK_ACTIVE);
                float damage = Math.max(0.0F, player.getHealth() - AbilityRuntime.DOMINANCE_MIN_SURVIVAL_HEALTH);
                if (damage > 0.0F) {
                    player.hurt(player.damageSources().magic(), damage);
                }
            }
            return;
        }

        data.putBoolean(AbilityRuntime.TAG_DOMINANCE_LINK_ACTIVE, true);
        LivingEntity forcedTarget = null;
        if (data.hasUUID(AbilityRuntime.TAG_DOMINANCE_TARGET_PLAYER) && player.level() instanceof ServerLevel serverLevel) {
            forcedTarget = serverLevel.getPlayerByUUID(data.getUUID(AbilityRuntime.TAG_DOMINANCE_TARGET_PLAYER));
            if (forcedTarget == null || !forcedTarget.isAlive()) {
                data.remove(AbilityRuntime.TAG_DOMINANCE_TARGET_PLAYER);
            }
        }

        for (Mob mob : dominatedMobs) {
            tagDominatedMob(mob, player.getUUID(), 1);
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
            if (forcedTarget != null && mob.getTarget() != forcedTarget) {
                mob.setTarget(forcedTarget);
            }
        }
    }

    public static void retargetDominatedMobs(Player player, LivingEntity target) {
        for (Mob mob : getDominatedMobs(player)) {
            if (target != player) {
                mob.setTarget(target);
            }
        }
    }

    public static void release(Player player) {
        for (Mob mob : getDominatedMobs(player)) {
            if (!isDominatedBy(mob, player)) {
                continue;
            }
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
            clearDominanceTag(mob);
        }

        AbilityRuntime.clear(player.getPersistentData(),
                AbilityRuntime.TAG_DOMINANCE_MOBS,
                AbilityRuntime.TAG_DOMINANCE_TARGET_PLAYER,
                AbilityRuntime.TAG_DOMINANCE_LINK_ACTIVE);
    }

    public static boolean isDominatedBy(Mob mob, Player owner) {
        CompoundTag tag = mob.getPersistentData();
        return tag.hasUUID(AbilityRuntime.TAG_DOMINANCE_OWNER)
                && owner.getUUID().equals(tag.getUUID(AbilityRuntime.TAG_DOMINANCE_OWNER));
    }

    public static List<Mob> getDominatedMobs(Player player) {
        CompoundTag data = player.getPersistentData();
        ListTag list = getDominatedMobList(data);
        ListTag cleaned = new ListTag();
        List<Mob> mobs = new ArrayList<>();

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }

        for (int i = 0; i < list.size(); i++) {
            String uuidString = list.getString(i);
            try {
                Entity entity = serverLevel.getEntity(UUID.fromString(uuidString));
                if (entity instanceof Mob mob && mob.isAlive()) {
                    mobs.add(mob);
                    cleaned.add(StringTag.valueOf(uuidString));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        data.put(AbilityRuntime.TAG_DOMINANCE_MOBS, cleaned);
        return mobs;
    }

    private static ListTag getDominatedMobList(CompoundTag data) {
        return data.contains(AbilityRuntime.TAG_DOMINANCE_MOBS, net.minecraft.nbt.Tag.TAG_LIST)
                ? data.getList(AbilityRuntime.TAG_DOMINANCE_MOBS, net.minecraft.nbt.Tag.TAG_STRING)
                : new ListTag();
    }

    private static void tagDominatedMob(Mob mob, UUID casterId, int spellLevel) {
        CompoundTag tag = mob.getPersistentData();
        tag.putUUID(AbilityRuntime.TAG_DOMINANCE_OWNER, casterId);
        tag.putInt(AbilityRuntime.TAG_DOMINANCE_LEVEL, spellLevel);
    }

    private static void clearDominanceTag(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        tag.remove(AbilityRuntime.TAG_DOMINANCE_OWNER);
        tag.remove(AbilityRuntime.TAG_DOMINANCE_LEVEL);
    }
}

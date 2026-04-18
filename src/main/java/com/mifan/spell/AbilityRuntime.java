package com.mifan.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AbilityRuntime {
    public static final int TOGGLE_DURATION_TICKS = 20 * 60 * 60 * 4;

    public static final String TAG_DANGER_LAST_ALERT = "corpse_campus_danger_last_alert";

    public static final String TAG_TELEKINESIS_TARGET_ID = "corpse_campus_telekinesis_target_id";
    public static final String TAG_TELEKINESIS_HOLD_UNTIL = "corpse_campus_telekinesis_hold_until";
    public static final String TAG_TELEKINESIS_LEVEL = "corpse_campus_telekinesis_level";
    public static final String TAG_TELEKINESIS_LOOK_X = "corpse_campus_telekinesis_look_x";
    public static final String TAG_TELEKINESIS_LOOK_Y = "corpse_campus_telekinesis_look_y";
    public static final String TAG_TELEKINESIS_LOOK_Z = "corpse_campus_telekinesis_look_z";

    public static final String TAG_MAGNETIC_CLINGING = "corpse_campus_magnetic_clinging";
    public static final String TAG_MAGNETIC_CLING_END = "corpse_campus_magnetic_cling_end";
    public static final String TAG_MAGNETIC_LAST_GROUND = "corpse_campus_magnetic_last_ground";
    public static final String TAG_MAGNETIC_SHOCK_READY = "corpse_campus_magnetic_shock_ready";

    public static final String TAG_INSTINCT_END = "corpse_campus_instinct_end";
    public static final String TAG_INSTINCT_LEVEL = "corpse_campus_instinct_level";
    public static final String TAG_INSTINCT_USED = "corpse_campus_instinct_used";
    public static final String TAG_INSTINCT_INVULNERABLE_UNTIL = "corpse_campus_instinct_invulnerable_until";

    public static final String TAG_MANIA_LAST_PROC = "corpse_campus_mania_last_proc";
    public static final String TAG_MANIA_LAST_SWING = "corpse_campus_mania_last_swing";

    public static final String TAG_EXECUTIONER_LAST_TICK = "corpse_campus_executioner_last_tick";

    public static final String TAG_DOMINANCE_MOBS = "corpse_campus_dominance_mobs";
    public static final String TAG_DOMINANCE_TARGET_PLAYER = "corpse_campus_dominance_target_player";
    public static final String TAG_DOMINANCE_LINK_ACTIVE = "corpse_campus_dominance_link_active";
    public static final String TAG_DOMINANCE_OWNER = "corpse_campus_dominance_owner";
    public static final String TAG_DOMINANCE_LEVEL = "corpse_campus_dominance_level";

    public static final int EXECUTIONER_DURABILITY_COST = 5;
    private static final float EXECUTIONER_DAMAGE_RATIO = 0.25F;

    private AbilityRuntime() {
    }

    public static void activateTimedState(LivingEntity entity, String endKey, String levelKey, long durationTicks,
            int spellLevel) {
        CompoundTag data = entity.getPersistentData();
        data.putLong(endKey, entity.level().getGameTime() + durationTicks);
        data.putInt(levelKey, spellLevel);
    }

    public static boolean isActive(CompoundTag data, String endKey, long gameTime) {
        return data.contains(endKey) && data.getLong(endKey) > gameTime;
    }

    public static int getLevel(CompoundTag data, String levelKey) {
        return Math.max(1, data.getInt(levelKey));
    }

    public static int getEffectLevel(MobEffectInstance effectInstance) {
        return effectInstance == null ? 1 : effectInstance.getAmplifier() + 1;
    }

    public static void clear(CompoundTag data, String... keys) {
        for (String key : keys) {
            data.remove(key);
        }
    }

    public static LivingEntity findTargetInSight(LivingEntity caster, double range, double minDot) {
        Vec3 eyePosition = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        AABB searchBox = caster.getBoundingBox().inflate(range);

        return caster.level().getEntitiesOfClass(LivingEntity.class, searchBox, target -> target != caster
                && target.isAlive()
                && caster.hasLineOfSight(target))
                .stream()
                .filter(target -> eyePosition.distanceToSqr(target.getEyePosition()) <= range * range)
                .filter(target -> {
                    Vec3 delta = target.getEyePosition().subtract(eyePosition);
                    if (delta.lengthSqr() < 1.0E-4D) {
                        return false;
                    }
                    return delta.normalize().dot(look) >= minDot;
                })
                .min(Comparator.comparingDouble(target -> eyePosition.distanceToSqr(target.getEyePosition())))
                .orElse(null);
    }

    public static LivingEntity findLivingEntityById(Level level, int entityId) {
        Entity entity = level.getEntity(entityId);
        return entity instanceof LivingEntity livingEntity && livingEntity.isAlive() ? livingEntity : null;
    }

    public static void storeLookVector(CompoundTag data, Vec3 look) {
        data.putDouble(TAG_TELEKINESIS_LOOK_X, look.x);
        data.putDouble(TAG_TELEKINESIS_LOOK_Y, look.y);
        data.putDouble(TAG_TELEKINESIS_LOOK_Z, look.z);
    }

    public static Vec3 readStoredLookVector(CompoundTag data) {
        return new Vec3(
                data.getDouble(TAG_TELEKINESIS_LOOK_X),
                data.getDouble(TAG_TELEKINESIS_LOOK_Y),
                data.getDouble(TAG_TELEKINESIS_LOOK_Z));
    }

    public static void pushNearbyEntities(LivingEntity source, double radius, double horizontalStrength,
            double verticalStrength) {
        for (LivingEntity target : source.level().getEntitiesOfClass(
                LivingEntity.class,
                source.getBoundingBox().inflate(radius),
                target -> target != source && target.isAlive())) {
            Vec3 horizontalDelta = target.position().subtract(source.position());
            double horizontalLength = Math
                    .sqrt(horizontalDelta.x * horizontalDelta.x + horizontalDelta.z * horizontalDelta.z);

            if (horizontalLength < 1.0E-3D) {
                Vec3 look = source.getLookAngle();
                horizontalDelta = new Vec3(look.x, 0.0D, look.z);
                horizontalLength = Math.max(1.0E-3D,
                        Math.sqrt(horizontalDelta.x * horizontalDelta.x + horizontalDelta.z * horizontalDelta.z));
            }

            double falloff = 1.0D - Math.min(0.9D, source.distanceTo(target) / radius);
            target.push(
                    horizontalDelta.x / horizontalLength * horizontalStrength * falloff,
                    verticalStrength,
                    horizontalDelta.z / horizontalLength * horizontalStrength * falloff);
            target.hurtMarked = true;
        }
    }

    public static LivingEntity findNearestFrontTarget(LivingEntity caster, double range, double minDot) {
        Vec3 eyePosition = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        AABB searchBox = caster.getBoundingBox().inflate(range);

        return caster.level().getEntitiesOfClass(LivingEntity.class, searchBox, target -> target != caster
                && target.isAlive())
                .stream()
                .filter(target -> eyePosition.distanceToSqr(target.getEyePosition()) <= range * range)
                .filter(target -> {
                    Vec3 delta = target.getEyePosition().subtract(eyePosition);
                    if (delta.lengthSqr() < 1.0E-4D) {
                        return false;
                    }
                    return delta.normalize().dot(look) >= minDot;
                })
                .min(Comparator.comparingDouble(target -> eyePosition.distanceToSqr(target.getEyePosition())))
                .orElse(null);
    }

    public static int getExecutionerDamagePercent() {
        return Math.round(EXECUTIONER_DAMAGE_RATIO * 100.0F);
    }

    public static boolean isExecutionerWeapon(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SwordItem;
    }

    public static Mob findDominanceMobTarget(LivingEntity caster, double range, double minDot) {
        LivingEntity target = findTargetInSight(caster, range, minDot);
        return target instanceof Mob mob ? mob : null;
    }

    public static boolean addDominatedMob(LivingEntity caster, Mob mob, int spellLevel, int maxControlled) {
        CompoundTag data = caster.getPersistentData();
        ListTag list = getDominatedMobList(data);
        String uuid = mob.getUUID().toString();
        for (int i = 0; i < list.size(); i++) {
            if (uuid.equals(list.getString(i))) {
                data.putBoolean(TAG_DOMINANCE_LINK_ACTIVE, true);
                tagDominatedMob(mob, caster.getUUID(), spellLevel);
                return true;
            }
        }

        if (list.size() >= maxControlled) {
            return false;
        }

        list.add(StringTag.valueOf(uuid));
        data.put(TAG_DOMINANCE_MOBS, list);
        data.putBoolean(TAG_DOMINANCE_LINK_ACTIVE, true);
        tagDominatedMob(mob, caster.getUUID(), spellLevel);
        return true;
    }

    public static void setDominanceTargetPlayer(ServerPlayer caster, UUID targetPlayerId) {
        if (targetPlayerId.equals(caster.getUUID())) {
            return;
        }

        Player target = caster.serverLevel().getPlayerByUUID(targetPlayerId);
        if (target == null) {
            return;
        }

        caster.getPersistentData().putUUID(TAG_DOMINANCE_TARGET_PLAYER, targetPlayerId);
        retargetDominatedMobs(caster, target);
        caster.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.corpse_campus.dominance_target_set", target.getDisplayName()), true);
    }

    public static void tickDominance(Player player) {
        CompoundTag data = player.getPersistentData();
        List<Mob> dominatedMobs = getDominatedMobs(player);

        if (dominatedMobs.isEmpty()) {
            clear(data, TAG_DOMINANCE_MOBS, TAG_DOMINANCE_TARGET_PLAYER);
            if (data.getBoolean(TAG_DOMINANCE_LINK_ACTIVE) && player.isAlive()) {
                data.remove(TAG_DOMINANCE_LINK_ACTIVE);
                player.hurt(player.damageSources().magic(), Float.MAX_VALUE);
            }
            return;
        }

        data.putBoolean(TAG_DOMINANCE_LINK_ACTIVE, true);
        LivingEntity forcedTarget = null;
        if (data.hasUUID(TAG_DOMINANCE_TARGET_PLAYER) && player.level() instanceof ServerLevel serverLevel) {
            forcedTarget = serverLevel.getPlayerByUUID(data.getUUID(TAG_DOMINANCE_TARGET_PLAYER));
            if (forcedTarget == null || !forcedTarget.isAlive()) {
                data.remove(TAG_DOMINANCE_TARGET_PLAYER);
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

    public static boolean isDominatedBy(Mob mob, Player owner) {
        CompoundTag tag = mob.getPersistentData();
        return tag.hasUUID(TAG_DOMINANCE_OWNER) && owner.getUUID().equals(tag.getUUID(TAG_DOMINANCE_OWNER));
    }

    public static List<Mob> getDominatedMobs(Player player) {
        CompoundTag data = player.getPersistentData();
        ListTag list = getDominatedMobList(data);
        ListTag cleaned = new ListTag();
        java.util.List<Mob> mobs = new java.util.ArrayList<>();

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return java.util.List.of();
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

        data.put(TAG_DOMINANCE_MOBS, cleaned);
        return mobs;
    }

    private static ListTag getDominatedMobList(CompoundTag data) {
        return data.contains(TAG_DOMINANCE_MOBS, Tag.TAG_LIST)
                ? data.getList(TAG_DOMINANCE_MOBS, Tag.TAG_STRING)
                : new ListTag();
    }

    private static void tagDominatedMob(Mob mob, UUID casterId, int spellLevel) {
        CompoundTag tag = mob.getPersistentData();
        tag.putUUID(TAG_DOMINANCE_OWNER, casterId);
        tag.putInt(TAG_DOMINANCE_LEVEL, spellLevel);
    }

    public static boolean canExecutionerUse(ItemStack stack) {
        return isExecutionerWeapon(stack) && (!stack.isDamageableItem()
                || stack.getDamageValue() + EXECUTIONER_DURABILITY_COST < stack.getMaxDamage());
    }

    public static void tickExecutionerCast(Level level, LivingEntity caster, int spellLevel) {
        CompoundTag data = caster.getPersistentData();
        long gameTime = level.getGameTime();
        long interval = Math.max(4L, 8L - spellLevel);
        if (gameTime - data.getLong(TAG_EXECUTIONER_LAST_TICK) < interval) {
            return;
        }

        ItemStack weapon = caster.getMainHandItem();
        if (!canExecutionerUse(weapon)) {
            return;
        }

        data.putLong(TAG_EXECUTIONER_LAST_TICK, gameTime);
        float damage = getExecutionerDamage(caster, weapon);
        if (caster.isCrouching()) {
            performExecutionerGroundSlash(level, caster, spellLevel, damage);
        } else {
            performExecutionerWaveSlash(level, caster, spellLevel, damage);
        }

        if (caster instanceof Player player) {
            player.swing(player.getUsedItemHand(), true);
        }

        if (weapon.isDamageableItem()) {
            weapon.hurtAndBreak(EXECUTIONER_DURABILITY_COST, caster, broken -> broken.broadcastBreakEvent(caster.getUsedItemHand()));
        }
    }

    private static float getExecutionerDamage(LivingEntity caster, ItemStack weapon) {
        float base = 4.0F;
        if (weapon.getItem() instanceof SwordItem swordItem) {
            base = swordItem.getDamage();
        }
        return Math.max(1.0F, base * EXECUTIONER_DAMAGE_RATIO + (float) caster.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.1F);
    }

    private static void performExecutionerWaveSlash(Level level, LivingEntity caster, int spellLevel, float damage) {
        Vec3 start = caster.getEyePosition().add(caster.getLookAngle().scale(0.7D));
        Vec3 direction = caster.getLookAngle().normalize();
        double range = 6.0D + spellLevel * 1.25D;
        BlockHitResult blockHitResult = level.clip(new net.minecraft.world.level.ClipContext(start,
                start.add(direction.scale(range)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                caster));
        double actualRange = blockHitResult.getType() == HitResult.Type.BLOCK
                ? Math.max(1.0D, start.distanceTo(blockHitResult.getLocation()))
                : range;
        Vec3 end = start.add(direction.scale(actualRange));
        AABB hitBox = new AABB(start, end).inflate(1.0D + spellLevel * 0.08D);

        hitEntitiesInSlash(level, caster, hitBox, start, direction, actualRange, 0.58D, damage);
        spawnExecutionerTrail(level, start, end, false);
        level.playSound(null, caster.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                0.35F, 0.85F + spellLevel * 0.03F);
    }

    private static void performExecutionerGroundSlash(Level level, LivingEntity caster, int spellLevel, float damage) {
        Vec3 start = caster.getEyePosition();
        Vec3 direction = caster.getLookAngle().normalize();
        double range = 5.0D + spellLevel;
        BlockHitResult blockHitResult = level.clip(new net.minecraft.world.level.ClipContext(start,
                start.add(direction.scale(range)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                caster));
        Vec3 center = blockHitResult.getType() == HitResult.Type.BLOCK
                ? blockHitResult.getLocation().subtract(direction.scale(0.25D))
                : start.add(direction.scale(range));
        double radius = 1.8D + spellLevel * 0.22D;
        AABB hitBox = new AABB(center, center).inflate(radius, 1.5D, radius);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitBox,
                target -> target != caster && target.isAlive())) {
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().mobAttack(caster), damage);
        }

        spawnExecutionerBurst(level, center, radius);
        level.playSound(null, caster.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                0.18F, 1.5F);
    }

    private static void hitEntitiesInSlash(Level level, LivingEntity caster, AABB hitBox, Vec3 start, Vec3 direction,
            double range, double minDot, float damage) {
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitBox,
                target -> target != caster && target.isAlive())) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(start);
            double along = delta.dot(direction);
            if (along < 0.0D || along > range) {
                continue;
            }

            double dot = delta.lengthSqr() <= 1.0E-4D ? 1.0D : delta.normalize().dot(direction);
            if (dot < minDot) {
                continue;
            }

            target.invulnerableTime = 0;
            target.hurt(level.damageSources().mobAttack(caster), damage);
        }
    }

    private static void spawnExecutionerTrail(Level level, Vec3 start, Vec3 end, boolean dense) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 delta = end.subtract(start);
        int steps = dense ? 18 : 12;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 point = start.add(delta.scale(t));
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
            serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.78F, 0.08F, 0.08F), 0.8F),
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.04D,
                    0.04D,
                    0.04D,
                    0.0D);
        }
    }

    private static void spawnExecutionerBurst(Level level, Vec3 center, double radius) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int points = 18;
        for (int i = 0; i < points; i++) {
            float angle = (float) (Math.PI * 2.0D * i / points);
            double x = center.x + Mth.cos(angle) * radius;
            double z = center.z + Mth.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    x,
                    center.y + 0.1D,
                    z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }
        serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.85F, 0.1F, 0.1F), 1.15F),
                center.x,
                center.y + 0.1D,
                center.z,
                20,
                radius * 0.35D,
                0.15D,
                radius * 0.35D,
                0.0D);
    }
}

package com.mifan.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

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
}

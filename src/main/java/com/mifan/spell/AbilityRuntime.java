package com.mifan.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public final class AbilityRuntime {
    public static final String TAG_SONIC_END = "corpse_campus_sonic_end";
    public static final String TAG_SONIC_LEVEL = "corpse_campus_sonic_level";

    public static final String TAG_DANGER_END = "corpse_campus_danger_end";
    public static final String TAG_DANGER_LEVEL = "corpse_campus_danger_level";
    public static final String TAG_DANGER_LAST_ALERT = "corpse_campus_danger_last_alert";

    public static final String TAG_TELEKINESIS_END = "corpse_campus_telekinesis_end";
    public static final String TAG_TELEKINESIS_X = "corpse_campus_telekinesis_x";
    public static final String TAG_TELEKINESIS_Y = "corpse_campus_telekinesis_y";
    public static final String TAG_TELEKINESIS_Z = "corpse_campus_telekinesis_z";

    public static final String TAG_MAGNETIC_END = "corpse_campus_magnetic_end";
    public static final String TAG_MAGNETIC_LEVEL = "corpse_campus_magnetic_level";
    public static final String TAG_MAGNETIC_CLINGING = "corpse_campus_magnetic_clinging";
    public static final String TAG_MAGNETIC_CLING_END = "corpse_campus_magnetic_cling_end";
    public static final String TAG_MAGNETIC_LAST_GROUND = "corpse_campus_magnetic_last_ground";

    public static final String TAG_INSTINCT_END = "corpse_campus_instinct_end";
    public static final String TAG_INSTINCT_LEVEL = "corpse_campus_instinct_level";
    public static final String TAG_INSTINCT_USED = "corpse_campus_instinct_used";
    public static final String TAG_INSTINCT_INVULNERABLE_UNTIL = "corpse_campus_instinct_invulnerable_until";

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
}

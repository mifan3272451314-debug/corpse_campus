package com.mifan.spell.runtime;

import com.mifan.spell.AbilityRuntime;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class TelekinesisRuntime {
    private TelekinesisRuntime() {
    }

    public static void storeLookVector(CompoundTag data, Vec3 look) {
        data.putDouble(AbilityRuntime.TAG_TELEKINESIS_LOOK_X, look.x);
        data.putDouble(AbilityRuntime.TAG_TELEKINESIS_LOOK_Y, look.y);
        data.putDouble(AbilityRuntime.TAG_TELEKINESIS_LOOK_Z, look.z);
    }

    public static Vec3 readStoredLookVector(CompoundTag data) {
        return new Vec3(
                data.getDouble(AbilityRuntime.TAG_TELEKINESIS_LOOK_X),
                data.getDouble(AbilityRuntime.TAG_TELEKINESIS_LOOK_Y),
                data.getDouble(AbilityRuntime.TAG_TELEKINESIS_LOOK_Z));
    }

    public static void beginCast(Level level, int spellLevel, LivingEntity caster, LivingEntity target) {
        Vec3 look = caster.getLookAngle().normalize();
        CompoundTag data = caster.getPersistentData();
        data.putInt(AbilityRuntime.TAG_TELEKINESIS_TARGET_ID, target.getId());
        data.putLong(AbilityRuntime.TAG_TELEKINESIS_HOLD_UNTIL, level.getGameTime() + 8L);
        data.putInt(AbilityRuntime.TAG_TELEKINESIS_LEVEL, spellLevel);
        storeLookVector(data, look);

        target.setNoGravity(true);
        target.fallDistance = 0.0F;
        target.setDeltaMovement(Vec3.ZERO);
        target.hasImpulse = true;
        target.hurtMarked = true;

        if (level.getGameTime() % 12L == 0L) {
            level.playSound(null, caster.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 0.12F, 1.5F);
        }
    }

    public static void tickCaster(LivingEntity caster, CompoundTag data, long gameTime) {
        if (!data.contains(AbilityRuntime.TAG_TELEKINESIS_TARGET_ID)) {
            return;
        }

        LivingEntity target = AbilityRuntime.findLivingEntityById(caster.level(),
                data.getInt(AbilityRuntime.TAG_TELEKINESIS_TARGET_ID));
        if (target == null || !target.isAlive()) {
            clearState(data);
            return;
        }

        int spellLevel = AbilityRuntime.getLevel(data, AbilityRuntime.TAG_TELEKINESIS_LEVEL);
        boolean stillHolding = gameTime <= data.getLong(AbilityRuntime.TAG_TELEKINESIS_HOLD_UNTIL);
        if (stillHolding) {
            Vec3 look = readStoredLookVector(data).normalize();
            Vec3 anchor = caster.getEyePosition().add(look.scale(2.6D + spellLevel * 0.2D));
            Vec3 motion = anchor.subtract(target.getBoundingBox().getCenter()).scale(0.45D);

            target.setNoGravity(true);
            target.setDeltaMovement(motion);
            target.fallDistance = 0.0F;
            target.hasImpulse = true;
            target.hurtMarked = true;

            if (caster.level() instanceof ServerLevel serverLevel && gameTime % 2L == 0L) {
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        target.getX(),
                        target.getY() + target.getBbHeight() * 0.5D,
                        target.getZ(),
                        2,
                        0.12D,
                        0.18D,
                        0.12D,
                        0.0D);
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        anchor.x,
                        anchor.y,
                        anchor.z,
                        2,
                        0.08D,
                        0.08D,
                        0.08D,
                        0.0D);
                serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.62F, 0.78F, 1.0F), 0.75F),
                        anchor.x,
                        anchor.y,
                        anchor.z,
                        2,
                        0.1D,
                        0.1D,
                        0.1D,
                        0.0D);
            }

            if (gameTime % 20L == 0L && caster instanceof Player player) {
                player.causeFoodExhaustion(0.12F + spellLevel * 0.03F);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 0, false, false, false));
            }
            return;
        }

        release(caster, target, data, true);
    }

    public static void releaseOnCastComplete(Level level, LivingEntity caster) {
        if (caster.getPersistentData().contains(AbilityRuntime.TAG_TELEKINESIS_TARGET_ID)) {
            caster.getPersistentData().putLong(AbilityRuntime.TAG_TELEKINESIS_HOLD_UNTIL, level.getGameTime() - 1L);
        }
    }

    public static void clearState(CompoundTag data) {
        AbilityRuntime.clear(
                data,
                AbilityRuntime.TAG_TELEKINESIS_TARGET_ID,
                AbilityRuntime.TAG_TELEKINESIS_HOLD_UNTIL,
                AbilityRuntime.TAG_TELEKINESIS_LEVEL,
                AbilityRuntime.TAG_TELEKINESIS_LOOK_X,
                AbilityRuntime.TAG_TELEKINESIS_LOOK_Y,
                AbilityRuntime.TAG_TELEKINESIS_LOOK_Z);
    }

    private static void release(LivingEntity caster, LivingEntity target, CompoundTag data, boolean throwTarget) {
        Vec3 releaseDirection = readStoredLookVector(data);
        int spellLevel = AbilityRuntime.getLevel(data, AbilityRuntime.TAG_TELEKINESIS_LEVEL);

        target.setNoGravity(false);
        target.fallDistance = 0.0F;
        if (throwTarget) {
            Vec3 releaseVelocity = releaseDirection.normalize().scale(1.15D + spellLevel * 0.22D)
                    .add(0.0D, 0.45D + spellLevel * 0.06D, 0.0D);
            target.setDeltaMovement(releaseVelocity);
            target.hasImpulse = true;
            target.hurtMarked = true;

            if (caster.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        target.getX(),
                        target.getY() + target.getBbHeight() * 0.5D,
                        target.getZ(),
                        6,
                        0.2D,
                        0.2D,
                        0.2D,
                        0.05D);
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        target.getX(),
                        target.getY() + target.getBbHeight() * 0.55D,
                        target.getZ(),
                        8,
                        0.22D,
                        0.22D,
                        0.22D,
                        0.03D);
                serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.72F, 0.88F, 1.0F), 1.0F),
                        target.getX(),
                        target.getY() + target.getBbHeight() * 0.45D,
                        target.getZ(),
                        6,
                        0.18D,
                        0.18D,
                        0.18D,
                        0.0D);
                caster.level().playSound(null, caster.blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS,
                        0.14F, 1.28F);
            }
        }

        clearState(data);
    }
}

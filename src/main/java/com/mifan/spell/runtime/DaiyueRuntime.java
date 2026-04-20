package com.mifan.spell.runtime;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

public final class DaiyueRuntime {
    private DaiyueRuntime() {
    }

    public static int getDashRange(int spellLevel) {
        return 6 + Math.max(0, spellLevel - 1);
    }

    public static double getHitWidth(int spellLevel) {
        return 2.5D + Math.max(0, spellLevel - 1) * 0.3D;
    }

    public static void cast(Level level, LivingEntity caster, int spellLevel, float spellPower) {
        Vec3 start = caster.position();
        Vec3 eyeStart = caster.getEyePosition();
        Vec3 direction = caster.getLookAngle();
        Vec3 horizontalDirection = new Vec3(direction.x, 0.0D, direction.z);
        if (horizontalDirection.lengthSqr() < 1.0E-4D) {
            horizontalDirection = new Vec3(0.0D, 0.0D, 1.0D);
        }
        horizontalDirection = horizontalDirection.normalize();

        double range = getDashRange(spellLevel);
        BlockHitResult blockHitResult = level.clip(new net.minecraft.world.level.ClipContext(
                eyeStart,
                eyeStart.add(horizontalDirection.scale(range)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                caster));
        double actualRange = blockHitResult.getType() == HitResult.Type.BLOCK
                ? Math.max(1.2D, eyeStart.distanceTo(blockHitResult.getLocation()) - 0.6D)
                : range;

        Vec3 end = start.add(horizontalDirection.scale(actualRange));
        float damage = Math.max(4.0F, spellPower + 3.0F + spellLevel * 0.8F);
        performDash(level, caster, start, end, horizontalDirection, spellLevel, damage);

        caster.setDeltaMovement(horizontalDirection.x * 2.35D,
                caster.onGround() ? 0.12D : Math.max(caster.getDeltaMovement().y, 0.03D),
                horizontalDirection.z * 2.35D);
        caster.hurtMarked = true;
        caster.fallDistance = 0.0F;

        spawnTrail(level, start, end, spellLevel);
        level.playSound(null, caster.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                0.55F, 0.75F + spellLevel * 0.04F);
        level.playSound(null, caster.blockPosition(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.PLAYERS,
                0.22F, 1.45F);
    }

    private static void performDash(Level level, LivingEntity caster, Vec3 start, Vec3 end, Vec3 direction,
            int spellLevel, float damage) {
        Vec3 delta = end.subtract(start);
        double distance = delta.length();
        if (distance < 1.0E-4D) {
            return;
        }

        int steps = Math.max(6, Mth.ceil(distance * 4.0D));
        double hitRadius = Math.max(0.75D, getHitWidth(spellLevel) * 0.4D);
        Set<Integer> hitEntityIds = new HashSet<>();

        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 point = start.add(delta.scale(t));
            caster.setPos(point.x, point.y, point.z);

            AABB stepBox = caster.getBoundingBox().inflate(hitRadius, 0.45D, hitRadius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, stepBox,
                    target -> target != caster && target.isAlive() && !hitEntityIds.contains(target.getId()))) {
                target.invulnerableTime = 0;
                if (target.hurt(level.damageSources().mobAttack(caster), damage)) {
                    hitEntityIds.add(target.getId());
                    target.knockback(0.2D + spellLevel * 0.03D, -direction.x, -direction.z);
                    spawnTargetSlash(level, target, direction);
                }
            }
        }
    }

    private static void spawnTrail(Level level, Vec3 start, Vec3 end, int spellLevel) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 delta = end.subtract(start);
        int steps = Math.max(4, 6 + spellLevel);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 point = start.add(delta.scale(t)).add(0.0D, 0.65D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.03D,
                    0.02D,
                    0.03D,
                    0.0D);
        }
    }

    private static void spawnTargetSlash(Level level, LivingEntity target, Vec3 direction) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 forward = new Vec3(direction.x, 0.0D, direction.z);
        if (forward.lengthSqr() < 1.0E-4D) {
            forward = new Vec3(0.0D, 0.0D, 1.0D);
        }
        forward = forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        Vec3 slashStart = center.add(side.scale(0.6D)).add(0.0D, 0.35D, 0.0D);
        Vec3 slashEnd = center.subtract(side.scale(0.6D)).add(0.0D, -0.2D, 0.0D);
        Vec3 slashDelta = slashEnd.subtract(slashStart);

        for (int i = 0; i <= 4; i++) {
            double t = i / 4.0D;
            Vec3 point = slashStart.add(slashDelta.scale(t));
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }

        serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.9F, 0.92F, 1.0F), 1.0F),
                center.x,
                center.y,
                center.z,
                6,
                0.15D,
                0.35D,
                0.15D,
                0.0D);
        serverLevel.sendParticles(ParticleTypes.CRIT,
                center.x,
                center.y,
                center.z,
                4,
                0.12D,
                0.25D,
                0.12D,
                0.0D);
    }
}

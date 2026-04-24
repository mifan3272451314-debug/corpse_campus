package com.mifan.spell.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public final class WanxiangRuntime {
    private static final int BLINK_VERTICAL_SEARCH = 24;
    private static final int BLINK_HORIZONTAL_SEARCH = 6;
    private static final double BOX_DEFLATE_EPSILON = 0.02D;

    private WanxiangRuntime() {
    }

    public static int getBlinkRange(int spellLevel) {
        return 3 + spellLevel * 7;
    }

    public static int getSwapRange(int spellLevel) {
        return 3 + spellLevel * 7;
    }

    public static boolean cast(ServerLevel level, LivingEntity caster, int spellLevel) {
        if (!caster.isAlive()) {
            return false;
        }
        if (caster.isCrouching()) {
            return swapWithTarget(level, caster, spellLevel);
        }
        return blinkForward(level, caster, spellLevel);
    }

    private static boolean blinkForward(ServerLevel level, LivingEntity caster, int spellLevel) {
        Vec3 eyePosition = caster.getEyePosition();
        Vec3 lookDirection = caster.getLookAngle();
        if (lookDirection.lengthSqr() < 1.0E-6D) {
            lookDirection = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            lookDirection = lookDirection.normalize();
        }
        double maxRange = getBlinkRange(spellLevel);

        BlockHitResult hitResult = level.clip(new ClipContext(
                eyePosition,
                eyePosition.add(lookDirection.scale(maxRange)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                caster));

        Vec3 rayEnd;
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            Vec3 hitLoc = hitResult.getLocation();
            double distToHit = Math.max(0.5D, eyePosition.distanceTo(hitLoc) - 0.55D);
            rayEnd = eyePosition.add(lookDirection.scale(distToHit));
        } else {
            rayEnd = eyePosition.add(lookDirection.scale(maxRange));
        }

        Vec3 start = caster.position();
        double eyeOffsetY = eyePosition.y - start.y;
        Vec3 desiredFeet = new Vec3(rayEnd.x, clampY(level, rayEnd.y - eyeOffsetY), rayEnd.z);

        Vec3 destination = findAnySafePosition(level, caster, desiredFeet);
        if (destination == null) {
            destination = new Vec3(desiredFeet.x, clampY(level, desiredFeet.y), desiredFeet.z);
        }

        teleportAnyEntity(caster, level, destination, caster.getYRot(), caster.getXRot());
        spawnTeleportParticles(level, start, destination);
        level.playSound(null, BlockPos.containing(start), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.45F, 1.15F);
        level.playSound(null, caster.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS,
                0.45F, 1.05F);
        return true;
    }

    private static boolean swapWithTarget(ServerLevel level, LivingEntity caster, int spellLevel) {
        double range = getSwapRange(spellLevel);
        Entity target = findAnyEntityInSight(caster, range);
        if (target == null) {
            showActionBar(caster, "message.corpse_campus.wanxiang_no_target");
            playFailSound(level, caster, 0.8F);
            return false;
        }

        Vec3 casterOrigin = caster.position();
        Vec3 targetOrigin = target.position();

        float casterYRot = caster.getYRot();
        float casterXRot = caster.getXRot();
        float targetYRot = target.getYRot();
        float targetXRot = target.getXRot();

        teleportAnyEntity(target, level, casterOrigin, targetYRot, targetXRot);
        teleportAnyEntity(caster, level, targetOrigin, casterYRot, casterXRot);

        spawnTeleportParticles(level, casterOrigin, targetOrigin);
        spawnTeleportParticles(level, targetOrigin, casterOrigin);

        level.playSound(null, BlockPos.containing(casterOrigin), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.45F, 0.95F);
        level.playSound(null, BlockPos.containing(targetOrigin), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.45F, 1.1F);

        if (caster instanceof Player player) {
            player.displayClientMessage(
                    Component.translatable("message.corpse_campus.wanxiang_swapped", target.getDisplayName()),
                    true);
        }
        return true;
    }

    private static Entity findAnyEntityInSight(LivingEntity caster, double range) {
        Vec3 eyePosition = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        if (look.lengthSqr() < 1.0E-6D) {
            look = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            look = look.normalize();
        }
        final Vec3 lookDir = look;
        final double rangeSqr = range * range;

        AABB searchBox = caster.getBoundingBox().inflate(range);
        return caster.level()
                .getEntitiesOfClass(Entity.class, searchBox, entity -> entity != caster && entity.isAlive())
                .stream()
                .filter(entity -> eyePosition.distanceToSqr(entity.position()) <= rangeSqr)
                .min(Comparator.comparingDouble(entity -> {
                    Vec3 center = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
                    Vec3 toEntity = center.subtract(eyePosition);
                    double len = toEntity.length();
                    if (len < 1.0E-4D) {
                        return -1000.0D;
                    }
                    double cosAngle = toEntity.scale(1.0D / len).dot(lookDir);
                    return -cosAngle * 64.0D + len;
                }))
                .orElse(null);
    }

    private static Vec3 findAnySafePosition(ServerLevel level, LivingEntity entity, Vec3 preferred) {
        if (canOccupy(level, entity, preferred)) {
            return preferred;
        }
        for (int dy = 1; dy <= BLINK_VERTICAL_SEARCH; dy++) {
            for (int sign = 0; sign < 2; sign++) {
                double yOff = sign == 0 ? dy : -dy;
                Vec3 cand = new Vec3(preferred.x, clampY(level, preferred.y + yOff), preferred.z);
                if (canOccupy(level, entity, cand)) {
                    return cand;
                }
            }
        }
        for (int r = 1; r <= BLINK_HORIZONTAL_SEARCH; r++) {
            for (int dy = 0; dy <= BLINK_VERTICAL_SEARCH; dy++) {
                for (int sign = 0; sign < (dy == 0 ? 1 : 2); sign++) {
                    double yOff = dy == 0 ? 0 : (sign == 0 ? dy : -dy);
                    for (int xi = -r; xi <= r; xi++) {
                        for (int zi = -r; zi <= r; zi++) {
                            if (Math.max(Math.abs(xi), Math.abs(zi)) != r) {
                                continue;
                            }
                            Vec3 cand = new Vec3(
                                    preferred.x + xi * 0.9D,
                                    clampY(level, preferred.y + yOff),
                                    preferred.z + zi * 0.9D);
                            if (canOccupy(level, entity, cand)) {
                                return cand;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean canOccupy(ServerLevel level, LivingEntity entity, Vec3 position) {
        AABB boundingBox = entity.getDimensions(entity.getPose())
                .makeBoundingBox(position.x, position.y, position.z)
                .deflate(BOX_DEFLATE_EPSILON);
        return level.noCollision(entity, boundingBox);
    }

    private static void teleportAnyEntity(Entity entity, ServerLevel level, Vec3 destination, float yRot, float xRot) {
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(level, destination.x, destination.y, destination.z, yRot, xRot);
        } else {
            entity.teleportTo(destination.x, destination.y, destination.z);
            entity.setYRot(yRot);
            entity.setXRot(xRot);
        }
        entity.fallDistance = 0.0F;
        entity.hurtMarked = true;
    }

    private static void spawnTeleportParticles(ServerLevel level, Vec3 from, Vec3 to) {
        level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 0.9D, from.z, 20, 0.3D, 0.45D, 0.3D, 0.15D);
        level.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 0.9D, to.z, 20, 0.3D, 0.45D, 0.3D, 0.15D);
    }

    private static void showActionBar(LivingEntity caster, String messageKey) {
        if (caster instanceof Player player) {
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    private static void playFailSound(ServerLevel level, LivingEntity caster, float pitch) {
        level.playSound(null, caster.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS,
                0.3F, pitch);
    }

    private static double clampY(ServerLevel level, double y) {
        return Mth.clamp(y, level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 2);
    }
}

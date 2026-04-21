package com.mifan.spell.runtime;

import com.mifan.spell.AbilityRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class WanxiangRuntime {
    private static final double[] POSITION_OFFSETS = { 0.0D, 0.25D, -0.25D, 0.5D, -0.5D, 0.8D, -0.8D, 1.1D, -1.1D };
    private static final int BLINK_VERTICAL_SEARCH = 6;
    private static final int SWAP_VERTICAL_SEARCH = 4;
    private static final double BLINK_BACKSTEP = 0.35D;
    private static final double MIN_BLINK_DISTANCE = 1.5D;

    private WanxiangRuntime() {
    }

    public static int getBlinkRange(int spellLevel) {
        return 6 + spellLevel;
    }

    public static int getSwapRange(int spellLevel) {
        return 8 + spellLevel * 2;
    }

    public static void cast(ServerLevel level, LivingEntity caster, int spellLevel) {
        if (caster.isCrouching()) {
            swapWithTarget(level, caster, spellLevel);
        } else {
            blinkForward(level, caster, spellLevel);
        }
    }

    private static void blinkForward(ServerLevel level, LivingEntity caster, int spellLevel) {
        Vec3 direction = getHorizontalLook(caster);
        Vec3 eyePosition = caster.getEyePosition();
        double maxRange = getBlinkRange(spellLevel);

        BlockHitResult hitResult = level.clip(new ClipContext(
                eyePosition,
                eyePosition.add(direction.scale(maxRange)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                caster));

        double travelDistance = hitResult.getType() == HitResult.Type.BLOCK
                ? Math.max(MIN_BLINK_DISTANCE, eyePosition.distanceTo(hitResult.getLocation()) - 0.75D)
                : maxRange;

        Vec3 start = caster.position();
        Vec3 safeDestination = findBlinkDestination(level, caster, start, direction, travelDistance);

        if (safeDestination == null) {
            showActionBar(caster, "message.corpse_campus.wanxiang_no_space");
            playFailSound(level, caster, 0.7F);
            return;
        }

        teleportEntity(caster, level, safeDestination, caster.getYRot(), caster.getXRot());
        spawnTeleportParticles(level, start, safeDestination);
        level.playSound(null, BlockPos.containing(start), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.45F, 1.15F);
        level.playSound(null, caster.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS,
                0.45F, 1.05F);
    }

    private static void swapWithTarget(ServerLevel level, LivingEntity caster, int spellLevel) {
        LivingEntity target = AbilityRuntime.findTargetInSight(caster, getSwapRange(spellLevel), 0.84D);
        if (target == null) {
            showActionBar(caster, "message.corpse_campus.wanxiang_no_target");
            playFailSound(level, caster, 0.8F);
            return;
        }

        Vec3 casterOrigin = caster.position();
        Vec3 targetOrigin = target.position();
        Vec3 casterDestination = findSafePosition(level, caster, targetOrigin, SWAP_VERTICAL_SEARCH);
        Vec3 targetDestination = findSafePosition(level, target, casterOrigin, SWAP_VERTICAL_SEARCH);

        if (casterDestination == null || targetDestination == null) {
            showActionBar(caster, "message.corpse_campus.wanxiang_swap_blocked");
            playFailSound(level, caster, 0.65F);
            return;
        }

        float casterYRot = caster.getYRot();
        float casterXRot = caster.getXRot();
        float targetYRot = target.getYRot();
        float targetXRot = target.getXRot();

        teleportEntity(target, level, targetDestination, targetYRot, targetXRot);
        teleportEntity(caster, level, casterDestination, casterYRot, casterXRot);

        spawnTeleportParticles(level, casterOrigin, casterDestination);
        spawnTeleportParticles(level, targetOrigin, targetDestination);

        level.playSound(null, BlockPos.containing(casterOrigin), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.45F, 0.95F);
        level.playSound(null, BlockPos.containing(targetOrigin), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.45F, 1.1F);

        if (caster instanceof Player player) {
            player.displayClientMessage(
                    Component.translatable("message.corpse_campus.wanxiang_swapped", target.getDisplayName()),
                    true);
        }
    }

    private static Vec3 findBlinkDestination(ServerLevel level, LivingEntity caster, Vec3 start, Vec3 direction,
            double desiredDistance) {
        double distance = desiredDistance;
        while (distance >= MIN_BLINK_DISTANCE) {
            Vec3 desired = start.add(direction.scale(distance));
            Vec3 safePosition = findSafePosition(level, caster, desired, BLINK_VERTICAL_SEARCH);
            if (safePosition != null) {
                return safePosition;
            }
            distance -= BLINK_BACKSTEP;
        }

        return findSafePosition(level, caster, start.add(direction.scale(MIN_BLINK_DISTANCE)), BLINK_VERTICAL_SEARCH);
    }

    private static Vec3 findSafePosition(ServerLevel level, LivingEntity entity, Vec3 preferred, int verticalSearch) {
        for (int dy = 0; dy <= verticalSearch; dy++) {
            if (dy == 0) {
                Vec3 match = tryOffsets(level, entity, preferred);
                if (match != null) {
                    return match;
                }
                continue;
            }

            Vec3 upward = tryOffsets(level, entity, preferred.add(0.0D, dy, 0.0D));
            if (upward != null) {
                return upward;
            }

            Vec3 downward = tryOffsets(level, entity, preferred.add(0.0D, -dy, 0.0D));
            if (downward != null) {
                return downward;
            }
        }
        return null;
    }

    private static Vec3 tryOffsets(ServerLevel level, LivingEntity entity, Vec3 base) {
        for (double xOffset : POSITION_OFFSETS) {
            for (double zOffset : POSITION_OFFSETS) {
                Vec3 candidate = new Vec3(base.x + xOffset, clampY(level, base.y), base.z + zOffset);
                if (canStandAt(level, entity, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean canStandAt(ServerLevel level, LivingEntity entity, Vec3 position) {
        AABB boundingBox = entity.getDimensions(entity.getPose()).makeBoundingBox(position.x, position.y, position.z);
        if (!level.noCollision(entity, boundingBox)) {
            return false;
        }

        BlockPos feetPos = BlockPos.containing(position.x, position.y, position.z);
        if (!level.getFluidState(feetPos).isEmpty()) {
            return false;
        }

        BlockPos supportPos = BlockPos.containing(position.x, position.y - 0.2D, position.z);
        return !level.getBlockState(supportPos).isAir();
    }

    private static void teleportEntity(LivingEntity entity, ServerLevel level, Vec3 destination, float yRot,
            float xRot) {
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

    private static Vec3 getHorizontalLook(LivingEntity caster) {
        Vec3 look = caster.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return horizontal.normalize();
    }

    private static double clampY(ServerLevel level, double y) {
        return Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 2);
    }
}
package com.mifan.spell.runtime;

import com.mifan.spell.AbilityRuntime;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class ExecutionerRuntime {
    private ExecutionerRuntime() {
    }

    public static int getDamagePercent(int spellLevel) {
        return Math.round(getDamageRatio(spellLevel) * 100.0F);
    }

    public static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SwordItem;
    }

    public static boolean canUse(ItemStack stack) {
        return isWeapon(stack) && (!stack.isDamageableItem()
                || stack.getDamageValue() + AbilityRuntime.EXECUTIONER_DURABILITY_COST < stack.getMaxDamage());
    }

    public static void incrementShieldPressure(LivingEntity target) {
        if (!(target instanceof Player player) || !player.isUsingItem()) {
            resetShieldPressure(target);
            return;
        }

        ItemStack usingItem = player.getUseItem();
        if (!(usingItem.getItem() instanceof ShieldItem)) {
            resetShieldPressure(target);
            return;
        }

        CompoundTag data = target.getPersistentData();
        long gameTime = target.level().getGameTime();
        long lastTick = data.getLong(AbilityRuntime.TAG_EXECUTIONER_BLOCK_LAST_TICK);
        int hits = gameTime - lastTick <= 100L ? data.getInt(AbilityRuntime.TAG_EXECUTIONER_BLOCK_HITS) + 1 : 1;
        data.putLong(AbilityRuntime.TAG_EXECUTIONER_BLOCK_LAST_TICK, gameTime);
        data.putInt(AbilityRuntime.TAG_EXECUTIONER_BLOCK_HITS, hits);

        if (hits >= 15) {
            player.stopUsingItem();
            player.getCooldowns().addCooldown(usingItem.getItem(), 100);
            resetShieldPressure(target);
        }
    }

    public static void resetShieldPressure(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        data.remove(AbilityRuntime.TAG_EXECUTIONER_BLOCK_HITS);
        data.remove(AbilityRuntime.TAG_EXECUTIONER_BLOCK_LAST_TICK);
    }

    public static void tickCast(Level level, LivingEntity caster, int spellLevel) {
        CompoundTag data = caster.getPersistentData();
        long gameTime = level.getGameTime();
        long interval = Math.max(4L, 8L - spellLevel);
        if (gameTime - data.getLong(AbilityRuntime.TAG_EXECUTIONER_LAST_TICK) < interval) {
            return;
        }

        ItemStack weapon = caster.getMainHandItem();
        if (!canUse(weapon)) {
            return;
        }

        data.putLong(AbilityRuntime.TAG_EXECUTIONER_LAST_TICK, gameTime);
        float damage = getDamage(caster, weapon, spellLevel);
        if (caster.isCrouching()) {
            performGroundSlash(level, caster, spellLevel, damage);
        } else {
            performWaveSlash(level, caster, spellLevel, damage);
        }

        if (caster instanceof Player player) {
            player.swing(player.getUsedItemHand(), true);
        }

        if (weapon.isDamageableItem()) {
            weapon.hurtAndBreak(AbilityRuntime.EXECUTIONER_DURABILITY_COST, caster,
                    broken -> broken.broadcastBreakEvent(caster.getUsedItemHand()));
        }
    }

    private static float getDamage(LivingEntity caster, ItemStack weapon, int spellLevel) {
        float base = 4.0F;
        if (weapon.getItem() instanceof SwordItem swordItem) {
            base = swordItem.getDamage();
        }
        return Math.max(1.0F, base * getDamageRatio(spellLevel)
                + (float) caster.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                        * 0.1F);
    }

    private static float getDamageRatio(int spellLevel) {
        return 0.25F + Math.max(0, spellLevel - 1) * 0.1F;
    }

    private static void performWaveSlash(Level level, LivingEntity caster, int spellLevel, float damage) {
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
        spawnTrail(level, start, end, false);
        level.playSound(null, caster.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                0.35F, 0.85F + spellLevel * 0.03F);
    }

    private static void performGroundSlash(Level level, LivingEntity caster, int spellLevel, float damage) {
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
            incrementShieldPressure(target);
        }

        spawnBurst(level, center, radius);
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
            incrementShieldPressure(target);
        }
    }

    private static void spawnTrail(Level level, Vec3 start, Vec3 end, boolean dense) {
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

    private static void spawnBurst(Level level, Vec3 center, double radius) {
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

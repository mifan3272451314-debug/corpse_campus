package com.mifan.spell.runtime;

import com.mifan.spell.AbilityRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class MarkRuntime {
    private MarkRuntime() {
    }

    public static int getRadius(int spellLevel) {
        return 3 + Math.max(0, spellLevel - 1);
    }

    public static int getDurationSeconds(int spellLevel) {
        return 20 + spellLevel * 5;
    }

    public static int getRootSeconds() {
        return 10;
    }

    public static void place(LivingEntity caster, int spellLevel, BlockPos blockPos, Direction face) {
        CompoundTag data = caster.getPersistentData();
        Vec3 center = Vec3.atCenterOf(blockPos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.501D));
        data.putBoolean(AbilityRuntime.TAG_MARK_ACTIVE, true);
        data.putDouble(AbilityRuntime.TAG_MARK_X, center.x);
        data.putDouble(AbilityRuntime.TAG_MARK_Y, center.y);
        data.putDouble(AbilityRuntime.TAG_MARK_Z, center.z);
        data.putLong(AbilityRuntime.TAG_MARK_END, caster.level().getGameTime() + getDurationSeconds(spellLevel) * 20L);
        data.putInt(AbilityRuntime.TAG_MARK_LEVEL, spellLevel);
        data.put(AbilityRuntime.TAG_MARK_TRIGGERED, new ListTag());
    }

    public static void clear(CompoundTag data) {
        AbilityRuntime.clear(data, AbilityRuntime.TAG_MARK_ACTIVE, AbilityRuntime.TAG_MARK_X, AbilityRuntime.TAG_MARK_Y,
                AbilityRuntime.TAG_MARK_Z, AbilityRuntime.TAG_MARK_END, AbilityRuntime.TAG_MARK_LEVEL,
                AbilityRuntime.TAG_MARK_TRIGGERED);
    }

    public static Vec3 getCenter(CompoundTag data) {
        return new Vec3(data.getDouble(AbilityRuntime.TAG_MARK_X), data.getDouble(AbilityRuntime.TAG_MARK_Y),
                data.getDouble(AbilityRuntime.TAG_MARK_Z));
    }

    public static void tick(Player player, CompoundTag data, long gameTime) {
        if (!data.getBoolean(AbilityRuntime.TAG_MARK_ACTIVE)) {
            return;
        }

        if (data.getLong(AbilityRuntime.TAG_MARK_END) <= gameTime) {
            clear(data);
            return;
        }

        int spellLevel = Math.max(1, data.getInt(AbilityRuntime.TAG_MARK_LEVEL));
        Vec3 center = getCenter(data);
        double radius = getRadius(spellLevel);

        if (player.level() instanceof ServerLevel serverLevel && gameTime % 8L == 0L) {
            spawnRing(serverLevel, center, radius);
        }

        ListTag triggered = AbilityRuntime.getStringList(data, AbilityRuntime.TAG_MARK_TRIGGERED);
        boolean changed = false;
        AABB box = new AABB(center, center).inflate(radius, 1.5D, radius);
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box,
                target -> target.isAlive() && target != player)) {
            Vec3 feet = target.position();
            double dx = feet.x - center.x;
            double dz = feet.z - center.z;
            if (dx * dx + dz * dz > radius * radius || AbilityRuntime.containsUuid(triggered, target.getUUID())) {
                continue;
            }

            AbilityRuntime.appendUuid(triggered, target.getUUID());
            changed = true;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    getRootSeconds() * 20,
                    10,
                    false,
                    true,
                    true));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP,
                    getRootSeconds() * 20,
                    128,
                    false,
                    false,
                    true));
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;

            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.corpse_campus.mark_triggered", target.getDisplayName()), true);
            player.level().playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 0.45F, 1.6F);
        }

        if (changed) {
            data.put(AbilityRuntime.TAG_MARK_TRIGGERED, triggered);
        }
    }

    private static void spawnRing(ServerLevel serverLevel, Vec3 center, double radius) {
        int points = 24;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.52F, 0.18F, 0.76F), 1.0F),
                    x,
                    center.y + 0.02D,
                    z,
                    1,
                    0.03D,
                    0.0D,
                    0.03D,
                    0.0D);
        }
    }
}

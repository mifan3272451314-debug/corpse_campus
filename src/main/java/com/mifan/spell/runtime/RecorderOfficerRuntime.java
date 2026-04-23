package com.mifan.spell.runtime;

import com.mifan.spell.AbilityRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RecorderOfficerRuntime {
    private RecorderOfficerRuntime() {
    }

    public static int clampSeconds(int seconds) {
        return Mth.clamp(seconds, AbilityRuntime.RECORDER_OFFICER_MIN_SECONDS,
                AbilityRuntime.RECORDER_OFFICER_MAX_SECONDS);
    }

    public static boolean hasRecord(ItemStack stack) {
        return stack.is(Items.PAPER)
                && stack.hasTag()
                && stack.getTag().contains(AbilityRuntime.ITEM_TAG_RECORDER_OFFICER_NOTE, Tag.TAG_COMPOUND);
    }

    public static void recordPaper(ItemStack stack, LivingEntity caster, BlockPos blockPos, Direction face) {
        CompoundTag note = new CompoundTag();
        Vec3 center = Vec3.atCenterOf(blockPos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.501D));
        note.putDouble(AbilityRuntime.ITEM_TAG_RECORDER_OFFICER_X, center.x);
        note.putDouble(AbilityRuntime.ITEM_TAG_RECORDER_OFFICER_Y, center.y);
        note.putDouble(AbilityRuntime.ITEM_TAG_RECORDER_OFFICER_Z, center.z);
        note.putString(AbilityRuntime.ITEM_TAG_RECORDER_OFFICER_DIMENSION, caster.level().dimension().location().toString());
        stack.getOrCreateTag().put(AbilityRuntime.ITEM_TAG_RECORDER_OFFICER_NOTE, note);
    }

    public static void armTarget(ServerPlayer caster, int spellLevel, int targetEntityId, int timerSeconds) {
        ItemStack stack = caster.getMainHandItem();
        if (!hasRecord(stack)) {
            caster.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.corpse_campus.recorder_officer_no_record"), true);
            return;
        }

        LivingEntity target = AbilityRuntime.findLivingEntityById(caster.level(), targetEntityId);
        if (target == null || target == caster) {
            caster.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.corpse_campus.recorder_officer_no_target"), true);
            return;
        }

        CompoundTag note = stack.getTag().getCompound(AbilityRuntime.ITEM_TAG_RECORDER_OFFICER_NOTE);
        CompoundTag data = target.getPersistentData();
        data.putBoolean(AbilityRuntime.TAG_RECORDER_OFFICER_ARMED, true);
        data.putLong(AbilityRuntime.TAG_RECORDER_OFFICER_END,
                caster.level().getGameTime() + clampSeconds(timerSeconds) * 20L);
        data.putDouble(AbilityRuntime.TAG_RECORDER_OFFICER_X, note.getDouble(AbilityRuntime.ITEM_TAG_RECORDER_OFFICER_X));
        data.putDouble(AbilityRuntime.TAG_RECORDER_OFFICER_Y, note.getDouble(AbilityRuntime.ITEM_TAG_RECORDER_OFFICER_Y));
        data.putDouble(AbilityRuntime.TAG_RECORDER_OFFICER_Z, note.getDouble(AbilityRuntime.ITEM_TAG_RECORDER_OFFICER_Z));
        data.putString(AbilityRuntime.TAG_RECORDER_OFFICER_DIMENSION,
                note.getString(AbilityRuntime.ITEM_TAG_RECORDER_OFFICER_DIMENSION));
        data.putUUID(AbilityRuntime.TAG_RECORDER_OFFICER_CASTER, caster.getUUID());

        stack.shrink(1);
        caster.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.corpse_campus.recorder_officer_armed", target.getDisplayName(), clampSeconds(timerSeconds)), true);
        caster.level().playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.5F, 1.15F);
    }

    public static void tick(LivingEntity entity, long gameTime) {
        CompoundTag data = entity.getPersistentData();
        if (!data.getBoolean(AbilityRuntime.TAG_RECORDER_OFFICER_ARMED)) {
            return;
        }

        long endTime = data.getLong(AbilityRuntime.TAG_RECORDER_OFFICER_END);
        double x = data.getDouble(AbilityRuntime.TAG_RECORDER_OFFICER_X);
        double y = data.getDouble(AbilityRuntime.TAG_RECORDER_OFFICER_Y);
        double z = data.getDouble(AbilityRuntime.TAG_RECORDER_OFFICER_Z);
        String dimensionKey = data.getString(AbilityRuntime.TAG_RECORDER_OFFICER_DIMENSION);

        if (endTime > gameTime) {
            return;
        }

        clear(data);

        if (!(entity.level() instanceof ServerLevel currentLevel)) {
            return;
        }

        ServerLevel destinationLevel = currentLevel;
        ResourceLocation dimensionId = ResourceLocation.tryParse(dimensionKey);
        if (dimensionId != null && currentLevel.getServer() != null) {
            ServerLevel resolved = currentLevel.getServer()
                    .getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            if (resolved != null) {
                destinationLevel = resolved;
            }
        }

        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(destinationLevel, x, y, z, serverPlayer.getYRot(), serverPlayer.getXRot());
        } else if (entity.level() == destinationLevel) {
            entity.teleportTo(x, y, z);
        } else {
            return;
        }

        entity.fallDistance = 0.0F;
        destinationLevel.playSound(null, BlockPos.containing(x, y, z), SoundEvents.CHORUS_FRUIT_TELEPORT,
                SoundSource.PLAYERS, 0.65F, 1.1F);
        destinationLevel.sendParticles(ParticleTypes.PORTAL, x, y + 0.6D, z, 30, 0.35D, 0.5D, 0.35D, 0.05D);
    }

    public static void clear(CompoundTag data) {
        AbilityRuntime.clear(data,
                AbilityRuntime.TAG_RECORDER_OFFICER_ARMED,
                AbilityRuntime.TAG_RECORDER_OFFICER_END,
                AbilityRuntime.TAG_RECORDER_OFFICER_X,
                AbilityRuntime.TAG_RECORDER_OFFICER_Y,
                AbilityRuntime.TAG_RECORDER_OFFICER_Z,
                AbilityRuntime.TAG_RECORDER_OFFICER_DIMENSION,
                AbilityRuntime.TAG_RECORDER_OFFICER_CASTER);
    }

    public static void clearArmedByCaster(ServerPlayer caster) {
        if (caster == null || caster.getServer() == null) {
            return;
        }
        java.util.UUID casterId = caster.getUUID();
        for (ServerLevel level : caster.getServer().getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity living)) {
                    continue;
                }
                CompoundTag data = living.getPersistentData();
                if (!data.getBoolean(AbilityRuntime.TAG_RECORDER_OFFICER_ARMED)) {
                    continue;
                }
                if (!data.hasUUID(AbilityRuntime.TAG_RECORDER_OFFICER_CASTER)) {
                    continue;
                }
                if (casterId.equals(data.getUUID(AbilityRuntime.TAG_RECORDER_OFFICER_CASTER))) {
                    clear(data);
                }
            }
        }
    }
}

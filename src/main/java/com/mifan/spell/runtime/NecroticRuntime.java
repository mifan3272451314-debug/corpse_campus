package com.mifan.spell.runtime;

import com.mifan.registry.ModMobEffects;
import com.mifan.spell.AbilityRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.ParticleTypes;

public final class NecroticRuntime {
    private NecroticRuntime() {
    }

    public static int getHealAmount(int spellLevel) {
        return 4 + Math.max(0, spellLevel - 1) * 2;
    }

    public static double getUndeadMaxHealth() {
        return 40.0D;
    }

    public static float getNonPlayerKillHeal() {
        return 4.0F;
    }

    public static int getProvokeDurationTicks() {
        return 20 * 20;
    }

    public static void markProvoked(Mob mob, Player player) {
        CompoundTag data = mob.getPersistentData();
        data.putUUID(AbilityRuntime.TAG_NECROTIC_PROVOKED_BY, player.getUUID());
        data.putLong(AbilityRuntime.TAG_NECROTIC_PROVOKED_UNTIL, mob.level().getGameTime() + getProvokeDurationTicks());
    }

    public static boolean canMobTargetPlayer(Mob mob, Player player) {
        CompoundTag data = mob.getPersistentData();
        return data.hasUUID(AbilityRuntime.TAG_NECROTIC_PROVOKED_BY)
                && player.getUUID().equals(data.getUUID(AbilityRuntime.TAG_NECROTIC_PROVOKED_BY))
                && data.getLong(AbilityRuntime.TAG_NECROTIC_PROVOKED_UNTIL) > mob.level().getGameTime();
    }

    public static void clearProvoked(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        data.remove(AbilityRuntime.TAG_NECROTIC_PROVOKED_BY);
        data.remove(AbilityRuntime.TAG_NECROTIC_PROVOKED_UNTIL);
    }

    public static void tickUndead(Player player, CompoundTag data, long gameTime) {
        if (!player.hasEffect(ModMobEffects.NECROTIC_UNDEAD.get())) {
            restoreMaxHealth(player, data);
            AbilityRuntime.clear(data,
                    AbilityRuntime.TAG_NECROTIC_ALLOW_HEAL_UNTIL,
                    AbilityRuntime.TAG_NECROTIC_LAST_KILL_HEAL,
                    AbilityRuntime.TAG_NECROTIC_REVIVE_USED,
                    AbilityRuntime.TAG_NECROTIC_ORIGINAL_MAX_HEALTH,
                    AbilityRuntime.TAG_NECROTIC_MAX_HEALTH_APPLIED);
            return;
        }

        applyMaxHealth(player, data);

        MobEffectInstance maniaEffect = player.getEffect(ModMobEffects.MANIA.get());
        if (maniaEffect == null || maniaEffect.getDuration() < 40) {
            player.addEffect(new MobEffectInstance(ModMobEffects.MANIA.get(), 100, 0, false, false, false));
        }

        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0D),
                candidate -> candidate.isAlive() && candidate.getTarget() == player)) {
            if (!canMobTargetPlayer(mob, player)) {
                mob.setTarget(null);
                clearProvoked(mob);
            }
        }

        if (gameTime % 20L == 0L) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 0, false, false, false));
        }

        if (player.level() instanceof ServerLevel serverLevel && gameTime % 10L == 0L) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    player.getX(),
                    player.getEyeY() - 0.1D,
                    player.getZ(),
                    2,
                    0.2D,
                    0.15D,
                    0.2D,
                    0.0D);
        }
    }

    public static void reviveCaster(net.minecraftforge.event.entity.living.LivingDeathEvent event, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        if (!player.hasEffect(ModMobEffects.NECROTIC_REBIRTH_ARMED.get())) {
            return;
        }

        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(AbilityRuntime.TAG_NECROTIC_REVIVE_USED)) {
            return;
        }

        event.setCanceled(true);
        data.putBoolean(AbilityRuntime.TAG_NECROTIC_REVIVE_USED, true);
        MobEffectInstance armedEffect = player.getEffect(ModMobEffects.NECROTIC_REBIRTH_ARMED.get());
        int spellLevel = AbilityRuntime.getEffectLevel(armedEffect);
        player.removeEffect(ModMobEffects.NECROTIC_REBIRTH_ARMED.get());
        player.setHealth(1.0F);
        player.removeAllEffects();
        MobEffectInstance undeadInstance = new MobEffectInstance(ModMobEffects.NECROTIC_UNDEAD.get(),
                MobEffectInstance.INFINITE_DURATION,
                spellLevel - 1,
                false,
                false,
                false);
        undeadInstance.setCurativeItems(new java.util.ArrayList<>());
        player.addEffect(undeadInstance);
        applyMaxHealth(player, data);
        player.setHealth((float) player.getMaxHealth());
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 30, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 30, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 30, 0, false, false, true));
        player.clearFire();
        player.invulnerableTime = 20;
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_INFECT, SoundSource.PLAYERS,
                0.8F, 0.85F);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    player.getX(),
                    player.getY() + 1.0D,
                    player.getZ(),
                    16,
                    0.45D,
                    0.55D,
                    0.45D,
                    0.02D);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.corpse_campus.necrotic_rebirth_revived"), false);
    }

    public static void rewardKill(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer player) || attacker == event.getEntity()) {
            return;
        }

        MobEffectInstance undeadEffect = player.getEffect(ModMobEffects.NECROTIC_UNDEAD.get());
        if (undeadEffect == null) {
            return;
        }

        int spellLevel = AbilityRuntime.getEffectLevel(undeadEffect);
        CompoundTag data = player.getPersistentData();

        float baseHealAmount = event.getEntity() instanceof Player
                ? getHealAmount(spellLevel)
                : getNonPlayerKillHeal();
        float healAmount = Math.min(baseHealAmount,
                Math.max(0.0F, (float) player.getMaxHealth() - player.getHealth()));
        if (healAmount <= 0.0F) {
            return;
        }

        data.putLong(AbilityRuntime.TAG_NECROTIC_ALLOW_HEAL_UNTIL, player.level().getGameTime() + 2L);
        data.putFloat(AbilityRuntime.TAG_NECROTIC_LAST_KILL_HEAL, healAmount);
        player.heal(healAmount);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS,
                0.35F, 1.15F);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.corpse_campus.necrotic_rebirth_kill_heal", Math.round(healAmount)), true);
    }

    public static void applyMaxHealth(Player player, CompoundTag data) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        if (!data.getBoolean(AbilityRuntime.TAG_NECROTIC_MAX_HEALTH_APPLIED)) {
            data.putDouble(AbilityRuntime.TAG_NECROTIC_ORIGINAL_MAX_HEALTH, maxHealth.getBaseValue());
            data.putBoolean(AbilityRuntime.TAG_NECROTIC_MAX_HEALTH_APPLIED, true);
        }

        if (Math.abs(maxHealth.getBaseValue() - getUndeadMaxHealth()) > 0.01D) {
            maxHealth.setBaseValue(getUndeadMaxHealth());
        }
    }

    public static void restoreMaxHealth(Player player, CompoundTag data) {
        if (!data.getBoolean(AbilityRuntime.TAG_NECROTIC_MAX_HEALTH_APPLIED)) {
            return;
        }

        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && data.contains(AbilityRuntime.TAG_NECROTIC_ORIGINAL_MAX_HEALTH)) {
            maxHealth.setBaseValue(data.getDouble(AbilityRuntime.TAG_NECROTIC_ORIGINAL_MAX_HEALTH));
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth((float) player.getMaxHealth());
            }
        }
    }
}

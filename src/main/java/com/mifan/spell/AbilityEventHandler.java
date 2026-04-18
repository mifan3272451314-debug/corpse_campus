package com.mifan.spell;

import com.mifan.corpsecampus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class AbilityEventHandler {
    private AbilityEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }

        Player player = event.player;
        CompoundTag data = player.getPersistentData();
        long gameTime = player.level().getGameTime();

        tickSonicSense(player, data, gameTime);
        tickDangerSense(player, data, gameTime);
        tickMagneticCling(player, data, gameTime);
        clearExpiredInstinct(player, data, gameTime);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = (LivingEntity) event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        tickTelekinesis(entity, entity.getPersistentData(), entity.level().getGameTime());
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        CompoundTag data = entity.getPersistentData();
        long gameTime = entity.level().getGameTime();
        if (data.contains(AbilityRuntime.TAG_INSTINCT_INVULNERABLE_UNTIL)
                && data.getLong(AbilityRuntime.TAG_INSTINCT_INVULNERABLE_UNTIL) > gameTime) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        CompoundTag data = entity.getPersistentData();
        long gameTime = entity.level().getGameTime();
        if (!AbilityRuntime.isActive(data, AbilityRuntime.TAG_INSTINCT_END, gameTime)) {
            return;
        }

        if (data.contains(AbilityRuntime.TAG_INSTINCT_INVULNERABLE_UNTIL)
                && data.getLong(AbilityRuntime.TAG_INSTINCT_INVULNERABLE_UNTIL) > gameTime) {
            event.setAmount(0.0F);
            return;
        }

        if (!isLethalMeleeAttack(entity, event.getSource(), event.getAmount())) {
            return;
        }

        int spellLevel = AbilityRuntime.getLevel(data, AbilityRuntime.TAG_INSTINCT_LEVEL);
        float dodgeChance = 0.10F + 0.02F * (spellLevel - 1);
        if (entity.getRandom().nextFloat() < dodgeChance) {
            data.putLong(AbilityRuntime.TAG_INSTINCT_INVULNERABLE_UNTIL, gameTime + 20L);
            event.setAmount(0.0F);
            entity.level().playSound(null, entity.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
                    SoundSource.PLAYERS, 0.9F, 1.6F);
            return;
        }

        if (!data.getBoolean(AbilityRuntime.TAG_INSTINCT_USED)) {
            data.putBoolean(AbilityRuntime.TAG_INSTINCT_USED, true);
            data.putLong(AbilityRuntime.TAG_INSTINCT_INVULNERABLE_UNTIL, gameTime + 60L);
            event.setAmount(Math.max(0.0F, entity.getHealth() - 1.0F));
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, false, false, true));
            entity.level().playSound(null, entity.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F,
                    1.2F);
        }
    }

    private static void tickSonicSense(Player player, CompoundTag data, long gameTime) {
        if (!AbilityRuntime.isActive(data, AbilityRuntime.TAG_SONIC_END, gameTime)) {
            AbilityRuntime.clear(data, AbilityRuntime.TAG_SONIC_END, AbilityRuntime.TAG_SONIC_LEVEL);
            return;
        }

        if (gameTime % 10L != 0L) {
            return;
        }

        int spellLevel = AbilityRuntime.getLevel(data, AbilityRuntime.TAG_SONIC_LEVEL);
        double radius = 12.0D + spellLevel * 2.0D;
        for (LivingEntity target : player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                target -> target != player && target.isAlive())) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, true));
        }

        player.causeFoodExhaustion(0.08F + 0.03F * spellLevel);
        if (gameTime % 20L == 0L) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 0, false, false, true));
        }
    }

    private static void tickDangerSense(Player player, CompoundTag data, long gameTime) {
        if (!AbilityRuntime.isActive(data, AbilityRuntime.TAG_DANGER_END, gameTime)) {
            AbilityRuntime.clear(
                    data,
                    AbilityRuntime.TAG_DANGER_END,
                    AbilityRuntime.TAG_DANGER_LEVEL,
                    AbilityRuntime.TAG_DANGER_LAST_ALERT);
            return;
        }

        if (gameTime % 5L != 0L) {
            return;
        }

        int spellLevel = AbilityRuntime.getLevel(data, AbilityRuntime.TAG_DANGER_LEVEL);
        double radius = 16.0D + spellLevel * 2.0D;
        boolean foundThreat = false;
        for (Mob mob : player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(radius),
                mob -> mob.isAlive() && mob.getTarget() == player)) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20, 0, false, false, true));
            foundThreat = true;
        }

        if (gameTime % 20L == 0L) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 0, false, false, true));
        }

        if (foundThreat && gameTime - data.getLong(AbilityRuntime.TAG_DANGER_LAST_ALERT) >= 20L) {
            data.putLong(AbilityRuntime.TAG_DANGER_LAST_ALERT, gameTime);
            player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(),
                    SoundSource.PLAYERS,
                    0.7F, 0.6F);
        }
    }

    private static void tickTelekinesis(LivingEntity entity, CompoundTag data, long gameTime) {
        if (!data.contains(AbilityRuntime.TAG_TELEKINESIS_END)) {
            return;
        }

        if (AbilityRuntime.isActive(data, AbilityRuntime.TAG_TELEKINESIS_END, gameTime)) {
            entity.setNoGravity(true);
            entity.setDeltaMovement(0.0D, 0.08D, 0.0D);
            entity.fallDistance = 0.0F;
            entity.hasImpulse = true;
            entity.hurtMarked = true;

            if (gameTime + 1L >= data.getLong(AbilityRuntime.TAG_TELEKINESIS_END)) {
                releaseTelekinesis(entity, data);
            }
            return;
        }

        releaseTelekinesis(entity, data);
    }

    private static void releaseTelekinesis(LivingEntity entity, CompoundTag data) {
        Vec3 releaseVelocity = new Vec3(
                data.getDouble(AbilityRuntime.TAG_TELEKINESIS_X),
                data.getDouble(AbilityRuntime.TAG_TELEKINESIS_Y),
                data.getDouble(AbilityRuntime.TAG_TELEKINESIS_Z));

        entity.setNoGravity(false);
        entity.setDeltaMovement(releaseVelocity);
        entity.hasImpulse = true;
        entity.hurtMarked = true;

        AbilityRuntime.clear(
                data,
                AbilityRuntime.TAG_TELEKINESIS_END,
                AbilityRuntime.TAG_TELEKINESIS_X,
                AbilityRuntime.TAG_TELEKINESIS_Y,
                AbilityRuntime.TAG_TELEKINESIS_Z);
    }

    private static void tickMagneticCling(Player player, CompoundTag data, long gameTime) {
        if (!AbilityRuntime.isActive(data, AbilityRuntime.TAG_MAGNETIC_END, gameTime)) {
            stopMagneticCling(player, data);
            AbilityRuntime.clear(
                    data,
                    AbilityRuntime.TAG_MAGNETIC_END,
                    AbilityRuntime.TAG_MAGNETIC_LEVEL,
                    AbilityRuntime.TAG_MAGNETIC_LAST_GROUND);
            return;
        }

        int spellLevel = AbilityRuntime.getLevel(data, AbilityRuntime.TAG_MAGNETIC_LEVEL);
        boolean onGround = player.onGround();
        boolean lastGround = data.getBoolean(AbilityRuntime.TAG_MAGNETIC_LAST_GROUND);

        if (player.horizontalCollision && !onGround && !data.getBoolean(AbilityRuntime.TAG_MAGNETIC_CLINGING)) {
            data.putBoolean(AbilityRuntime.TAG_MAGNETIC_CLINGING, true);
            data.putLong(AbilityRuntime.TAG_MAGNETIC_CLING_END, gameTime + 40L);
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
        }

        if (data.getBoolean(AbilityRuntime.TAG_MAGNETIC_CLINGING)) {
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;

            if (gameTime >= data.getLong(AbilityRuntime.TAG_MAGNETIC_CLING_END) || player.isShiftKeyDown()
                    || onGround) {
                stopMagneticCling(player, data);
                emitShockwave(player, spellLevel);
            }
        } else if (onGround && !lastGround) {
            emitShockwave(player, spellLevel);
        }

        data.putBoolean(AbilityRuntime.TAG_MAGNETIC_LAST_GROUND, onGround);
    }

    private static void stopMagneticCling(Player player, CompoundTag data) {
        player.setNoGravity(false);
        data.remove(AbilityRuntime.TAG_MAGNETIC_CLING_END);
        data.putBoolean(AbilityRuntime.TAG_MAGNETIC_CLINGING, false);
    }

    private static void emitShockwave(Player player, int spellLevel) {
        double radius = 3.0D + spellLevel * 0.75D;
        double horizontalStrength = 1.1D + spellLevel * 0.15D;
        double verticalStrength = 0.35D + spellLevel * 0.05D;

        AbilityRuntime.pushNearbyEntities(player, radius, horizontalStrength, verticalStrength);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                0.8F, 0.8F);
    }

    private static void clearExpiredInstinct(Player player, CompoundTag data, long gameTime) {
        if (AbilityRuntime.isActive(data, AbilityRuntime.TAG_INSTINCT_END, gameTime)) {
            return;
        }

        AbilityRuntime.clear(
                data,
                AbilityRuntime.TAG_INSTINCT_END,
                AbilityRuntime.TAG_INSTINCT_LEVEL,
                AbilityRuntime.TAG_INSTINCT_USED,
                AbilityRuntime.TAG_INSTINCT_INVULNERABLE_UNTIL);
    }

    private static boolean isLethalMeleeAttack(LivingEntity entity, DamageSource source, float amount) {
        Entity directEntity = source.getDirectEntity();
        Entity sourceEntity = source.getEntity();
        return amount >= entity.getHealth()
                && directEntity instanceof LivingEntity
                && directEntity == sourceEntity;
    }
}

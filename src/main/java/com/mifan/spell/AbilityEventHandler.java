package com.mifan.spell;

import com.mifan.corpsecampus;
import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.DangerSensePingPacket;
import com.mifan.network.clientbound.InstinctProcPacket;
import com.mifan.registry.ModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

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

        tickSonicSense(player, gameTime);
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

        tickTelekinesisCaster(entity, entity.getPersistentData(), entity.level().getGameTime());
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
        MobEffectInstance instinctEffect = entity.getEffect(ModMobEffects.INSTINCT.get());
        if (instinctEffect == null) {
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

        int spellLevel = AbilityRuntime.getEffectLevel(instinctEffect);
        float dodgeChance = 0.10F + 0.02F * (spellLevel - 1);
        if (entity.getRandom().nextFloat() < dodgeChance) {
            data.putLong(AbilityRuntime.TAG_INSTINCT_INVULNERABLE_UNTIL, gameTime + 20L);
            event.setAmount(0.0F);
            if (entity instanceof ServerPlayer serverPlayer) {
                ModNetwork.sendToPlayer(new InstinctProcPacket(false), serverPlayer);
            }
            return;
        }

        if (!data.getBoolean(AbilityRuntime.TAG_INSTINCT_USED)) {
            data.putBoolean(AbilityRuntime.TAG_INSTINCT_USED, true);
            data.putLong(AbilityRuntime.TAG_INSTINCT_INVULNERABLE_UNTIL, gameTime + 60L);
            event.setAmount(Math.max(0.0F, entity.getHealth() - 1.0F));
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, false, false, true));
            if (entity instanceof ServerPlayer serverPlayer) {
                ModNetwork.sendToPlayer(new InstinctProcPacket(true), serverPlayer);
            }
        }
    }

    private static void tickSonicSense(Player player, long gameTime) {
        MobEffectInstance effectInstance = player.getEffect(ModMobEffects.SONIC_ATTUNEMENT.get());
        if (effectInstance == null) {
            return;
        }

        int spellLevel = AbilityRuntime.getEffectLevel(effectInstance);
        if (gameTime % 20L == 0L) {
            player.causeFoodExhaustion(0.03F + 0.015F * spellLevel);
        }
        if (gameTime % 40L == 0L) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 50, 0, false, false, false));
        }

        if (player.level() instanceof ServerLevel serverLevel && gameTime % 12L == 0L) {
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                    player.getX(),
                    player.getEyeY() - 0.2D,
                    player.getZ(),
                    2,
                    0.18D,
                    0.08D,
                    0.18D,
                    0.0D);
        }
    }

    private static void tickDangerSense(Player player, CompoundTag data, long gameTime) {
        MobEffectInstance effectInstance = player.getEffect(ModMobEffects.DANGER_SENSE.get());
        if (effectInstance == null) {
            data.remove(AbilityRuntime.TAG_DANGER_LAST_ALERT);
            return;
        }

        if (gameTime % 5L != 0L) {
            return;
        }

        int spellLevel = AbilityRuntime.getEffectLevel(effectInstance);
        double radius = 18.0D + spellLevel * 3.0D;
        boolean foundThreat = false;
        boolean playAlert = gameTime - data.getLong(AbilityRuntime.TAG_DANGER_LAST_ALERT) >= 16L;

        for (Mob mob : player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(radius),
                mob -> mob.isAlive() && mob.getTarget() == player)) {
            foundThreat = true;

            if (player instanceof ServerPlayer serverPlayer) {
                ModNetwork.sendToPlayer(new DangerSensePingPacket(mob.getId(), 16, playAlert), serverPlayer);
                playAlert = false;
            }
        }

        if (gameTime % 20L == 0L) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 0, false, false, false));
        }

        if (foundThreat) {
            data.putLong(AbilityRuntime.TAG_DANGER_LAST_ALERT, gameTime);
        }
    }

    private static void tickTelekinesisCaster(LivingEntity caster, CompoundTag data, long gameTime) {
        if (!data.contains(AbilityRuntime.TAG_TELEKINESIS_TARGET_ID)) {
            return;
        }

        LivingEntity target = AbilityRuntime.findLivingEntityById(caster.level(),
                data.getInt(AbilityRuntime.TAG_TELEKINESIS_TARGET_ID));
        if (target == null || !target.isAlive()) {
            AbilityRuntime.clear(
                    data,
                    AbilityRuntime.TAG_TELEKINESIS_TARGET_ID,
                    AbilityRuntime.TAG_TELEKINESIS_HOLD_UNTIL,
                    AbilityRuntime.TAG_TELEKINESIS_LEVEL,
                    AbilityRuntime.TAG_TELEKINESIS_LOOK_X,
                    AbilityRuntime.TAG_TELEKINESIS_LOOK_Y,
                    AbilityRuntime.TAG_TELEKINESIS_LOOK_Z);
            return;
        }

        int spellLevel = AbilityRuntime.getLevel(data, AbilityRuntime.TAG_TELEKINESIS_LEVEL);
        boolean stillHolding = gameTime <= data.getLong(AbilityRuntime.TAG_TELEKINESIS_HOLD_UNTIL);
        if (stillHolding) {
            Vec3 look = AbilityRuntime.readStoredLookVector(data).normalize();
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

        releaseTelekinesis(caster, target, data, true);
    }

    private static void releaseTelekinesis(LivingEntity caster, LivingEntity target, CompoundTag data,
            boolean throwTarget) {
        Vec3 releaseDirection = AbilityRuntime.readStoredLookVector(data);
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

        AbilityRuntime.clear(
                data,
                AbilityRuntime.TAG_TELEKINESIS_TARGET_ID,
                AbilityRuntime.TAG_TELEKINESIS_HOLD_UNTIL,
                AbilityRuntime.TAG_TELEKINESIS_LEVEL,
                AbilityRuntime.TAG_TELEKINESIS_LOOK_X,
                AbilityRuntime.TAG_TELEKINESIS_LOOK_Y,
                AbilityRuntime.TAG_TELEKINESIS_LOOK_Z);
    }

    private static void tickMagneticCling(Player player, CompoundTag data, long gameTime) {
        MobEffectInstance effectInstance = player.getEffect(ModMobEffects.MAGNETIC_CLING.get());
        if (effectInstance == null) {
            stopMagneticCling(player, data);
            AbilityRuntime.clear(data, AbilityRuntime.TAG_MAGNETIC_LAST_GROUND,
                    AbilityRuntime.TAG_MAGNETIC_SHOCK_READY);
            return;
        }

        int spellLevel = AbilityRuntime.getEffectLevel(effectInstance);
        boolean onGround = player.onGround();
        boolean lastGround = data.getBoolean(AbilityRuntime.TAG_MAGNETIC_LAST_GROUND);

        if (!onGround && player.fallDistance >= 4.0F) {
            data.putBoolean(AbilityRuntime.TAG_MAGNETIC_SHOCK_READY, true);
        }

        if (player.horizontalCollision && !onGround && !data.getBoolean(AbilityRuntime.TAG_MAGNETIC_CLINGING)) {
            data.putBoolean(AbilityRuntime.TAG_MAGNETIC_CLINGING, true);
            data.putLong(AbilityRuntime.TAG_MAGNETIC_CLING_END, gameTime + 40L);
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX(),
                        player.getY() + 0.9D,
                        player.getZ(),
                        8,
                        0.18D,
                        0.35D,
                        0.18D,
                        0.02D);
                serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.65F, 0.58F, 1.0F), 0.85F),
                        player.getX(),
                        player.getY() + 0.88D,
                        player.getZ(),
                        6,
                        0.16D,
                        0.3D,
                        0.16D,
                        0.0D);
            }
        }

        if (data.getBoolean(AbilityRuntime.TAG_MAGNETIC_CLINGING)) {
            player.setNoGravity(true);
            BlockPos headPos = BlockPos.containing(player.getX(), player.getBoundingBox().maxY + 0.05D, player.getZ());
            boolean blockedAbove = !player.level().getBlockState(headPos).isAir();
            double climbSpeed = blockedAbove ? 0.0D : (0.12D + spellLevel * 0.02D);

            player.setDeltaMovement(0.0D, climbSpeed, 0.0D);
            player.fallDistance = 0.0F;

            if (player.level() instanceof ServerLevel serverLevel && gameTime % 4L == 0L) {
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX(),
                        player.getY() + 0.8D,
                        player.getZ(),
                        3,
                        0.16D,
                        0.28D,
                        0.16D,
                        0.0D);
                serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.52F, 0.45F, 0.95F), 0.55F),
                        player.getX(),
                        player.getY() + 0.82D,
                        player.getZ(),
                        2,
                        0.12D,
                        0.22D,
                        0.12D,
                        0.0D);
            }

            if (onGround || !player.horizontalCollision) {
                stopMagneticCling(player, data);
                if (data.getBoolean(AbilityRuntime.TAG_MAGNETIC_SHOCK_READY)) {
                    emitShockwave(player, spellLevel);
                    data.remove(AbilityRuntime.TAG_MAGNETIC_SHOCK_READY);
                }
            }
        } else if (onGround && !lastGround && data.getBoolean(AbilityRuntime.TAG_MAGNETIC_SHOCK_READY)) {
            emitShockwave(player, spellLevel);
            data.remove(AbilityRuntime.TAG_MAGNETIC_SHOCK_READY);
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
                0.2F, 0.75F);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.65F, 0.55F, 1.0F), 1.2F),
                    player.getX(),
                    player.getY() + 0.1D,
                    player.getZ(),
                    12,
                    radius * 0.18D,
                    0.08D,
                    radius * 0.18D,
                    0.0D);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(),
                    player.getY() + 0.12D,
                    player.getZ(),
                    10,
                    radius * 0.16D,
                    0.05D,
                    radius * 0.16D,
                    0.03D);
        }
    }

    private static void clearExpiredInstinct(Player player, CompoundTag data, long gameTime) {
        if (player.hasEffect(ModMobEffects.INSTINCT.get())) {
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

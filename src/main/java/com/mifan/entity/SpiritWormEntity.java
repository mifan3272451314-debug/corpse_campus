package com.mifan.entity;

import com.mifan.registry.ModEntities;
import com.mifan.spell.AbilityRuntime;
import com.mifan.spell.runtime.MysticAttendantRuntime;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public class SpiritWormEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(
            SpiritWormEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private long expireTick = Long.MAX_VALUE;

    public SpiritWormEntity(EntityType<? extends SpiritWormEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public SpiritWormEntity(Level level, LivingEntity caster) {
        this(ModEntities.SPIRIT_WORM.get(), level);
        this.setOwnerUUID(caster.getUUID());
        this.expireTick = level.getGameTime() + AbilityRuntime.SPIRIT_WORM_DURATION_TICKS;
        this.setPos(caster.getX(), caster.getY() + 0.2D, caster.getZ());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, AbilityRuntime.SPIRIT_WORM_SPEED)
                .add(Attributes.FOLLOW_RANGE, AbilityRuntime.SPIRIT_WORM_SEARCH_RADIUS + 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SeekPlayerGoal(this));
    }

    public void setOwnerUUID(UUID uuid) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(uuid));
    }

    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public void setExpireTick(long tick) {
        this.expireTick = tick;
    }

    public long getExpireTick() {
        return this.expireTick;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effectInstance) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void push(double dx, double dy, double dz) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity other) {
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        if (other instanceof Player player && !player.isSpectator() && player.isAlive()) {
            tryConsumeOnPlayer(player);
        }
    }

    @Override
    public void playerTouch(Player player) {
        if (this.level().isClientSide) {
            return;
        }
        tryConsumeOnPlayer(player);
    }

    private void tryConsumeOnPlayer(Player player) {
        if (!this.isAlive() || this.isRemoved()) {
            return;
        }
        UUID owner = getOwnerUUID();
        if (owner != null && player.getUUID().equals(owner)) {
            return;
        }
        if (player.isSpectator() || !player.isAlive()) {
            return;
        }
        if (player instanceof ServerPlayer victim) {
            MysticAttendantRuntime.onWormTouchPlayer(this, victim);
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.3D, this.getZ(),
                        10, 0.25D, 0.25D, 0.25D, 0.04D);
            }
            this.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.random.nextFloat() < 0.35F) {
                this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getRandomX(0.3D), this.getY() + 0.2D + this.random.nextDouble() * 0.2D,
                        this.getRandomZ(0.3D),
                        0.0D, 0.01D, 0.0D);
            }
        } else {
            if (this.level().getGameTime() >= this.expireTick) {
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.3D, this.getZ(),
                            8, 0.2D, 0.2D, 0.2D, 0.02D);
                }
                this.discard();
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putLong("ExpireTick", this.expireTick);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            setOwnerUUID(tag.getUUID("Owner"));
        }
        if (tag.contains("ExpireTick")) {
            this.expireTick = tag.getLong("ExpireTick");
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private static final class SeekPlayerGoal extends Goal {
        private final SpiritWormEntity worm;
        private Player target;
        private int pathCooldown;

        SeekPlayerGoal(SpiritWormEntity worm) {
            this.worm = worm;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player p = findTarget();
            if (p == null) {
                return false;
            }
            this.target = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (target == null || !target.isAlive() || target.isSpectator()) {
                return false;
            }
            double r = AbilityRuntime.SPIRIT_WORM_SEARCH_RADIUS;
            if (worm.distanceToSqr(target) > r * r) {
                return false;
            }
            UUID owner = worm.getOwnerUUID();
            return owner == null || !target.getUUID().equals(owner);
        }

        @Override
        public void start() {
            worm.getNavigation().moveTo(target, 1.0D);
            pathCooldown = 0;
        }

        @Override
        public void stop() {
            worm.getNavigation().stop();
            target = null;
        }

        @Override
        public void tick() {
            if (target == null) {
                return;
            }
            worm.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--pathCooldown <= 0) {
                worm.getNavigation().moveTo(target, 1.0D);
                pathCooldown = 10;
            }
        }

        private Player findTarget() {
            UUID ownerId = worm.getOwnerUUID();
            double range = AbilityRuntime.SPIRIT_WORM_SEARCH_RADIUS;
            double bestDistSqr = range * range;
            Player best = null;
            for (Player p : worm.level().players()) {
                if (!p.isAlive() || p.isSpectator()) {
                    continue;
                }
                if (ownerId != null && p.getUUID().equals(ownerId)) {
                    continue;
                }
                double d = worm.distanceToSqr(p);
                if (d <= bestDistSqr) {
                    bestDistSqr = d;
                    best = p;
                }
            }
            return best;
        }
    }
}

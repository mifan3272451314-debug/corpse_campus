package com.mifan.entity;

import com.mifan.registry.ModEntities;
import com.mifan.registry.ModMobEffects;
import com.mifan.spell.AbilityRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class GoldenCrowSunEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> DATA_THROWN = SynchedEntityData.defineId(
            GoldenCrowSunEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_MANA_SPENT = SynchedEntityData.defineId(
            GoldenCrowSunEntity.class, EntityDataSerializers.FLOAT);

    private int lifeTicks = 0;

    public GoldenCrowSunEntity(EntityType<? extends GoldenCrowSunEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public GoldenCrowSunEntity(Level level, LivingEntity owner, float manaSpent) {
        this(ModEntities.GOLDEN_CROW_SUN.get(), level);
        this.setOwner(owner);
        this.setManaSpent(manaSpent);
        Vec3 anchor = computeHoverAnchor(owner);
        this.setPos(anchor.x, anchor.y, anchor.z);
        this.xo = anchor.x;
        this.yo = anchor.y;
        this.zo = anchor.z;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_THROWN, false);
        this.entityData.define(DATA_MANA_SPENT, 0.0F);
    }

    public boolean isThrown() {
        return this.entityData.get(DATA_THROWN);
    }

    public void setThrown(boolean thrown) {
        this.entityData.set(DATA_THROWN, thrown);
    }

    public float getManaSpent() {
        return this.entityData.get(DATA_MANA_SPENT);
    }

    public void setManaSpent(float mana) {
        this.entityData.set(DATA_MANA_SPENT, Math.max(0.0F, mana));
    }

    public int getLifeTicks() {
        return lifeTicks;
    }

    public static Vec3 computeHoverAnchor(LivingEntity owner) {
        return new Vec3(owner.getX(), owner.getEyeY() + 2.0D, owner.getZ());
    }

    public void throwTowards(LivingEntity caster) {
        Vec3 look = caster.getLookAngle().normalize();
        this.setThrown(true);
        this.setDeltaMovement(look.scale(AbilityRuntime.GOLDEN_CROW_THROW_SPEED));
        this.hasImpulse = true;
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                    1.2F, 0.8F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.lifeTicks++;

        if (!this.level().isClientSide && this.lifeTicks >= AbilityRuntime.GOLDEN_CROW_DURATION_TICKS) {
            if (this.isThrown()) {
                this.explodeAt(this.position());
            } else {
                this.discard();
            }
            return;
        }

        if (this.isThrown()) {
            tickThrown();
        } else {
            tickHovering();
        }

        emitAmbientParticles();
    }

    private void tickHovering() {
        Entity owner = this.getOwner();
        if (owner == null || owner.isRemoved() || !owner.isAlive()) {
            if (!this.level().isClientSide) {
                this.discard();
            }
            return;
        }
        if (owner instanceof LivingEntity living) {
            Vec3 anchor = computeHoverAnchor(living);
            this.setPos(anchor.x, anchor.y, anchor.z);
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    private void tickThrown() {
        if (!this.level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                this.onHit(hit);
                if (this.isRemoved()) {
                    return;
                }
            }
        }
        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
    }

    private void emitAmbientParticles() {
        Level level = this.level();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        // 核心：每 tick 铺一层「日面」粒子，撑满 2.4×2.4 的实体碰撞箱
        // 用 FLAME 大量随机分布形成球体轮廓，让玩家明显看到一个耀眼日轮
        server.sendParticles(ParticleTypes.FLAME, x, y, z, 28, 1.6D, 1.6D, 1.6D, 0.015D);
        server.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, 18, 1.9D, 1.9D, 1.9D, 0.01D);
        server.sendParticles(ParticleTypes.LAVA, x, y, z, 2, 1.2D, 1.2D, 1.2D, 0.0D);

        // 每 4 tick 一次「心跳脉冲」：强烈闪光 + 端棒亮点，营造日面跳动感
        if (this.tickCount % 4 == 0) {
            server.sendParticles(ParticleTypes.END_ROD, x, y, z, 12, 2.0D, 2.0D, 2.0D, 0.04D);
            server.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        // 每 10 tick 一次「日冕环」：一圈扩散的光粒，像日冕
        if (this.tickCount % 10 == 0) {
            emitCoronaRing(server, x, y, z, 2.6D, 24);
        }

        // 投掷阶段：额外长拖尾
        if (this.isThrown()) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 4, 0.4D, 0.4D, 0.4D, 0.01D);
            if (this.tickCount % 2 == 0) {
                server.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void emitCoronaRing(ServerLevel server, double cx, double cy, double cz,
            double radius, int count) {
        for (int i = 0; i < count; i++) {
            double theta = (Math.PI * 2.0D * i) / count;
            double dx = Math.cos(theta) * radius;
            double dz = Math.sin(theta) * radius;
            server.sendParticles(ParticleTypes.FLAME, cx + dx, cy, cz + dz, 1, 0.0D, 0.05D, 0.0D, 0.0D);
        }
    }

    private static void emitShockwaveRing(ServerLevel server, Vec3 center, double radius, int count,
            net.minecraft.core.particles.SimpleParticleType particle) {
        for (int i = 0; i < count; i++) {
            double theta = (Math.PI * 2.0D * i) / count;
            double dx = Math.cos(theta) * radius;
            double dz = Math.sin(theta) * radius;
            server.sendParticles(particle, center.x + dx, center.y + 0.2D, center.z + dz,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (target == this.getOwner()) {
            return false;
        }
        return super.canHitEntity(target);
    }

    @Override
    protected void onHit(HitResult result) {
        if (this.level().isClientSide || !this.isThrown()) {
            return;
        }
        Vec3 hitPos = result.getType() == HitResult.Type.ENTITY
                ? ((EntityHitResult) result).getEntity().position()
                : result.getLocation();
        explodeAt(hitPos);
    }

    private void explodeAt(Vec3 pos) {
        if (!(this.level() instanceof ServerLevel server)) {
            this.discard();
            return;
        }

        float manaSpent = this.getManaSpent();
        float damage = manaSpent * AbilityRuntime.GOLDEN_CROW_DAMAGE_PER_MANA;
        double explosionRadius = AbilityRuntime.GOLDEN_CROW_EXPLOSION_RADIUS;
        double stunRadius = AbilityRuntime.GOLDEN_CROW_STUN_RADIUS;

        Entity owner = this.getOwner();
        LivingEntity livingOwner = owner instanceof LivingEntity le ? le : null;

        server.explode(this, null, null, pos.x, pos.y, pos.z,
                (float) explosionRadius, true, Level.ExplosionInteraction.TNT);

        AABB stunBox = new AABB(
                pos.x - stunRadius, pos.y - stunRadius, pos.z - stunRadius,
                pos.x + stunRadius, pos.y + stunRadius, pos.z + stunRadius);
        DamageSource damageSource = livingOwner != null
                ? server.damageSources().mobAttack(livingOwner)
                : server.damageSources().explosion(null, null);

        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, stunBox)) {
            if (target == livingOwner) {
                continue;
            }
            if (target.isRemoved() || !target.isAlive()) {
                continue;
            }
            double dist = target.distanceToSqr(pos.x, pos.y, pos.z);
            double explosionSqr = explosionRadius * explosionRadius;
            if (dist <= explosionSqr && damage > 0.0F) {
                target.hurt(damageSource, damage);
            }
            target.addEffect(new MobEffectInstance(
                    ModMobEffects.GOLDEN_CROW_STUN.get(),
                    AbilityRuntime.GOLDEN_CROW_STUN_DURATION_TICKS,
                    0, false, true, true));
        }

        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        server.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 8, 2.0D, 2.0D, 2.0D, 0.0D);
        server.playSound(null, BlockPos.containing(pos.x, pos.y, pos.z), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 4.0F, 0.6F);

        // 震撼视觉：多重爆炸发射器 + 巨型冲击波环 + 火焰飞溅
        for (int i = 0; i < 6; i++) {
            double ox = (server.random.nextDouble() - 0.5D) * 6.0D;
            double oy = (server.random.nextDouble() - 0.5D) * 3.0D;
            double oz = (server.random.nextDouble() - 0.5D) * 6.0D;
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.x + ox, pos.y + oy, pos.z + oz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // 三环冲击波：小中大，营造震荡感
        emitShockwaveRing(server, pos, 3.0D, 32, ParticleTypes.FLAME);
        emitShockwaveRing(server, pos, 6.5D, 48, ParticleTypes.FLAME);
        emitShockwaveRing(server, pos, 10.0D, 64, ParticleTypes.END_ROD);
        // 向四周飞溅的火焰
        server.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 60, 5.0D, 3.0D, 5.0D, 0.3D);
        server.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 180, 6.0D, 4.0D, 6.0D, 0.4D);
        server.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 3, 0.0D, 0.0D, 0.0D, 0.0D);
        // 附加额外音效层，提升听觉反馈
        server.playSound(null, BlockPos.containing(pos.x, pos.y, pos.z), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 2.5F, 0.7F);
        server.playSound(null, BlockPos.containing(pos.x, pos.y, pos.z), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 2.0F, 0.5F);

        if (livingOwner instanceof Player playerOwner) {
            CompoundTag data = playerOwner.getPersistentData();
            data.putBoolean(AbilityRuntime.TAG_GOLDEN_CROW_ACTIVE, false);
            data.remove(AbilityRuntime.TAG_GOLDEN_CROW_ENTITY_UUID);
            data.remove(AbilityRuntime.TAG_GOLDEN_CROW_EXPIRE_TICK);
            data.remove(AbilityRuntime.TAG_GOLDEN_CROW_MANA_SPENT);
        }

        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Thrown", this.isThrown());
        tag.putFloat("ManaSpent", this.getManaSpent());
        tag.putInt("LifeTicks", this.lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setThrown(tag.getBoolean("Thrown"));
        this.setManaSpent(tag.getFloat("ManaSpent"));
        this.lifeTicks = tag.getInt("LifeTicks");
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 4096.0D * 4096.0D;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }
}

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
        return new Vec3(owner.getX(),
                owner.getEyeY() + AbilityRuntime.GOLDEN_CROW_HOVER_HEIGHT,
                owner.getZ());
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

        float mana = getManaSpent();
        double orb = AbilityRuntime.goldenCrowOrbRadius(mana);                  // 3~30 格
        double outer = Math.max(orb * 1.35D, orb + 3.0D);                       // 外壳
        float pScale = AbilityRuntime.goldenCrowParticleScale(mana);            // 0.3~3.0

        // A) 核心：实心球内填充 FLAME，数量随法力缩放
        int coreFlame = Math.max(20, (int) (400 * pScale));
        int coreSmall = Math.max(15, (int) (260 * pScale));
        int coreLava = Math.max(2, (int) (20 * pScale));
        server.sendParticles(ParticleTypes.FLAME, x, y, z, coreFlame,
                orb, orb, orb, 0.05D);
        server.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, coreSmall,
                orb * 1.1D, orb * 1.1D, orb * 1.1D, 0.03D);
        server.sendParticles(ParticleTypes.LAVA, x, y, z, coreLava,
                orb * 0.8D, orb * 0.8D, orb * 0.8D, 0.0D);

        // B) 外壳：扩散到 outer 半径
        int shellSmall = Math.max(10, (int) (180 * pScale));
        int shellEndRod = Math.max(6, (int) (90 * pScale));
        server.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, shellSmall,
                outer, outer * 0.6D, outer, 0.02D);
        server.sendParticles(ParticleTypes.END_ROD, x, y, z, shellEndRod,
                outer * 0.9D, outer * 0.9D, outer * 0.9D, 0.03D);

        // C) 每 2 tick 日冕环，半径在 orb~outer 间脉动；每圈粒子数按法力放大
        if (this.tickCount % 2 == 0) {
            double pulse = 0.5D + 0.5D * Math.sin(this.tickCount * 0.08D);
            double ring = orb + pulse * (outer - orb);
            emitCoronaRing(server, x, y, z, ring, Math.max(16, (int) (128 * pScale)));
        }

        // D) 每 4 tick 心跳脉冲
        if (this.tickCount % 4 == 0) {
            server.sendParticles(ParticleTypes.FLASH, x, y, z, Math.max(1, (int) (4 * pScale)),
                    orb, orb, orb, 0.0D);
            server.sendParticles(ParticleTypes.END_ROD, x, y, z, Math.max(20, (int) (260 * pScale)),
                    orb, orb, orb, 0.15D);
        }

        // E) 每 8 tick 三圈日冕
        if (this.tickCount % 8 == 0) {
            emitCoronaRing(server, x, y, z, orb * 0.6D, Math.max(12, (int) (96 * pScale)));
            emitCoronaRing(server, x, y, z, orb, Math.max(16, (int) (120 * pScale)));
            emitCoronaRing(server, x, y, z, outer, Math.max(20, (int) (160 * pScale)));
        }

        // 投掷阶段：额外拖尾
        if (this.isThrown()) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, Math.max(4, (int) (40 * pScale)),
                    orb * 0.4D, orb * 0.4D, orb * 0.4D, 0.05D);
            server.sendParticles(ParticleTypes.LAVA, x, y, z, Math.max(6, (int) (60 * pScale)),
                    orb * 0.5D, orb * 0.5D, orb * 0.5D, 0.2D);
            if (this.tickCount % 2 == 0) {
                server.sendParticles(ParticleTypes.FLASH, x, y, z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
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
        double explosionRadius = AbilityRuntime.goldenCrowExplosionRadius(manaSpent);   // 5~50
        double stunRadius = AbilityRuntime.goldenCrowStunRadius(manaSpent);             // 10~60
        float pScale = AbilityRuntime.goldenCrowParticleScale(manaSpent);

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
        server.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, Math.max(2, (int) (8 * pScale)),
                2.0D, 2.0D, 2.0D, 0.0D);
        server.playSound(null, BlockPos.containing(pos.x, pos.y, pos.z), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 4.0F, 0.6F);

        // 多重爆炸发射器：数量随法力缩放，位置撒在爆炸球面内
        int emitterCount = Math.max(4, (int) (24 * pScale));
        for (int i = 0; i < emitterCount; i++) {
            double ox = (server.random.nextDouble() - 0.5D) * explosionRadius * 1.6D;
            double oy = (server.random.nextDouble() - 0.5D) * explosionRadius * 0.8D;
            double oz = (server.random.nextDouble() - 0.5D) * explosionRadius * 1.6D;
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.x + ox, pos.y + oy, pos.z + oz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // 7 圈震荡波，按实际 explosionRadius / stunRadius 布置；粒子密度随 pScale
        emitShockwaveRing(server, pos, explosionRadius * 0.16D, Math.max(12, (int) (80 * pScale)), ParticleTypes.FLAME);
        emitShockwaveRing(server, pos, explosionRadius * 0.32D, Math.max(16, (int) (128 * pScale)), ParticleTypes.FLAME);
        emitShockwaveRing(server, pos, explosionRadius * 0.50D, Math.max(24, (int) (200 * pScale)), ParticleTypes.END_ROD);
        emitShockwaveRing(server, pos, explosionRadius * 0.68D, Math.max(32, (int) (256 * pScale)), ParticleTypes.FLAME);
        emitShockwaveRing(server, pos, explosionRadius * 0.84D, Math.max(40, (int) (320 * pScale)), ParticleTypes.SMALL_FLAME);
        emitShockwaveRing(server, pos, explosionRadius, Math.max(48, (int) (400 * pScale)), ParticleTypes.END_ROD);
        emitShockwaveRing(server, pos, stunRadius, Math.max(64, (int) (480 * pScale)), ParticleTypes.FLASH);
        // 超大飞溅：数量 × pScale（最高 × 3）
        server.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, Math.max(30, (int) (600 * pScale)),
                explosionRadius * 0.6D, explosionRadius * 0.3D, explosionRadius * 0.6D, 1.2D);
        server.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, Math.max(80, (int) (1800 * pScale)),
                explosionRadius * 0.8D, explosionRadius * 0.4D, explosionRadius * 0.8D, 1.6D);
        server.sendParticles(ParticleTypes.SMALL_FLAME, pos.x, pos.y, pos.z, Math.max(60, (int) (1200 * pScale)),
                explosionRadius * 0.9D, explosionRadius * 0.5D, explosionRadius * 0.9D, 1.0D);
        server.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, Math.max(3, (int) (12 * pScale)),
                explosionRadius * 0.4D, explosionRadius * 0.4D, explosionRadius * 0.4D, 0.0D);
        // 音效：音量随法力越强越响
        float vol = 2.0F + pScale * 4.0F;
        server.playSound(null, BlockPos.containing(pos.x, pos.y, pos.z), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, vol, 0.5F);
        server.playSound(null, BlockPos.containing(pos.x, pos.y, pos.z), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, vol, 0.8F);
        server.playSound(null, BlockPos.containing(pos.x, pos.y, pos.z), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, vol * 0.7F, 0.4F);
        server.playSound(null, BlockPos.containing(pos.x, pos.y, pos.z), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, vol * 1.3F, 0.3F);

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

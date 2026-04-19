package com.mifan.spell;

import com.mifan.corpsecampus;
import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.DangerSensePingPacket;
import com.mifan.network.clientbound.InstinctProcPacket;
import com.mifan.registry.ModMobEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
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
        if (player.level() instanceof ServerLevel serverLevel) {
            AbilityRuntime.tickElementalDomainRestoration(serverLevel);
        }
        CompoundTag data = player.getPersistentData();
        long gameTime = player.level().getGameTime();

        tickSonicSense(player, gameTime);
        tickDangerSense(player, data, gameTime);
        tickOlfaction(player, gameTime);
        tickElementalDomain(player, data, gameTime);
        AbilityRuntime.tickDominance(player);
        MidasBombRuntime.tickPlayerInventory(player);
        tickMagneticCling(player, data, gameTime);
        tickMania(player, data, gameTime);
        tickNecroticUndead(player, data, gameTime);
        tickMark(player, data, gameTime);
        clearExpiredInstinct(player, data, gameTime);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }
        if (event.level instanceof ServerLevel serverLevel) {
            MidasBombRuntime.tickLevel(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = (LivingEntity) event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        tickTelekinesisCaster(entity, entity.getPersistentData(), entity.level().getGameTime());
        updateOlfactionTrail(entity, entity.level().getGameTime());
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        trackDangerSenseAttacker(event);

        Entity directEntity = event.getSource().getDirectEntity();
        if (entity instanceof Player player && directEntity instanceof Mob mob
                && AbilityRuntime.isDominatedBy(mob, player)) {
            event.setCanceled(true);
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
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

        if (entity instanceof Player magneticPlayer
                && magneticPlayer.hasEffect(ModMobEffects.MAGNETIC_CLING.get())
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            event.setAmount(Math.min(event.getAmount(), Math.max(0.0F, magneticPlayer.getHealth() - 1.0F)));
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

        int spellLevel = AbilityRuntime.getEffectLevel(instinctEffect);
        if (entity.getRandom().nextFloat() < 0.15F) {
            data.putLong(AbilityRuntime.TAG_INSTINCT_INVULNERABLE_UNTIL, gameTime + 20L);
            event.setAmount(0.0F);
            if (entity instanceof ServerPlayer serverPlayer) {
                ModNetwork.sendToPlayer(new InstinctProcPacket(false), serverPlayer);
            }
            return;
        }

        if (event.getAmount() < entity.getHealth()) {
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

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        CompoundTag data = entity.getPersistentData();
        long gameTime = entity.level().getGameTime();
        if (entity.hasEffect(ModMobEffects.NECROTIC_UNDEAD.get())
                && data.getLong(AbilityRuntime.TAG_NECROTIC_ALLOW_HEAL_UNTIL) < gameTime) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        reviveNecroticCaster(event, entity);
        rewardNecroticKill(event);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }

        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = event.getEntity().getPersistentData();
        if (oldData.getBoolean(AbilityRuntime.TAG_NECROTIC_REVIVE_USED)) {
            newData.putBoolean(AbilityRuntime.TAG_NECROTIC_REVIVE_USED, true);
        }
    }

    @SubscribeEvent
    public static void onLivingAttackDealt(LivingAttackEvent event) {
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker) || attacker.level().isClientSide) {
            return;
        }

        MobEffectInstance maniaEffect = livingAttacker.getEffect(ModMobEffects.MANIA.get());
        if (maniaEffect == null) {
            return;
        }

        Entity directEntity = source.getDirectEntity();
        LivingEntity target = event.getEntity();
        if (directEntity != attacker) {
            return;
        }

        CompoundTag data = livingAttacker.getPersistentData();
        long gameTime = livingAttacker.level().getGameTime();
        long lastProc = data.getLong(AbilityRuntime.TAG_MANIA_LAST_PROC);
        if (lastProc == gameTime) {
            return;
        }

        data.putLong(AbilityRuntime.TAG_MANIA_LAST_PROC, gameTime);
        event.setCanceled(true);

        float amount = event.getAmount() * 1.5F;
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(source, amount);
        if (hurt) {
            spawnManiaCrit(livingAttacker, target);
        }
    }

    @SubscribeEvent
    public static void onPlayerAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !(event.getTarget() instanceof LivingEntity livingTarget) || livingTarget == player) {
            return;
        }

        AbilityRuntime.retargetDominatedMobs(player, livingTarget);
        player.getPersistentData().remove(AbilityRuntime.TAG_DOMINANCE_TARGET_PLAYER);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (MidasBombRuntime.detonateHeldBomb(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        boolean explodedBlock = MidasBombRuntime.detonateBlockIfArmed(event.getLevel(), event.getPos());
        boolean explodedItem = MidasBombRuntime.detonateHeldBomb(event.getEntity(), event.getHand());
        if (explodedBlock || explodedItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (MidasBombRuntime.detonateBlockIfArmed(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
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
            data.remove(AbilityRuntime.TAG_DANGER_RECENT_ATTACKERS);
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

        for (Player otherPlayer : player.level().getEntitiesOfClass(
                Player.class,
                player.getBoundingBox().inflate(radius),
                other -> other != player && other.isAlive() && isDangerSenseThreat(player, other, data, gameTime))) {
            foundThreat = true;

            if (player instanceof ServerPlayer serverPlayer) {
                ModNetwork.sendToPlayer(new DangerSensePingPacket(otherPlayer.getId(), 16, playAlert), serverPlayer);
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

    private static void tickOlfaction(Player player, long gameTime) {
        MobEffectInstance effectInstance = player.getEffect(ModMobEffects.OLFACTION.get());
        if (effectInstance == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int spellLevel = AbilityRuntime.getEffectLevel(effectInstance);
        double radius = AbilityRuntime.getOlfactionTrackRange(spellLevel);
        AABB box = player.getBoundingBox().inflate(radius, 6.0D, radius);
        boolean foundTrail = false;

        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != player && entity.isAlive() && isOlfactionTrackable(entity))) {
            if (isNearOlfactionTrail(player, target.getPersistentData(), gameTime)) {
                foundTrail = true;
                break;
            }
        }

        if (foundTrail) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 1, false, false, true));
        }
    }

    private static void tickElementalDomain(Player player, CompoundTag data, long gameTime) {
        MobEffectInstance effectInstance = player.getEffect(ModMobEffects.ELEMENTAL_DOMAIN.get());
        if (effectInstance == null) {
            AbilityRuntime.clearElementalDomain(data);
            return;
        }

        int spellLevel = AbilityRuntime.getEffectLevel(effectInstance);
        MagicData magicData = MagicData.getPlayerMagicData(player);

        if (gameTime % 5L == 0L) {
            float manaCost = AbilityRuntime.getElementalistManaDrain(spellLevel);
            if (magicData.getMana() < manaCost) {
                player.removeEffect(ModMobEffects.ELEMENTAL_DOMAIN.get());
                if (player.level() instanceof ServerLevel currentServerLevel) {
                    AbilityRuntime.endElementalDomain(currentServerLevel, player);
                }
                AbilityRuntime.clearElementalDomain(data);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.corpse_campus.elementalist_no_mana"), true);
                player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.PLAYERS, 0.24F, 0.8F);
                return;
            }

            magicData.setMana(magicData.getMana() - manaCost);
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        AbilityRuntime.tickElementalDomainTerrain(serverLevel, player, gameTime);

        if (gameTime % 4L == 0L) {
            spawnElementalDomainRing(serverLevel, player, spellLevel, gameTime);
        }

        if (gameTime - data.getLong(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_LAST_TICK) < AbilityRuntime.getElementalistInterval()) {
            return;
        }

        data.putLong(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_LAST_TICK, gameTime);
        double radius = AbilityRuntime.getElementalistRadius();
        AABB area = player.getBoundingBox().inflate(radius, 8.0D, radius);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                entity -> AbilityRuntime.isElementalistValidTarget(player, entity)
                        && entity.distanceToSqr(player) <= radius * radius)) {
            AbilityRuntime.triggerElementalistBurst(serverLevel, player, target, spellLevel);
        }
    }

    private static void spawnElementalDomainRing(ServerLevel level, Player player, int spellLevel, long gameTime) {
        double radius = AbilityRuntime.getElementalistRadius();
        int points = 24;
        double y = player.getY() + 0.1D;
        float rotation = (gameTime % 80L) / 80.0F * Mth.TWO_PI;
        for (int i = 0; i < points; i++) {
            float angle = rotation + Mth.TWO_PI * i / points;
            double x = player.getX() + Mth.cos(angle) * radius;
            double z = player.getZ() + Mth.sin(angle) * radius;
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.2D, z, 1, 0.0D, 0.08D, 0.0D, 0.0D);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.52F, 0.7F + 0.04F * spellLevel, 1.0F), 1.0F),
                    x, y, z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(),
                player.getY() + 1.0D,
                player.getZ(),
                6,
                0.45D,
                0.6D,
                0.45D,
                0.01D);
        level.sendParticles(ParticleTypes.FLAME,
                player.getX(),
                player.getY() + 0.2D,
                player.getZ(),
                4,
                0.35D,
                0.08D,
                0.35D,
                0.01D);
    }

    private static void updateOlfactionTrail(LivingEntity entity, long gameTime) {
        CompoundTag data = entity.getPersistentData();
        ListTag trail = data.getList(AbilityRuntime.TAG_OLFACTION_TRAIL, Tag.TAG_COMPOUND);
        pruneExpiredOlfactionTrail(trail, gameTime);

        if (!isOlfactionTrackable(entity)) {
            if (trail.isEmpty()) {
                data.remove(AbilityRuntime.TAG_OLFACTION_TRAIL);
                data.remove(AbilityRuntime.TAG_OLFACTION_LAST_TRAIL_TICK);
            } else {
                data.put(AbilityRuntime.TAG_OLFACTION_TRAIL, trail);
            }
            return;
        }

        if (gameTime - data.getLong(AbilityRuntime.TAG_OLFACTION_LAST_TRAIL_TICK) < 6L) {
            if (!trail.isEmpty()) {
                data.put(AbilityRuntime.TAG_OLFACTION_TRAIL, trail);
            }
            return;
        }

        data.putLong(AbilityRuntime.TAG_OLFACTION_LAST_TRAIL_TICK, gameTime);

        CompoundTag footprint = new CompoundTag();
        footprint.putDouble("x", entity.getX());
        footprint.putDouble("y", entity.getY() + 0.02D);
        footprint.putDouble("z", entity.getZ());
        footprint.putLong("expire", gameTime + 40L);
        trail.add(footprint);

        while (trail.size() > 12) {
            trail.remove(0);
        }

        data.put(AbilityRuntime.TAG_OLFACTION_TRAIL, trail);
    }

    private static void pruneExpiredOlfactionTrail(ListTag trail, long gameTime) {
        for (int i = trail.size() - 1; i >= 0; i--) {
            if (!(trail.get(i) instanceof CompoundTag entry) || entry.getLong("expire") <= gameTime) {
                trail.remove(i);
            }
        }
    }

    private static boolean isNearOlfactionTrail(Player player, CompoundTag data, long gameTime) {
        ListTag trail = data.getList(AbilityRuntime.TAG_OLFACTION_TRAIL, Tag.TAG_COMPOUND);
        pruneExpiredOlfactionTrail(trail, gameTime);
        if (trail.isEmpty()) {
            return false;
        }

        Vec3 feet = player.position();
        for (Tag tag : trail) {
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }

            double dx = feet.x - entry.getDouble("x");
            double dz = feet.z - entry.getDouble("z");
            if (dx * dx + dz * dz <= 3.24D) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOlfactionTrackable(LivingEntity entity) {
        return entity.getMaxHealth() > 0.0F && entity.getHealth() / entity.getMaxHealth() < 0.75F;
    }

    private static void tickMania(Player player, CompoundTag data, long gameTime) {
        MobEffectInstance effectInstance = player.getEffect(ModMobEffects.MANIA.get());
        if (effectInstance == null) {
            AbilityRuntime.clear(data, AbilityRuntime.TAG_MANIA_LAST_PROC, AbilityRuntime.TAG_MANIA_LAST_SWING);
            return;
        }

        int spellLevel = AbilityRuntime.getEffectLevel(effectInstance);
        if (gameTime % 10L == 0L) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 0, false, false, false));
        }

        long lastSwing = data.getLong(AbilityRuntime.TAG_MANIA_LAST_SWING);
        if (gameTime - lastSwing < Math.max(4L, 12L - spellLevel)) {
            return;
        }

        double range = 3.25D + Math.max(0, spellLevel - 1) * 0.35D;
        LivingEntity target = AbilityRuntime.findNearestFrontTarget(player, range, 0.72D);
        if (target == null) {
            return;
        }

        double meleeRange = getAttackReach(player) + target.getBbWidth() * 0.5D;
        if (player.distanceTo(target) > meleeRange) {
            Vec3 toward = target.position().subtract(player.position());
            Vec3 horizontal = new Vec3(toward.x, 0.0D, toward.z);
            if (horizontal.lengthSqr() > 1.0E-4D) {
                Vec3 push = horizontal.normalize().scale(Math.min(0.55D, horizontal.length() * 0.22D + 0.14D));
                player.push(push.x, player.onGround() ? 0.12D : 0.02D, push.z);
                player.hurtMarked = true;
            }
            return;
        }

        data.putLong(AbilityRuntime.TAG_MANIA_LAST_SWING, gameTime);
        player.swing(player.getUsedItemHand(), true);
        player.resetAttackStrengthTicker();
        player.attack(target);
    }

    private static void tickNecroticUndead(Player player, CompoundTag data, long gameTime) {
        if (!player.hasEffect(ModMobEffects.NECROTIC_UNDEAD.get())) {
            AbilityRuntime.clear(data,
                    AbilityRuntime.TAG_NECROTIC_ALLOW_HEAL_UNTIL,
                    AbilityRuntime.TAG_NECROTIC_LAST_KILL_HEAL,
                    AbilityRuntime.TAG_NECROTIC_REVIVE_USED);
            return;
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

        if (player.getHealth() >= player.getMaxHealth() - 0.01F) {
            player.removeEffect(ModMobEffects.NECROTIC_UNDEAD.get());
            AbilityRuntime.clear(data,
                    AbilityRuntime.TAG_NECROTIC_ALLOW_HEAL_UNTIL,
                    AbilityRuntime.TAG_NECROTIC_LAST_KILL_HEAL,
                    AbilityRuntime.TAG_NECROTIC_REVIVE_USED);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.corpse_campus.necrotic_rebirth_restored"), true);
        }
    }

    private static void tickMark(Player player, CompoundTag data, long gameTime) {
        if (!data.getBoolean(AbilityRuntime.TAG_MARK_ACTIVE)) {
            return;
        }

        if (data.getLong(AbilityRuntime.TAG_MARK_END) <= gameTime) {
            AbilityRuntime.clearMark(data);
            return;
        }

        int spellLevel = Math.max(1, data.getInt(AbilityRuntime.TAG_MARK_LEVEL));
        Vec3 center = AbilityRuntime.getMarkCenter(data);
        double radius = AbilityRuntime.getMarkRadius(spellLevel);

        if (player.level() instanceof ServerLevel serverLevel && gameTime % 8L == 0L) {
            spawnMarkRing(serverLevel, center, radius);
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
                    AbilityRuntime.getMarkRootSeconds() * 20,
                    10,
                    false,
                    true,
                    true));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP,
                    AbilityRuntime.getMarkRootSeconds() * 20,
                    128,
                    false,
                    false,
                    true));
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;

            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.corpse_campus.mark_triggered", target.getDisplayName()), true);
            player.level().playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                    0.45F, 1.6F);
        }

        if (changed) {
            data.put(AbilityRuntime.TAG_MARK_TRIGGERED, triggered);
        }
    }

    private static void reviveNecroticCaster(LivingDeathEvent event, LivingEntity entity) {
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
        player.addEffect(new MobEffectInstance(ModMobEffects.NECROTIC_UNDEAD.get(),
                AbilityRuntime.TOGGLE_DURATION_TICKS,
                spellLevel - 1,
                false,
                false,
                false));
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

    private static void rewardNecroticKill(LivingDeathEvent event) {
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
        float healAmount = AbilityRuntime.getNecroticHealAmount(spellLevel);
        CompoundTag data = player.getPersistentData();
        data.putLong(AbilityRuntime.TAG_NECROTIC_ALLOW_HEAL_UNTIL, player.level().getGameTime() + 2L);
        data.putFloat(AbilityRuntime.TAG_NECROTIC_LAST_KILL_HEAL, healAmount);
        player.heal(healAmount);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS,
                0.35F, 1.15F);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.corpse_campus.necrotic_rebirth_kill_heal", Math.round(healAmount)), true);
    }

    private static void spawnMarkRing(ServerLevel serverLevel, Vec3 center, double radius) {
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
        boolean clingActive = data.getBoolean(AbilityRuntime.TAG_MAGNETIC_CLINGING);
        boolean touchingClimbableWall = player.horizontalCollision || isTouchingClimbableWall(player);

        if (!onGround && player.fallDistance >= 4.0F) {
            data.putBoolean(AbilityRuntime.TAG_MAGNETIC_SHOCK_READY, true);
        }

        if (!clingActive && !onGround && touchingClimbableWall) {
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
            clingActive = true;
        }

        if (clingActive) {
            player.setNoGravity(true);
            BlockPos headPos = BlockPos.containing(player.getX(), player.getBoundingBox().maxY + 0.05D, player.getZ());
            boolean blockedAbove = !player.level().getBlockState(headPos).isAir();
            double climbSpeed = blockedAbove ? 0.0D : (0.12D + spellLevel * 0.02D);
            Vec3 motion = player.getDeltaMovement();
            double horizontalDamping = 0.08D;

            player.setDeltaMovement(
                    motion.x * horizontalDamping,
                    climbSpeed,
                    motion.z * horizontalDamping);
            player.fallDistance = 0.0F;
            player.hurtMarked = true;

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

            if (onGround || !touchingClimbableWall) {
                stopMagneticCling(player, data);
                if (data.getBoolean(AbilityRuntime.TAG_MAGNETIC_SHOCK_READY)) {
                    emitShockwave(player, spellLevel, player.fallDistance);
                    data.remove(AbilityRuntime.TAG_MAGNETIC_SHOCK_READY);
                }
            }
        } else if (onGround && !lastGround && data.getBoolean(AbilityRuntime.TAG_MAGNETIC_SHOCK_READY)) {
            emitShockwave(player, spellLevel, player.fallDistance);
            data.remove(AbilityRuntime.TAG_MAGNETIC_SHOCK_READY);
        }

        data.putBoolean(AbilityRuntime.TAG_MAGNETIC_LAST_GROUND, onGround);
    }

    private static void stopMagneticCling(Player player, CompoundTag data) {
        player.setNoGravity(false);
        data.remove(AbilityRuntime.TAG_MAGNETIC_CLING_END);
        data.putBoolean(AbilityRuntime.TAG_MAGNETIC_CLINGING, false);
    }

    private static boolean isTouchingClimbableWall(Player player) {
        AABB box = player.getBoundingBox();
        double sampleInset = 0.05D;
        double minY = box.minY + 0.1D;
        double maxY = box.maxY - 0.1D;

        return hasSolidWallAt(player, box.maxX + sampleInset, minY, maxY, box.minZ + 0.1D, box.maxZ - 0.1D)
                || hasSolidWallAt(player, box.minX - sampleInset, minY, maxY, box.minZ + 0.1D, box.maxZ - 0.1D)
                || hasSolidWallAlongZ(player, box.maxZ + sampleInset, minY, maxY, box.minX + 0.1D, box.maxX - 0.1D)
                || hasSolidWallAlongZ(player, box.minZ - sampleInset, minY, maxY, box.minX + 0.1D, box.maxX - 0.1D);
    }

    private static boolean hasSolidWallAt(Player player, double sampleX, double minY, double maxY, double minZ,
            double maxZ) {
        return isSolidWallBlock(player, sampleX, minY, minZ)
                || isSolidWallBlock(player, sampleX, minY, maxZ)
                || isSolidWallBlock(player, sampleX, maxY, minZ)
                || isSolidWallBlock(player, sampleX, maxY, maxZ);
    }

    private static boolean hasSolidWallAlongZ(Player player, double sampleZ, double minY, double maxY, double minX,
            double maxX) {
        return isSolidWallBlock(player, minX, minY, sampleZ)
                || isSolidWallBlock(player, maxX, minY, sampleZ)
                || isSolidWallBlock(player, minX, maxY, sampleZ)
                || isSolidWallBlock(player, maxX, maxY, sampleZ);
    }

    private static boolean isSolidWallBlock(Player player, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = player.level().getBlockState(pos);
        return !state.isAir() && state.isCollisionShapeFullBlock(player.level(), pos);
    }

    private static void emitShockwave(Player player, int spellLevel, float fallDistance) {
        double fallScale = Math.max(0.0D, fallDistance - 4.0F) * 0.18D;
        double radius = 3.0D + spellLevel * 0.75D + fallScale;
        double horizontalStrength = 1.1D + spellLevel * 0.15D + fallScale * 0.22D;
        double verticalStrength = 0.35D + spellLevel * 0.05D + fallScale * 0.08D;

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

    private static double getAttackReach(Player player) {
        return 3.0D;
    }

    private static void spawnManiaCrit(LivingEntity attacker, LivingEntity target) {
        attacker.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                0.22F, 0.9F);
        if (attacker.level() instanceof ServerLevel serverLevel) {
            AABB box = target.getBoundingBox();
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.55D,
                    target.getZ(),
                    14,
                    box.getXsize() * 0.25D,
                    box.getYsize() * 0.2D,
                    box.getZsize() * 0.25D,
                    0.15D);
        }
    }

    private static void trackDangerSenseAttacker(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player attackingPlayer) || attackingPlayer == player) {
            return;
        }

        CompoundTag attackers = player.getPersistentData().getCompound(AbilityRuntime.TAG_DANGER_RECENT_ATTACKERS);
        attackers.putLong(attackingPlayer.getStringUUID(), player.level().getGameTime() + 400L);
        player.getPersistentData().put(AbilityRuntime.TAG_DANGER_RECENT_ATTACKERS, attackers);
    }

    private static boolean isDangerSenseThreat(Player player, Player otherPlayer, CompoundTag data, long gameTime) {
        ItemStack mainHand = otherPlayer.getMainHandItem();
        boolean dangerousWeapon = AbilityRuntime.isDangerSenseWeapon(mainHand)
                && AbilityRuntime.getDangerSenseWeaponDamage(mainHand) > 5.0D;

        CompoundTag attackers = data.getCompound(AbilityRuntime.TAG_DANGER_RECENT_ATTACKERS);
        if (attackers.contains(otherPlayer.getStringUUID())) {
            long expireAt = attackers.getLong(otherPlayer.getStringUUID());
            if (expireAt > gameTime) {
                return true;
            }
            attackers.remove(otherPlayer.getStringUUID());
            data.put(AbilityRuntime.TAG_DANGER_RECENT_ATTACKERS, attackers);
        }

        return dangerousWeapon;
    }
}

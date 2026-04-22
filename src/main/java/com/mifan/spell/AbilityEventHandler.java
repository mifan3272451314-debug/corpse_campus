package com.mifan.spell;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.corpsecampus;
import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.DangerSensePingPacket;
import com.mifan.network.clientbound.InstinctProcPacket;
import com.mifan.network.clientbound.OlfactionTrailSyncPacket;
import com.mifan.registry.ModMobEffects;
import com.mifan.spell.runtime.MarkRuntime;
import com.mifan.spell.runtime.NecromancerRuntime;
import com.mifan.spell.runtime.NecroticRuntime;
import com.mifan.spell.runtime.TelekinesisRuntime;
import com.mifan.spell.yuzhe.LifeThiefSpell;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = corpsecampus.MODID)
public final class AbilityEventHandler {
    private static final long OLFACTION_SYNC_INTERVAL_TICKS = 6L;
    private static final String TAG_STAMINA_DURABILITY_TRACKER = "corpse_campus_stamina_durability_tracker";
    private static final String TAG_STAMINA_ITEM_ID = "item_id";
    private static final String TAG_STAMINA_MAX_DAMAGE = "max_damage";
    private static final String TAG_STAMINA_LAST_DAMAGE = "last_damage";
    private static final String TAG_STAMINA_CARRY = "carry";

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

        tickRizhaoEnergy(player, data);
        tickGoldenCrowSun(player, data, gameTime);
        tickSonicSense(player, gameTime);
        tickStamina(player, data, gameTime);
        tickDangerSense(player, data, gameTime);
        tickOlfaction(player, gameTime);
        tickElementalDomain(player, data, gameTime);
        AbilityRuntime.tickDominance(player);
        tickAuthorityGrasp(player, data, gameTime);
        MidasBombRuntime.tickPlayerInventory(player);
        tickMagneticCling(player, data, gameTime);
        tickMania(player, data, gameTime);
        NecroticRuntime.tickUndead(player, data, gameTime);
        MarkRuntime.tick(player, data, gameTime);
        tickNinghe(player, gameTime);
        tickSunlight(player, gameTime);
        clearExpiredInstinct(player, data, gameTime);
        clearLifeThiefTargetIfInvalid(player);
        clearMimicSlotsIfInactive(player, data);
        clearImpermanenceIfInactive(player, data);
        NecromancerRuntime.tick(player);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }
        if (event.level instanceof ServerLevel serverLevel) {
            MidasBombRuntime.tickLevel(serverLevel);
            // 只在主世界 tick 时驱动回溯之虫镜像备份扫描（避免每维度重复调度）
            if (serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD
                    && serverLevel.getServer() != null) {
                com.mifan.spell.runtime.RewindBackupService.tick(serverLevel.getServer());
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = (LivingEntity) event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        AbilityRuntime.tickRecorderOfficer(entity, entity.level().getGameTime());
        TelekinesisRuntime.tickCaster(entity, entity.getPersistentData(), entity.level().getGameTime());
        updateOlfactionTrail(entity, entity.level().getGameTime());

        if (entity instanceof Mob sunlightNeutralizedMob
                && sunlightNeutralizedMob.hasEffect(ModMobEffects.SUNLIGHT_NEUTRALIZED.get())
                && sunlightNeutralizedMob.getTarget() != null) {
            sunlightNeutralizedMob.setTarget(null);
        }
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

        if (entity instanceof Player necroOwner && directEntity instanceof Mob necroMinion
                && NecromancerRuntime.isMinionOf(necroMinion, necroOwner)) {
            event.setCanceled(true);
            if (necroMinion.getTarget() == necroOwner) {
                necroMinion.setTarget(null);
            }
            return;
        }

        // 无常僧：被感染的玩家无法对自己的感染源施法者造成伤害
        Entity attackerEntity = event.getSource().getEntity();
        if (attackerEntity instanceof LivingEntity attackerLiving
                && entity instanceof Player possibleInfector
                && com.mifan.spell.runtime.ImpermanenceMonkRuntime
                        .isInfectedBy(attackerLiving, possibleInfector.getUUID())) {
            event.setCanceled(true);
            return;
        }

        // 万权一手：施法者命中任何目标时，对目标施加抽能 + 减速（持续到万权一手本体结束）
        if (directEntity instanceof LivingEntity authorityCaster
                && authorityCaster != entity
                && authorityCaster.hasEffect(ModMobEffects.AUTHORITY_GRASP_CASTER.get())) {
            applyAuthorityDrain(authorityCaster, entity, entity.level().getGameTime());
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

        // 万权一手：施法者在持续期间每次实际受到伤害，就从已有支配列表中把一只拉到身边，最多 5 只
        if (entity instanceof Player authorityCaster
                && authorityCaster.hasEffect(ModMobEffects.AUTHORITY_GRASP_CASTER.get())
                && event.getAmount() > 0.0F) {
            triggerAuthorityHurtSummon(authorityCaster);
        }

        if (entity instanceof Player magneticPlayer
                && magneticPlayer.hasEffect(ModMobEffects.MAGNETIC_CLING.get())
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            event.setAmount(Math.min(event.getAmount(), Math.max(0.0F, magneticPlayer.getHealth() - 1.0F)));
        }

        if (entity instanceof Player lifeThiefPlayer && handleLifeThiefRedirect(event, lifeThiefPlayer)) {
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

        ServerPlayer deadPlayer = entity instanceof ServerPlayer player ? player : null;
        if (deadPlayer != null) {
            cleanupElementalDomain(deadPlayer);
        }

        NecroticRuntime.reviveCaster(event, entity);

        if (deadPlayer != null && !event.isCanceled()) {
            AbilityRuntime.releaseDominance(deadPlayer);
            AbilityRuntime.onFerrymanTargetDeath(deadPlayer);
            NecromancerRuntime.release(deadPlayer);
        }

        NecroticRuntime.rewardKill(event);

        Entity killerEntity = event.getSource().getEntity();
        if (killerEntity instanceof Player killerPlayer && !(entity instanceof Player)) {
            NecromancerRuntime.onMonsterKilled(killerPlayer, entity);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }

        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = event.getEntity().getPersistentData();
        AbilityRuntime.clearElementalDomain(newData);
        AbilityRuntime.clear(newData,
                AbilityRuntime.TAG_DOMINANCE_MOBS,
                AbilityRuntime.TAG_DOMINANCE_TARGET_PLAYER,
                AbilityRuntime.TAG_DOMINANCE_LINK_ACTIVE,
                AbilityRuntime.TAG_MIMIC_SLOTS,
                AbilityRuntime.TAG_MIMIC_ACTIVE_SLOT,
                AbilityRuntime.TAG_IMPERMANENCE_INFECTED_LIST,
                AbilityRuntime.TAG_IMPERMANENCE_INFECTOR_UUID,
                AbilityRuntime.TAG_IMPERMANENCE_GRANTED_SPELL,
                AbilityRuntime.TAG_IMPERMANENCE_GRANTED_LEVEL,
                AbilityRuntime.TAG_FERRYMAN_TARGET,
                AbilityRuntime.TAG_FERRYMAN_LEVEL);
        if (oldData.getBoolean(AbilityRuntime.TAG_NECROTIC_REVIVE_USED)) {
            newData.putBoolean(AbilityRuntime.TAG_NECROTIC_REVIVE_USED, true);
        }
        // 回溯之虫：保留 CD、清 in_mirror 标记，锚点不再复制（死后老锚点无意义）
        com.mifan.spell.runtime.RewindWormRuntime.onDeath(oldData, newData);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cleanupElementalDomain(player);
            AbilityRuntime.releaseDominance(player);
            com.mifan.spell.runtime.NecromancerRuntime.discardSession(player);
        }
    }

    /**
     * 镜像维度（corpse_campus:rewind_mirror）"时间冻结"：
     * <ul>
     *   <li>拦截所有非复制流程加入镜像的 Mob（自然刷怪、命令召唤、生物蛋等）</li>
     *   <li>放行：打了 {@link com.mifan.spell.runtime.RewindBackupService#TAG_ENTITY_REWIND_COPIED} 标志的实体（= 我们从主维度复制过来的）</li>
     *   <li>放行：非 Mob（如画、物品展示框、箭；这些如果误进镜像通常是玩家亲自放的）</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onEntityJoinMirror(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!serverLevel.dimension().equals(com.mifan.spell.runtime.RewindBackupService.MIRROR_DIMENSION)) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof net.minecraft.world.entity.Mob)) {
            return;
        }
        if (entity.getPersistentData()
                .getBoolean(com.mifan.spell.runtime.RewindBackupService.TAG_ENTITY_REWIND_COPIED)) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NecromancerRuntime.syncSoulCount(player);
        }
    }

    @SubscribeEvent
    public static void onLivingAttackDealt(LivingAttackEvent event) {
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker) || attacker.level().isClientSide) {
            return;
        }

        if (livingAttacker instanceof Player player
                && player.hasEffect(ModMobEffects.NECROTIC_UNDEAD.get())
                && event.getEntity() instanceof Mob mobTarget) {
            AbilityRuntime.markNecroticProvoked(mobTarget, player);
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

        if (player.hasEffect(ModMobEffects.NECROTIC_UNDEAD.get()) && livingTarget instanceof Mob mobTarget) {
            AbilityRuntime.markNecroticProvoked(mobTarget, player);
        }

        AbilityRuntime.retargetDominatedMobs(player, livingTarget);
        player.getPersistentData().remove(AbilityRuntime.TAG_DOMINANCE_TARGET_PLAYER);
        NecromancerRuntime.onCasterAttackedTarget(player, livingTarget);
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
            applySonicSenseDebuffs(player, spellLevel);
        }
        if (gameTime % 40L == 0L) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 50, 0, false, false, false));
        }
    }

    private static void applySonicSenseDebuffs(Player player, int spellLevel) {
        double revealRange = getSonicSenseRevealRange(spellLevel);
        double revealRangeSqr = revealRange * revealRange;
        AABB area = player.getBoundingBox().inflate(revealRange, 6.0D, revealRange);

        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player
                        && entity.isAlive()
                        && !player.isAlliedTo(entity)
                        && player.distanceToSqr(entity) <= revealRangeSqr)) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true, true));
        }
    }

    private static double getSonicSenseRevealRange(int spellLevel) {
        return 18.0D + spellLevel * 4.0D;
    }

    private static void tickStamina(Player player, CompoundTag data, long gameTime) {
        MobEffectInstance effectInstance = player.getEffect(ModMobEffects.STAMINA.get());
        if (effectInstance == null) {
            clearStaminaDurabilityTracker(data);
            return;
        }

        int spellLevel = AbilityRuntime.getEffectLevel(effectInstance);
        if (gameTime % 10L == 0L) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, Math.min(1, spellLevel - 1), false,
                    false, true));
        }
        if (gameTime % 80L == 0L && player.getFoodData().getFoodLevel() < 20) {
            player.getFoodData().eat(1, 0.0F);
        }
        updateStaminaDurabilityTracker(player, data);
    }

    private static void clearStaminaDurabilityTracker(CompoundTag data) {
        data.remove(TAG_STAMINA_DURABILITY_TRACKER);
    }

    private static void updateStaminaDurabilityTracker(Player player, CompoundTag data) {
        CompoundTag tracker = data.getCompound(TAG_STAMINA_DURABILITY_TRACKER);
        trackStaminaSlots(tracker, "inventory", player.getInventory().items);
        trackStaminaSlots(tracker, "armor", player.getInventory().armor);
        trackStaminaSlots(tracker, "offhand", player.getInventory().offhand);

        if (tracker.isEmpty()) {
            data.remove(TAG_STAMINA_DURABILITY_TRACKER);
        } else {
            data.put(TAG_STAMINA_DURABILITY_TRACKER, tracker);
        }
    }

    private static void trackStaminaSlots(CompoundTag tracker, String group, List<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            trackStaminaSlot(tracker, group + "_" + i, stacks.get(i));
        }
    }

    private static void trackStaminaSlot(CompoundTag tracker, String key, ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            tracker.remove(key);
            return;
        }

        String itemId = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
        int currentDamage = stack.getDamageValue();
        int maxDamage = stack.getMaxDamage();

        if (!tracker.contains(key, Tag.TAG_COMPOUND)) {
            tracker.put(key, createStaminaSlotState(itemId, maxDamage, currentDamage, 0));
            return;
        }

        CompoundTag slotState = tracker.getCompound(key);
        if (!itemId.equals(slotState.getString(TAG_STAMINA_ITEM_ID))
                || maxDamage != slotState.getInt(TAG_STAMINA_MAX_DAMAGE)
                || currentDamage < slotState.getInt(TAG_STAMINA_LAST_DAMAGE)) {
            tracker.put(key, createStaminaSlotState(itemId, maxDamage, currentDamage, 0));
            return;
        }

        int lastDamage = slotState.getInt(TAG_STAMINA_LAST_DAMAGE);
        int delta = currentDamage - lastDamage;
        int carry = slotState.getInt(TAG_STAMINA_CARRY);
        if (delta > 0) {
            int adjustedDelta = (delta + carry) / 2;
            carry = (delta + carry) % 2;
            currentDamage = lastDamage + adjustedDelta;
            stack.setDamageValue(currentDamage);
        }

        tracker.put(key, createStaminaSlotState(itemId, maxDamage, currentDamage, carry));
    }

    private static CompoundTag createStaminaSlotState(String itemId, int maxDamage, int lastDamage, int carry) {
        CompoundTag slotState = new CompoundTag();
        slotState.putString(TAG_STAMINA_ITEM_ID, itemId);
        slotState.putInt(TAG_STAMINA_MAX_DAMAGE, maxDamage);
        slotState.putInt(TAG_STAMINA_LAST_DAMAGE, lastDamage);
        slotState.putInt(TAG_STAMINA_CARRY, carry);
        return slotState;
    }

    private static boolean handleLifeThiefRedirect(LivingHurtEvent event, Player player) {
        if (!player.hasEffect(ModMobEffects.LIFE_THIEF.get())) {
            return false;
        }

        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (magicData == null || magicData.getMana() + 1.0E-4F < LifeThiefSpell.REDIRECT_MANA_COST) {
            player.removeEffect(ModMobEffects.LIFE_THIEF.get());
            AbilityRuntime.clearLifeThief(player.getPersistentData());
            player.displayClientMessage(Component.translatable("message.corpse_campus.life_thief_no_mana"), true);
            return false;
        }

        DamageSource source = event.getSource();

        LivingEntity redirectTarget = AbilityRuntime.findRandomNearbyTarget(player, 12.0D);
        if (redirectTarget == null) {
            return false;
        }

        long gameTime = player.level().getGameTime();
        CompoundTag data = player.getPersistentData();
        if (data.getLong(AbilityRuntime.TAG_LIFE_THIEF_LAST_REDIRECT_TICK) == gameTime) {
            return false;
        }

        float incoming = event.getAmount();
        float transferable = Math.max(0.0F, redirectTarget.getHealth() - AbilityRuntime.LIFE_THIEF_MIN_SURVIVAL_HEALTH);
        if (incoming <= 0.0F || transferable <= 0.0F) {
            return false;
        }

        float redirected = Math.min(incoming, transferable);
        magicData.setMana(Math.max(0.0F, magicData.getMana() - LifeThiefSpell.REDIRECT_MANA_COST));
        data.putLong(AbilityRuntime.TAG_LIFE_THIEF_LAST_REDIRECT_TICK, gameTime);
        redirectTarget.invulnerableTime = 0;
        redirectTarget.hurt(source, redirected);
        if (redirectTarget.getHealth() < AbilityRuntime.LIFE_THIEF_MIN_SURVIVAL_HEALTH) {
            redirectTarget.setHealth(AbilityRuntime.LIFE_THIEF_MIN_SURVIVAL_HEALTH);
        }
        event.setAmount(Math.max(0.0F, incoming - redirected));

        if (event.getAmount() <= 0.0F) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                    SoundSource.PLAYERS, 0.18F, 1.15F);
        }

        if (magicData.getMana() + 1.0E-4F < LifeThiefSpell.REDIRECT_MANA_COST) {
            player.removeEffect(ModMobEffects.LIFE_THIEF.get());
            AbilityRuntime.clearLifeThief(player.getPersistentData());
            player.displayClientMessage(Component.translatable("message.corpse_campus.life_thief_no_mana"), true);
        }
        return event.getAmount() <= 0.0F;
    }

    private static void clearLifeThiefTargetIfInvalid(Player player) {
        if (player.hasEffect(ModMobEffects.LIFE_THIEF.get())) {
            return;
        }
        AbilityRuntime.clearLifeThief(player.getPersistentData());
    }

    private static void clearMimicSlotsIfInactive(Player player, CompoundTag data) {
        if (player.hasEffect(ModMobEffects.MIMIC.get())) {
            return;
        }
        if (data.contains(AbilityRuntime.TAG_MIMIC_SLOTS) || data.contains(AbilityRuntime.TAG_MIMIC_ACTIVE_SLOT)) {
            com.mifan.spell.runtime.MimicRuntime.clearAllSlots(data);
        }
    }

    private static void clearImpermanenceIfInactive(Player player, CompoundTag data) {
        if (player.hasEffect(ModMobEffects.IMPERMANENCE_MONK.get())) {
            return;
        }
        if (data.contains(AbilityRuntime.TAG_IMPERMANENCE_INFECTED_LIST)) {
            com.mifan.spell.runtime.ImpermanenceMonkRuntime.clearAllInfections(player);
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
        List<LivingEntity> nearbyTrackables = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != player && entity.isAlive() && isOlfactionTrackable(entity));
        boolean foundTrail = false;

        if (player instanceof ServerPlayer serverPlayer && gameTime % OLFACTION_SYNC_INTERVAL_TICKS == 0L) {
            syncOlfactionTrails(serverPlayer, nearbyTrackables, gameTime);
        }

        for (LivingEntity target : nearbyTrackables) {
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

        if (gameTime - data.getLong(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_LAST_TICK) < AbilityRuntime.getElementalistInterval(data)) {
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
        footprint.putLong("expire", gameTime + 100L);
        trail.add(footprint);

        while (trail.size() > 24) {
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

    private static void syncOlfactionTrails(ServerPlayer player, List<LivingEntity> nearbyTrackables, long gameTime) {
        List<OlfactionTrailSyncPacket.Entry> entries = new ArrayList<>();
        for (LivingEntity target : nearbyTrackables) {
            ListTag trail = target.getPersistentData().getList(AbilityRuntime.TAG_OLFACTION_TRAIL, Tag.TAG_COMPOUND);
            pruneExpiredOlfactionTrail(trail, gameTime);

            int stepIndex = 0;
            for (Tag tag : trail) {
                if (!(tag instanceof CompoundTag entry)) {
                    continue;
                }

                int remainingTicks = (int) Math.max(0L, entry.getLong("expire") - gameTime);
                if (remainingTicks <= 0) {
                    continue;
                }

                entries.add(new OlfactionTrailSyncPacket.Entry(
                        target.getId(),
                        stepIndex++,
                        entry.getDouble("x"),
                        entry.getDouble("y"),
                        entry.getDouble("z"),
                        remainingTicks));
            }
        }

        ModNetwork.sendToPlayer(new OlfactionTrailSyncPacket(entries), player);
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

    private static void cleanupElementalDomain(ServerPlayer player) {
        if (player.hasEffect(ModMobEffects.ELEMENTAL_DOMAIN.get())) {
            player.removeEffect(ModMobEffects.ELEMENTAL_DOMAIN.get());
        }
        AbilityRuntime.endElementalDomain(player.serverLevel(), player);
        AbilityRuntime.clearElementalDomain(player.getPersistentData());
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

    private static void tickRizhaoEnergy(Player player, CompoundTag data) {
        if (!AnomalyBookService.isRizhaoSequencePlayer(player)) {
            data.remove(AbilityRuntime.TAG_RIZHAO_LAST_MANA);
            data.remove(AbilityRuntime.TAG_RIZHAO_INITIALIZED);
            return;
        }

        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (magicData == null) {
            return;
        }

        float maxMana = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        float currentMana = magicData.getMana();

        if (!data.getBoolean(AbilityRuntime.TAG_RIZHAO_INITIALIZED)) {
            float initial = Math.min(AbilityRuntime.RIZHAO_INITIAL_MANA, maxMana);
            magicData.setMana(initial);
            data.putBoolean(AbilityRuntime.TAG_RIZHAO_INITIALIZED, true);
            data.putFloat(AbilityRuntime.TAG_RIZHAO_LAST_MANA, initial);
            return;
        }

        float lastMana = data.contains(AbilityRuntime.TAG_RIZHAO_LAST_MANA)
                ? data.getFloat(AbilityRuntime.TAG_RIZHAO_LAST_MANA)
                : currentMana;

        float baseline;
        if (currentMana > lastMana + 1.0E-4F) {
            baseline = lastMana;
        } else {
            baseline = currentMana;
        }

        boolean goldenCrowActive = data.getBoolean(AbilityRuntime.TAG_GOLDEN_CROW_ACTIVE);
        float regenThisTick = (!goldenCrowActive && isDaytimeAndOpenSky(player))
                ? AbilityRuntime.RIZHAO_REGEN_PER_SECOND / 20.0F
                : 0.0F;

        float newMana = Math.min(baseline + regenThisTick, maxMana);
        if (Math.abs(newMana - currentMana) > 1.0E-4F) {
            magicData.setMana(newMana);
        }
        data.putFloat(AbilityRuntime.TAG_RIZHAO_LAST_MANA, newMana);
    }

    public static void addRizhaoEnergy(Player player, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (magicData == null) {
            return;
        }
        float maxMana = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        float newMana = Math.min(magicData.getMana() + amount, maxMana);
        magicData.setMana(newMana);
        player.getPersistentData().putFloat(AbilityRuntime.TAG_RIZHAO_LAST_MANA, newMana);
    }

    private static boolean isDaytimeAndOpenSky(Player player) {
        Level level = player.level();
        if (!level.dimensionType().hasSkyLight()) {
            return false;
        }
        if (!level.isDay()) {
            return false;
        }
        return level.canSeeSky(net.minecraft.core.BlockPos.containing(player.getX(),
                player.getBoundingBox().maxY + 0.1D,
                player.getZ()));
    }

    private static void tickGoldenCrowSun(Player player, CompoundTag data, long gameTime) {
        if (!data.getBoolean(AbilityRuntime.TAG_GOLDEN_CROW_ACTIVE)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean shouldClear = false;

        if (data.contains(AbilityRuntime.TAG_GOLDEN_CROW_EXPIRE_TICK)
                && gameTime > data.getLong(AbilityRuntime.TAG_GOLDEN_CROW_EXPIRE_TICK)) {
            shouldClear = true;
        }

        if (!shouldClear && data.hasUUID(AbilityRuntime.TAG_GOLDEN_CROW_ENTITY_UUID)) {
            java.util.UUID uuid = data.getUUID(AbilityRuntime.TAG_GOLDEN_CROW_ENTITY_UUID);
            Entity entity = serverLevel.getEntity(uuid);
            if (entity == null || entity.isRemoved()) {
                shouldClear = true;
            }
        } else if (!shouldClear) {
            shouldClear = true;
        }

        if (shouldClear) {
            if (data.hasUUID(AbilityRuntime.TAG_GOLDEN_CROW_ENTITY_UUID)) {
                java.util.UUID uuid = data.getUUID(AbilityRuntime.TAG_GOLDEN_CROW_ENTITY_UUID);
                Entity entity = serverLevel.getEntity(uuid);
                if (entity != null && !entity.isRemoved()) {
                    entity.discard();
                }
            }
            data.putBoolean(AbilityRuntime.TAG_GOLDEN_CROW_ACTIVE, false);
            data.remove(AbilityRuntime.TAG_GOLDEN_CROW_ENTITY_UUID);
            data.remove(AbilityRuntime.TAG_GOLDEN_CROW_EXPIRE_TICK);
            data.remove(AbilityRuntime.TAG_GOLDEN_CROW_MANA_SPENT);
        }
    }

    private static void tickNinghe(Player player, long gameTime) {
        MobEffectInstance effect = player.getEffect(ModMobEffects.NINGHE.get());
        if (effect == null) {
            return;
        }

        if (gameTime % 20L != 0L) {
            return;
        }

        double radius = AbilityRuntime.NINGHE_RADIUS;
        double radiusSqr = radius * radius;
        float healPerSecond = AbilityRuntime.NINGHE_HEAL_PER_SECOND;

        AABB area = player.getBoundingBox().inflate(radius, 4.0D, radius);
        for (Player target : player.level().getEntitiesOfClass(Player.class, area,
                candidate -> candidate.isAlive()
                        && !candidate.isSpectator()
                        && candidate.distanceToSqr(player) <= radiusSqr)) {
            if (target.getHealth() < target.getMaxHealth()) {
                target.heal(healPerSecond);
            }
        }

        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0, false, false, true));
        player.causeFoodExhaustion(AbilityRuntime.NINGHE_HUNGER_EXHAUSTION_PER_SECOND);
    }

    private static void tickSunlight(Player player, long gameTime) {
        MobEffectInstance effect = player.getEffect(ModMobEffects.SUNLIGHT.get());
        if (effect == null) {
            return;
        }

        double radius = AbilityRuntime.SUNLIGHT_RADIUS;
        double radiusSqr = radius * radius;
        AABB area = player.getBoundingBox().inflate(radius, 4.0D, radius);

        if (gameTime % 10L == 0L) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, area,
                    candidate -> candidate.isAlive()
                            && !player.isAlliedTo(candidate)
                            && isHostileMob(candidate)
                            && candidate.distanceToSqr(player) <= radiusSqr)) {
                mob.setTarget(null);
                mob.addEffect(new MobEffectInstance(ModMobEffects.SUNLIGHT_NEUTRALIZED.get(),
                        AbilityRuntime.SUNLIGHT_NEUTRAL_DURATION_TICKS, 0, false, false, true));
            }
        }

        if (gameTime % 20L == 0L) {
            for (Player target : player.level().getEntitiesOfClass(Player.class, area,
                    candidate -> candidate.isAlive()
                            && !candidate.isSpectator()
                            && candidate.distanceToSqr(player) <= radiusSqr)) {
                if (AnomalyBookService.isRizhaoSequencePlayer(target)) {
                    addRizhaoEnergy(target, AbilityRuntime.SUNLIGHT_RIZHAO_MANA_PER_SECOND);
                } else {
                    target.setSecondsOnFire(AbilityRuntime.SUNLIGHT_BURN_DURATION_SECONDS);
                }
            }
        }
    }

    private static boolean isHostileMob(Mob mob) {
        return mob instanceof net.minecraft.world.entity.monster.Enemy;
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

    // ──────────────────────────────────────────────────────────────────
    // 万权一手（authority_grasp）：愚者 S 级终阶
    // 核心链路：
    //   1. tick 扫描 1.5 格内的敌对实体，自动施加抽能（与攻击命中同效果）。
    //   2. 攻击命中时，在 onLivingAttack 里调用 applyAuthorityDrain。
    //   3. 施法者受到实际伤害时（onLivingHurt），从已有支配列表拉 1 只到身边，
    //      本次施法最多 5 只。
    //   4. 被抽能者尝试施法时，SpellPreCastEvent 订阅者会取消施法。
    // ──────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onSpellPreCast(SpellPreCastEvent event) {
        Player caster = event.getEntity();
        if (caster == null || caster.level().isClientSide) {
            return;
        }
        if (caster.hasEffect(ModMobEffects.AUTHORITY_GRASP_DRAINED.get())) {
            event.setCanceled(true);
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.authority_grasp_drained"),
                    true);
        }
    }

    private static void tickAuthorityGrasp(Player player, CompoundTag data, long gameTime) {
        if (!player.hasEffect(ModMobEffects.AUTHORITY_GRASP_CASTER.get())) {
            AbilityRuntime.clear(data,
                    AbilityRuntime.TAG_AUTHORITY_GRASP_EXPIRE_TICK,
                    AbilityRuntime.TAG_AUTHORITY_GRASP_SUMMON_COUNT);
            return;
        }

        double range = AbilityRuntime.AUTHORITY_GRASP_PROXIMITY_RANGE;
        AABB box = player.getBoundingBox().inflate(range);
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != player
                        && entity.isAlive()
                        && !player.isAlliedTo(entity))) {
            applyAuthorityDrain(player, target, gameTime);
        }
    }

    private static void applyAuthorityDrain(LivingEntity caster, LivingEntity target, long gameTime) {
        long casterExpire = caster.getPersistentData().getLong(AbilityRuntime.TAG_AUTHORITY_GRASP_EXPIRE_TICK);
        if (casterExpire <= gameTime) {
            return;
        }
        int remainingTicks = (int) Math.max(20L, casterExpire - gameTime);

        target.addEffect(new MobEffectInstance(
                ModMobEffects.AUTHORITY_GRASP_DRAINED.get(),
                remainingTicks,
                0,
                false,
                false,
                true));
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                remainingTicks,
                AbilityRuntime.AUTHORITY_GRASP_SLOW_AMPLIFIER,
                false,
                false,
                true));
        target.getPersistentData().putLong(AbilityRuntime.TAG_AUTHORITY_GRASP_DRAINED_EXPIRE, casterExpire);
    }

    private static void triggerAuthorityHurtSummon(Player caster) {
        CompoundTag data = caster.getPersistentData();
        int count = data.getInt(AbilityRuntime.TAG_AUTHORITY_GRASP_SUMMON_COUNT);
        if (count >= AbilityRuntime.AUTHORITY_GRASP_MAX_SUMMONS) {
            return;
        }

        List<Mob> dominated = AbilityRuntime.getDominatedMobs(caster);
        if (dominated.isEmpty()) {
            return;
        }

        List<Mob> candidates = new ArrayList<>();
        for (Mob mob : dominated) {
            if (mob.isAlive() && mob.distanceTo(caster) > 2.0D) {
                candidates.add(mob);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        Mob chosen = candidates.get(caster.getRandom().nextInt(candidates.size()));
        chosen.teleportTo(caster.getX(), caster.getY(), caster.getZ());
        if (caster.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    chosen.getX(), chosen.getY() + chosen.getBbHeight() * 0.5D, chosen.getZ(),
                    12, 0.3D, 0.5D, 0.3D, 0.15D);
        }
        caster.level().playSound(null, caster.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.6F, 0.9F);

        data.putInt(AbilityRuntime.TAG_AUTHORITY_GRASP_SUMMON_COUNT, count + 1);
    }
}

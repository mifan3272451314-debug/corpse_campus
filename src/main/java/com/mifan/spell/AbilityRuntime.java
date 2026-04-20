package com.mifan.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import com.mifan.spell.runtime.DominanceRuntime;
import com.mifan.spell.runtime.DaiyueRuntime;
import com.mifan.spell.runtime.ElementalDomainRuntime;
import com.mifan.spell.runtime.ExecutionerRuntime;
import com.mifan.spell.runtime.MarkRuntime;
import com.mifan.spell.runtime.NecroticRuntime;
import com.mifan.spell.runtime.RecorderOfficerRuntime;
import com.mifan.spell.runtime.TelekinesisRuntime;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;

import java.util.Random;
import java.util.UUID;

public final class AbilityRuntime {
    public static final int TOGGLE_DURATION_TICKS = 20 * 60 * 60 * 4;

    public static final String TAG_DANGER_LAST_ALERT = "corpse_campus_danger_last_alert";

    public static final String TAG_RECORDER_OFFICER_ARMED = "corpse_campus_recorder_officer_armed";
    public static final String TAG_RECORDER_OFFICER_END = "corpse_campus_recorder_officer_end";
    public static final String TAG_RECORDER_OFFICER_X = "corpse_campus_recorder_officer_x";
    public static final String TAG_RECORDER_OFFICER_Y = "corpse_campus_recorder_officer_y";
    public static final String TAG_RECORDER_OFFICER_Z = "corpse_campus_recorder_officer_z";
    public static final String TAG_RECORDER_OFFICER_DIMENSION = "corpse_campus_recorder_officer_dimension";
    public static final String TAG_RECORDER_OFFICER_CASTER = "corpse_campus_recorder_officer_caster";

    public static final String ITEM_TAG_RECORDER_OFFICER_NOTE = "corpse_campus_recorder_officer_note";
    public static final String ITEM_TAG_RECORDER_OFFICER_X = "x";
    public static final String ITEM_TAG_RECORDER_OFFICER_Y = "y";
    public static final String ITEM_TAG_RECORDER_OFFICER_Z = "z";
    public static final String ITEM_TAG_RECORDER_OFFICER_DIMENSION = "dimension";

    public static final String TAG_TELEKINESIS_TARGET_ID = "corpse_campus_telekinesis_target_id";
    public static final String TAG_TELEKINESIS_HOLD_UNTIL = "corpse_campus_telekinesis_hold_until";
    public static final String TAG_TELEKINESIS_LEVEL = "corpse_campus_telekinesis_level";
    public static final String TAG_TELEKINESIS_LOOK_X = "corpse_campus_telekinesis_look_x";
    public static final String TAG_TELEKINESIS_LOOK_Y = "corpse_campus_telekinesis_look_y";
    public static final String TAG_TELEKINESIS_LOOK_Z = "corpse_campus_telekinesis_look_z";

    public static final String TAG_MAGNETIC_CLINGING = "corpse_campus_magnetic_clinging";
    public static final String TAG_MAGNETIC_CLING_END = "corpse_campus_magnetic_cling_end";
    public static final String TAG_MAGNETIC_LAST_GROUND = "corpse_campus_magnetic_last_ground";
    public static final String TAG_MAGNETIC_SHOCK_READY = "corpse_campus_magnetic_shock_ready";

    public static final String TAG_INSTINCT_END = "corpse_campus_instinct_end";
    public static final String TAG_INSTINCT_LEVEL = "corpse_campus_instinct_level";
    public static final String TAG_INSTINCT_USED = "corpse_campus_instinct_used";
    public static final String TAG_INSTINCT_INVULNERABLE_UNTIL = "corpse_campus_instinct_invulnerable_until";

    public static final String TAG_MANIA_LAST_PROC = "corpse_campus_mania_last_proc";
    public static final String TAG_MANIA_LAST_SWING = "corpse_campus_mania_last_swing";

    public static final String TAG_NECROTIC_ALLOW_HEAL_UNTIL = "corpse_campus_necrotic_allow_heal_until";
    public static final String TAG_NECROTIC_LAST_KILL_HEAL = "corpse_campus_necrotic_last_kill_heal";
    public static final String TAG_NECROTIC_REVIVE_USED = "corpse_campus_necrotic_revive_used";
    public static final String TAG_NECROTIC_ORIGINAL_MAX_HEALTH = "corpse_campus_necrotic_original_max_health";
    public static final String TAG_NECROTIC_MAX_HEALTH_APPLIED = "corpse_campus_necrotic_max_health_applied";
    public static final String TAG_NECROTIC_PROVOKED_BY = "corpse_campus_necrotic_provoked_by";
    public static final String TAG_NECROTIC_PROVOKED_UNTIL = "corpse_campus_necrotic_provoked_until";

    public static final String TAG_MARK_ACTIVE = "corpse_campus_mark_active";
    public static final String TAG_MARK_X = "corpse_campus_mark_x";
    public static final String TAG_MARK_Y = "corpse_campus_mark_y";
    public static final String TAG_MARK_Z = "corpse_campus_mark_z";
    public static final String TAG_MARK_END = "corpse_campus_mark_end";
    public static final String TAG_MARK_LEVEL = "corpse_campus_mark_level";
    public static final String TAG_MARK_TRIGGERED = "corpse_campus_mark_triggered";

    public static final String TAG_EXECUTIONER_LAST_TICK = "corpse_campus_executioner_last_tick";
    public static final String TAG_EXECUTIONER_BLOCK_HITS = "corpse_campus_executioner_block_hits";
    public static final String TAG_EXECUTIONER_BLOCK_LAST_TICK = "corpse_campus_executioner_block_last_tick";

    public static final String TAG_DOMINANCE_MOBS = "corpse_campus_dominance_mobs";
    public static final String TAG_DOMINANCE_TARGET_PLAYER = "corpse_campus_dominance_target_player";
    public static final String TAG_DOMINANCE_LINK_ACTIVE = "corpse_campus_dominance_link_active";
    public static final String TAG_DOMINANCE_OWNER = "corpse_campus_dominance_owner";
    public static final String TAG_DOMINANCE_LEVEL = "corpse_campus_dominance_level";

    public static final String TAG_DANGER_RECENT_ATTACKERS = "corpse_campus_danger_recent_attackers";
    public static final String TAG_OLFACTION_TRAIL = "corpse_campus_olfaction_trail";
    public static final String TAG_OLFACTION_LAST_TRAIL_TICK = "corpse_campus_olfaction_last_trail_tick";
    public static final String TAG_ELEMENTAL_DOMAIN_LAST_TICK = "corpse_campus_elemental_domain_last_tick";
    public static final String TAG_ELEMENTAL_DOMAIN_START_TICK = "corpse_campus_elemental_domain_start_tick";
    public static final String TAG_ELEMENTAL_DOMAIN_CENTER_X = "corpse_campus_elemental_domain_center_x";
    public static final String TAG_ELEMENTAL_DOMAIN_CENTER_Y = "corpse_campus_elemental_domain_center_y";
    public static final String TAG_ELEMENTAL_DOMAIN_CENTER_Z = "corpse_campus_elemental_domain_center_z";
    public static final String TAG_ELEMENTAL_DOMAIN_CLOSED = "corpse_campus_elemental_domain_closed";

    public static final int EXECUTIONER_DURABILITY_COST = 15;
    public static final int RECORDER_OFFICER_DEFAULT_SECONDS = 15;
    public static final int RECORDER_OFFICER_MIN_SECONDS = 5;
    public static final int RECORDER_OFFICER_MAX_SECONDS = 120;
    private static final float EXECUTIONER_DAMAGE_RATIO = 0.25F;
    public static final float DOMINANCE_MIN_SURVIVAL_HEALTH = 1.0F;
    public static final float DOMINANCE_MAX_HEALTH_LIMIT = 35.0F;
    private static final int NECROTIC_KILL_HEAL_BASE = 4;
    private static final double NECROTIC_UNDEAD_MAX_HEALTH = 40.0D;
    private static final float NECROTIC_NON_PLAYER_KILL_HEAL = 4.0F;
    private static final int NECROTIC_PROVOKE_DURATION_TICKS = 20 * 20;
    private static final int MARK_ROOT_DURATION_TICKS = 200;
    private static final int ELEMENTAL_DOMAIN_RADIUS = 30;
    private static final int ELEMENTAL_DOMAIN_CLOSED_RADIUS = 15;
    private static final int ELEMENTAL_DOMAIN_INTERVAL = 20;

    private AbilityRuntime() {
    }

    public static void activateTimedState(LivingEntity entity, String endKey, String levelKey, long durationTicks,
            int spellLevel) {
        CompoundTag data = entity.getPersistentData();
        data.putLong(endKey, entity.level().getGameTime() + durationTicks);
        data.putInt(levelKey, spellLevel);
    }

    public static boolean isActive(CompoundTag data, String endKey, long gameTime) {
        return data.contains(endKey) && data.getLong(endKey) > gameTime;
    }

    public static int getLevel(CompoundTag data, String levelKey) {
        return Math.max(1, data.getInt(levelKey));
    }

    public static int getEffectLevel(MobEffectInstance effectInstance) {
        return effectInstance == null ? 1 : effectInstance.getAmplifier() + 1;
    }

    public static void clear(CompoundTag data, String... keys) {
        for (String key : keys) {
            data.remove(key);
        }
    }

    public static LivingEntity findTargetInSight(LivingEntity caster, double range, double minDot) {
        Vec3 eyePosition = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        AABB searchBox = caster.getBoundingBox().inflate(range);

        return caster.level().getEntitiesOfClass(LivingEntity.class, searchBox, target -> target != caster
                && target.isAlive()
                && caster.hasLineOfSight(target))
                .stream()
                .filter(target -> eyePosition.distanceToSqr(target.getEyePosition()) <= range * range)
                .filter(target -> {
                    Vec3 delta = target.getEyePosition().subtract(eyePosition);
                    if (delta.lengthSqr() < 1.0E-4D) {
                        return false;
                    }
                    return delta.normalize().dot(look) >= minDot;
                })
                .min(Comparator.comparingDouble(target -> eyePosition.distanceToSqr(target.getEyePosition())))
                .orElse(null);
    }

    public static LivingEntity findLivingEntityById(Level level, int entityId) {
        Entity entity = level.getEntity(entityId);
        return entity instanceof LivingEntity livingEntity && livingEntity.isAlive() ? livingEntity : null;
    }

    public static void storeLookVector(CompoundTag data, Vec3 look) {
        TelekinesisRuntime.storeLookVector(data, look);
    }

    public static Vec3 readStoredLookVector(CompoundTag data) {
        return TelekinesisRuntime.readStoredLookVector(data);
    }

    public static void pushNearbyEntities(LivingEntity source, double radius, double horizontalStrength,
            double verticalStrength) {
        for (LivingEntity target : source.level().getEntitiesOfClass(
                LivingEntity.class,
                source.getBoundingBox().inflate(radius),
                target -> target != source && target.isAlive())) {
            Vec3 horizontalDelta = target.position().subtract(source.position());
            double horizontalLength = Math
                    .sqrt(horizontalDelta.x * horizontalDelta.x + horizontalDelta.z * horizontalDelta.z);

            if (horizontalLength < 1.0E-3D) {
                Vec3 look = source.getLookAngle();
                horizontalDelta = new Vec3(look.x, 0.0D, look.z);
                horizontalLength = Math.max(1.0E-3D,
                        Math.sqrt(horizontalDelta.x * horizontalDelta.x + horizontalDelta.z * horizontalDelta.z));
            }

            double falloff = 1.0D - Math.min(0.9D, source.distanceTo(target) / radius);
            target.push(
                    horizontalDelta.x / horizontalLength * horizontalStrength * falloff,
                    verticalStrength,
                    horizontalDelta.z / horizontalLength * horizontalStrength * falloff);
            target.hurtMarked = true;
        }
    }

    public static LivingEntity findNearestFrontTarget(LivingEntity caster, double range, double minDot) {
        Vec3 eyePosition = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        AABB searchBox = caster.getBoundingBox().inflate(range);

        return caster.level().getEntitiesOfClass(LivingEntity.class, searchBox, target -> target != caster
                && target.isAlive())
                .stream()
                .filter(target -> eyePosition.distanceToSqr(target.getEyePosition()) <= range * range)
                .filter(target -> {
                    Vec3 delta = target.getEyePosition().subtract(eyePosition);
                    if (delta.lengthSqr() < 1.0E-4D) {
                        return false;
                    }
                    return delta.normalize().dot(look) >= minDot;
                })
                .min(Comparator.comparingDouble(target -> eyePosition.distanceToSqr(target.getEyePosition())))
                .orElse(null);
    }

    public static int getExecutionerDamagePercent() {
        return ExecutionerRuntime.getDamagePercent();
    }

    public static int getNecroticHealAmount(int spellLevel) {
        return NecroticRuntime.getHealAmount(spellLevel);
    }

    public static double getNecroticUndeadMaxHealth() {
        return NecroticRuntime.getUndeadMaxHealth();
    }

    public static float getNecroticNonPlayerKillHeal() {
        return NecroticRuntime.getNonPlayerKillHeal();
    }

    public static int getDaiyueDashRange(int spellLevel) {
        return DaiyueRuntime.getDashRange(spellLevel);
    }

    public static double getDaiyueHitWidth(int spellLevel) {
        return DaiyueRuntime.getHitWidth(spellLevel);
    }

    public static int getNecroticProvokeDurationTicks() {
        return NecroticRuntime.getProvokeDurationTicks();
    }

    public static void markNecroticProvoked(Mob mob, Player player) {
        NecroticRuntime.markProvoked(mob, player);
    }

    public static boolean canMobTargetNecroticPlayer(Mob mob, Player player) {
        return NecroticRuntime.canMobTargetPlayer(mob, player);
    }

    public static void clearNecroticProvoked(Mob mob) {
        NecroticRuntime.clearProvoked(mob);
    }

    public static int getMarkRadius(int spellLevel) {
        return MarkRuntime.getRadius(spellLevel);
    }

    public static int getMarkDurationSeconds(int spellLevel) {
        return MarkRuntime.getDurationSeconds(spellLevel);
    }

    public static int getMarkRootSeconds() {
        return MarkRuntime.getRootSeconds();
    }

    public static int clampRecorderOfficerSeconds(int seconds) {
        return RecorderOfficerRuntime.clampSeconds(seconds);
    }

    public static boolean hasRecorderOfficerRecord(ItemStack stack) {
        return RecorderOfficerRuntime.hasRecord(stack);
    }

    public static void recordRecorderOfficerPaper(ItemStack stack, LivingEntity caster, BlockPos blockPos,
            Direction face) {
        RecorderOfficerRuntime.recordPaper(stack, caster, blockPos, face);
    }

    public static void armRecorderOfficerTarget(ServerPlayer caster, int spellLevel, int targetEntityId,
            int timerSeconds) {
        RecorderOfficerRuntime.armTarget(caster, spellLevel, targetEntityId, timerSeconds);
    }

    public static void tickRecorderOfficer(LivingEntity entity, long gameTime) {
        RecorderOfficerRuntime.tick(entity, gameTime);
    }

    public static void clearRecorderOfficer(CompoundTag data) {
        RecorderOfficerRuntime.clear(data);
    }

    public static int getElementalistRadius() {
        return ELEMENTAL_DOMAIN_RADIUS;
    }

    public static int getElementalistClosedRadius() {
        return ELEMENTAL_DOMAIN_CLOSED_RADIUS;
    }

    public static int getElementalistActiveRadius(CompoundTag data) {
        return data.getBoolean(TAG_ELEMENTAL_DOMAIN_CLOSED) ? ELEMENTAL_DOMAIN_CLOSED_RADIUS : ELEMENTAL_DOMAIN_RADIUS;
    }

    public static int getOlfactionSilenceRadius() {
        return 10;
    }

    public static int getOlfactionTrackRange(int spellLevel) {
        return 20 + Math.max(0, spellLevel - 1) * 4;
    }

    public static int getOlfactionLowHealthPercent() {
        return 75;
    }

    public static int getElementalistManaDrain(int spellLevel) {
        return 8 + Math.max(0, spellLevel - 1) * 2;
    }

    public static int getElementalistInterval(CompoundTag data) {
        return data.getBoolean(TAG_ELEMENTAL_DOMAIN_CLOSED) ? 12 : ELEMENTAL_DOMAIN_INTERVAL * 2;
    }

    public static void clearElementalDomain(CompoundTag data) {
        ElementalDomainRuntime.clear(data);
    }

    public static void beginElementalDomain(ServerLevel level, Player caster, boolean closedDomain) {
        ElementalDomainRuntime.begin(level, caster, closedDomain);
    }

    public static void endElementalDomain(ServerLevel level, Player caster) {
        ElementalDomainRuntime.end(level, caster);
    }

    public static Vec3 getElementalistCenter(Player caster) {
        return ElementalDomainRuntime.getCenter(caster);
    }

    public static boolean hasElementalistCenter(CompoundTag data) {
        return ElementalDomainRuntime.hasCenter(data);
    }

    public static void tickElementalDomainTerrain(ServerLevel level, Player caster, long gameTime) {
        ElementalDomainRuntime.tickTerrain(level, caster, gameTime);
    }

    public static void tickElementalDomainRestoration(ServerLevel level) {
        ElementalDomainRuntime.tickRestoration(level);
    }

    public static void triggerElementalistBurst(ServerLevel level, Player caster, LivingEntity target, int spellLevel) {
        ElementalDomainRuntime.triggerBurst(level, caster, target, spellLevel);
    }

    public static boolean isElementalistValidTarget(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive()) {
            return false;
        }

        if (target instanceof Player otherPlayer && otherPlayer.isSpectator()) {
            return false;
        }

        return !target.getType().is(net.minecraft.tags.EntityTypeTags.FALL_DAMAGE_IMMUNE)
                || !target.isInvulnerableTo(caster.damageSources().magic());
    }

    public static boolean isElementalDomainVisualBlock(BlockState state) {
        return ElementalDomainRuntime.isVisualBlock(state);
    }

    public static void placeMark(LivingEntity caster, int spellLevel, net.minecraft.core.BlockPos blockPos,
            net.minecraft.core.Direction face) {
        MarkRuntime.place(caster, spellLevel, blockPos, face);
    }

    public static void clearMark(CompoundTag data) {
        MarkRuntime.clear(data);
    }

    public static Vec3 getMarkCenter(CompoundTag data) {
        return MarkRuntime.getCenter(data);
    }

    public static ListTag getStringList(CompoundTag data, String key) {
        return data.contains(key, Tag.TAG_LIST) ? data.getList(key, Tag.TAG_STRING) : new ListTag();
    }

    public static boolean containsUuid(ListTag list, UUID uuid) {
        String uuidString = uuid.toString();
        for (int i = 0; i < list.size(); i++) {
            if (uuidString.equals(list.getString(i))) {
                return true;
            }
        }
        return false;
    }

    public static void appendUuid(ListTag list, UUID uuid) {
        if (!containsUuid(list, uuid)) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
    }

    public static boolean isExecutionerWeapon(ItemStack stack) {
        return ExecutionerRuntime.isWeapon(stack);
    }

    public static Mob findDominanceMobTarget(LivingEntity caster, double range, double minDot) {
        return DominanceRuntime.findMobTarget(caster, range, minDot);
    }

    public static boolean addDominatedMob(LivingEntity caster, Mob mob, int spellLevel, int maxControlled) {
        return DominanceRuntime.addDominatedMob(caster, mob, spellLevel, maxControlled);
    }

    public static void setDominanceTargetPlayer(ServerPlayer caster, UUID targetPlayerId) {
        DominanceRuntime.setTargetPlayer(caster, targetPlayerId);
    }

    public static void tickDominance(Player player) {
        DominanceRuntime.tick(player);
    }

    public static void retargetDominatedMobs(Player player, LivingEntity target) {
        DominanceRuntime.retargetDominatedMobs(player, target);
    }

    public static boolean isDominatedBy(Mob mob, Player owner) {
        return DominanceRuntime.isDominatedBy(mob, owner);
    }

    public static List<Mob> getDominatedMobs(Player player) {
        return DominanceRuntime.getDominatedMobs(player);
    }

    public static boolean canExecutionerUse(ItemStack stack) {
        return ExecutionerRuntime.canUse(stack);
    }

    public static boolean isDangerSenseWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    public static double getDangerSenseWeaponDamage(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }

        return stack.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                .stream()
                .mapToDouble(modifier -> modifier.getAmount())
                .sum();
    }

    public static void incrementExecutionerShieldPressure(LivingEntity target) {
        ExecutionerRuntime.incrementShieldPressure(target);
    }

    public static void resetExecutionerShieldPressure(LivingEntity target) {
        ExecutionerRuntime.resetShieldPressure(target);
    }

    public static void tickExecutionerCast(Level level, LivingEntity caster, int spellLevel) {
        ExecutionerRuntime.tickCast(level, caster, spellLevel);
    }

    public static void castDaiyue(Level level, LivingEntity caster, int spellLevel, float spellPower) {
        DaiyueRuntime.cast(level, caster, spellLevel, spellPower);
    }

}

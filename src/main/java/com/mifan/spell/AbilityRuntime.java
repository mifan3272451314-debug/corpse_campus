package com.mifan.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class AbilityRuntime {
    public static final int TOGGLE_DURATION_TICKS = 20 * 60 * 60 * 4;

    public static final String TAG_DANGER_LAST_ALERT = "corpse_campus_danger_last_alert";

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
    private static final float EXECUTIONER_DAMAGE_RATIO = 0.25F;
    private static final float DOMINANCE_MIN_SURVIVAL_HEALTH = 1.0F;
    private static final float DOMINANCE_MAX_HEALTH_LIMIT = 35.0F;
    private static final int NECROTIC_KILL_HEAL_BASE = 4;
    private static final double NECROTIC_UNDEAD_MAX_HEALTH = 40.0D;
    private static final int MARK_ROOT_DURATION_TICKS = 200;
    private static final int ELEMENTAL_DOMAIN_RADIUS = 30;
    private static final int ELEMENTAL_DOMAIN_CLOSED_RADIUS = 15;
    private static final int ELEMENTAL_DOMAIN_INTERVAL = 20;
    private static final float ELEMENTAL_DOMAIN_EXPAND_RATE = 2.5F;
    private static final int ELEMENTAL_DOMAIN_RESTORE_PER_TICK = 384;
    private static final BlockState[] ELEMENTAL_DOMAIN_INNER_BLOCKS = {
            Blocks.PRISMARINE.defaultBlockState(),
            Blocks.DARK_PRISMARINE.defaultBlockState(),
            Blocks.BLUE_ICE.defaultBlockState(),
            Blocks.SEA_LANTERN.defaultBlockState(),
            Blocks.MAGMA_BLOCK.defaultBlockState(),
            Blocks.CRYING_OBSIDIAN.defaultBlockState()
    };
    private static final BlockState[] ELEMENTAL_DOMAIN_SHELL_BLOCKS = {
            Blocks.CRYING_OBSIDIAN.defaultBlockState(),
            Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
            Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),
            Blocks.RESPAWN_ANCHOR.defaultBlockState(),
            Blocks.OBSIDIAN.defaultBlockState()
    };
    private static final Map<UUID, ElementalDomainState> ACTIVE_ELEMENTAL_DOMAINS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> ELEMENTAL_DOMAIN_RESTORE_TICKS = new HashMap<>();
    private static final Random DOMAIN_RANDOM = new Random();

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
        data.putDouble(TAG_TELEKINESIS_LOOK_X, look.x);
        data.putDouble(TAG_TELEKINESIS_LOOK_Y, look.y);
        data.putDouble(TAG_TELEKINESIS_LOOK_Z, look.z);
    }

    public static Vec3 readStoredLookVector(CompoundTag data) {
        return new Vec3(
                data.getDouble(TAG_TELEKINESIS_LOOK_X),
                data.getDouble(TAG_TELEKINESIS_LOOK_Y),
                data.getDouble(TAG_TELEKINESIS_LOOK_Z));
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
        return Math.round(EXECUTIONER_DAMAGE_RATIO * 100.0F);
    }

    public static int getNecroticHealAmount(int spellLevel) {
        return NECROTIC_KILL_HEAL_BASE + Math.max(0, spellLevel - 1) * 2;
    }

    public static double getNecroticUndeadMaxHealth() {
        return NECROTIC_UNDEAD_MAX_HEALTH;
    }

    public static int getMarkRadius(int spellLevel) {
        return 3 + Math.max(0, spellLevel - 1);
    }

    public static int getMarkDurationSeconds(int spellLevel) {
        return 20 + spellLevel * 5;
    }

    public static int getMarkRootSeconds() {
        return MARK_ROOT_DURATION_TICKS / 20;
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

    public static int getElementalistInterval() {
        return ELEMENTAL_DOMAIN_INTERVAL;
    }

    public static void clearElementalDomain(CompoundTag data) {
        clear(data,
                TAG_ELEMENTAL_DOMAIN_LAST_TICK,
                TAG_ELEMENTAL_DOMAIN_START_TICK,
                TAG_ELEMENTAL_DOMAIN_CENTER_X,
                TAG_ELEMENTAL_DOMAIN_CENTER_Y,
                TAG_ELEMENTAL_DOMAIN_CENTER_Z,
                TAG_ELEMENTAL_DOMAIN_CLOSED);
    }

    public static void beginElementalDomain(ServerLevel level, Player caster, boolean closedDomain) {
        CompoundTag data = caster.getPersistentData();
        data.putLong(TAG_ELEMENTAL_DOMAIN_START_TICK, level.getGameTime());
        data.putLong(TAG_ELEMENTAL_DOMAIN_LAST_TICK, 0L);
        data.putDouble(TAG_ELEMENTAL_DOMAIN_CENTER_X, caster.getX());
        data.putDouble(TAG_ELEMENTAL_DOMAIN_CENTER_Y, caster.getY());
        data.putDouble(TAG_ELEMENTAL_DOMAIN_CENTER_Z, caster.getZ());
        data.putBoolean(TAG_ELEMENTAL_DOMAIN_CLOSED, closedDomain);

        ACTIVE_ELEMENTAL_DOMAINS.compute(caster.getUUID(), (uuid, existing) -> {
            if (existing != null) {
                existing.forceDiscard(level);
            }
            return new ElementalDomainState(level.dimension(), new BlockPos(
                    Mth.floor(caster.getX()),
                    Mth.floor(caster.getY()),
                    Mth.floor(caster.getZ())), closedDomain);
        });
    }

    public static void endElementalDomain(ServerLevel level, Player caster) {
        ElementalDomainState state = ACTIVE_ELEMENTAL_DOMAINS.get(caster.getUUID());
        if (state != null) {
            state.beginRestore(level, caster.blockPosition());
        }
    }

    public static Vec3 getElementalistCenter(Player caster) {
        CompoundTag data = caster.getPersistentData();
        return new Vec3(
                data.getDouble(TAG_ELEMENTAL_DOMAIN_CENTER_X),
                data.getDouble(TAG_ELEMENTAL_DOMAIN_CENTER_Y),
                data.getDouble(TAG_ELEMENTAL_DOMAIN_CENTER_Z));
    }

    public static boolean hasElementalistCenter(CompoundTag data) {
        return data.contains(TAG_ELEMENTAL_DOMAIN_CENTER_X)
                && data.contains(TAG_ELEMENTAL_DOMAIN_CENTER_Y)
                && data.contains(TAG_ELEMENTAL_DOMAIN_CENTER_Z);
    }

    public static void tickElementalDomainTerrain(ServerLevel level, Player caster, long gameTime) {
        ElementalDomainState state = ACTIVE_ELEMENTAL_DOMAINS.get(caster.getUUID());
        if (state == null || state.restoring || state.dimension != level.dimension()) {
            return;
        }

        long startTick = caster.getPersistentData().getLong(TAG_ELEMENTAL_DOMAIN_START_TICK);
        int maxRadius = getElementalistActiveRadius(caster.getPersistentData());
        float radius = Math.min(maxRadius, Math.max(0.0F, (gameTime - startTick) * ELEMENTAL_DOMAIN_EXPAND_RATE));
        state.replaceSphere(level, state.center, state.lastRadius, radius);
        state.lastRadius = radius;
        if (state.closedDomain && radius >= maxRadius && !state.shellBuilt) {
            state.buildShell(level, caster.blockPosition());
        }
    }

    public static void tickElementalDomainRestoration(ServerLevel level) {
        long gameTime = level.getGameTime();
        Long lastTick = ELEMENTAL_DOMAIN_RESTORE_TICKS.get(level.dimension());
        if (lastTick != null && lastTick == gameTime) {
            return;
        }
        ELEMENTAL_DOMAIN_RESTORE_TICKS.put(level.dimension(), gameTime);

        ACTIVE_ELEMENTAL_DOMAINS.entrySet().removeIf(entry -> {
            ElementalDomainState state = entry.getValue();
            if (state.dimension != level.dimension() || !state.restoring) {
                return false;
            }
            return state.tickRestore(level);
        });
    }

    public static void triggerElementalistBurst(ServerLevel level, Player caster, LivingEntity target, int spellLevel) {
        int element = level.random.nextInt(3);
        switch (element) {
            case 0 -> castRegisteredElementalSpell(level, caster, target, spellLevel,
                    SpellRegistry.FIREBOLT_SPELL.get(), ElementalSpellType.FIRE);
            case 1 -> castRegisteredElementalSpell(level, caster, target, spellLevel,
                    SpellRegistry.ICICLE_SPELL.get(), ElementalSpellType.WATER);
            default -> castRegisteredElementalSpell(level, caster, target, spellLevel,
                    SpellRegistry.LIGHTNING_BOLT_SPELL.get(), ElementalSpellType.LIGHTNING);
        }
    }

    private static void castRegisteredElementalSpell(ServerLevel level, Player caster, LivingEntity target, int spellLevel,
            AbstractSpell spell, ElementalSpellType type) {
        MagicData magicData = MagicData.getPlayerMagicData(caster);
        Vec3 originalPos = caster.position();
        float originalYRot = caster.getYRot();
        float originalXRot = caster.getXRot();

        Vec3 castOrigin = getElementalCastOrigin(target, type);
        Vec3 direction = target.getEyePosition().subtract(castOrigin).normalize();

        caster.teleportTo(castOrigin.x, castOrigin.y, castOrigin.z);
        float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        float pitch = (float) Mth.wrapDegrees(-Math.toDegrees(Math.atan2(direction.y,
                Math.sqrt(direction.x * direction.x + direction.z * direction.z))));
        caster.setYRot(yaw);
        caster.setYHeadRot(yaw);
        caster.setYBodyRot(yaw);
        caster.setXRot(pitch);

        spell.onCast(level, Math.max(1, Math.min(3, spellLevel)), caster, CastSource.NONE, magicData);

        caster.teleportTo(originalPos.x, originalPos.y, originalPos.z);
        caster.setYRot(originalYRot);
        caster.setYHeadRot(originalYRot);
        caster.setYBodyRot(originalYRot);
        caster.setXRot(originalXRot);
    }

    private static Vec3 getElementalCastOrigin(LivingEntity target, ElementalSpellType type) {
        Vec3 base = target.getEyePosition();
        return switch (type) {
            case FIRE -> base.add(0.0D, 0.15D, -1.4D);
            case WATER -> base.add(0.0D, 0.1D, -1.6D);
            case LIGHTNING -> base.add(0.0D, 6.0D, 0.0D);
        };
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

    private static BlockState pickElementalReplaceBlock() {
        return ELEMENTAL_DOMAIN_INNER_BLOCKS[DOMAIN_RANDOM.nextInt(ELEMENTAL_DOMAIN_INNER_BLOCKS.length)];
    }

    private static BlockState pickElementalShellBlock() {
        return ELEMENTAL_DOMAIN_SHELL_BLOCKS[DOMAIN_RANDOM.nextInt(ELEMENTAL_DOMAIN_SHELL_BLOCKS.length)];
    }

    private static boolean isElementalDomainBlock(BlockState state) {
        for (BlockState blockState : ELEMENTAL_DOMAIN_INNER_BLOCKS) {
            if (blockState.getBlock() == state.getBlock()) {
                return true;
            }
        }
        for (BlockState blockState : ELEMENTAL_DOMAIN_SHELL_BLOCKS) {
            if (blockState.getBlock() == state.getBlock()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isElementalDomainVisualBlock(BlockState state) {
        return isElementalDomainBlock(state);
    }

    private enum ElementalSpellType {
        FIRE,
        WATER,
        LIGHTNING
    }

    private static final class ElementalDomainState {
        private final ResourceKey<Level> dimension;
        private final BlockPos center;
        private final boolean closedDomain;
        private final Map<BlockPos, BlockState> savedStates = new LinkedHashMap<>();
        private final Map<BlockPos, CompoundTag> savedNbt = new HashMap<>();
        private List<BlockPos> restoreQueue;
        private int restoreIndex;
        private float lastRadius;
        private boolean shellBuilt;
        private boolean restoring;

        private ElementalDomainState(ResourceKey<Level> dimension, BlockPos center, boolean closedDomain) {
            this.dimension = dimension;
            this.center = center.immutable();
            this.closedDomain = closedDomain;
        }

        private void replaceSphere(ServerLevel level, BlockPos centerPos, float innerR, float outerR) {
            if (outerR <= 0.0F) {
                return;
            }

            float innerSq = innerR * innerR;
            float outerSq = outerR * outerR;
            int outerInt = Mth.ceil(outerR);
            List<BlockPos> positions = new ArrayList<>();
            for (int dx = -outerInt; dx <= outerInt; dx++) {
                for (int dy = -outerInt; dy <= outerInt; dy++) {
                    for (int dz = -outerInt; dz <= outerInt; dz++) {
                        float d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 > outerSq || d2 <= innerSq) {
                            continue;
                        }

                        BlockPos pos = centerPos.offset(dx, dy, dz);
                        if (savedStates.containsKey(pos)) {
                            continue;
                        }

                        positions.add(pos.immutable());
                    }
                }
            }

            positions.sort(Comparator.comparingInt((BlockPos blockPos) -> blockPos.getY()).reversed());
            for (BlockPos pos : positions) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir()
                        || state.getDestroySpeed(level, pos) < 0.0F
                        || isElementalDomainBlock(state)
                        || shouldSkipElementalReplacement(level, pos, state)) {
                    continue;
                }

                savedStates.put(pos.immutable(), state);
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    savedNbt.put(pos.immutable(), blockEntity.saveWithFullMetadata());
                    level.removeBlockEntity(pos);
                }
                level.setBlock(pos, pickElementalReplaceBlock(), 18);
            }
        }

        private void buildShell(ServerLevel level, BlockPos soundPos) {
            int domainRadius = closedDomain ? ELEMENTAL_DOMAIN_CLOSED_RADIUS : ELEMENTAL_DOMAIN_RADIUS;
            int outerRadius = domainRadius + 1;
            float innerSq = (domainRadius - 2.0F) * (domainRadius - 2.0F);
            float outerSq = outerRadius * outerRadius;
            List<BlockPos> positions = new ArrayList<>();
            for (int dx = -outerRadius; dx <= outerRadius; dx++) {
                for (int dy = -outerRadius; dy <= outerRadius; dy++) {
                    for (int dz = -outerRadius; dz <= outerRadius; dz++) {
                        float d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 < innerSq || d2 > outerSq) {
                            continue;
                        }

                        BlockPos pos = center.offset(dx, dy, dz);
                        if (savedStates.containsKey(pos)) {
                            continue;
                        }

                        positions.add(pos.immutable());
                    }
                }
            }

            positions.sort(Comparator.comparingInt((BlockPos blockPos) -> blockPos.getY()).reversed());
            for (BlockPos pos : positions) {
                BlockState state = level.getBlockState(pos);
                if (state.getDestroySpeed(level, pos) < 0.0F
                        || isElementalDomainBlock(state)
                        || shouldSkipElementalReplacement(level, pos, state)) {
                    continue;
                }

                savedStates.put(pos.immutable(), state);
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    savedNbt.put(pos.immutable(), blockEntity.saveWithFullMetadata());
                    level.removeBlockEntity(pos);
                }
                level.setBlock(pos, pickElementalShellBlock(), 18);
            }

            shellBuilt = true;
            level.playSound(null, soundPos, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 2.2F, 0.55F);
        }

        private void beginRestore(ServerLevel level, BlockPos soundPos) {
            if (restoring || savedStates.isEmpty()) {
                return;
            }

            restoreQueue = new ArrayList<>(savedStates.keySet());
            restoreQueue.sort(Comparator.comparingInt((BlockPos blockPos) -> blockPos.getY()));
            restoreIndex = 0;
            restoring = true;
            level.playSound(null, soundPos, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.5F, 1.15F);
        }

        private boolean tickRestore(ServerLevel level) {
            if (restoreQueue == null) {
                return true;
            }

            int end = Math.min(restoreIndex + ELEMENTAL_DOMAIN_RESTORE_PER_TICK, restoreQueue.size());
            for (int i = restoreIndex; i < end; i++) {
                BlockPos pos = restoreQueue.get(i);
                BlockState original = savedStates.get(pos);
                if (original == null) {
                    continue;
                }

                level.setBlock(pos, original, 18);
                CompoundTag nbt = savedNbt.get(pos);
                if (nbt != null) {
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null) {
                        blockEntity.load(nbt);
                        blockEntity.setChanged();
                    }
                }
            }

            restoreIndex = end;
            if (restoreIndex >= restoreQueue.size()) {
                savedStates.clear();
                savedNbt.clear();
                restoreQueue = null;
                return true;
            }
            return false;
        }

        private void forceDiscard(ServerLevel level) {
            restoreQueue = new ArrayList<>(savedStates.keySet());
            restoreIndex = 0;
            restoring = true;
            tickRestore(level);
            while (restoreQueue != null) {
                tickRestore(level);
            }
        }

        private boolean shouldSkipElementalReplacement(ServerLevel level, BlockPos pos, BlockState state) {
            if (!state.getFluidState().isEmpty()) {
                return true;
            }

            return false;
        }
    }

    public static void placeMark(LivingEntity caster, int spellLevel, net.minecraft.core.BlockPos blockPos,
            net.minecraft.core.Direction face) {
        CompoundTag data = caster.getPersistentData();
        Vec3 center = Vec3.atCenterOf(blockPos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.501D));
        data.putBoolean(TAG_MARK_ACTIVE, true);
        data.putDouble(TAG_MARK_X, center.x);
        data.putDouble(TAG_MARK_Y, center.y);
        data.putDouble(TAG_MARK_Z, center.z);
        data.putLong(TAG_MARK_END, caster.level().getGameTime() + getMarkDurationSeconds(spellLevel) * 20L);
        data.putInt(TAG_MARK_LEVEL, spellLevel);
        data.put(TAG_MARK_TRIGGERED, new ListTag());
    }

    public static void clearMark(CompoundTag data) {
        clear(data, TAG_MARK_ACTIVE, TAG_MARK_X, TAG_MARK_Y, TAG_MARK_Z, TAG_MARK_END, TAG_MARK_LEVEL, TAG_MARK_TRIGGERED);
    }

    public static Vec3 getMarkCenter(CompoundTag data) {
        return new Vec3(data.getDouble(TAG_MARK_X), data.getDouble(TAG_MARK_Y), data.getDouble(TAG_MARK_Z));
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
        return !stack.isEmpty() && stack.getItem() instanceof SwordItem;
    }

    public static Mob findDominanceMobTarget(LivingEntity caster, double range, double minDot) {
        LivingEntity target = findTargetInSight(caster, range, minDot);
        return target instanceof Mob mob ? mob : null;
    }

    public static boolean addDominatedMob(LivingEntity caster, Mob mob, int spellLevel, int maxControlled) {
        if (mob.getMaxHealth() > DOMINANCE_MAX_HEALTH_LIMIT) {
            return false;
        }

        CompoundTag data = caster.getPersistentData();
        ListTag list = getDominatedMobList(data);
        String uuid = mob.getUUID().toString();
        for (int i = 0; i < list.size(); i++) {
            if (uuid.equals(list.getString(i))) {
                data.putBoolean(TAG_DOMINANCE_LINK_ACTIVE, true);
                tagDominatedMob(mob, caster.getUUID(), spellLevel);
                return true;
            }
        }

        if (list.size() >= maxControlled) {
            return false;
        }

        list.add(StringTag.valueOf(uuid));
        data.put(TAG_DOMINANCE_MOBS, list);
        data.putBoolean(TAG_DOMINANCE_LINK_ACTIVE, true);
        tagDominatedMob(mob, caster.getUUID(), spellLevel);
        return true;
    }

    public static void setDominanceTargetPlayer(ServerPlayer caster, UUID targetPlayerId) {
        if (targetPlayerId.equals(caster.getUUID())) {
            return;
        }

        Player target = caster.serverLevel().getPlayerByUUID(targetPlayerId);
        if (target == null) {
            return;
        }

        caster.getPersistentData().putUUID(TAG_DOMINANCE_TARGET_PLAYER, targetPlayerId);
        retargetDominatedMobs(caster, target);
        caster.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.corpse_campus.dominance_target_set", target.getDisplayName()), true);
    }

    public static void tickDominance(Player player) {
        CompoundTag data = player.getPersistentData();
        List<Mob> dominatedMobs = getDominatedMobs(player);

        if (dominatedMobs.isEmpty()) {
            clear(data, TAG_DOMINANCE_MOBS, TAG_DOMINANCE_TARGET_PLAYER);
            if (data.getBoolean(TAG_DOMINANCE_LINK_ACTIVE) && player.isAlive()) {
                data.remove(TAG_DOMINANCE_LINK_ACTIVE);
                float damage = Math.max(0.0F, player.getHealth() - DOMINANCE_MIN_SURVIVAL_HEALTH);
                if (damage > 0.0F) {
                    player.hurt(player.damageSources().magic(), damage);
                }
            }
            return;
        }

        data.putBoolean(TAG_DOMINANCE_LINK_ACTIVE, true);
        LivingEntity forcedTarget = null;
        if (data.hasUUID(TAG_DOMINANCE_TARGET_PLAYER) && player.level() instanceof ServerLevel serverLevel) {
            forcedTarget = serverLevel.getPlayerByUUID(data.getUUID(TAG_DOMINANCE_TARGET_PLAYER));
            if (forcedTarget == null || !forcedTarget.isAlive()) {
                data.remove(TAG_DOMINANCE_TARGET_PLAYER);
            }
        }

        for (Mob mob : dominatedMobs) {
            tagDominatedMob(mob, player.getUUID(), 1);
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
            if (forcedTarget != null && mob.getTarget() != forcedTarget) {
                mob.setTarget(forcedTarget);
            }
        }
    }

    public static void retargetDominatedMobs(Player player, LivingEntity target) {
        for (Mob mob : getDominatedMobs(player)) {
            if (target != player) {
                mob.setTarget(target);
            }
        }
    }

    public static boolean isDominatedBy(Mob mob, Player owner) {
        CompoundTag tag = mob.getPersistentData();
        return tag.hasUUID(TAG_DOMINANCE_OWNER) && owner.getUUID().equals(tag.getUUID(TAG_DOMINANCE_OWNER));
    }

    public static List<Mob> getDominatedMobs(Player player) {
        CompoundTag data = player.getPersistentData();
        ListTag list = getDominatedMobList(data);
        ListTag cleaned = new ListTag();
        java.util.List<Mob> mobs = new java.util.ArrayList<>();

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return java.util.List.of();
        }

        for (int i = 0; i < list.size(); i++) {
            String uuidString = list.getString(i);
            try {
                Entity entity = serverLevel.getEntity(UUID.fromString(uuidString));
                if (entity instanceof Mob mob && mob.isAlive()) {
                    mobs.add(mob);
                    cleaned.add(StringTag.valueOf(uuidString));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        data.put(TAG_DOMINANCE_MOBS, cleaned);
        return mobs;
    }

    private static ListTag getDominatedMobList(CompoundTag data) {
        return data.contains(TAG_DOMINANCE_MOBS, Tag.TAG_LIST)
                ? data.getList(TAG_DOMINANCE_MOBS, Tag.TAG_STRING)
                : new ListTag();
    }

    private static void tagDominatedMob(Mob mob, UUID casterId, int spellLevel) {
        CompoundTag tag = mob.getPersistentData();
        tag.putUUID(TAG_DOMINANCE_OWNER, casterId);
        tag.putInt(TAG_DOMINANCE_LEVEL, spellLevel);
    }

    public static boolean canExecutionerUse(ItemStack stack) {
        return isExecutionerWeapon(stack) && (!stack.isDamageableItem()
                || stack.getDamageValue() + EXECUTIONER_DURABILITY_COST < stack.getMaxDamage());
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
        if (!(target instanceof Player player) || !player.isUsingItem()) {
            resetExecutionerShieldPressure(target);
            return;
        }

        ItemStack usingItem = player.getUseItem();
        if (!(usingItem.getItem() instanceof ShieldItem)) {
            resetExecutionerShieldPressure(target);
            return;
        }

        CompoundTag data = target.getPersistentData();
        long gameTime = target.level().getGameTime();
        long lastTick = data.getLong(TAG_EXECUTIONER_BLOCK_LAST_TICK);
        int hits = gameTime - lastTick <= 100L ? data.getInt(TAG_EXECUTIONER_BLOCK_HITS) + 1 : 1;
        data.putLong(TAG_EXECUTIONER_BLOCK_LAST_TICK, gameTime);
        data.putInt(TAG_EXECUTIONER_BLOCK_HITS, hits);

        if (hits >= 15) {
            player.stopUsingItem();
            player.getCooldowns().addCooldown(usingItem.getItem(), 100);
            resetExecutionerShieldPressure(target);
        }
    }

    public static void resetExecutionerShieldPressure(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        data.remove(TAG_EXECUTIONER_BLOCK_HITS);
        data.remove(TAG_EXECUTIONER_BLOCK_LAST_TICK);
    }

    public static void tickExecutionerCast(Level level, LivingEntity caster, int spellLevel) {
        CompoundTag data = caster.getPersistentData();
        long gameTime = level.getGameTime();
        long interval = Math.max(4L, 8L - spellLevel);
        if (gameTime - data.getLong(TAG_EXECUTIONER_LAST_TICK) < interval) {
            return;
        }

        ItemStack weapon = caster.getMainHandItem();
        if (!canExecutionerUse(weapon)) {
            return;
        }

        data.putLong(TAG_EXECUTIONER_LAST_TICK, gameTime);
        float damage = getExecutionerDamage(caster, weapon);
        if (caster.isCrouching()) {
            performExecutionerGroundSlash(level, caster, spellLevel, damage);
        } else {
            performExecutionerWaveSlash(level, caster, spellLevel, damage);
        }

        if (caster instanceof Player player) {
            player.swing(player.getUsedItemHand(), true);
        }

        if (weapon.isDamageableItem()) {
            weapon.hurtAndBreak(EXECUTIONER_DURABILITY_COST, caster, broken -> broken.broadcastBreakEvent(caster.getUsedItemHand()));
        }
    }

    private static float getExecutionerDamage(LivingEntity caster, ItemStack weapon) {
        float base = 4.0F;
        if (weapon.getItem() instanceof SwordItem swordItem) {
            base = swordItem.getDamage();
        }
        return Math.max(1.0F, base * EXECUTIONER_DAMAGE_RATIO + (float) caster.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.1F);
    }

    private static void performExecutionerWaveSlash(Level level, LivingEntity caster, int spellLevel, float damage) {
        Vec3 start = caster.getEyePosition().add(caster.getLookAngle().scale(0.7D));
        Vec3 direction = caster.getLookAngle().normalize();
        double range = 6.0D + spellLevel * 1.25D;
        BlockHitResult blockHitResult = level.clip(new net.minecraft.world.level.ClipContext(start,
                start.add(direction.scale(range)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                caster));
        double actualRange = blockHitResult.getType() == HitResult.Type.BLOCK
                ? Math.max(1.0D, start.distanceTo(blockHitResult.getLocation()))
                : range;
        Vec3 end = start.add(direction.scale(actualRange));
        AABB hitBox = new AABB(start, end).inflate(1.0D + spellLevel * 0.08D);

        hitEntitiesInSlash(level, caster, hitBox, start, direction, actualRange, 0.58D, damage);
        spawnExecutionerTrail(level, start, end, false);
        level.playSound(null, caster.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                0.35F, 0.85F + spellLevel * 0.03F);
    }

    private static void performExecutionerGroundSlash(Level level, LivingEntity caster, int spellLevel, float damage) {
        Vec3 start = caster.getEyePosition();
        Vec3 direction = caster.getLookAngle().normalize();
        double range = 5.0D + spellLevel;
        BlockHitResult blockHitResult = level.clip(new net.minecraft.world.level.ClipContext(start,
                start.add(direction.scale(range)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                caster));
        Vec3 center = blockHitResult.getType() == HitResult.Type.BLOCK
                ? blockHitResult.getLocation().subtract(direction.scale(0.25D))
                : start.add(direction.scale(range));
        double radius = 1.8D + spellLevel * 0.22D;
        AABB hitBox = new AABB(center, center).inflate(radius, 1.5D, radius);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitBox,
                target -> target != caster && target.isAlive())) {
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().mobAttack(caster), damage);
            incrementExecutionerShieldPressure(target);
        }

        spawnExecutionerBurst(level, center, radius);
        level.playSound(null, caster.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                0.18F, 1.5F);
    }

    private static void hitEntitiesInSlash(Level level, LivingEntity caster, AABB hitBox, Vec3 start, Vec3 direction,
            double range, double minDot, float damage) {
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitBox,
                target -> target != caster && target.isAlive())) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(start);
            double along = delta.dot(direction);
            if (along < 0.0D || along > range) {
                continue;
            }

            double dot = delta.lengthSqr() <= 1.0E-4D ? 1.0D : delta.normalize().dot(direction);
            if (dot < minDot) {
                continue;
            }
            
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().mobAttack(caster), damage);
            incrementExecutionerShieldPressure(target);
        }
    }

    private static void spawnExecutionerTrail(Level level, Vec3 start, Vec3 end, boolean dense) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 delta = end.subtract(start);
        int steps = dense ? 18 : 12;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 point = start.add(delta.scale(t));
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
            serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.78F, 0.08F, 0.08F), 0.8F),
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.04D,
                    0.04D,
                    0.04D,
                    0.0D);
        }
    }

    private static void spawnExecutionerBurst(Level level, Vec3 center, double radius) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int points = 18;
        for (int i = 0; i < points; i++) {
            float angle = (float) (Math.PI * 2.0D * i / points);
            double x = center.x + Mth.cos(angle) * radius;
            double z = center.z + Mth.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    x,
                    center.y + 0.1D,
                    z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }
        serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.85F, 0.1F, 0.1F), 1.15F),
                center.x,
                center.y + 0.1D,
                center.z,
                20,
                radius * 0.35D,
                0.15D,
                radius * 0.35D,
                0.0D);
    }
}

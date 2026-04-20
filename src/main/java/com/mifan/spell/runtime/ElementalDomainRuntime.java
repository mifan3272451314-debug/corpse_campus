package com.mifan.spell.runtime;

import com.mifan.spell.AbilityRuntime;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class ElementalDomainRuntime {
    private static final BlockState[] INNER_BLOCKS = {
            net.minecraft.world.level.block.Blocks.PRISMARINE.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.DARK_PRISMARINE.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.BLUE_ICE.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN.defaultBlockState()
    };
    private static final BlockState[] SHELL_BLOCKS = {
            net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState()
    };
    private static final Map<UUID, ElementalDomainState> ACTIVE_DOMAINS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> RESTORE_TICKS = new HashMap<>();
    private static final Random DOMAIN_RANDOM = new Random();

    private ElementalDomainRuntime() {
    }

    public static void clear(CompoundTag data) {
        AbilityRuntime.clear(data,
                AbilityRuntime.TAG_ELEMENTAL_DOMAIN_LAST_TICK,
                AbilityRuntime.TAG_ELEMENTAL_DOMAIN_START_TICK,
                AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_X,
                AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_Y,
                AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_Z,
                AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CLOSED);
    }

    public static void begin(ServerLevel level, Player caster, boolean closedDomain) {
        CompoundTag data = caster.getPersistentData();
        data.putLong(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_START_TICK, level.getGameTime());
        data.putLong(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_LAST_TICK, 0L);
        data.putDouble(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_X, caster.getX());
        data.putDouble(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_Y, caster.getY());
        data.putDouble(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_Z, caster.getZ());
        data.putBoolean(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CLOSED, closedDomain);

        ACTIVE_DOMAINS.compute(caster.getUUID(), (uuid, existing) -> {
            if (existing != null) {
                existing.forceDiscard(level);
            }
            return new ElementalDomainState(level.dimension(), new BlockPos(
                    Mth.floor(caster.getX()),
                    Mth.floor(caster.getY()),
                    Mth.floor(caster.getZ())), closedDomain);
        });
    }

    public static void end(ServerLevel level, Player caster) {
        ElementalDomainState state = ACTIVE_DOMAINS.get(caster.getUUID());
        if (state != null) {
            state.beginRestore(level, caster.blockPosition());
        }
    }

    public static Vec3 getCenter(Player caster) {
        CompoundTag data = caster.getPersistentData();
        return new Vec3(
                data.getDouble(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_X),
                data.getDouble(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_Y),
                data.getDouble(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_Z));
    }

    public static boolean hasCenter(CompoundTag data) {
        return data.contains(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_X)
                && data.contains(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_Y)
                && data.contains(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_Z);
    }

    public static void tickTerrain(ServerLevel level, Player caster, long gameTime) {
        ElementalDomainState state = ACTIVE_DOMAINS.get(caster.getUUID());
        if (state == null || state.restoring || state.dimension != level.dimension()) {
            return;
        }

        long startTick = caster.getPersistentData().getLong(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_START_TICK);
        int maxRadius = AbilityRuntime.getElementalistActiveRadius(caster.getPersistentData());
        float radius = Math.min(maxRadius, Math.max(0.0F, (gameTime - startTick) * 2.5F));
        state.replaceSphere(level, state.center, state.lastRadius, radius);
        state.lastRadius = radius;
        if (state.closedDomain && radius >= maxRadius && !state.shellBuilt) {
            state.buildShell(level, caster.blockPosition());
        }
    }

    public static void tickRestoration(ServerLevel level) {
        long gameTime = level.getGameTime();
        Long lastTick = RESTORE_TICKS.get(level.dimension());
        if (lastTick != null && lastTick == gameTime) {
            return;
        }
        RESTORE_TICKS.put(level.dimension(), gameTime);

        ACTIVE_DOMAINS.entrySet().removeIf(entry -> {
            ElementalDomainState state = entry.getValue();
            if (state.dimension != level.dimension() || !state.restoring) {
                return false;
            }
            return state.tickRestore(level);
        });
    }

    public static void triggerBurst(ServerLevel level, Player caster, LivingEntity target, int spellLevel) {
        boolean closedDomain = caster.getPersistentData().getBoolean(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CLOSED);
        int rapidCastCount = closedDomain ? 2 : 1;
        ElementalBurstOption burst = switch (level.random.nextInt(3)) {
            case 0 -> pickRandomFireBurst(level.random);
            case 1 -> pickRandomIceBurst(level.random);
            default -> new ElementalBurstOption(SpellRegistry.LIGHTNING_BOLT_SPELL.get(), ElementalSpellType.LIGHTNING, 1);
        };

        castRegisteredSpell(level, caster, target, spellLevel,
                burst.spell(), burst.type(), rapidCastCount * burst.castMultiplier());
    }

    public static boolean isVisualBlock(BlockState state) {
        return isDomainBlock(state);
    }

    private static ElementalBurstOption pickRandomFireBurst(RandomSource random) {
        return switch (random.nextInt(5)) {
            case 0 -> new ElementalBurstOption(SpellRegistry.FIREBOLT_SPELL.get(), ElementalSpellType.FIRE, 1);
            case 1 -> new ElementalBurstOption(SpellRegistry.FIREBALL_SPELL.get(), ElementalSpellType.FIRE, 1);
            case 2 -> new ElementalBurstOption(SpellRegistry.BLAZE_STORM_SPELL.get(), ElementalSpellType.FIRE, 1);
            case 3 -> new ElementalBurstOption(SpellRegistry.MAGMA_BOMB_SPELL.get(), ElementalSpellType.FIRE, 1);
            default -> new ElementalBurstOption(SpellRegistry.FIREFLY_SWARM_SPELL.get(), ElementalSpellType.FIRE, 2);
        };
    }

    private static ElementalBurstOption pickRandomIceBurst(RandomSource random) {
        return switch (random.nextInt(2)) {
            case 0 -> new ElementalBurstOption(SpellRegistry.ICICLE_SPELL.get(), ElementalSpellType.WATER, 1);
            default -> new ElementalBurstOption(SpellRegistry.ICE_BLOCK_SPELL.get(), ElementalSpellType.WATER, 1);
        };
    }

    private static void castRegisteredSpell(ServerLevel level, Player caster, LivingEntity target, int spellLevel,
            AbstractSpell spell, ElementalSpellType type, int casts) {
        MagicData magicData = MagicData.getPlayerMagicData(caster);
        Vec3 originalPos = caster.position();
        float originalYRot = caster.getYRot();
        float originalXRot = caster.getXRot();

        int actualCasts = 0;
        for (int i = 0; i < casts; i++) {
            if (level.random.nextBoolean()) {
                continue;
            }

            actualCasts++;
            Vec3 castOrigin = getCastOrigin(caster, target, type, i);
            Vec3 aimTarget = target.getBoundingBox().getCenter().add(0.0D, target.getBbHeight() * 0.15D, 0.0D);
            Vec3 direction = aimTarget.subtract(castOrigin);
            if (direction.lengthSqr() < 1.0E-4D) {
                continue;
            }

            direction = direction.normalize();
            caster.teleportTo(castOrigin.x, castOrigin.y, castOrigin.z);
            float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            float pitch = (float) Mth.wrapDegrees(-Math.toDegrees(Math.atan2(direction.y,
                    Math.sqrt(direction.x * direction.x + direction.z * direction.z))));
            caster.setYRot(yaw);
            caster.setYHeadRot(yaw);
            caster.setYBodyRot(yaw);
            caster.setXRot(pitch);
            spell.onCast(level, spellLevel, caster, CastSource.NONE, magicData);
        }

        if (actualCasts == 0 && casts > 0) {
            Vec3 castOrigin = getCastOrigin(caster, target, type, 0);
            Vec3 aimTarget = target.getBoundingBox().getCenter().add(0.0D, target.getBbHeight() * 0.15D, 0.0D);
            Vec3 direction = aimTarget.subtract(castOrigin);
            if (direction.lengthSqr() >= 1.0E-4D) {
                direction = direction.normalize();
                caster.teleportTo(castOrigin.x, castOrigin.y, castOrigin.z);
                float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(-direction.x, direction.z)));
                float pitch = (float) Mth.wrapDegrees(-Math.toDegrees(Math.atan2(direction.y,
                        Math.sqrt(direction.x * direction.x + direction.z * direction.z))));
                caster.setYRot(yaw);
                caster.setYHeadRot(yaw);
                caster.setYBodyRot(yaw);
                caster.setXRot(pitch);
                spell.onCast(level, spellLevel, caster, CastSource.NONE, magicData);
            }
        }

        caster.teleportTo(originalPos.x, originalPos.y, originalPos.z);
        caster.setYRot(originalYRot);
        caster.setYHeadRot(originalYRot);
        caster.setYBodyRot(originalYRot);
        caster.setXRot(originalXRot);
    }

    private static Vec3 getCastOrigin(LivingEntity caster, LivingEntity target, ElementalSpellType type, int castIndex) {
        Vec3 base = target.getBoundingBox().getCenter();
        Vec3 fromCaster = base.subtract(caster.getEyePosition());
        Vec3 horizontal = new Vec3(fromCaster.x, 0.0D, fromCaster.z);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        }
        horizontal = horizontal.normalize();
        Vec3 side = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        double lateral = castIndex == 0 ? -0.45D : 0.45D;
        return switch (type) {
            case FIRE -> base.subtract(horizontal.scale(3.0D)).add(side.scale(lateral)).add(0.0D, 0.4D, 0.0D);
            case WATER -> base.subtract(horizontal.scale(3.3D)).add(side.scale(lateral * 0.85D)).add(0.0D, 0.55D, 0.0D);
            case LIGHTNING -> base.add(0.0D, 6.0D, 0.0D);
        };
    }

    private static BlockState pickReplaceBlock() {
        return INNER_BLOCKS[DOMAIN_RANDOM.nextInt(INNER_BLOCKS.length)];
    }

    private static BlockState pickShellBlock() {
        return SHELL_BLOCKS[DOMAIN_RANDOM.nextInt(SHELL_BLOCKS.length)];
    }

    private static boolean isDomainBlock(BlockState state) {
        for (BlockState blockState : INNER_BLOCKS) {
            if (blockState.getBlock() == state.getBlock()) {
                return true;
            }
        }
        for (BlockState blockState : SHELL_BLOCKS) {
            if (blockState.getBlock() == state.getBlock()) {
                return true;
            }
        }
        return false;
    }

    private enum ElementalSpellType {
        FIRE,
        WATER,
        LIGHTNING
    }

    private record ElementalBurstOption(AbstractSpell spell, ElementalSpellType type, int castMultiplier) {
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
                        || isDomainBlock(state)
                        || shouldSkipReplacement(level, pos, state)) {
                    continue;
                }

                savedStates.put(pos.immutable(), state);
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    savedNbt.put(pos.immutable(), blockEntity.saveWithFullMetadata());
                    level.removeBlockEntity(pos);
                }
                level.setBlock(pos, pickReplaceBlock(), 18);
            }
        }

        private void buildShell(ServerLevel level, BlockPos soundPos) {
            int domainRadius = closedDomain ? AbilityRuntime.getElementalistClosedRadius() : AbilityRuntime.getElementalistRadius();
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
                        || isDomainBlock(state)
                        || shouldSkipReplacement(level, pos, state)) {
                    continue;
                }

                savedStates.put(pos.immutable(), state);
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    savedNbt.put(pos.immutable(), blockEntity.saveWithFullMetadata());
                    level.removeBlockEntity(pos);
                }
                level.setBlock(pos, pickShellBlock(), 18);
            }

            shellBuilt = true;
            level.playSound(null, soundPos, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 2.2F, 0.55F);
        }

        private void beginRestore(ServerLevel level, BlockPos soundPos) {
            if (restoring || savedStates.isEmpty()) {
                return;
            }

            restoreQueue = new ArrayList<>(savedStates.keySet());
            restoreQueue.sort(Comparator.comparingInt(BlockPos::getY));
            restoreIndex = 0;
            restoring = true;
            level.playSound(null, soundPos, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.5F, 1.15F);
        }

        private boolean tickRestore(ServerLevel level) {
            if (restoreQueue == null) {
                return true;
            }

            int end = Math.min(restoreIndex + 384, restoreQueue.size());
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

        private boolean shouldSkipReplacement(ServerLevel level, BlockPos pos, BlockState state) {
            return !state.getFluidState().isEmpty();
        }
    }
}

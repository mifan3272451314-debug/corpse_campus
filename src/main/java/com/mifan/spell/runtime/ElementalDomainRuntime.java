package com.mifan.spell.runtime;

import com.mifan.registry.ModMobEffects;
import com.mifan.spell.AbilityRuntime;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class ElementalDomainRuntime {
    public static final float BATTLE_MANA_MULTIPLIER = 0.8F;
    private static final BlockState COAL_BLOCK = Blocks.COAL_BLOCK.defaultBlockState();
    private static final int BATTLE_CHECK_INTERVAL = 20;
    private static final int FLICKER_INTERVAL = 5;
    private static final int COAL_TICK_INTERVAL = 10;
    private static final float COAL_SPEED_BASE = 0.02F;
    private static final float COAL_SPEED_PER_DIFF = 0.02F;
    private static final float COAL_SPEED_MAX = 0.10F;
    private static final float FLICKER_FRACTION = 0.04F;
    private static final BlockState[] INNER_BLOCKS = {
            Blocks.PRISMARINE.defaultBlockState(),
            Blocks.DARK_PRISMARINE.defaultBlockState(),
            Blocks.BLUE_ICE.defaultBlockState(),
            Blocks.SEA_LANTERN.defaultBlockState(),
            Blocks.MAGMA_BLOCK.defaultBlockState(),
            Blocks.CRYING_OBSIDIAN.defaultBlockState()
    };
    private static final BlockState[] SHELL_BLOCKS = {
            Blocks.CRYING_OBSIDIAN.defaultBlockState(),
            Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
            Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),
            Blocks.RESPAWN_ANCHOR.defaultBlockState(),
            Blocks.OBSIDIAN.defaultBlockState()
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

    /**
     * 始终成功开启。若场上有对方领域重叠：
     * <ul>
     *   <li>自己更强 → 对方被标记 suppressed，煤炭化速率由等级差决定；</li>
     *   <li>自己更弱 → 自己立即被标记 suppressed，同样按等级差煤炭化；</li>
     *   <li>平手 → 双方进入对抗但都不煤炭化（只享受 80% 法力减免）。</li>
     * </ul>
     * 返回值保留为 boolean 以便未来扩展；当前始终返回 true。
     */
    public static boolean begin(ServerLevel level, Player caster, boolean closedDomain, int spellLevel) {
        int selfEffective = effectiveLevel(spellLevel, closedDomain);
        List<ElementalDomainState> opponents = new ArrayList<>();
        int selfRadius = closedDomain ? AbilityRuntime.getElementalistClosedRadius() : AbilityRuntime.getElementalistRadius();
        Vec3 selfCenter = caster.position();

        for (ElementalDomainState other : ACTIVE_DOMAINS.values()) {
            if (other.dimension != level.dimension()
                    || other.ownerUuid.equals(caster.getUUID())
                    || other.restoring) {
                continue;
            }
            int otherRadius = other.closedDomain
                    ? AbilityRuntime.getElementalistClosedRadius()
                    : AbilityRuntime.getElementalistRadius();
            double gap = selfCenter.distanceTo(Vec3.atCenterOf(other.center));
            if (gap > selfRadius + otherRadius + 1) {
                continue;
            }
            opponents.add(other);
        }

        CompoundTag data = caster.getPersistentData();
        data.putLong(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_START_TICK, level.getGameTime());
        data.putLong(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_LAST_TICK, 0L);
        data.putDouble(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_X, caster.getX());
        data.putDouble(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_Y, caster.getY());
        data.putDouble(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CENTER_Z, caster.getZ());
        data.putBoolean(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_CLOSED, closedDomain);

        ElementalDomainState newState = new ElementalDomainState(
                caster.getUUID(),
                level.dimension(),
                new BlockPos(Mth.floor(caster.getX()), Mth.floor(caster.getY()), Mth.floor(caster.getZ())),
                closedDomain,
                spellLevel);

        ElementalDomainState existing = ACTIVE_DOMAINS.get(caster.getUUID());
        if (existing != null) {
            existing.forceDiscard(level);
        }
        ACTIVE_DOMAINS.put(caster.getUUID(), newState);

        long now = level.getGameTime();
        for (ElementalDomainState other : opponents) {
            int otherEffective = effectiveLevel(other.spellLevel, other.closedDomain);
            int diff = selfEffective - otherEffective;
            newState.battleSince = now;
            other.battleSince = now;
            if (diff > 0) {
                other.suppressed = true;
                other.coalSpeed = computeCoalSpeed(diff);
                notifyBattleStart(level, caster, other, true);
            } else if (diff < 0) {
                newState.suppressed = true;
                newState.coalSpeed = computeCoalSpeed(-diff);
                notifyBattleStart(level, caster, other, false);
            } else {
                notifyBattleStalemate(level, caster, other);
            }
        }

        return true;
    }

    private static float computeCoalSpeed(int levelDiff) {
        int diff = Math.max(1, Math.abs(levelDiff));
        float speed = COAL_SPEED_BASE + COAL_SPEED_PER_DIFF * (diff - 1);
        return Math.min(COAL_SPEED_MAX, speed);
    }

    public static float getEffectivenessFactor(Player player) {
        if (player == null) {
            return 1.0F;
        }
        ElementalDomainState state = ACTIVE_DOMAINS.get(player.getUUID());
        if (state == null || !state.suppressed) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - state.coalProgress);
    }

    public static void end(ServerLevel level, Player caster) {
        ElementalDomainState state = ACTIVE_DOMAINS.get(caster.getUUID());
        if (state != null) {
            state.beginRestore(level, caster.blockPosition());
        }
        clearBattleFlagsInvolving(caster.getUUID());
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

    public static boolean isInBattle(Player player) {
        if (player == null) {
            return false;
        }
        ElementalDomainState state = ACTIVE_DOMAINS.get(player.getUUID());
        return state != null && state.battleSince >= 0L;
    }

    public static void tickTerrain(ServerLevel level, Player caster, long gameTime) {
        ElementalDomainState state = ACTIVE_DOMAINS.get(caster.getUUID());
        if (state == null || state.restoring || state.dimension != level.dimension()) {
            return;
        }

        if (gameTime % BATTLE_CHECK_INTERVAL == 0L) {
            reassessCollisions(level, caster, state, gameTime);
            state = ACTIVE_DOMAINS.get(caster.getUUID());
            if (state == null || state.restoring) {
                return;
            }
        }

        long startTick = caster.getPersistentData().getLong(AbilityRuntime.TAG_ELEMENTAL_DOMAIN_START_TICK);
        int maxRadius = AbilityRuntime.getElementalistActiveRadius(caster.getPersistentData());
        float radius = Math.min(maxRadius, Math.max(0.0F, (gameTime - startTick) * 2.5F));
        state.replaceSphere(level, state.center, state.lastRadius, radius);
        state.lastRadius = radius;
        if (state.closedDomain && radius >= maxRadius && !state.shellBuilt) {
            state.buildShell(level, caster.blockPosition());
        }

        if (state.battleSince >= 0L && gameTime % FLICKER_INTERVAL == 0L) {
            state.flickerBlocks(level);
        }

        if (state.suppressed && gameTime % COAL_TICK_INTERVAL == 0L) {
            boolean finished = state.tickCoal(level);
            if (finished) {
                crushOpponent(level, state, findVictor(state));
            }
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

    private static int effectiveLevel(int spellLevel, boolean closedDomain) {
        return spellLevel + (closedDomain ? 0 : 1);
    }

    private static void reassessCollisions(ServerLevel level, Player caster, ElementalDomainState self, long now) {
        int selfEffective = effectiveLevel(self.spellLevel, self.closedDomain);
        int selfRadius = self.closedDomain
                ? AbilityRuntime.getElementalistClosedRadius()
                : AbilityRuntime.getElementalistRadius();
        Vec3 selfCenter = caster.position();
        boolean anyContact = false;
        int worstDiffAgainstSelf = 0;

        for (ElementalDomainState other : new ArrayList<>(ACTIVE_DOMAINS.values())) {
            if (other == self || other.dimension != level.dimension() || other.restoring) {
                continue;
            }
            int otherRadius = other.closedDomain
                    ? AbilityRuntime.getElementalistClosedRadius()
                    : AbilityRuntime.getElementalistRadius();
            double gap = selfCenter.distanceTo(Vec3.atCenterOf(other.center));
            if (gap > selfRadius + otherRadius + 1) {
                continue;
            }

            anyContact = true;
            int otherEffective = effectiveLevel(other.spellLevel, other.closedDomain);
            int diff = selfEffective - otherEffective;
            self.battleSince = now;
            other.battleSince = now;
            if (diff > 0) {
                if (!other.suppressed) {
                    other.suppressed = true;
                    other.coalSpeed = computeCoalSpeed(diff);
                    notifyBattleStart(level, caster, other, true);
                } else {
                    other.coalSpeed = Math.max(other.coalSpeed, computeCoalSpeed(diff));
                }
            } else if (diff < 0) {
                int absDiff = -diff;
                if (absDiff > worstDiffAgainstSelf) {
                    worstDiffAgainstSelf = absDiff;
                }
            }
        }

        if (worstDiffAgainstSelf > 0) {
            float newSpeed = computeCoalSpeed(worstDiffAgainstSelf);
            if (!self.suppressed) {
                self.suppressed = true;
                self.coalSpeed = newSpeed;
            } else {
                self.coalSpeed = Math.max(self.coalSpeed, newSpeed);
            }
        }

        if (!anyContact) {
            self.battleSince = -1L;
            self.suppressed = false;
            self.coalProgress = 0.0F;
            self.coalSpeed = COAL_SPEED_BASE;
            self.recoverFromCoal(level);
        }
    }

    private static ElementalDomainState findVictor(ElementalDomainState loser) {
        int loserEffective = effectiveLevel(loser.spellLevel, loser.closedDomain);
        ElementalDomainState victor = null;
        int victorEffective = Integer.MIN_VALUE;
        for (ElementalDomainState other : ACTIVE_DOMAINS.values()) {
            if (other == loser || other.dimension != loser.dimension || other.restoring) {
                continue;
            }
            int otherEffective = effectiveLevel(other.spellLevel, other.closedDomain);
            if (otherEffective > loserEffective && otherEffective > victorEffective) {
                victor = other;
                victorEffective = otherEffective;
            }
        }
        return victor;
    }

    private static void crushOpponent(ServerLevel level, ElementalDomainState loser, ElementalDomainState victor) {
        if (loser.restoring) {
            return;
        }
        BlockPos soundPos = loser.center;
        loser.beginRestore(level, soundPos);
        // 不能立即从 ACTIVE_DOMAINS 移除：tickRestoration 必须扫到 loser 才能推进 restore 队列。
        // 等 tickRestore 跑完会在 removeIf 中自动剔除。
        clearBattleFlagsInvolving(loser.ownerUuid);

        level.sendParticles(ParticleTypes.EXPLOSION,
                soundPos.getX() + 0.5D, soundPos.getY() + 1.0D, soundPos.getZ() + 0.5D,
                18, 4.0D, 3.0D, 4.0D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                soundPos.getX() + 0.5D, soundPos.getY() + 2.0D, soundPos.getZ() + 0.5D,
                80, 6.0D, 4.0D, 6.0D, 0.02D);
        level.playSound(null, soundPos, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.6F);
        level.playSound(null, soundPos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.5F, 0.7F);

        ServerPlayer loserPlayer = level.getServer().getPlayerList().getPlayer(loser.ownerUuid);
        if (loserPlayer != null) {
            loserPlayer.removeEffect(ModMobEffects.ELEMENTAL_DOMAIN.get());
            clear(loserPlayer.getPersistentData());
            applyElementalistCooldown(loserPlayer);
            Component victorName = victor != null
                    ? resolvePlayerName(level, victor.ownerUuid)
                    : Component.translatable("message.corpse_campus.elementalist_domain_unknown_foe");
            loserPlayer.displayClientMessage(
                    Component.translatable("message.corpse_campus.elementalist_domain_crushed", victorName), true);
        }

        if (victor != null) {
            ServerPlayer victorPlayer = level.getServer().getPlayerList().getPlayer(victor.ownerUuid);
            if (victorPlayer != null) {
                Component loserName = resolvePlayerName(level, loser.ownerUuid);
                victorPlayer.displayClientMessage(
                        Component.translatable("message.corpse_campus.elementalist_crushed_opponent", loserName), true);
            }
        }
    }

    private static void applyElementalistCooldown(ServerPlayer player) {
        AbstractSpell spell = findElementalistSpell();
        if (spell == null) {
            return;
        }
        MagicData magicData = MagicData.getPlayerMagicData(player);
        magicData.getPlayerCooldowns().addCooldown(spell, AbilityRuntime.getElementalistCooldownTicks());
        magicData.getPlayerCooldowns().syncToPlayer(player);
    }

    private static AbstractSpell findElementalistSpell() {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "corpse_campus", "elementalist");
        return SpellRegistry.REGISTRY.get().getValue(id);
    }

    private static Component resolvePlayerName(ServerLevel level, UUID uuid) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
        if (player != null) {
            return player.getDisplayName();
        }
        return Component.literal(uuid.toString().substring(0, 8));
    }

    private static void notifyBattleStart(ServerLevel level, Player initiator, ElementalDomainState other,
            boolean initiatorIsStronger) {
        ServerPlayer otherPlayer = level.getServer().getPlayerList().getPlayer(other.ownerUuid);
        if (initiatorIsStronger) {
            if (otherPlayer != null) {
                otherPlayer.displayClientMessage(
                        Component.translatable("message.corpse_campus.elementalist_being_suppressed",
                                initiator.getDisplayName()),
                        true);
            }
            if (initiator instanceof ServerPlayer initiatorPlayer) {
                initiatorPlayer.displayClientMessage(
                        Component.translatable("message.corpse_campus.elementalist_suppressing",
                                resolvePlayerName(level, other.ownerUuid)),
                        true);
            }
        } else {
            if (otherPlayer != null) {
                otherPlayer.displayClientMessage(
                        Component.translatable("message.corpse_campus.elementalist_battle_stalemate"), true);
            }
            if (initiator instanceof ServerPlayer initiatorPlayer) {
                initiatorPlayer.displayClientMessage(
                        Component.translatable("message.corpse_campus.elementalist_battle_stalemate"), true);
            }
        }
    }

    private static void notifyBattleStalemate(ServerLevel level, Player initiator, ElementalDomainState other) {
        ServerPlayer otherPlayer = level.getServer().getPlayerList().getPlayer(other.ownerUuid);
        if (otherPlayer != null) {
            otherPlayer.displayClientMessage(
                    Component.translatable("message.corpse_campus.elementalist_battle_stalemate"), true);
        }
        if (initiator instanceof ServerPlayer initiatorPlayer) {
            initiatorPlayer.displayClientMessage(
                    Component.translatable("message.corpse_campus.elementalist_battle_stalemate"), true);
        }
    }

    private static void clearBattleFlagsInvolving(UUID leavingUuid) {
        Collection<ElementalDomainState> remaining = ACTIVE_DOMAINS.values();
        for (ElementalDomainState state : remaining) {
            if (state.ownerUuid.equals(leavingUuid)) {
                continue;
            }
            boolean stillAdjacent = false;
            for (ElementalDomainState other : remaining) {
                if (other == state || other.ownerUuid.equals(leavingUuid) || other.dimension != state.dimension
                        || other.restoring) {
                    continue;
                }
                int selfRadius = state.closedDomain
                        ? AbilityRuntime.getElementalistClosedRadius()
                        : AbilityRuntime.getElementalistRadius();
                int otherRadius = other.closedDomain
                        ? AbilityRuntime.getElementalistClosedRadius()
                        : AbilityRuntime.getElementalistRadius();
                double gap = Math.sqrt(state.center.distSqr(other.center));
                if (gap <= selfRadius + otherRadius + 1) {
                    stillAdjacent = true;
                    break;
                }
            }
            if (!stillAdjacent) {
                state.battleSince = -1L;
                state.suppressed = false;
                state.coalProgress = 0.0F;
            }
        }
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
        if (state.getBlock() == COAL_BLOCK.getBlock()) {
            return true;
        }
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
        private final UUID ownerUuid;
        private final ResourceKey<Level> dimension;
        private final BlockPos center;
        private final boolean closedDomain;
        private final int spellLevel;
        private final Map<BlockPos, BlockState> savedStates = new LinkedHashMap<>();
        private final Map<BlockPos, CompoundTag> savedNbt = new HashMap<>();
        private final java.util.Set<BlockPos> coalPositions = new java.util.HashSet<>();
        private List<BlockPos> restoreQueue;
        private int restoreIndex;
        private float lastRadius;
        private boolean shellBuilt;
        private boolean restoring;
        private boolean suppressed;
        private long battleSince = -1L;
        private float coalProgress;
        private float coalSpeed = COAL_SPEED_BASE;

        private ElementalDomainState(UUID ownerUuid, ResourceKey<Level> dimension, BlockPos center,
                boolean closedDomain, int spellLevel) {
            this.ownerUuid = ownerUuid;
            this.dimension = dimension;
            this.center = center.immutable();
            this.closedDomain = closedDomain;
            this.spellLevel = spellLevel;
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

        private void flickerBlocks(ServerLevel level) {
            if (savedStates.isEmpty()) {
                return;
            }
            List<BlockPos> candidates = new ArrayList<>(savedStates.keySet());
            int samples = Math.max(1, Mth.ceil(candidates.size() * FLICKER_FRACTION));
            RandomSource random = level.random;
            for (int i = 0; i < samples; i++) {
                BlockPos pos = candidates.get(random.nextInt(candidates.size()));
                if (coalPositions.contains(pos)) {
                    continue;
                }
                BlockState current = level.getBlockState(pos);
                if (!isDomainBlock(current)) {
                    continue;
                }
                BlockState next = suppressed && random.nextFloat() < 0.35F
                        ? COAL_BLOCK
                        : (random.nextFloat() < 0.15F ? pickShellBlock() : pickReplaceBlock());
                if (next == COAL_BLOCK) {
                    coalPositions.add(pos.immutable());
                }
                level.setBlock(pos, next, 18);
            }
        }

        private void recoverFromCoal(ServerLevel level) {
            if (coalPositions.isEmpty()) {
                return;
            }
            for (BlockPos pos : coalPositions) {
                BlockState current = level.getBlockState(pos);
                if (current.getBlock() == COAL_BLOCK.getBlock()) {
                    level.setBlock(pos, pickReplaceBlock(), 18);
                }
            }
            coalPositions.clear();
        }

        private boolean tickCoal(ServerLevel level) {
            if (savedStates.isEmpty()) {
                return true;
            }
            coalProgress = Math.min(1.0F, coalProgress + coalSpeed);
            int totalTarget = Mth.ceil(savedStates.size() * coalProgress);
            if (coalPositions.size() >= totalTarget) {
                return coalProgress >= 1.0F;
            }
            List<BlockPos> pool = new ArrayList<>();
            for (BlockPos pos : savedStates.keySet()) {
                if (!coalPositions.contains(pos)) {
                    pool.add(pos);
                }
            }
            int need = Math.min(pool.size(), totalTarget - coalPositions.size());
            RandomSource random = level.random;
            for (int i = 0; i < need; i++) {
                BlockPos pos = pool.remove(random.nextInt(pool.size()));
                BlockState current = level.getBlockState(pos);
                if (!isDomainBlock(current)) {
                    continue;
                }
                level.setBlock(pos, COAL_BLOCK, 18);
                coalPositions.add(pos.immutable());
            }
            return coalProgress >= 1.0F;
        }

        private void beginRestore(ServerLevel level, BlockPos soundPos) {
            if (restoring || savedStates.isEmpty()) {
                restoring = true;
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
                coalPositions.clear();
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

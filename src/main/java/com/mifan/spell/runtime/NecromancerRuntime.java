package com.mifan.spell.runtime;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.NecromancerSoulCountPacket;
import com.mifan.registry.ModSpells;
import com.mifan.spell.AbilityRuntime;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NecromancerRuntime {
    private NecromancerRuntime() {
    }

    public static boolean playerOwnsNecromancer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        ResourceLocation targetId = ModSpells.GREAT_NECROMANCER.getId();
        for (var slot : AnomalyBookService.getPlayerLoadedSpellSlots(serverPlayer)) {
            if (slot.getSpell() != null && targetId.equals(slot.getSpell().getSpellResource())) {
                return true;
            }
        }
        for (ItemStack stack : serverPlayer.getInventory().items) {
            if (bookContainsNecromancer(stack, targetId)) {
                return true;
            }
        }
        return bookContainsNecromancer(serverPlayer.getItemInHand(InteractionHand.MAIN_HAND), targetId)
                || bookContainsNecromancer(serverPlayer.getItemInHand(InteractionHand.OFF_HAND), targetId);
    }

    private static boolean bookContainsNecromancer(ItemStack stack, ResourceLocation targetId) {
        if (stack.isEmpty() || !(stack.getItem() instanceof SpellBook)) {
            return false;
        }
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null) {
            return false;
        }
        return container.getActiveSpells().stream()
                .anyMatch(slot -> slot.getSpell() != null && targetId.equals(slot.getSpell().getSpellResource()));
    }

    public static void onMonsterKilled(Player killer, LivingEntity dead) {
        if (killer == null || dead == null || killer == dead) {
            return;
        }
        if (killer instanceof ServerPlayer && !playerOwnsNecromancer(killer)) {
            return;
        }
        EntityType<?> type = dead.getType();
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (typeId == null) {
            return;
        }
        CompoundTag data = killer.getPersistentData();
        CompoundTag souls = data.getCompound(AbilityRuntime.TAG_NECROMANCER_SOULS);
        souls.putInt(typeId.toString(), souls.getInt(typeId.toString()) + 1);
        data.put(AbilityRuntime.TAG_NECROMANCER_SOULS, souls);
        data.putString(AbilityRuntime.TAG_NECROMANCER_LAST_KILL, typeId.toString());

        killer.level().playSound(null, killer.blockPosition(), SoundEvents.SOUL_ESCAPE,
                SoundSource.PLAYERS, 0.35F, 0.9F + killer.getRandom().nextFloat() * 0.2F);
        if (killer.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    dead.getX(), dead.getY() + dead.getBbHeight() * 0.5D, dead.getZ(),
                    6, 0.2D, 0.4D, 0.2D, 0.04D);
        }
        if (killer instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.corpse_campus.necromancer_soul_gained",
                    type.getDescription(), souls.getInt(typeId.toString())), true);
            syncSoulCount(serverPlayer);
        }
    }

    public static void syncSoulCount(ServerPlayer player) {
        ModNetwork.sendToPlayer(new NecromancerSoulCountPacket(getTotalSouls(player)), player);
    }

    public static int getSoulCount(Player player, ResourceLocation typeId) {
        if (typeId == null) {
            return 0;
        }
        CompoundTag souls = player.getPersistentData().getCompound(AbilityRuntime.TAG_NECROMANCER_SOULS);
        return souls.getInt(typeId.toString());
    }

    public static int getTotalSouls(Player player) {
        CompoundTag souls = player.getPersistentData().getCompound(AbilityRuntime.TAG_NECROMANCER_SOULS);
        int total = 0;
        for (String key : souls.getAllKeys()) {
            total += souls.getInt(key);
        }
        return total;
    }

    public static Map<ResourceLocation, Integer> collectSouls(Player player) {
        CompoundTag souls = player.getPersistentData().getCompound(AbilityRuntime.TAG_NECROMANCER_SOULS);
        Map<ResourceLocation, Integer> result = new HashMap<>();
        for (String key : souls.getAllKeys()) {
            int count = souls.getInt(key);
            if (count <= 0) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                result.put(id, count);
            }
        }
        return result;
    }

    public static ResourceLocation getLastKillType(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(AbilityRuntime.TAG_NECROMANCER_LAST_KILL)) {
            return null;
        }
        return ResourceLocation.tryParse(data.getString(AbilityRuntime.TAG_NECROMANCER_LAST_KILL));
    }

    public static boolean consumeSoul(Player player, ResourceLocation typeId) {
        if (typeId == null) {
            return false;
        }
        CompoundTag data = player.getPersistentData();
        CompoundTag souls = data.getCompound(AbilityRuntime.TAG_NECROMANCER_SOULS);
        int current = souls.getInt(typeId.toString());
        if (current <= 0) {
            return false;
        }
        if (current == 1) {
            souls.remove(typeId.toString());
        } else {
            souls.putInt(typeId.toString(), current - 1);
        }
        data.put(AbilityRuntime.TAG_NECROMANCER_SOULS, souls);
        return true;
    }

    public static SummonResult summon(ServerPlayer caster, ResourceLocation typeId, boolean forceEnhanced) {
        if (typeId == null) {
            return SummonResult.fail("message.corpse_campus.necromancer_no_soul");
        }
        if (getSoulCount(caster, typeId) <= 0) {
            return SummonResult.fail("message.corpse_campus.necromancer_no_soul");
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
        if (type == null) {
            consumeSoul(caster, typeId);
            return SummonResult.fail("message.corpse_campus.necromancer_type_missing");
        }

        boolean enhanced = forceEnhanced || caster.getRandom().nextFloat() < AbilityRuntime.NECROMANCER_BUFF_NATURAL_CHANCE;

        if (forceEnhanced) {
            MagicData magicData = MagicData.getPlayerMagicData(caster);
            if (magicData == null || magicData.getMana() + 1.0E-4F < AbilityRuntime.NECROMANCER_ENHANCE_MANA_COST) {
                return SummonResult.fail("message.corpse_campus.necromancer_no_mana");
            }
            magicData.setMana(magicData.getMana() - AbilityRuntime.NECROMANCER_ENHANCE_MANA_COST);
        }

        Entity created;
        try {
            created = type.create(caster.serverLevel());
        } catch (Exception ex) {
            consumeSoul(caster, typeId);
            return SummonResult.fail("message.corpse_campus.necromancer_type_missing");
        }

        if (!(created instanceof Mob mob)) {
            if (created != null) {
                created.discard();
            }
            consumeSoul(caster, typeId);
            return SummonResult.fail("message.corpse_campus.necromancer_type_missing");
        }

        if (!consumeSoul(caster, typeId)) {
            mob.discard();
            return SummonResult.fail("message.corpse_campus.necromancer_no_soul");
        }

        Vec3 spawnPos = pickSpawnPosition(caster);
        mob.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, caster.getYRot(), 0.0F);

        try {
            mob.finalizeSpawn(caster.serverLevel(),
                    caster.serverLevel().getCurrentDifficultyAt(BlockPos.containing(spawnPos)),
                    MobSpawnType.MOB_SUMMONED, null, null);
        } catch (Exception ignored) {
        }

        tagMinion(mob, caster, enhanced);
        if (enhanced) {
            applyEnhancement(mob, caster.level().getGameTime());
        }

        caster.serverLevel().addFreshEntity(mob);

        appendMinionUuid(caster, mob.getUUID());
        spawnSummonEffects(caster.serverLevel(), mob);
        syncSoulCount(caster);

        return SummonResult.success(enhanced, type);
    }

    private static Vec3 pickSpawnPosition(Player caster) {
        double offsetX = (caster.getRandom().nextDouble() - 0.5D) * 3.0D;
        double offsetZ = (caster.getRandom().nextDouble() - 0.5D) * 3.0D;
        double x = caster.getX() + offsetX;
        double z = caster.getZ() + offsetZ;
        double y = caster.getY();
        return new Vec3(x, y, z);
    }

    private static void tagMinion(Mob mob, Player owner, boolean enhanced) {
        CompoundTag tag = mob.getPersistentData();
        tag.putUUID(AbilityRuntime.TAG_NECROMANCER_OWNER, owner.getUUID());
        tag.putBoolean(AbilityRuntime.TAG_NECROMANCER_BUFFED, enhanced);
        mob.setPersistenceRequired();
    }

    private static void applyEnhancement(Mob mob, long gameTime) {
        AttributeInstance healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            AttributeModifier mod = new AttributeModifier(
                    UUID.fromString(AbilityRuntime.NECROMANCER_HEALTH_BUFF_UUID),
                    "corpse_campus_necromancer_buff_health",
                    AbilityRuntime.NECROMANCER_BUFF_HEALTH_MULT - 1.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL);
            if (!healthAttr.hasModifier(mod)) {
                healthAttr.addPermanentModifier(mod);
            }
            mob.setHealth(mob.getMaxHealth());
        }

        AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            AttributeModifier mod = new AttributeModifier(
                    UUID.fromString(AbilityRuntime.NECROMANCER_SPEED_BUFF_UUID),
                    "corpse_campus_necromancer_buff_speed",
                    AbilityRuntime.NECROMANCER_BUFF_SPEED_MULT - 1.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL);
            if (!speedAttr.hasModifier(mod)) {
                speedAttr.addPermanentModifier(mod);
            }
        }

        mob.addEffect(new MobEffectInstance(
                MobEffects.ABSORPTION,
                AbilityRuntime.NECROMANCER_BUFF_ABSORPTION_DURATION_TICKS,
                AbilityRuntime.NECROMANCER_BUFF_ABSORPTION_AMP,
                false, false, true));
        mob.getPersistentData().putLong(
                AbilityRuntime.TAG_NECROMANCER_BUFF_HEAL_EXPIRE,
                gameTime + AbilityRuntime.NECROMANCER_BUFF_ABSORPTION_DURATION_TICKS);
    }

    private static void spawnSummonEffects(ServerLevel level, Mob mob) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                mob.getX(), mob.getY() + mob.getBbHeight() * 0.6D, mob.getZ(),
                18, 0.35D, 0.55D, 0.35D, 0.03D);
        level.sendParticles(ParticleTypes.SMOKE,
                mob.getX(), mob.getY() + 0.2D, mob.getZ(),
                10, 0.3D, 0.2D, 0.3D, 0.02D);
        level.playSound(null, mob.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED,
                SoundSource.HOSTILE, 0.4F, 0.8F);
    }

    public static boolean isMinionOf(Mob mob, Player owner) {
        CompoundTag tag = mob.getPersistentData();
        return tag.hasUUID(AbilityRuntime.TAG_NECROMANCER_OWNER)
                && owner.getUUID().equals(tag.getUUID(AbilityRuntime.TAG_NECROMANCER_OWNER));
    }

    public static boolean isAnyNecromancerMinion(Entity entity) {
        return entity instanceof Mob mob
                && mob.getPersistentData().hasUUID(AbilityRuntime.TAG_NECROMANCER_OWNER);
    }

    public static UUID getMinionOwnerId(Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return null;
        }
        CompoundTag tag = mob.getPersistentData();
        if (!tag.hasUUID(AbilityRuntime.TAG_NECROMANCER_OWNER)) {
            return null;
        }
        return tag.getUUID(AbilityRuntime.TAG_NECROMANCER_OWNER);
    }

    private static void appendMinionUuid(Player owner, UUID mobUuid) {
        CompoundTag data = owner.getPersistentData();
        ListTag list = getMinionList(data);
        AbilityRuntime.appendUuid(list, mobUuid);
        data.put(AbilityRuntime.TAG_NECROMANCER_MINIONS, list);
    }

    private static ListTag getMinionList(CompoundTag data) {
        return data.contains(AbilityRuntime.TAG_NECROMANCER_MINIONS, Tag.TAG_LIST)
                ? data.getList(AbilityRuntime.TAG_NECROMANCER_MINIONS, Tag.TAG_STRING)
                : new ListTag();
    }

    public static List<Mob> getMinions(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        CompoundTag data = player.getPersistentData();
        ListTag list = getMinionList(data);
        ListTag cleaned = new ListTag();
        List<Mob> mobs = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String uuidString = list.getString(i);
            try {
                Entity entity = serverLevel.getEntity(UUID.fromString(uuidString));
                if (entity instanceof Mob mob && mob.isAlive() && isMinionOf(mob, player)) {
                    mobs.add(mob);
                    cleaned.add(StringTag.valueOf(uuidString));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        data.put(AbilityRuntime.TAG_NECROMANCER_MINIONS, cleaned);
        return mobs;
    }

    public static void tick(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        List<Mob> minions = getMinions(player);
        if (minions.isEmpty()) {
            return;
        }

        long gameTime = serverLevel.getGameTime();
        CompoundTag data = player.getPersistentData();
        LivingEntity focusedTarget = resolveFocusedTarget(serverLevel, data, gameTime);
        LivingEntity aggressor = findAggressorTargetingOwner(serverLevel, player);

        for (Mob mob : minions) {
            guardMinionAgainstOwner(mob, player);

            LivingEntity currentTarget = mob.getTarget();
            boolean currentValid = currentTarget != null && currentTarget.isAlive()
                    && !isInvalidMinionTarget(currentTarget, player);
            if (!currentValid) {
                mob.setTarget(null);
                currentTarget = null;
            }

            LivingEntity desired = null;
            if (focusedTarget != null && !isInvalidMinionTarget(focusedTarget, player)) {
                desired = focusedTarget;
            } else if (aggressor != null && !isInvalidMinionTarget(aggressor, player)) {
                desired = aggressor;
            }

            if (desired != null && currentTarget != desired) {
                mob.setTarget(desired);
            } else if (desired == null && currentTarget == null) {
                pacifyAndRecall(serverLevel, mob, player);
            }
        }
    }

    private static LivingEntity resolveFocusedTarget(ServerLevel level, CompoundTag data, long gameTime) {
        if (!data.hasUUID(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TARGET)) {
            return null;
        }
        long lastTick = data.getLong(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TICK);
        if (gameTime - lastTick > AbilityRuntime.NECROMANCER_ATTACK_FOCUS_TICKS) {
            data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TARGET);
            data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TICK);
            return null;
        }
        Entity candidate = level.getEntity(data.getUUID(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TARGET));
        if (candidate instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TARGET);
        data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TICK);
        return null;
    }

    private static LivingEntity findAggressorTargetingOwner(ServerLevel level, Player owner) {
        AABB area = owner.getBoundingBox().inflate(AbilityRuntime.NECROMANCER_PATROL_RADIUS);
        return level.getEntitiesOfClass(Mob.class, area,
                        candidate -> candidate.isAlive()
                                && candidate.getTarget() == owner
                                && !isMinionOf(candidate, owner))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static boolean isInvalidMinionTarget(LivingEntity candidate, Player owner) {
        if (candidate == owner) {
            return true;
        }
        if (candidate instanceof Player) {
            return true;
        }
        if (candidate instanceof Mob mob && isMinionOf(mob, owner)) {
            return true;
        }
        return !candidate.isAlive();
    }

    private static void guardMinionAgainstOwner(Mob mob, Player owner) {
        if (mob.getTarget() == owner) {
            mob.setTarget(null);
        }
        if (mob.getLastHurtByMob() == owner) {
            mob.setLastHurtByMob(null);
        }
    }

    private static void pacifyAndRecall(ServerLevel level, Mob mob, Player owner) {
        double distance = mob.distanceTo(owner);
        if (distance > AbilityRuntime.NECROMANCER_TELEPORT_RADIUS) {
            Vec3 recall = pickSpawnPosition(owner);
            mob.teleportTo(recall.x, recall.y, recall.z);
            level.sendParticles(ParticleTypes.PORTAL,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5D, mob.getZ(),
                    10, 0.3D, 0.5D, 0.3D, 0.08D);
            return;
        }
        if (distance > AbilityRuntime.NECROMANCER_PATROL_RADIUS) {
            mob.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), 1.1D);
        }
    }

    public static void onCasterAttackedTarget(Player caster, Entity target) {
        if (!(target instanceof LivingEntity living) || living == caster || living instanceof Player) {
            return;
        }
        CompoundTag data = caster.getPersistentData();
        data.putUUID(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TARGET, living.getUUID());
        data.putLong(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TICK, caster.level().getGameTime());
        for (Mob mob : getMinions(caster)) {
            mob.setTarget(living);
        }
    }

    public static void release(Player player) {
        for (Mob mob : getMinions(player)) {
            CompoundTag tag = mob.getPersistentData();
            tag.remove(AbilityRuntime.TAG_NECROMANCER_OWNER);
            tag.remove(AbilityRuntime.TAG_NECROMANCER_BUFFED);
            tag.remove(AbilityRuntime.TAG_NECROMANCER_BUFF_HEAL_EXPIRE);
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
        CompoundTag data = player.getPersistentData();
        data.remove(AbilityRuntime.TAG_NECROMANCER_MINIONS);
        data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TARGET);
        data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TICK);
    }

    public record SummonResult(boolean success, boolean enhanced, EntityType<?> type, String failKey) {
        public static SummonResult fail(String translationKey) {
            return new SummonResult(false, false, null, translationKey);
        }

        public static SummonResult success(boolean enhanced, EntityType<?> type) {
            return new SummonResult(true, enhanced, type, null);
        }
    }
}

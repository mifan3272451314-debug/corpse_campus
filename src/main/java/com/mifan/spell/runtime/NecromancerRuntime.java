package com.mifan.spell.runtime;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.NecromancerSoulCountPacket;
import com.mifan.network.clientbound.OpenNecromancerScreenPacket;
import com.mifan.network.clientbound.UpdateNecromancerScreenPacket;
import com.mifan.registry.ModSpells;
import com.mifan.spell.AbilityRuntime;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NecromancerRuntime {
    private NecromancerRuntime() {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static final class Session {
        private final int normalCost;
        private final int enhancedCost;
        private final int spellLevel;
        private boolean summonedAtLeastOnce;

        private Session(int normalCost, int enhancedCost, int spellLevel) {
            this.normalCost = normalCost;
            this.enhancedCost = enhancedCost;
            this.spellLevel = spellLevel;
        }

        public int normalCost() {
            return normalCost;
        }

        public int enhancedCost() {
            return enhancedCost;
        }

        public int spellLevel() {
            return spellLevel;
        }

        public boolean summonedAtLeastOnce() {
            return summonedAtLeastOnce;
        }
    }

    public static Session startSession(ServerPlayer player, int spellLevel, int normalCost, int enhancedCost) {
        Session session = new Session(normalCost, enhancedCost, spellLevel);
        SESSIONS.put(player.getUUID(), session);
        return session;
    }

    public static Session getSession(Player player) {
        return SESSIONS.get(player.getUUID());
    }

    public static void endSession(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.summonedAtLeastOnce) {
            applyCooldown(player);
        }
    }

    public static void discardSession(Player player) {
        SESSIONS.remove(player.getUUID());
    }

    public static void refundManaAndClearCooldown(ServerPlayer player, MagicData magicData, float manaAmount) {
        if (magicData != null && manaAmount > 0) {
            magicData.setMana(magicData.getMana() + manaAmount);
            io.redspace.ironsspellbooks.setup.PacketDistributor.sendToPlayer(player, new SyncManaPacket(magicData));
        }
        clearNecromancerCooldown(player);
    }

    public static void clearNecromancerCooldown(ServerPlayer player) {
        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (magicData == null) {
            return;
        }
        AbstractSpell spell = ModSpells.GREAT_NECROMANCER.get();
        if (magicData.getPlayerCooldowns().removeCooldown(spell.getSpellId())) {
            magicData.getPlayerCooldowns().syncToPlayer(player);
        }
    }

    public static void applyCooldown(ServerPlayer player) {
        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (magicData == null) {
            return;
        }
        AbstractSpell spell = ModSpells.GREAT_NECROMANCER.get();
        io.redspace.ironsspellbooks.api.magic.MagicHelper.MAGIC_MANAGER.addCooldown(player, spell, CastSource.SPELLBOOK);
    }

    public static boolean trySpendMana(ServerPlayer player, MagicData magicData, int amount) {
        if (magicData == null) {
            return amount <= 0;
        }
        if (amount <= 0) {
            return true;
        }
        if (magicData.getMana() + 1.0E-4F < amount) {
            return false;
        }
        magicData.setMana(Math.max(0.0F, magicData.getMana() - amount));
        io.redspace.ironsspellbooks.setup.PacketDistributor.sendToPlayer(player, new SyncManaPacket(magicData));
        return true;
    }

    public static void pushScreenUpdate(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        ModNetwork.sendToPlayer(buildUpdatePacket(player, session), player);
    }

    public static OpenNecromancerScreenPacket buildOpenPacket(ServerPlayer player, Session session) {
        List<OpenNecromancerScreenPacket.SoulEntry> entries = collectSoulEntries(player);
        MagicData magicData = MagicData.getPlayerMagicData(player);
        float mana = magicData == null ? 0.0F : magicData.getMana();
        return new OpenNecromancerScreenPacket(
                entries,
                mana,
                AbilityRuntime.NECROMANCER_ENHANCE_MANA_COST,
                mana + 1.0E-4F >= session.normalCost,
                mana + 1.0E-4F >= session.enhancedCost,
                getMode(player).toByte());
    }

    public static UpdateNecromancerScreenPacket buildUpdatePacket(ServerPlayer player, Session session) {
        List<OpenNecromancerScreenPacket.SoulEntry> entries = collectSoulEntries(player);
        MagicData magicData = MagicData.getPlayerMagicData(player);
        float mana = magicData == null ? 0.0F : magicData.getMana();
        return new UpdateNecromancerScreenPacket(
                entries,
                mana,
                AbilityRuntime.NECROMANCER_ENHANCE_MANA_COST,
                mana + 1.0E-4F >= session.normalCost,
                mana + 1.0E-4F >= session.enhancedCost,
                getMode(player).toByte());
    }

    private static List<OpenNecromancerScreenPacket.SoulEntry> collectSoulEntries(Player player) {
        Map<ResourceLocation, Integer> souls = collectSouls(player);
        List<OpenNecromancerScreenPacket.SoulEntry> entries = new ArrayList<>(souls.size());
        for (Map.Entry<ResourceLocation, Integer> entry : souls.entrySet()) {
            entries.add(new OpenNecromancerScreenPacket.SoulEntry(entry.getKey().toString(), entry.getValue()));
        }
        return entries;
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

    public static SummonResult summon(ServerPlayer caster, ResourceLocation typeId, EnhancementType enhancement,
            int manaCost) {
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

        MagicData magicData = MagicData.getPlayerMagicData(caster);
        if (manaCost > 0) {
            if (magicData == null || magicData.getMana() + 1.0E-4F < manaCost) {
                return SummonResult.fail("message.corpse_campus.necromancer_no_mana");
            }
        }

        EnhancementType resolved = enhancement != null ? enhancement : EnhancementType.NONE;
        if (resolved == EnhancementType.NONE
                && caster.getRandom().nextFloat() < AbilityRuntime.NECROMANCER_BUFF_NATURAL_CHANCE) {
            resolved = rollRandomEnhancement(caster);
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

        tagMinion(mob, caster, resolved);
        if (resolved.isEnhanced()) {
            applyEnhancement(mob, resolved, caster.level().getGameTime());
        }

        caster.serverLevel().addFreshEntity(mob);

        appendMinionUuid(caster, mob.getUUID());
        spawnSummonEffects(caster.serverLevel(), mob);
        syncSoulCount(caster);

        if (manaCost > 0 && magicData != null) {
            magicData.setMana(Math.max(0.0F, magicData.getMana() - manaCost));
            io.redspace.ironsspellbooks.setup.PacketDistributor.sendToPlayer(caster, new SyncManaPacket(magicData));
        }

        Session session = SESSIONS.get(caster.getUUID());
        if (session != null) {
            session.summonedAtLeastOnce = true;
        }

        return SummonResult.success(resolved, type);
    }

    private static EnhancementType rollRandomEnhancement(ServerPlayer caster) {
        EnhancementType[] pool = {
                EnhancementType.SPEED,
                EnhancementType.ATTACK,
                EnhancementType.DEFENSE,
                EnhancementType.HEALTH
        };
        return pool[caster.getRandom().nextInt(pool.length)];
    }

    private static Vec3 pickSpawnPosition(Player caster) {
        double offsetX = (caster.getRandom().nextDouble() - 0.5D) * 3.0D;
        double offsetZ = (caster.getRandom().nextDouble() - 0.5D) * 3.0D;
        double x = caster.getX() + offsetX;
        double z = caster.getZ() + offsetZ;
        double y = caster.getY();
        return new Vec3(x, y, z);
    }

    private static void tagMinion(Mob mob, Player owner, EnhancementType enhancement) {
        CompoundTag tag = mob.getPersistentData();
        tag.putUUID(AbilityRuntime.TAG_NECROMANCER_OWNER, owner.getUUID());
        tag.putBoolean(AbilityRuntime.TAG_NECROMANCER_BUFFED, enhancement.isEnhanced());
        tag.putByte(AbilityRuntime.TAG_NECROMANCER_ENHANCEMENT, enhancement.toByte());
        mob.setPersistenceRequired();
    }

    public static EnhancementType getEnhancement(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        if (!tag.contains(AbilityRuntime.TAG_NECROMANCER_ENHANCEMENT)) {
            return tag.getBoolean(AbilityRuntime.TAG_NECROMANCER_BUFFED)
                    ? EnhancementType.HEALTH
                    : EnhancementType.NONE;
        }
        return EnhancementType.fromByte(tag.getByte(AbilityRuntime.TAG_NECROMANCER_ENHANCEMENT));
    }

    private static void applyEnhancement(Mob mob, EnhancementType type, long gameTime) {
        switch (type) {
            case SPEED -> applySpeedEnhancement(mob);
            case ATTACK -> applyAttackEnhancement(mob);
            case DEFENSE -> applyDefenseEnhancement(mob);
            case HEALTH -> applyHealthEnhancement(mob, gameTime);
            default -> {
            }
        }
    }

    private static void applySpeedEnhancement(Mob mob) {
        AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            AttributeModifier mod = new AttributeModifier(
                    UUID.fromString(AbilityRuntime.NECROMANCER_SPEED_BUFF_UUID),
                    "corpse_campus_necromancer_type_speed",
                    AbilityRuntime.NECROMANCER_TYPE_SPEED_MULT - 1.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL);
            replaceModifier(speedAttr, mod);
        }
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1, false, false, true));
    }

    private static void applyAttackEnhancement(Mob mob) {
        AttributeInstance attackAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            AttributeModifier mod = new AttributeModifier(
                    UUID.fromString(AbilityRuntime.NECROMANCER_ATTACK_BUFF_UUID),
                    "corpse_campus_necromancer_type_attack",
                    AbilityRuntime.NECROMANCER_TYPE_ATTACK_MULT - 1.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL);
            replaceModifier(attackAttr, mod);
        }
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 1, false, false, true));
    }

    private static void applyDefenseEnhancement(Mob mob) {
        AttributeInstance armorAttr = mob.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            AttributeModifier mod = new AttributeModifier(
                    UUID.fromString(AbilityRuntime.NECROMANCER_ARMOR_BUFF_UUID),
                    "corpse_campus_necromancer_type_armor",
                    AbilityRuntime.NECROMANCER_TYPE_DEFENSE_ARMOR_ADD,
                    AttributeModifier.Operation.ADDITION);
            replaceModifier(armorAttr, mod);
        }
        AttributeInstance toughAttr = mob.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughAttr != null) {
            AttributeModifier mod = new AttributeModifier(
                    UUID.fromString(AbilityRuntime.NECROMANCER_ARMOR_TOUGH_UUID),
                    "corpse_campus_necromancer_type_toughness",
                    AbilityRuntime.NECROMANCER_TYPE_DEFENSE_TOUGH_ADD,
                    AttributeModifier.Operation.ADDITION);
            replaceModifier(toughAttr, mod);
        }
        AttributeInstance kbAttr = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) {
            AttributeModifier mod = new AttributeModifier(
                    UUID.fromString(AbilityRuntime.NECROMANCER_KB_RES_UUID),
                    "corpse_campus_necromancer_type_kb_resist",
                    AbilityRuntime.NECROMANCER_TYPE_DEFENSE_KB_RES,
                    AttributeModifier.Operation.ADDITION);
            replaceModifier(kbAttr, mod);
        }
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, true));
    }

    private static void applyHealthEnhancement(Mob mob, long gameTime) {
        AttributeInstance healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            AttributeModifier mod = new AttributeModifier(
                    UUID.fromString(AbilityRuntime.NECROMANCER_HEALTH_BUFF_UUID),
                    "corpse_campus_necromancer_type_health",
                    AbilityRuntime.NECROMANCER_TYPE_HEALTH_MULT - 1.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL);
            replaceModifier(healthAttr, mod);
            mob.setHealth(mob.getMaxHealth());
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

    private static void replaceModifier(AttributeInstance attr, AttributeModifier mod) {
        AttributeModifier existing = attr.getModifier(mod.getId());
        if (existing != null) {
            attr.removeModifier(existing);
        }
        attr.addPermanentModifier(mod);
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

    public static MinionMode getMode(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(AbilityRuntime.TAG_NECROMANCER_MODE)) {
            return MinionMode.FOLLOW;
        }
        return MinionMode.fromByte(data.getByte(AbilityRuntime.TAG_NECROMANCER_MODE));
    }

    public static void setMode(Player player, MinionMode mode) {
        CompoundTag data = player.getPersistentData();
        data.putByte(AbilityRuntime.TAG_NECROMANCER_MODE, mode.toByte());
        // 切模式时清掉指派/aggressor 旧状态，避免串味
        if (mode == MinionMode.RETREAT || mode == MinionMode.STAND) {
            data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TARGET);
            data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TICK);
            data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_ALLOW_PLAYER);
        }
        for (Mob mob : getMinions(player)) {
            if (mode == MinionMode.RETREAT || mode == MinionMode.STAND) {
                mob.setTarget(null);
                mob.setLastHurtByMob(null);
            }
        }
    }

    public static MinionMode cycleMode(Player player) {
        MinionMode next = getMode(player).cycleNext();
        setMode(player, next);
        return next;
    }

    public static boolean assignLookedTarget(ServerPlayer player) {
        LivingEntity target = pickLookedLiving(player);
        if (target == null) {
            player.displayClientMessage(
                    Component.translatable("message.corpse_campus.necromancer_assign_no_target"), true);
            return false;
        }
        if (target == player) {
            return false;
        }
        assignTarget(player, target);
        Component name = target.getDisplayName();
        player.displayClientMessage(
                Component.translatable("message.corpse_campus.necromancer_assign_set", name), true);
        return true;
    }

    public static void assignTarget(Player owner, LivingEntity target) {
        if (target == null || target == owner) {
            return;
        }
        CompoundTag data = owner.getPersistentData();
        data.putUUID(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TARGET, target.getUUID());
        data.putLong(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TICK, owner.level().getGameTime());
        data.putBoolean(AbilityRuntime.TAG_NECROMANCER_ASSIGN_ALLOW_PLAYER, target instanceof Player);
        for (Mob mob : getMinions(owner)) {
            mob.setTarget(target);
        }
    }

    public static void clearAssignedTarget(Player owner) {
        CompoundTag data = owner.getPersistentData();
        data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TARGET);
        data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TICK);
        data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_ALLOW_PLAYER);
        for (Mob mob : getMinions(owner)) {
            if (mob.getTarget() instanceof Player) {
                mob.setTarget(null);
            }
        }
    }

    private static LivingEntity pickLookedLiving(ServerPlayer player) {
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = AbilityRuntime.NECROMANCER_ASSIGN_PICK_RANGE;
        Vec3 end = eyes.add(look.scale(range));
        AABB search = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player.level(), player, eyes, end, search,
                e -> e.isAlive() && e != player
                        && (e instanceof LivingEntity living)
                        && !isMinionOrSame(living, player));
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            return null;
        }
        Entity target = hit.getEntity();
        return target instanceof LivingEntity living ? living : null;
    }

    private static boolean isMinionOrSame(LivingEntity candidate, Player owner) {
        if (candidate == owner) {
            return true;
        }
        if (candidate instanceof Mob mob && isMinionOf(mob, owner)) {
            return true;
        }
        return false;
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
        MinionMode mode = getMode(player);

        LivingEntity assigned = resolveAssignedTarget(serverLevel, data, gameTime);
        LivingEntity focusedTarget = assigned != null
                ? assigned
                : resolveFocusedTarget(serverLevel, data, gameTime);
        LivingEntity aggressor = resolveAggressor(serverLevel, data, gameTime, player);

        for (Mob mob : minions) {
            guardMinionAgainstOwner(mob, player);

            if (mode == MinionMode.RETREAT) {
                driveRetreat(serverLevel, mob, player);
                continue;
            }
            if (mode == MinionMode.STAND) {
                driveStand(mob, player);
                continue;
            }

            LivingEntity currentTarget = mob.getTarget();
            boolean currentValid = currentTarget != null && currentTarget.isAlive()
                    && !isInvalidMinionTarget(currentTarget, player, focusedTarget, aggressor);
            if (!currentValid) {
                mob.setTarget(null);
                currentTarget = null;
            }

            LivingEntity desired = pickDesiredTarget(mode, serverLevel, player, focusedTarget, aggressor);

            if (desired != null && currentTarget != desired) {
                mob.setTarget(desired);
            } else if (desired == null && currentTarget == null) {
                pacifyAndRecall(serverLevel, mob, player, mode);
            }
        }
    }

    private static LivingEntity pickDesiredTarget(MinionMode mode, ServerLevel level, Player owner,
            LivingEntity focusedTarget, LivingEntity aggressor) {
        if (focusedTarget != null) {
            return focusedTarget;
        }
        if (aggressor != null) {
            return aggressor;
        }
        if (mode == MinionMode.AGGRESSIVE) {
            return findAggressiveHuntTarget(level, owner);
        }
        return null;
    }

    private static LivingEntity findAggressiveHuntTarget(ServerLevel level, Player owner) {
        AABB area = owner.getBoundingBox().inflate(AbilityRuntime.NECROMANCER_AGGRESSIVE_RADIUS);
        LivingEntity best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != owner && !(e instanceof Mob m && isMinionOf(m, owner))
                        && !isAlly(e, owner))) {
            double d = candidate.distanceToSqr(owner);
            if (d < bestDistSqr) {
                bestDistSqr = d;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isAlly(LivingEntity candidate, Player owner) {
        if (candidate == owner) {
            return true;
        }
        if (candidate instanceof Player) {
            return false;
        }
        return owner.isAlliedTo(candidate);
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

    private static LivingEntity resolveAssignedTarget(ServerLevel level, CompoundTag data, long gameTime) {
        if (!data.hasUUID(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TARGET)) {
            return null;
        }
        long lastTick = data.getLong(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TICK);
        if (gameTime - lastTick > AbilityRuntime.NECROMANCER_ASSIGN_FOCUS_TICKS) {
            data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TARGET);
            data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TICK);
            data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_ALLOW_PLAYER);
            return null;
        }
        Entity candidate = level.getEntity(data.getUUID(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TARGET));
        if (candidate instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TARGET);
        data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TICK);
        data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_ALLOW_PLAYER);
        return null;
    }

    private static LivingEntity resolveAggressor(ServerLevel level, CompoundTag data, long gameTime, Player owner) {
        if (data.hasUUID(AbilityRuntime.TAG_NECROMANCER_LAST_AGGRESSOR)) {
            long lastTick = data.getLong(AbilityRuntime.TAG_NECROMANCER_LAST_AGGRESSOR_TICK);
            if (gameTime - lastTick <= AbilityRuntime.NECROMANCER_AGGRESSOR_FOCUS_TICKS) {
                Entity candidate = level.getEntity(data.getUUID(AbilityRuntime.TAG_NECROMANCER_LAST_AGGRESSOR));
                if (candidate instanceof LivingEntity living && living.isAlive() && living != owner) {
                    return living;
                }
            }
            data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_AGGRESSOR);
            data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_AGGRESSOR_TICK);
        }
        return findMobTargetingOwner(level, owner);
    }

    private static LivingEntity findMobTargetingOwner(ServerLevel level, Player owner) {
        AABB area = owner.getBoundingBox().inflate(AbilityRuntime.NECROMANCER_PATROL_RADIUS);
        return level.getEntitiesOfClass(Mob.class, area,
                        candidate -> candidate.isAlive()
                                && candidate.getTarget() == owner
                                && !isMinionOf(candidate, owner))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static boolean isInvalidMinionTarget(LivingEntity candidate, Player owner,
            LivingEntity focusedTarget, LivingEntity aggressor) {
        if (candidate == owner) {
            return true;
        }
        if (!candidate.isAlive()) {
            return true;
        }
        if (candidate instanceof Mob mob && isMinionOf(mob, owner)) {
            return true;
        }
        if (candidate instanceof Player) {
            // 仅当玩家是被指派目标 / aggressor 时允许
            return candidate != focusedTarget && candidate != aggressor;
        }
        return false;
    }

    private static void guardMinionAgainstOwner(Mob mob, Player owner) {
        if (mob.getTarget() == owner) {
            mob.setTarget(null);
        }
        if (mob.getLastHurtByMob() == owner) {
            mob.setLastHurtByMob(null);
        }
    }

    private static void pacifyAndRecall(ServerLevel level, Mob mob, Player owner, MinionMode mode) {
        double distance = mob.distanceTo(owner);
        double patrolRadius = mode == MinionMode.PATROL
                ? AbilityRuntime.NECROMANCER_PATROL_RADIUS
                : Math.min(AbilityRuntime.NECROMANCER_PATROL_RADIUS, 12.0D);
        if (distance > AbilityRuntime.NECROMANCER_TELEPORT_RADIUS) {
            Vec3 recall = pickSpawnPosition(owner);
            mob.teleportTo(recall.x, recall.y, recall.z);
            level.sendParticles(ParticleTypes.PORTAL,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5D, mob.getZ(),
                    10, 0.3D, 0.5D, 0.3D, 0.08D);
            return;
        }
        if (distance > patrolRadius) {
            mob.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), 1.1D);
        }
    }

    private static void driveStand(Mob mob, Player owner) {
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        if (!mob.getNavigation().isDone()) {
            mob.getNavigation().stop();
        }
    }

    private static void driveRetreat(ServerLevel level, Mob mob, Player owner) {
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        double distance = mob.distanceTo(owner);
        if (distance > AbilityRuntime.NECROMANCER_TELEPORT_RADIUS * 0.4D) {
            Vec3 recall = pickSpawnPosition(owner);
            mob.teleportTo(recall.x, recall.y, recall.z);
            level.sendParticles(ParticleTypes.PORTAL,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5D, mob.getZ(),
                    8, 0.3D, 0.5D, 0.3D, 0.06D);
            return;
        }
        if (distance > 3.0D) {
            mob.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), 1.25D);
        }
    }

    public static void onCasterAttackedTarget(Player caster, Entity target) {
        if (!(target instanceof LivingEntity living) || living == caster) {
            return;
        }
        CompoundTag data = caster.getPersistentData();
        data.putUUID(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TARGET, living.getUUID());
        data.putLong(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TICK, caster.level().getGameTime());
        for (Mob mob : getMinions(caster)) {
            mob.setTarget(living);
        }
    }

    public static void onOwnerAttackedBy(Player owner, LivingEntity attacker) {
        if (attacker == null || attacker == owner) {
            return;
        }
        MinionMode mode = getMode(owner);
        if (mode == MinionMode.RETREAT || mode == MinionMode.STAND) {
            return;
        }
        CompoundTag data = owner.getPersistentData();
        data.putUUID(AbilityRuntime.TAG_NECROMANCER_LAST_AGGRESSOR, attacker.getUUID());
        data.putLong(AbilityRuntime.TAG_NECROMANCER_LAST_AGGRESSOR_TICK, owner.level().getGameTime());
        for (Mob mob : getMinions(owner)) {
            if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
                mob.setTarget(attacker);
            }
        }
    }

    public static void release(Player player) {
        for (Mob mob : getMinions(player)) {
            CompoundTag tag = mob.getPersistentData();
            tag.remove(AbilityRuntime.TAG_NECROMANCER_OWNER);
            tag.remove(AbilityRuntime.TAG_NECROMANCER_BUFFED);
            tag.remove(AbilityRuntime.TAG_NECROMANCER_BUFF_HEAL_EXPIRE);
            tag.remove(AbilityRuntime.TAG_NECROMANCER_ENHANCEMENT);
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
        CompoundTag data = player.getPersistentData();
        data.remove(AbilityRuntime.TAG_NECROMANCER_MINIONS);
        data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TARGET);
        data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_ATTACK_TICK);
        data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_AGGRESSOR);
        data.remove(AbilityRuntime.TAG_NECROMANCER_LAST_AGGRESSOR_TICK);
        data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TARGET);
        data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_TICK);
        data.remove(AbilityRuntime.TAG_NECROMANCER_ASSIGN_ALLOW_PLAYER);
    }

    public record SummonResult(boolean success, EnhancementType enhancement, EntityType<?> type, String failKey) {
        public static SummonResult fail(String translationKey) {
            return new SummonResult(false, EnhancementType.NONE, null, translationKey);
        }

        public static SummonResult success(EnhancementType enhancement, EntityType<?> type) {
            return new SummonResult(true, enhancement, type, null);
        }

        public boolean enhanced() {
            return enhancement != null && enhancement.isEnhanced();
        }
    }
}

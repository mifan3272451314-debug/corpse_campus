package com.mifan.spell.rizhao;

import com.mifan.entity.GoldenCrowSunEntity;
import com.mifan.registry.ModSchools;
import com.mifan.spell.AbilityRuntime;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@AutoSpellConfig
public class GoldenCrowSunSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "golden_crow_sun");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(ModSchools.RIZHAO_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(AbilityRuntime.GOLDEN_CROW_COOLDOWN_SECONDS)
            .build();

    public GoldenCrowSunSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 60;
        this.baseManaCost = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.golden_crow_channel"),
                Component.translatable("tooltip.corpse_campus.golden_crow_summon"),
                Component.translatable("tooltip.corpse_campus.golden_crow_throw"),
                Component.translatable("tooltip.corpse_campus.golden_crow_damage",
                        AbilityRuntime.GOLDEN_CROW_DAMAGE_PER_MANA),
                Component.translatable("tooltip.corpse_campus.golden_crow_explosion_radius",
                        (int) AbilityRuntime.GOLDEN_CROW_EXPLOSION_RADIUS),
                Component.translatable("tooltip.corpse_campus.golden_crow_stun_radius",
                        (int) AbilityRuntime.GOLDEN_CROW_STUN_RADIUS,
                        AbilityRuntime.GOLDEN_CROW_STUN_DURATION_TICKS / 20),
                Component.translatable("tooltip.corpse_campus.golden_crow_duration",
                        AbilityRuntime.GOLDEN_CROW_DURATION_TICKS / 20 / 60),
                Component.translatable("tooltip.corpse_campus.golden_crow_mana_lock"),
                Component.translatable("tooltip.corpse_campus.golden_crow_daily"));
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.BEACON_POWER_SELECT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.GENERIC_EXPLODE);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (!level.isClientSide && entity instanceof Player player) {
            handleCast(level, player, playerMagicData);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void handleCast(Level level, Player player, MagicData magicData) {
        CompoundTag data = player.getPersistentData();

        GoldenCrowSunEntity existing = resolveExistingSun(level, data);
        if (existing != null) {
            if (!existing.isThrown()) {
                existing.throwTowards(player);
                player.displayClientMessage(
                        Component.translatable("message.corpse_campus.golden_crow_thrown"), true);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.corpse_campus.golden_crow_already_thrown"), true);
            }
            return;
        }

        long currentDay = level.getDayTime() / 24000L;
        if (data.contains(AbilityRuntime.TAG_GOLDEN_CROW_LAST_DAY)
                && data.getLong(AbilityRuntime.TAG_GOLDEN_CROW_LAST_DAY) == currentDay) {
            player.displayClientMessage(
                    Component.translatable("message.corpse_campus.golden_crow_daily_limit"), true);
            return;
        }

        float manaSpent = magicData != null ? magicData.getMana() : 0.0F;
        if (magicData != null) {
            magicData.setMana(0.0F);
        }
        player.getPersistentData().putFloat(AbilityRuntime.TAG_RIZHAO_LAST_MANA, 0.0F);

        GoldenCrowSunEntity sun = new GoldenCrowSunEntity(level, player, manaSpent);
        level.addFreshEntity(sun);

        data.putBoolean(AbilityRuntime.TAG_GOLDEN_CROW_ACTIVE, true);
        data.putUUID(AbilityRuntime.TAG_GOLDEN_CROW_ENTITY_UUID, sun.getUUID());
        data.putLong(AbilityRuntime.TAG_GOLDEN_CROW_EXPIRE_TICK,
                level.getGameTime() + AbilityRuntime.GOLDEN_CROW_DURATION_TICKS);
        data.putFloat(AbilityRuntime.TAG_GOLDEN_CROW_MANA_SPENT, manaSpent);
        data.putLong(AbilityRuntime.TAG_GOLDEN_CROW_LAST_DAY, currentDay);

        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                1.2F, 0.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                1.6F, 0.9F);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLASH,
                    player.getX(), player.getEyeY() + 2.0D, player.getZ(),
                    4, 0.2D, 0.2D, 0.2D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.LAVA,
                    player.getX(), player.getEyeY() + 2.0D, player.getZ(),
                    24, 0.8D, 0.8D, 0.8D, 0.05D);
        }

        player.displayClientMessage(
                Component.translatable("message.corpse_campus.golden_crow_summoned",
                        String.format("%.0f", manaSpent)),
                true);
    }

    private GoldenCrowSunEntity resolveExistingSun(Level level, CompoundTag data) {
        if (!data.getBoolean(AbilityRuntime.TAG_GOLDEN_CROW_ACTIVE)) {
            return null;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (!data.hasUUID(AbilityRuntime.TAG_GOLDEN_CROW_ENTITY_UUID)) {
            return null;
        }
        UUID uuid = data.getUUID(AbilityRuntime.TAG_GOLDEN_CROW_ENTITY_UUID);
        Entity entity = serverLevel.getEntity(uuid);
        if (entity instanceof GoldenCrowSunEntity sun && !sun.isRemoved()) {
            return sun;
        }
        return null;
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.RIZHAO.get();
    }
}

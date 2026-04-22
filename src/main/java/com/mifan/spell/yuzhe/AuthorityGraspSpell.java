package com.mifan.spell.yuzhe;

import com.mifan.registry.ModMobEffects;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class AuthorityGraspSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "authority_grasp");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(ModSchools.YUZHE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(AbilityRuntime.AUTHORITY_GRASP_COOLDOWN_SECONDS)
            .build();

    public AuthorityGraspSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        int durationSeconds = AbilityRuntime.AUTHORITY_GRASP_DURATION_TICKS / 20;
        return List.of(
                Component.translatable("tooltip.corpse_campus.authority_grasp_duration", durationSeconds / 60),
                Component.translatable("tooltip.corpse_campus.authority_grasp_drain"),
                Component.translatable("tooltip.corpse_campus.authority_grasp_touch",
                        (int) (AbilityRuntime.AUTHORITY_GRASP_PROXIMITY_RANGE * 10) / 10.0D),
                Component.translatable("tooltip.corpse_campus.authority_grasp_hurt_summon",
                        AbilityRuntime.AUTHORITY_GRASP_MAX_SUMMONS),
                Component.translatable("tooltip.corpse_campus.authority_grasp_cost_all_mana"),
                Component.translatable("tooltip.corpse_campus.authority_grasp_cooldown",
                        AbilityRuntime.AUTHORITY_GRASP_COOLDOWN_SECONDS / 60));
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
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
        return Optional.of(SoundEvents.WARDEN_EMERGE);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.WARDEN_SONIC_BOOM);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (!level.isClientSide) {
            long gameTime = level.getGameTime();
            long expireTick = gameTime + AbilityRuntime.AUTHORITY_GRASP_DURATION_TICKS;

            entity.addEffect(new MobEffectInstance(
                    ModMobEffects.AUTHORITY_GRASP_CASTER.get(),
                    AbilityRuntime.AUTHORITY_GRASP_DURATION_TICKS,
                    0,
                    false,
                    false,
                    true));

            CompoundTag data = entity.getPersistentData();
            data.putLong(AbilityRuntime.TAG_AUTHORITY_GRASP_EXPIRE_TICK, expireTick);
            data.putInt(AbilityRuntime.TAG_AUTHORITY_GRASP_SUMMON_COUNT, 0);

            if (playerMagicData != null) {
                playerMagicData.setMana(0.0F);
            }

            level.playSound(null, entity.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.0F, 0.7F);

            if (entity instanceof Player player) {
                player.displayClientMessage(
                        Component.translatable("message.corpse_campus.authority_grasp_on"),
                        true);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.YUZHE.get();
    }
}

package com.mifan.spell.yuzhe;

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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class MagneticClingSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "magnetic_cling");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(ModSchools.YUZHE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(16)
            .build();

    public MagneticClingSpell() {
        this.manaCostPerLevel = 4;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 18;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.duration_seconds", getActivationTicks(spellLevel) / 20),
                Component.translatable("tooltip.corpse_campus.wall_cling_seconds", 2),
                Component.translatable("tooltip.corpse_campus.shockwave_radius", getShockwaveRadius(spellLevel)));
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
        return Optional.of(SoundEvents.AMETHYST_CLUSTER_BREAK);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.IRON_GOLEM_ATTACK);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        AbilityRuntime.activateTimedState(
                entity,
                AbilityRuntime.TAG_MAGNETIC_END,
                AbilityRuntime.TAG_MAGNETIC_LEVEL,
                getActivationTicks(spellLevel),
                spellLevel);
        entity.getPersistentData().putBoolean(AbilityRuntime.TAG_MAGNETIC_LAST_GROUND, entity.onGround());
        entity.addEffect(new MobEffectInstance(MobEffects.JUMP, Math.min(120, getActivationTicks(spellLevel)), 0, false,
                false, true));
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.YUZHE.get();
    }

    private int getActivationTicks(int spellLevel) {
        return 100 + spellLevel * 20;
    }

    private int getShockwaveRadius(int spellLevel) {
        return 3 + spellLevel;
    }
}

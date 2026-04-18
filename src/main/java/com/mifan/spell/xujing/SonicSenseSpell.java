package com.mifan.spell.xujing;

import com.mifan.registry.ModSchools;
import com.mifan.spell.AbilityRuntime;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class SonicSenseSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "sonic_sense");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(ModSchools.XUJING_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(18)
            .build();

    public SonicSenseSpell() {
        this.manaCostPerLevel = 4;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 60;
        this.baseManaCost = 18;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.range_blocks", getRevealRange(spellLevel)),
                Component.translatable("tooltip.corpse_campus.duration_seconds", getDurationTicks(spellLevel) / 20));
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
        return Optional.of(SoundEvents.AMETHYST_CLUSTER_BREAK);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.AMETHYST_BLOCK_CHIME);
    }

    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData,
            boolean canceled) {
        if (!canceled) {
            AbilityRuntime.activateTimedState(
                    entity,
                    AbilityRuntime.TAG_SONIC_END,
                    AbilityRuntime.TAG_SONIC_LEVEL,
                    getDurationTicks(spellLevel),
                    spellLevel);
        }
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, canceled);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.XUJING.get();
    }

    private int getRevealRange(int spellLevel) {
        return 12 + spellLevel * 2;
    }

    private int getDurationTicks(int spellLevel) {
        return 120 + spellLevel * 40;
    }
}

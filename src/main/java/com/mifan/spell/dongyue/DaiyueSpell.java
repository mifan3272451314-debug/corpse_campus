package com.mifan.spell.dongyue;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class DaiyueSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "daiyue");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(ModSchools.DONGYUE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(10)
            .build();

    public DaiyueSpell() {
        this.manaCostPerLevel = 1;
        this.baseSpellPower = 2;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
        this.baseManaCost = 12;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.daiyue_dash_range",
                        AbilityRuntime.getDaiyueDashRange(spellLevel)),
                Component.translatable("tooltip.corpse_campus.daiyue_width",
                        AbilityRuntime.getDaiyueHitWidth(spellLevel)),
                Component.translatable("tooltip.corpse_campus.daiyue_path_attack"),
                Component.translatable("tooltip.corpse_campus.daiyue_sword_wave"));
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
        return Optional.of(SoundEvents.PLAYER_ATTACK_SWEEP);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (!level.isClientSide) {
            AbilityRuntime.castDaiyue(level, entity, spellLevel, getSpellPower(spellLevel, entity));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.DONGYUE.get();
    }
}

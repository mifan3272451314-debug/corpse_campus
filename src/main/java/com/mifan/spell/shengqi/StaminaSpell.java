package com.mifan.spell.shengqi;

import com.mifan.registry.ModMobEffects;
import com.mifan.registry.ModSchools;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class StaminaSpell extends AbstractSpell {
    private static final int DURATION_SECONDS = 60;
    private static final int COOLDOWN_SECONDS = 20 * 60;

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "stamina");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(ModSchools.SHENGQI_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(COOLDOWN_SECONDS)
            .build();

    public StaminaSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 100;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.duration_seconds", DURATION_SECONDS),
                Component.translatable("tooltip.corpse_campus.stamina_speed_boost"),
                Component.translatable("tooltip.corpse_campus.stamina_hunger_efficiency"),
                Component.translatable("tooltip.corpse_campus.stamina_durability_efficiency"));
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
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (!level.isClientSide) {
            entity.addEffect(new MobEffectInstance(
                    ModMobEffects.STAMINA.get(),
                    DURATION_SECONDS * 20,
                    spellLevel - 1,
                    false,
                    true,
                    true));
            level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                    0.4F, 1.25F);

            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.corpse_campus.stamina_on"), true);
            }
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.SHENGQI.get();
    }
}

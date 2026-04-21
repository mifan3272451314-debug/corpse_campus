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
public class LifeThiefSpell extends AbstractSpell {
    private static final int BIND_RANGE = 12;
    public static final float REDIRECT_MANA_COST = 5.0F;

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "life_thief");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(ModSchools.YUZHE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(0)
            .build();

    public LifeThiefSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.range_blocks", BIND_RANGE),
                Component.translatable("tooltip.corpse_campus.life_thief_random_nearby"),
                Component.translatable("tooltip.corpse_campus.life_thief_redirect"),
                Component.translatable("tooltip.corpse_campus.life_thief_redirect_mana", (int) REDIRECT_MANA_COST),
                Component.translatable("tooltip.corpse_campus.life_thief_non_lethal"),
                Component.translatable("tooltip.corpse_campus.toggle_cast"));
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
            boolean enabling = !entity.hasEffect(ModMobEffects.LIFE_THIEF.get());
            if (enabling) {
                entity.addEffect(new MobEffectInstance(
                        ModMobEffects.LIFE_THIEF.get(),
                        AbilityRuntime.TOGGLE_DURATION_TICKS,
                        spellLevel - 1,
                        false,
                        false,
                        false));
            } else {
                entity.removeEffect(ModMobEffects.LIFE_THIEF.get());
                AbilityRuntime.clearLifeThief(entity.getPersistentData());
            }

            level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                    0.22F, enabling ? 0.86F : 1.22F);

            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable(
                        enabling ? "message.corpse_campus.life_thief_on" : "message.corpse_campus.life_thief_off"),
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

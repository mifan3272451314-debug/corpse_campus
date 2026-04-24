package com.mifan.spell.xujing;

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
public class ElementalistSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "elementalist");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(ModSchools.XUJING_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(80)
            .build();

    public ElementalistSpell() {
        this.manaCostPerLevel = 3;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 10;
        this.baseManaCost = 30;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.elementalist_domain_radius", AbilityRuntime.getElementalistRadius()),
                Component.translatable("tooltip.corpse_campus.elementalist_closed_domain_radius", AbilityRuntime.getElementalistClosedRadius()),
                Component.translatable("tooltip.corpse_campus.elementalist_mana_drain", AbilityRuntime.getElementalistManaDrain(spellLevel)),
                Component.translatable("tooltip.corpse_campus.elementalist_random_barrage"),
                Component.translatable("tooltip.corpse_campus.elementalist_crouch_closed_domain"),
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
            boolean enabling = !entity.hasEffect(ModMobEffects.ELEMENTAL_DOMAIN.get());
            if (enabling) {
                entity.addEffect(new MobEffectInstance(
                        ModMobEffects.ELEMENTAL_DOMAIN.get(),
                        AbilityRuntime.TOGGLE_DURATION_TICKS,
                        spellLevel - 1,
                        false,
                        false,
                        false));
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && entity instanceof Player player) {
                    AbilityRuntime.beginElementalDomain(serverLevel, player, entity.isCrouching(), spellLevel);
                }
            } else {
                entity.removeEffect(ModMobEffects.ELEMENTAL_DOMAIN.get());
                AbilityRuntime.clearElementalDomain(entity.getPersistentData());
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && entity instanceof Player player) {
                    AbilityRuntime.endElementalDomain(serverLevel, player);
                }
            }

            level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                    0.28F, enabling ? 1.15F : 0.8F);

            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable(
                        enabling ? "message.corpse_campus.elementalist_on" : "message.corpse_campus.elementalist_off"), true);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.XUJING.get();
    }
}

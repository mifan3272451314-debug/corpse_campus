package com.mifan.spell.rizhao;

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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class LightPrayerSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "light_prayer");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(ModSchools.RIZHAO_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(300)
            .build();

    public LightPrayerSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.light_prayer_duration",
                        AbilityRuntime.LIGHT_PRAYER_DURATION_TICKS / 20),
                Component.translatable("tooltip.corpse_campus.light_prayer_radius",
                        (int) AbilityRuntime.LIGHT_PRAYER_RADIUS),
                Component.translatable("tooltip.corpse_campus.light_prayer_burn",
                        AbilityRuntime.LIGHT_PRAYER_BURN_DAMAGE_PER_SECOND),
                Component.translatable("tooltip.corpse_campus.light_prayer_monster_bonus",
                        (int) ((AbilityRuntime.LIGHT_PRAYER_MONSTER_BONUS_MULTIPLIER - 1.0F) * 100)),
                Component.translatable("tooltip.corpse_campus.light_prayer_double_resist"),
                Component.translatable("tooltip.corpse_campus.light_prayer_weapon_fire"),
                Component.translatable("tooltip.corpse_campus.light_prayer_drain_all_energy"));
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
        return Optional.of(SoundEvents.BEACON_ACTIVATE);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (!level.isClientSide) {
            int duration = AbilityRuntime.LIGHT_PRAYER_DURATION_TICKS;

            entity.addEffect(new MobEffectInstance(
                    ModMobEffects.LIGHT_PRAYER.get(),
                    duration,
                    Math.max(0, spellLevel - 1),
                    false,
                    false,
                    true));

            entity.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE,
                    duration,
                    AbilityRuntime.LIGHT_PRAYER_RESISTANCE_AMPLIFIER,
                    false,
                    false,
                    true));

            if (playerMagicData != null) {
                playerMagicData.setMana(0.0F);
            }
            if (entity instanceof Player caster) {
                caster.getPersistentData().putFloat(AbilityRuntime.TAG_RIZHAO_LAST_MANA, 0.0F);
            }

            level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                    0.5F, 1.35F);
            level.playSound(null, entity.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                    0.6F, 1.2F);

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.FLASH,
                        entity.getX(),
                        entity.getY() + entity.getBbHeight() * 0.5D,
                        entity.getZ(),
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        entity.getX(),
                        entity.getY() + 0.2D,
                        entity.getZ(),
                        24, 0.6D, 0.2D, 0.6D, 0.02D);
            }

            if (entity instanceof Player player) {
                player.displayClientMessage(
                        Component.translatable("message.corpse_campus.light_prayer_on"), true);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.RIZHAO.get();
    }
}

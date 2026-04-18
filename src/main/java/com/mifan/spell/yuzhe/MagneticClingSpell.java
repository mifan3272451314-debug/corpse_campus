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
public class MagneticClingSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "magnetic_cling");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(ModSchools.YUZHE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(0)
            .build();

    public MagneticClingSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.wall_cling_seconds", 2),
                Component.translatable("tooltip.corpse_campus.shockwave_radius", getShockwaveRadius(spellLevel)),
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
            boolean enabling = !entity.hasEffect(ModMobEffects.MAGNETIC_CLING.get());
            if (enabling) {
                entity.addEffect(new MobEffectInstance(
                        ModMobEffects.MAGNETIC_CLING.get(),
                        AbilityRuntime.TOGGLE_DURATION_TICKS,
                        spellLevel - 1,
                        false,
                        false,
                        false));
                entity.getPersistentData().putBoolean(AbilityRuntime.TAG_MAGNETIC_LAST_GROUND, entity.onGround());
            } else {
                entity.removeEffect(ModMobEffects.MAGNETIC_CLING.get());
            }

            level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS,
                    0.18F, enabling ? 0.85F : 1.25F);

            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable(
                        enabling ? "message.corpse_campus.magnetic_on" : "message.corpse_campus.magnetic_off"), true);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.YUZHE.get();
    }

    private int getShockwaveRadius(int spellLevel) {
        return 3 + spellLevel;
    }
}

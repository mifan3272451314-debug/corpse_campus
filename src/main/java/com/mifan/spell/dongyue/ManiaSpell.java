package com.mifan.spell.dongyue;

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
public class ManiaSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "mania");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(ModSchools.DONGYUE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(0)
            .build();

    public ManiaSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.auto_lunge_range", getAutoLungeRange(spellLevel)),
                Component.translatable("tooltip.corpse_campus.melee_bonus_percent", getBonusDamagePercent()),
                Component.translatable("tooltip.corpse_campus.mania_forces_front_attack"),
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
            boolean enabling = !entity.hasEffect(ModMobEffects.MANIA.get());
            if (enabling) {
                entity.addEffect(new MobEffectInstance(
                        ModMobEffects.MANIA.get(),
                        AbilityRuntime.TOGGLE_DURATION_TICKS,
                        spellLevel - 1,
                        false,
                        false,
                        false));
            } else {
                entity.removeEffect(ModMobEffects.MANIA.get());
                AbilityRuntime.clear(entity.getPersistentData(),
                        AbilityRuntime.TAG_MANIA_LAST_PROC,
                        AbilityRuntime.TAG_MANIA_LAST_SWING);
            }

            level.playSound(null, entity.blockPosition(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS,
                    0.18F, enabling ? 1.2F : 0.85F);

            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable(
                        enabling ? "message.corpse_campus.mania_on" : "message.corpse_campus.mania_off"), true);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.DONGYUE.get();
    }

    private int getAutoLungeRange(int spellLevel) {
        return 3 + Math.max(0, spellLevel - 1);
    }

    private int getBonusDamagePercent() {
        return 50;
    }
}

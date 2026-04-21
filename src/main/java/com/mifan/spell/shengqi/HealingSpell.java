package com.mifan.spell.shengqi;

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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class HealingSpell extends AbstractSpell {
    private static final int DURATION_SECONDS = 60;

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "healing");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(ModSchools.SHENGQI_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(60)
            .build();

    public HealingSpell() {
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
                Component.translatable("tooltip.corpse_campus.range_blocks", getCastRange(spellLevel)),
                Component.translatable("tooltip.corpse_campus.healing_target_player"),
                Component.translatable("tooltip.corpse_campus.healing_self_fallback"));
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
            LivingEntity lookedTarget = AbilityRuntime.findTargetInSight(entity, getCastRange(spellLevel), 0.85D);
            Player targetPlayer = lookedTarget instanceof Player player ? player : null;
            Player recipient = targetPlayer != null ? targetPlayer : entity instanceof Player player ? player : null;

            if (recipient != null) {
                recipient.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                        DURATION_SECONDS * 20,
                        0,
                        false,
                        true,
                        true));
                level.playSound(null,
                        recipient.blockPosition(),
                        SoundEvents.BEACON_POWER_SELECT,
                        SoundSource.PLAYERS,
                        0.45F,
                        recipient == entity ? 1.1F : 1.25F);

                if (entity instanceof Player caster) {
                    caster.displayClientMessage(Component.translatable(
                            recipient == caster
                                    ? "message.corpse_campus.healing_self"
                                    : "message.corpse_campus.healing_target",
                            recipient.getDisplayName()), true);
                }
            }
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.SHENGQI.get();
    }

    private int getCastRange(int spellLevel) {
        return 8 + spellLevel * 2;
    }
}

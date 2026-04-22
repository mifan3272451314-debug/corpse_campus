package com.mifan.spell.dongyue;

import com.mifan.registry.ModMobEffects;
import com.mifan.registry.ModSchools;
import com.mifan.spell.AbilityRuntime;
import com.mifan.spell.runtime.ImpermanenceMonkRuntime;
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
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class ImpermanenceMonkSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "impermanence_monk");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(ModSchools.DONGYUE_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(4)
            .build();

    public ImpermanenceMonkSpell() {
        this.manaCostPerLevel = 6;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 20;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.impermanence_max_targets",
                        AbilityRuntime.IMPERMANENCE_MAX_TARGETS),
                Component.translatable("tooltip.corpse_campus.impermanence_range",
                        AbilityRuntime.IMPERMANENCE_INFECTION_RANGE),
                Component.translatable("tooltip.corpse_campus.impermanence_block_attack"),
                Component.translatable("tooltip.corpse_campus.impermanence_self_boost"),
                Component.translatable("tooltip.corpse_campus.impermanence_sneak_clear"));
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
        return Optional.of(SoundEvents.WITHER_SPAWN);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (level.isClientSide || !(entity instanceof Player caster)) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        if (caster.isShiftKeyDown()) {
            ImpermanenceMonkRuntime.clearAllInfections(caster);
            caster.removeEffect(ModMobEffects.IMPERMANENCE_MONK.get());
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.impermanence_off"), true);
            level.playSound(null, caster.blockPosition(), SoundEvents.WITHER_AMBIENT,
                    SoundSource.PLAYERS, 0.35F, 1.4F);
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        Player target = findNearbyPlayer(level, caster);
        if (target == null) {
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.impermanence_no_target"), true);
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        if (ImpermanenceMonkRuntime.isInfectedBy(target, caster.getUUID())) {
            ImpermanenceMonkRuntime.releaseTarget(caster, target);
            target.removeEffect(ModMobEffects.IMPERMANENCE_MONK_INFECTED.get());
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.impermanence_released", target.getDisplayName()), true);
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        if (ImpermanenceMonkRuntime.getInfectedCount(caster) >= AbilityRuntime.IMPERMANENCE_MAX_TARGETS) {
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.impermanence_full"), true);
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        if (ImpermanenceMonkRuntime.infectTarget(caster, target)) {
            target.addEffect(new MobEffectInstance(
                    ModMobEffects.IMPERMANENCE_MONK_INFECTED.get(),
                    AbilityRuntime.TOGGLE_DURATION_TICKS,
                    spellLevel - 1,
                    false, true, true));

            if (!caster.hasEffect(ModMobEffects.IMPERMANENCE_MONK.get())) {
                caster.addEffect(new MobEffectInstance(
                        ModMobEffects.IMPERMANENCE_MONK.get(),
                        AbilityRuntime.TOGGLE_DURATION_TICKS,
                        spellLevel - 1,
                        false, false, false));
            }

            int count = ImpermanenceMonkRuntime.getInfectedCount(caster);

            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.impermanence_infected",
                    target.getDisplayName(),
                    count,
                    AbilityRuntime.IMPERMANENCE_MAX_TARGETS), false);

            target.displayClientMessage(
                    Component.translatable("message.corpse_campus.impermanence_infected_you",
                            caster.getDisplayName()),
                    false);

            // "给予自己的一阶技能"：基于感染数量给予施法者递进的增益（一阶技能的抽象化表达）
            applySelfBoost(caster, count, spellLevel);

            level.playSound(null, caster.blockPosition(), SoundEvents.WITHER_SHOOT,
                    SoundSource.PLAYERS, 0.45F, 1.6F);
            level.playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_INFECT,
                    SoundSource.PLAYERS, 0.6F, 0.9F);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void applySelfBoost(Player caster, int infectedCount, int spellLevel) {
        int durationTicks = 20 * 30 * infectedCount;
        int amplifier = Math.min(2, infectedCount - 1);
        caster.addEffect(new MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DAMAGE_BOOST,
                durationTicks, amplifier, false, true, true));
        caster.addEffect(new MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
                durationTicks, Math.max(0, amplifier - 1), false, true, true));
    }

    private Player findNearbyPlayer(Level level, Player caster) {
        AABB box = caster.getBoundingBox().inflate(AbilityRuntime.IMPERMANENCE_INFECTION_RANGE);
        return level.getEntitiesOfClass(Player.class, box,
                        p -> p != caster && p.isAlive() && caster.hasLineOfSight(p))
                .stream()
                .min((a, b) -> Double.compare(caster.distanceToSqr(a), caster.distanceToSqr(b)))
                .orElse(null);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.DONGYUE.get();
    }
}

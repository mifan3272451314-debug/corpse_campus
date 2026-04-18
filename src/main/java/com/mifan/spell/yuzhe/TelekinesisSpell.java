package com.mifan.spell.yuzhe;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class TelekinesisSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "telekinesis");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(ModSchools.YUZHE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(14)
            .build();

    public TelekinesisSpell() {
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 20;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.range_blocks", getCastRange(spellLevel)),
                Component.translatable("tooltip.corpse_campus.suspend_seconds", getSuspendTicks() / 20));
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
        return Optional.of(SoundEvents.ENDERMAN_TELEPORT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.SHULKER_SHOOT);
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return AbilityRuntime.findTargetInSight(entity, getCastRange(spellLevel), 0.88D) != null
                && super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        LivingEntity target = AbilityRuntime.findTargetInSight(entity, getCastRange(spellLevel), 0.88D);
        if (target != null) {
            Vec3 look = entity.getLookAngle().normalize();
            Vec3 releaseVelocity = look.scale(1.0D + spellLevel * 0.15D).add(0.0D, 0.75D + spellLevel * 0.05D, 0.0D);
            target.getPersistentData().putLong(AbilityRuntime.TAG_TELEKINESIS_END,
                    level.getGameTime() + getSuspendTicks());
            target.getPersistentData().putDouble(AbilityRuntime.TAG_TELEKINESIS_X, releaseVelocity.x);
            target.getPersistentData().putDouble(AbilityRuntime.TAG_TELEKINESIS_Y, releaseVelocity.y);
            target.getPersistentData().putDouble(AbilityRuntime.TAG_TELEKINESIS_Z, releaseVelocity.z);
            target.setNoGravity(true);
            target.setDeltaMovement(Vec3.ZERO);
            target.fallDistance = 0.0F;
            target.hurtMarked = true;
            target.hasImpulse = true;

            entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0, false, false, true));
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.YUZHE.get();
    }

    private int getCastRange(int spellLevel) {
        return 10 + spellLevel * 2;
    }

    private int getSuspendTicks() {
        return 30;
    }
}

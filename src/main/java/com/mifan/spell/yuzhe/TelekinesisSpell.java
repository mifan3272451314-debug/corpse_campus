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
            .setCooldownSeconds(45)
            .build();

    public TelekinesisSpell() {
        this.manaCostPerLevel = 1;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 100;
        this.baseManaCost = 2;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.range_blocks", getCastRange(spellLevel)),
                Component.translatable("tooltip.corpse_campus.hold_cast"),
                Component.translatable("tooltip.corpse_campus.release_throw"));
    }

    @Override
    public CastType getCastType() {
        return CastType.CONTINUOUS;
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
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return (entity.getPersistentData().contains(AbilityRuntime.TAG_TELEKINESIS_TARGET_ID)
                || AbilityRuntime.findTargetInSight(entity, getCastRange(spellLevel), 0.84D) != null)
                && super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (!level.isClientSide) {
            LivingEntity target = AbilityRuntime.findLivingEntityById(level,
                    entity.getPersistentData().getInt(AbilityRuntime.TAG_TELEKINESIS_TARGET_ID));
            if (target == null) {
                target = AbilityRuntime.findTargetInSight(entity, getCastRange(spellLevel), 0.84D);
            }

            if (target != null) {
                Vec3 look = entity.getLookAngle().normalize();
                entity.getPersistentData().putInt(AbilityRuntime.TAG_TELEKINESIS_TARGET_ID, target.getId());
                entity.getPersistentData().putLong(AbilityRuntime.TAG_TELEKINESIS_HOLD_UNTIL, level.getGameTime() + 8L);
                entity.getPersistentData().putInt(AbilityRuntime.TAG_TELEKINESIS_LEVEL, spellLevel);
                AbilityRuntime.storeLookVector(entity.getPersistentData(), look);

                target.setNoGravity(true);
                target.fallDistance = 0.0F;
                target.setDeltaMovement(Vec3.ZERO);
                target.hasImpulse = true;
                target.hurtMarked = true;

                if (level.getGameTime() % 12L == 0L) {
                    level.playSound(null, entity.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.12F, 1.5F);
                }
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData,
            boolean canceled) {
        if (!level.isClientSide && entity.getPersistentData().contains(AbilityRuntime.TAG_TELEKINESIS_TARGET_ID)) {
            entity.getPersistentData().putLong(AbilityRuntime.TAG_TELEKINESIS_HOLD_UNTIL, level.getGameTime() - 1L);
        }
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, canceled);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.YUZHE.get();
    }

    private int getCastRange(int spellLevel) {
        return 10 + spellLevel * 2;
    }
}

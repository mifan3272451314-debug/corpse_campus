package com.mifan.spell.yuzhe;

import com.mifan.registry.ModSchools;
import com.mifan.spell.AbilityRuntime;
import com.mifan.spell.runtime.MimicHandler;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class MimicSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "mimic");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(ModSchools.YUZHE_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(0)
            .build();

    public MimicSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.mimic_slot_count", AbilityRuntime.MIMIC_MAX_SLOTS),
                Component.translatable("tooltip.corpse_campus.mimic_copy_range", AbilityRuntime.MIMIC_COPY_RANGE),
                Component.translatable("tooltip.corpse_campus.mimic_b_rank_only"),
                Component.translatable("tooltip.corpse_campus.mimic_sneak_absorb"),
                Component.translatable("tooltip.corpse_campus.mimic_stand_release"),
                Component.translatable("tooltip.corpse_campus.mimic_full_locks"));
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
        if (level.isClientSide || !(entity instanceof ServerPlayer caster)) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        if (caster.isShiftKeyDown()) {
            ServerPlayer target = findNearbyPlayer(level, caster);
            if (target == null) {
                caster.displayClientMessage(
                        Component.translatable("message.corpse_campus.mimic_no_target"), true);
            } else {
                MimicHandler.beginAbsorb(caster, target);
            }
        } else {
            MimicHandler.beginRelease(caster);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private ServerPlayer findNearbyPlayer(Level level, Player caster) {
        AABB box = caster.getBoundingBox().inflate(AbilityRuntime.MIMIC_COPY_RANGE);
        return level.getEntitiesOfClass(ServerPlayer.class, box,
                        p -> p != caster && p.isAlive() && caster.hasLineOfSight(p))
                .stream()
                .min((a, b) -> Double.compare(caster.distanceToSqr(a), caster.distanceToSqr(b)))
                .orElse(null);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.YUZHE.get();
    }
}

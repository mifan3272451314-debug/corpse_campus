package com.mifan.spell.shengqi;

import com.mifan.registry.ModSchools;
import com.mifan.spell.runtime.GrafterRuntime;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class GrafterSpell extends AbstractSpell {
    public static final int GRAFT_RANGE = 6;

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "grafter");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(ModSchools.SHENGQI_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(20)
            .build();

    public GrafterSpell() {
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 200;
        this.baseManaCost = 40;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.grafter_range", GRAFT_RANGE),
                Component.translatable("tooltip.corpse_campus.grafter_cast_time"),
                Component.translatable("tooltip.corpse_campus.grafter_absorb_mode"),
                Component.translatable("tooltip.corpse_campus.grafter_graft_mode"),
                Component.translatable("tooltip.corpse_campus.grafter_drop_absorb"),
                Component.translatable("tooltip.corpse_campus.grafter_no_mimic_copy"),
                Component.translatable("tooltip.corpse_campus.grafter_forbidden"),
                Component.translatable("tooltip.corpse_campus.grafter_cross_sequence"));
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
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
        return Optional.of(SoundEvents.BEACON_POWER_SELECT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.BEACON_ACTIVATE);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (level.isClientSide || !(entity instanceof ServerPlayer caster)) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        ServerPlayer target = findNearbyPlayer(level, caster);
        if (target == null) {
            // 没有可作为对象的玩家时，嫁接师可吸收地上掉落的异能物品
            net.minecraft.world.entity.item.ItemEntity droppedTrait = GrafterRuntime.findNearbyDroppedTraitItem(caster,
                    GRAFT_RANGE);
            if (droppedTrait != null) {
                String absorbedSpellName = GrafterRuntime.absorbDroppedTraitItem(caster, droppedTrait);
                if (absorbedSpellName != null) {
                    caster.displayClientMessage(Component.translatable(
                            "message.corpse_campus.grafter_absorbed_dropped", absorbedSpellName), false);
                    level.playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                            SoundSource.PLAYERS, 0.45F, 1.1F);
                } else {
                    caster.displayClientMessage(
                            Component.translatable("message.corpse_campus.grafter_dropped_empty"), true);
                }
            } else {
                caster.displayClientMessage(
                        Component.translatable("message.corpse_campus.grafter_no_target"), true);
            }
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        if (caster.isShiftKeyDown()) {
            doGraft(caster, target);
        } else {
            doAbsorb(caster, target);
        }

        level.playSound(null, caster.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 0.4F, 1.3F);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void doAbsorb(ServerPlayer caster, ServerPlayer target) {
        List<GrafterRuntime.EligibleSpell> eligible = GrafterRuntime.collectTransferableSpells(target);
        if (eligible.isEmpty()) {
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.grafter_target_no_spells",
                            target.getDisplayName()),
                    true);
            return;
        }

        if (GrafterRuntime.absorb(caster, target)) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.grafter_absorbed",
                    target.getDisplayName()), false);
            target.displayClientMessage(Component.translatable(
                    "message.corpse_campus.grafter_absorbed_victim",
                    caster.getDisplayName()), false);
        } else {
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.grafter_absorb_failed"), true);
        }
    }

    private void doGraft(ServerPlayer caster, ServerPlayer target) {
        if (!GrafterRuntime.isGraftTargetEligible(target)) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.grafter_target_has_abilities",
                    target.getDisplayName()), true);
            return;
        }

        List<GrafterRuntime.EligibleSpell> eligible = GrafterRuntime.collectTransferableSpells(caster);
        if (eligible.isEmpty()) {
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.grafter_no_transferable"), true);
            return;
        }

        if (GrafterRuntime.graft(caster, target)) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.grafter_grafted_to",
                    target.getDisplayName()), false);
            target.displayClientMessage(Component.translatable(
                    "message.corpse_campus.grafter_grafted_received",
                    caster.getDisplayName()), false);
        } else {
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.grafter_graft_failed"), true);
        }
    }

    private ServerPlayer findNearbyPlayer(Level level, Player caster) {
        AABB box = caster.getBoundingBox().inflate(GRAFT_RANGE);
        return level.getEntitiesOfClass(ServerPlayer.class, box,
                p -> p != caster && p.isAlive() && caster.hasLineOfSight(p))
                .stream()
                .min((a, b) -> Double.compare(caster.distanceToSqr(a), caster.distanceToSqr(b)))
                .orElse(null);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.SHENGQI.get();
    }
}

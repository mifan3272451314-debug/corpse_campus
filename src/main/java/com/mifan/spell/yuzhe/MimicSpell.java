package com.mifan.spell.yuzhe;

import com.mifan.registry.ModMobEffects;
import com.mifan.registry.ModSchools;
import com.mifan.spell.AbilityRuntime;
import com.mifan.spell.runtime.MimicRuntime;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
                Component.translatable("tooltip.corpse_campus.mimic_sneak_cycle"),
                Component.translatable("tooltip.corpse_campus.mimic_cast_execute"));
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
        if (level.isClientSide || !(entity instanceof Player player)) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        CompoundTag data = player.getPersistentData();

        if (player.isShiftKeyDown()) {
            MimicRuntime.cycleActiveSlot(data);
            int active = MimicRuntime.getActiveSlot(data);
            player.displayClientMessage(Component.translatable(
                    "message.corpse_campus.mimic_slot_switched",
                    active + 1,
                    MimicRuntime.buildSlotsStatus(data)), true);
            level.playSound(null, entity.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.get(),
                    SoundSource.PLAYERS, 0.3F, 0.9F + active * 0.15F);
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        ServerPlayer targetPlayer = findNearbyPlayer(level, player);
        if (targetPlayer != null) {
            tryCopyFromTarget(level, player, targetPlayer, data, spellLevel);
        } else {
            tryExecuteActiveSlot(level, player, data, spellLevel, playerMagicData);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void tryCopyFromTarget(Level level, Player caster, ServerPlayer target,
            CompoundTag data, int spellLevel) {
        List<ResourceLocation> bSpells = MimicRuntime.getTargetBRankSpells(target);
        if (bSpells.isEmpty()) {
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.mimic_target_no_b_spells",
                            target.getDisplayName()),
                    true);
            return;
        }

        ResourceLocation chosen = bSpells.get(caster.getRandom().nextInt(bSpells.size()));

        int emptySlot = MimicRuntime.findNextEmptySlot(data);
        int writeSlot = emptySlot >= 0 ? emptySlot : MimicRuntime.getActiveSlot(data);

        int copyLevel = Math.max(1, Math.min(spellLevel, 3));
        MimicRuntime.setSlot(data, writeSlot, chosen.toString(), copyLevel);
        data.putInt(AbilityRuntime.TAG_MIMIC_ACTIVE_SLOT, writeSlot);

        if (!caster.hasEffect(ModMobEffects.MIMIC.get())) {
            caster.addEffect(new MobEffectInstance(
                    ModMobEffects.MIMIC.get(),
                    AbilityRuntime.TOGGLE_DURATION_TICKS,
                    spellLevel - 1,
                    false, false, false));
        }

        AbstractSpell spell = MimicRuntime.resolveSlotSpell(data, writeSlot);
        String spellName = spell != null ? spell.getSpellName() : chosen.getPath();
        caster.displayClientMessage(Component.translatable(
                "message.corpse_campus.mimic_copied",
                target.getDisplayName(),
                spellName,
                writeSlot + 1,
                MimicRuntime.buildSlotsStatus(data)), false);

        level.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    private void tryExecuteActiveSlot(Level level, Player caster, CompoundTag data,
            int spellLevel, MagicData playerMagicData) {
        if (!caster.hasEffect(ModMobEffects.MIMIC.get())) {
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.mimic_no_copies"), true);
            return;
        }

        int active = MimicRuntime.getActiveSlot(data);
        AbstractSpell spell = MimicRuntime.resolveSlotSpell(data, active);
        if (spell == null) {
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.mimic_slot_empty", active + 1), true);
            return;
        }

        int castLevel = MimicRuntime.getSlotLevel(data, active);
        spell.onCast(level, castLevel, caster, CastSource.SPELLBOOK, playerMagicData);

        level.playSound(null, caster.blockPosition(), SoundEvents.CHORUS_FLOWER_GROW,
                SoundSource.PLAYERS, 0.4F, 1.2F);
        caster.displayClientMessage(Component.translatable(
                "message.corpse_campus.mimic_executed",
                spell.getSpellName(),
                active + 1), true);
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

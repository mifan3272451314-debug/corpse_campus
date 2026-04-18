package com.mifan.spell.xujing;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class MarkSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "mark");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(ModSchools.XUJING_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(180)
            .build();

    public MarkSpell() {
        this.manaCostPerLevel = 1;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 6;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.range_blocks", getCastRange(spellLevel)),
                Component.translatable("tooltip.corpse_campus.mark_radius", AbilityRuntime.getMarkRadius(spellLevel)),
                Component.translatable("tooltip.corpse_campus.mark_duration_seconds",
                        AbilityRuntime.getMarkDurationSeconds(spellLevel)),
                Component.translatable("tooltip.corpse_campus.mark_root_seconds",
                        AbilityRuntime.getMarkRootSeconds()));
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
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return findMarkedBlock(level, entity, spellLevel).getType() == HitResult.Type.BLOCK
                && super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (!level.isClientSide) {
            BlockHitResult hitResult = findMarkedBlock(level, entity, spellLevel);
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                AbilityRuntime.placeMark(entity, spellLevel, hitResult.getBlockPos(), hitResult.getDirection());
                level.playSound(null, hitResult.getBlockPos(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS,
                        0.45F, 1.35F);

                if (entity instanceof Player player) {
                    player.displayClientMessage(Component.translatable("message.corpse_campus.mark_placed"), true);
                }
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.XUJING.get();
    }

    private int getCastRange(int spellLevel) {
        return 10 + spellLevel * 2;
    }

    private BlockHitResult findMarkedBlock(Level level, LivingEntity entity, int spellLevel) {
        return level.clip(new ClipContext(
                entity.getEyePosition(),
                entity.getEyePosition().add(entity.getLookAngle().scale(getCastRange(spellLevel))),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                entity));
    }
}

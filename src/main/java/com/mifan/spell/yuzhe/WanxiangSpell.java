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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class WanxiangSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "wanxiang");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(ModSchools.YUZHE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(12)
            .build();

    public WanxiangSpell() {
        this.manaCostPerLevel = 2;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 14;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.wanxiang_blink_range",
                        AbilityRuntime.getWanxiangBlinkRange(spellLevel)),
                Component.translatable("tooltip.corpse_campus.wanxiang_swap_range",
                        AbilityRuntime.getWanxiangSwapRange(spellLevel)),
                Component.translatable("tooltip.corpse_campus.wanxiang_dash_mode"),
                Component.translatable("tooltip.corpse_campus.wanxiang_swap_mode"));
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
        return Optional.empty();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        boolean success = true;
        if (level instanceof ServerLevel serverLevel) {
            success = AbilityRuntime.castWanxiang(serverLevel, entity, spellLevel);
        }
        if (success) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
        } else {
            refundCast(entity, playerMagicData, spellLevel);
        }
    }

    private void refundCast(LivingEntity entity, MagicData playerMagicData, int spellLevel) {
        if (playerMagicData == null) {
            return;
        }
        float refund = getManaCost(spellLevel);
        playerMagicData.setMana(playerMagicData.getMana() + refund);
        if (playerMagicData.getPlayerCooldowns().removeCooldown(getSpellId())) {
            if (entity instanceof ServerPlayer serverPlayer) {
                playerMagicData.getPlayerCooldowns().syncToPlayer(serverPlayer);
            }
        }
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.YUZHE.get();
    }
}
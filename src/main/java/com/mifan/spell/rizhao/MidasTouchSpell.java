package com.mifan.spell.rizhao;

import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.OpenMidasTouchScreenPacket;
import com.mifan.registry.ModSchools;
import com.mifan.spell.MidasBombRuntime;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class MidasTouchSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "midas_touch");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(ModSchools.RIZHAO_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(90)
            .build();

    public MidasTouchSpell() {
        this.manaCostPerLevel = 2;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 18;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.midas_touch_targets"),
                Component.translatable("tooltip.corpse_campus.midas_touch_timer_range",
                        MidasBombRuntime.MIN_TIMER_SECONDS,
                        MidasBombRuntime.MAX_TIMER_SECONDS),
                Component.translatable("tooltip.corpse_campus.midas_touch_remote_trigger"),
                Component.translatable("tooltip.corpse_campus.midas_touch_inventory_trigger"));
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
        if (!level.isClientSide && entity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ModNetwork.sendToPlayer(new OpenMidasTouchScreenPacket(spellLevel,
                    MidasBombRuntime.DEFAULT_TIMER_SECONDS,
                    MidasBombRuntime.MIN_TIMER_SECONDS,
                    MidasBombRuntime.MAX_TIMER_SECONDS), serverPlayer);
            level.playSound(null, entity.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.35F, 1.1F);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.RIZHAO.get();
    }
}

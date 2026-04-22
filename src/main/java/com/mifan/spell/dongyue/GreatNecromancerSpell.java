package com.mifan.spell.dongyue;

import com.mifan.registry.ModSchools;
import com.mifan.spell.AbilityRuntime;
import com.mifan.spell.runtime.NecromancerRuntime;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class GreatNecromancerSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "great_necromancer");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(ModSchools.DONGYUE_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(4)
            .build();

    public GreatNecromancerSpell() {
        this.manaCostPerLevel = 2;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 12;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.necromancer_soul_gain"),
                Component.translatable("tooltip.corpse_campus.necromancer_soul_summon"),
                Component.translatable("tooltip.corpse_campus.necromancer_patrol",
                        AbilityRuntime.NECROMANCER_PATROL_RADIUS),
                Component.translatable("tooltip.corpse_campus.necromancer_buff_chance",
                        (int) (AbilityRuntime.NECROMANCER_BUFF_NATURAL_CHANCE * 100)),
                Component.translatable("tooltip.corpse_campus.necromancer_enhance_cost",
                        AbilityRuntime.NECROMANCER_ENHANCE_MANA_COST),
                Component.translatable("tooltip.corpse_campus.necromancer_sneak_quick"));
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
        return Optional.of(SoundEvents.SOUL_ESCAPE);
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
            handleQuickSummon(caster, false);
        } else {
            handleQuickSummon(caster, true);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void handleQuickSummon(ServerPlayer caster, boolean forceEnhanced) {
        ResourceLocation lastKill = NecromancerRuntime.getLastKillType(caster);
        if (lastKill == null || NecromancerRuntime.getSoulCount(caster, lastKill) <= 0) {
            for (var entry : NecromancerRuntime.collectSouls(caster).entrySet()) {
                if (entry.getValue() > 0) {
                    lastKill = entry.getKey();
                    break;
                }
            }
        }
        if (lastKill == null) {
            caster.displayClientMessage(Component.translatable("message.corpse_campus.necromancer_no_soul"), true);
            return;
        }

        NecromancerRuntime.SummonResult result = NecromancerRuntime.summon(caster, lastKill, forceEnhanced);
        if (!result.success()) {
            caster.displayClientMessage(Component.translatable(result.failKey()), true);
            return;
        }

        EntityType<?> type = result.type();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        Component name = type != null ? type.getDescription() : Component.literal(String.valueOf(id));
        String messageKey = result.enhanced()
                ? "message.corpse_campus.necromancer_summoned_enhanced"
                : "message.corpse_campus.necromancer_summoned";
        caster.displayClientMessage(Component.translatable(messageKey, name), false);
        caster.level().playSound(null, caster.blockPosition(), SoundEvents.WITHER_SPAWN,
                SoundSource.PLAYERS, 0.35F, result.enhanced() ? 1.4F : 1.0F);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.DONGYUE.get();
    }
}

package com.mifan.spell.yuzhe;

import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.OpenDominanceScreenPacket;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class DominanceSpell extends AbstractSpell {
    private static final int MAX_CONTROLLED = 8;

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "dominance");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(ModSchools.YUZHE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(4)
            .build();

    public DominanceSpell() {
        this.manaCostPerLevel = 1;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 6;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.dominance_limit", MAX_CONTROLLED),
                Component.translatable("tooltip.corpse_campus.dominance_health_limit", 35),
                Component.translatable("tooltip.corpse_campus.dominance_crouch_capture"),
                Component.translatable("tooltip.corpse_campus.dominance_open_list"),
                Component.translatable("tooltip.corpse_campus.dominance_death_link"));
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
        if (entity.isCrouching()) {
            if (!level.isClientSide) {
                Mob target = AbilityRuntime.findDominanceMobTarget(entity, getCastRange(spellLevel), 0.8D);
                if (target == null) {
                    if (entity instanceof Player player) {
                        player.displayClientMessage(Component.translatable("message.corpse_campus.dominance_no_target"), true);
                    }
                } else {
                    boolean added = AbilityRuntime.addDominatedMob(entity, target, spellLevel, MAX_CONTROLLED);
                    level.playSound(null,
                            entity.blockPosition(),
                            added ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.NOTE_BLOCK_BASS.value(),
                            SoundSource.PLAYERS,
                            0.2F,
                            added ? 1.18F : 0.75F);
                    if (entity instanceof Player player) {
                        player.displayClientMessage(Component.translatable(
                                added
                                        ? "message.corpse_campus.dominance_bound"
                                        : target.getMaxHealth() > 35.0F
                                                ? "message.corpse_campus.dominance_too_healthy"
                                                : "message.corpse_campus.dominance_full"), true);
                    }
                }
            }
        } else if (!level.isClientSide && entity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ModNetwork.sendToPlayer(new OpenDominanceScreenPacket(), serverPlayer);
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
}

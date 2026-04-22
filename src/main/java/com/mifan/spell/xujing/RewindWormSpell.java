package com.mifan.spell.xujing;

import com.mifan.registry.ModSchools;
import com.mifan.spell.runtime.RewindWormRuntime;
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
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * 虚境 · S 级 · 回溯之虫。
 * <ul>
 *   <li>施法瞬间完成（INSTANT），不需引导。</li>
 *   <li>镜像维度必须由管理员通过 {@code /magic rewind backup create} 预先扫描，否则施法直接拒绝。</li>
 *   <li>30 分钟共享 CD：去/回共用同一个冷却，由 ISS 法术自身的 {@code defaultConfig.setCooldownSeconds(...)} 管理；
 *       Runtime 不再维护任何内置 NBT 冷却。</li>
 *   <li>不消耗法力（S 级终阶语义）。</li>
 * </ul>
 */
@AutoSpellConfig
public class RewindWormSpell extends AbstractSpell {
    private final ResourceLocation spellId = RewindWormRuntime.REWIND_WORM_ID;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(ModSchools.XUJING_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(1800) // 30 min，唯一的冷却来源（ISS 内置）
            .build();

    public RewindWormSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.rewind_worm_toggle"),
                Component.translatable("tooltip.corpse_campus.rewind_worm_cooldown"),
                Component.translatable("tooltip.corpse_campus.rewind_worm_require_backup"),
                Component.translatable("tooltip.corpse_campus.rewind_worm_mirror_semantics"));
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
        return Optional.of(SoundEvents.PORTAL_TRAVEL);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.PORTAL_TRAVEL);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
                       MagicData playerMagicData) {
        if (!level.isClientSide && entity instanceof ServerPlayer caster) {
            RewindWormRuntime.CastResult result = RewindWormRuntime.cast(caster);
            switch (result.outcome()) {
                case MIRROR_NOT_READY -> caster.displayClientMessage(
                        Component.translatable("message.corpse_campus.rewind_mirror_not_ready"), false);
                case MIRROR_LEVEL_MISSING -> caster.displayClientMessage(
                        Component.translatable("message.corpse_campus.rewind_mirror_missing"), false);
                case ANCHOR_LOST -> caster.displayClientMessage(
                        Component.translatable("message.corpse_campus.rewind_anchor_lost"), false);
                case INTO_MIRROR -> {
                    level.playSound(null, caster.blockPosition(), SoundEvents.PORTAL_TRAVEL,
                            SoundSource.PLAYERS, 0.4F, 1.6F);
                    caster.displayClientMessage(
                            Component.translatable("message.corpse_campus.rewind_into_mirror"), false);
                }
                case OUT_OF_MIRROR -> {
                    level.playSound(null, caster.blockPosition(), SoundEvents.PORTAL_TRAVEL,
                            SoundSource.PLAYERS, 0.4F, 0.6F);
                    caster.displayClientMessage(
                            Component.translatable("message.corpse_campus.rewind_out_of_mirror"), false);
                }
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.XUJING.get();
    }
}

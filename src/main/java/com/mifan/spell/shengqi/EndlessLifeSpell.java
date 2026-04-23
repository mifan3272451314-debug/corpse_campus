package com.mifan.spell.shengqi;

import com.mifan.registry.ModSchools;
import com.mifan.spell.runtime.EndlessLifeRuntime;
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

@AutoSpellConfig
public class EndlessLifeSpell extends AbstractSpell {
    private final ResourceLocation spellId = EndlessLifeRuntime.ENDLESS_LIFE_ID;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(ModSchools.SHENGQI_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(0)
            .build();

    public EndlessLifeSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        // 长引导：与终阶法术地位匹配；同时给玩家一个反悔窗口
        this.castTime = 100;
        this.baseManaCost = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.endless_life_revive"),
                Component.translatable("tooltip.corpse_campus.endless_life_inherit"),
                Component.translatable("tooltip.corpse_campus.endless_life_self_consume"),
                Component.translatable("tooltip.corpse_campus.endless_life_global_seal"));
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
    public void onClientCast(Level level, int spellLevel, LivingEntity entity,
            io.redspace.ironsspellbooks.api.spells.ICastData castData) {
        com.mifan.screeneffect.client.ScreenEffectClientHook.triggerIfLocalPlayer(entity, spellId);
        super.onClientCast(level, spellLevel, entity, castData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (!level.isClientSide && entity instanceof ServerPlayer caster) {
            // 1) 通知 OP（替代实际的群体复活动作）
            EndlessLifeRuntime.notifyOperators(caster);

            // 2) 自身继承圣祈一阶 + 二阶共 6 项
            int granted = EndlessLifeRuntime.grantInheritedSpells(caster);

            // 3) 从自己的异能法术书里移除"生生不息"本身
            EndlessLifeRuntime.removeEndlessLifeFromBook(caster);

            // 4) 写入全局封锁标记，自此再无人能获得"生生不息"
            //    （除非管理员用 /magic seal endless_life false 解封）
            EndlessLifeRuntime.setSealed(caster.server, true);

            level.playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                    0.6F, 0.85F);
            caster.displayClientMessage(
                    Component.translatable("message.corpse_campus.endless_life_cast_self", granted), false);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.SHENGQI.get();
    }
}

package com.mifan.spell.yuzhe;

import com.mifan.entity.SpiritWormEntity;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * 注册 ID {@code authority_grasp}（历史沿用），功能为"诡秘侍者":
 * 消耗 50 法力，在身前召唤一只不死的灵之虫；灵之虫追踪 30 格内的其他玩家并在触碰时消失，
 * 将被触碰玩家的最高非 S 级异能法术写入施法者的法术书。无冷却。
 */
@AutoSpellConfig
public class AuthorityGraspSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "authority_grasp");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(ModSchools.YUZHE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(0.0D)
            .build();

    public AuthorityGraspSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = AbilityRuntime.MYSTIC_ATTENDANT_MANA_COST;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        int durationMinutes = AbilityRuntime.SPIRIT_WORM_DURATION_TICKS / (20 * 60);
        return List.of(
                Component.translatable("tooltip.corpse_campus.mystic_attendant_summon",
                        (int) AbilityRuntime.SPIRIT_WORM_SEARCH_RADIUS, durationMinutes),
                Component.translatable("tooltip.corpse_campus.mystic_attendant_copy"),
                Component.translatable("tooltip.corpse_campus.mystic_attendant_devour"),
                Component.translatable("tooltip.corpse_campus.mystic_attendant_cost",
                        AbilityRuntime.MYSTIC_ATTENDANT_MANA_COST),
                Component.translatable("tooltip.corpse_campus.mystic_attendant_no_cooldown"));
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
        return Optional.of(SoundEvents.ENDERMAN_AMBIENT);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
            MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            SpiritWormEntity worm = new SpiritWormEntity(serverLevel, entity);
            serverLevel.addFreshEntity(worm);
            serverLevel.playSound(null, entity.blockPosition(), SoundEvents.SOUL_ESCAPE,
                    SoundSource.PLAYERS, 1.0F, 0.9F);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.YUZHE.get();
    }
}

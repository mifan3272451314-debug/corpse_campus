package com.mifan.spell.shengqi;

import com.mifan.registry.ModSchools;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@AutoSpellConfig
public class HuihunSpell extends AbstractSpell {
    private static final int COOLDOWN_SECONDS = 60;

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "huihun");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(ModSchools.SHENGQI_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(COOLDOWN_SECONDS)
            .build();

    public HuihunSpell() {
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.huihun_notify_ops"),
                Component.translatable("tooltip.corpse_campus.huihun_click_teleport"),
                Component.translatable("tooltip.corpse_campus.duration_seconds", COOLDOWN_SECONDS));
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
        if (!level.isClientSide && entity instanceof ServerPlayer caster) {
            notifyOperators(caster);
            level.playSound(null, caster.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
                    0.45F, 1.35F);
            caster.displayClientMessage(Component.translatable("message.corpse_campus.huihun_cast_self"), true);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public SchoolType getSchoolType() {
        return ModSchools.SHENGQI.get();
    }

    private static void notifyOperators(ServerPlayer caster) {
        String dimensionId = caster.level().dimension().location().toString();
        String teleportCommand = String.format(Locale.ROOT,
                "/execute in %s run tp @s %.2f %.2f %.2f",
                dimensionId,
                caster.getX(),
                caster.getY(),
                caster.getZ());

        MutableComponent locationComponent = Component.translatable(
                "message.corpse_campus.huihun_location",
                String.format(Locale.ROOT, "%.1f", caster.getX()),
                String.format(Locale.ROOT, "%.1f", caster.getY()),
                String.format(Locale.ROOT, "%.1f", caster.getZ()),
                dimensionId).withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, teleportCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.corpse_campus.huihun_click_hint"))));

        MutableComponent notifyMessage = Component.translatable(
                "message.corpse_campus.huihun_notify_ops",
                caster.getDisplayName(),
                locationComponent);

        for (ServerPlayer player : caster.server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(3)) {
                player.sendSystemMessage(notifyMessage);
            }
        }
    }
}

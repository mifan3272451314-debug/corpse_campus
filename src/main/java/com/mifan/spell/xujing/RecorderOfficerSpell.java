package com.mifan.spell.xujing;

import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.OpenRecorderOfficerScreenPacket;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class RecorderOfficerSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath("corpse_campus", "recorder_officer");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(ModSchools.XUJING_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(75)
            .build();

    public RecorderOfficerSpell() {
        this.manaCostPerLevel = 2;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = 18;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("tooltip.corpse_campus.range_blocks", getCastRange(spellLevel)),
                Component.translatable("tooltip.corpse_campus.recorder_officer_requires_paper"),
                Component.translatable("tooltip.corpse_campus.recorder_officer_record_mode"),
                Component.translatable("tooltip.corpse_campus.recorder_officer_attach_mode"),
                Component.translatable("tooltip.corpse_campus.recorder_officer_timer_range",
                        AbilityRuntime.RECORDER_OFFICER_MIN_SECONDS,
                        AbilityRuntime.RECORDER_OFFICER_MAX_SECONDS));
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
        if (!level.isClientSide) {
            ItemStack stack = entity.getMainHandItem();
            if (!stack.is(Items.PAPER)) {
                if (entity instanceof Player player) {
                    player.displayClientMessage(Component.translatable("message.corpse_campus.recorder_officer_need_paper"), true);
                }
            } else if (entity.isCrouching()) {
                BlockHitResult hitResult = findMarkedBlock(level, entity, spellLevel);
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    AbilityRuntime.recordRecorderOfficerPaper(stack, entity, hitResult.getBlockPos(), hitResult.getDirection());
                    level.playSound(null, hitResult.getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.55F, 0.9F);
                    if (entity instanceof Player player) {
                        player.displayClientMessage(Component.translatable("message.corpse_campus.recorder_officer_recorded"), true);
                    }
                } else if (entity instanceof Player player) {
                    player.displayClientMessage(Component.translatable("message.corpse_campus.recorder_officer_no_block"), true);
                }
            } else {
                if (!AbilityRuntime.hasRecorderOfficerRecord(stack)) {
                    if (entity instanceof Player player) {
                        player.displayClientMessage(Component.translatable("message.corpse_campus.recorder_officer_no_record"), true);
                    }
                } else {
                    LivingEntity target = AbilityRuntime.findTargetInSight(entity, getCastRange(spellLevel), 0.85D);
                    if (target == null || target == entity) {
                        if (entity instanceof Player player) {
                            player.displayClientMessage(Component.translatable("message.corpse_campus.recorder_officer_no_target"), true);
                        }
                    } else if (entity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        ModNetwork.sendToPlayer(new OpenRecorderOfficerScreenPacket(
                                spellLevel,
                                target.getId(),
                                target.getDisplayName().getString(),
                                AbilityRuntime.RECORDER_OFFICER_DEFAULT_SECONDS,
                                AbilityRuntime.RECORDER_OFFICER_MIN_SECONDS,
                                AbilityRuntime.RECORDER_OFFICER_MAX_SECONDS), serverPlayer);
                        level.playSound(null, entity.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.35F, 0.95F);
                    }
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
        return 12 + spellLevel * 2;
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

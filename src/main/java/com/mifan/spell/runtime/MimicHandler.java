package com.mifan.spell.runtime;

import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.OpenMimicAbsorbScreenPacket;
import com.mifan.network.clientbound.OpenMimicReleaseScreenPacket;
import com.mifan.registry.ModMobEffects;
import com.mifan.spell.AbilityRuntime;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 模仿者按键的服务端业务入口。
 *
 * 触发分流（由 {@link com.mifan.spell.yuzhe.MimicSpell#onCast} 调用）：
 * - 蹲下 + 面向玩家：{@link #beginAbsorb} 弹吸取界面，玩家点击后回流到 {@link #handleAbsorbChoice}。
 * - 站立按键：{@link #beginRelease} 0 槽提示 / 1 槽直接释放 / >=2 槽弹释放界面，
 *   玩家点击后回流到 {@link #handleReleaseChoice}。
 */
public final class MimicHandler {
    private MimicHandler() {
    }

    /* ------------------------------ Absorb ------------------------------ */

    public static void beginAbsorb(ServerPlayer caster, ServerPlayer target) {
        CompoundTag data = caster.getPersistentData();

        if (MimicRuntime.isFull(data)) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.mimic_slots_full",
                    MimicRuntime.buildSlotsStatus(data)), true);
            return;
        }

        List<ResourceLocation> candidates = MimicRuntime.getCopyCandidates(caster, target);
        if (candidates.isEmpty()) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.mimic_target_no_b_spells",
                    target.getDisplayName()), true);
            return;
        }

        List<String> ids = new ArrayList<>(candidates.size());
        for (ResourceLocation loc : candidates) {
            ids.add(loc.toString());
        }
        ModNetwork.sendToPlayer(new OpenMimicAbsorbScreenPacket(
                target.getUUID(),
                target.getGameProfile().getName(),
                ids), caster);
    }

    public static void handleAbsorbChoice(ServerPlayer caster, UUID targetId, String spellId) {
        CompoundTag data = caster.getPersistentData();

        if (MimicRuntime.isFull(data)) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.mimic_slots_full",
                    MimicRuntime.buildSlotsStatus(data)), true);
            return;
        }
        if (MimicRuntime.isAlreadyCopied(data, spellId)) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.mimic_already_copied",
                    MimicRuntime.displayName(spellId)), true);
            return;
        }

        ResourceLocation loc = ResourceLocation.tryParse(spellId);
        if (loc == null || MimicRuntime.isForbiddenForCopy(loc)) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.mimic_invalid_choice"), true);
            return;
        }

        // 校验目标确实拥有这条一阶
        ServerPlayer target = caster.serverLevel().getServer().getPlayerList().getPlayer(targetId);
        if (target == null
                || caster.distanceTo(target) > AbilityRuntime.MIMIC_COPY_RANGE * 2.0F
                || !MimicRuntime.getCopyCandidates(caster, target).contains(loc)) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.mimic_invalid_choice"), true);
            return;
        }

        int slot = MimicRuntime.findNextEmptySlot(data);
        if (slot < 0) {
            // 理论上 isFull 已挡，这里再保险一道
            return;
        }

        MimicRuntime.setSlot(data, slot, spellId, 1);
        data.putInt(AbilityRuntime.TAG_MIMIC_ACTIVE_SLOT, slot);

        if (!caster.hasEffect(ModMobEffects.MIMIC.get())) {
            caster.addEffect(new MobEffectInstance(
                    ModMobEffects.MIMIC.get(),
                    AbilityRuntime.TOGGLE_DURATION_TICKS,
                    0,
                    false, false, false));
        }

        caster.displayClientMessage(Component.translatable(
                "message.corpse_campus.mimic_copied",
                target.getDisplayName(),
                MimicRuntime.displayName(spellId),
                slot + 1,
                MimicRuntime.buildSlotsStatus(data)), false);

        ServerLevel level = caster.serverLevel();
        level.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    /* ------------------------------ Release ------------------------------ */

    public static void beginRelease(ServerPlayer caster) {
        CompoundTag data = caster.getPersistentData();
        List<MimicRuntime.SlotEntry> filled = MimicRuntime.listFilledSlots(data);

        if (filled.isEmpty()) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.mimic_no_copies"), true);
            return;
        }

        if (filled.size() == 1) {
            // 单条直接释放，省一步操作
            executeSlot(caster, filled.get(0).slot());
            return;
        }

        List<OpenMimicReleaseScreenPacket.SlotEntry> payload = new ArrayList<>(filled.size());
        for (MimicRuntime.SlotEntry e : filled) {
            payload.add(new OpenMimicReleaseScreenPacket.SlotEntry(e.slot(), e.spellId()));
        }
        ModNetwork.sendToPlayer(new OpenMimicReleaseScreenPacket(payload), caster);
    }

    public static void handleReleaseChoice(ServerPlayer caster, int slot) {
        if (slot < 0 || slot >= AbilityRuntime.MIMIC_MAX_SLOTS) {
            return;
        }
        executeSlot(caster, slot);
    }

    private static void executeSlot(ServerPlayer caster, int slot) {
        CompoundTag data = caster.getPersistentData();
        AbstractSpell spell = MimicRuntime.resolveSlotSpell(data, slot);
        if (spell == null) {
            caster.displayClientMessage(Component.translatable(
                    "message.corpse_campus.mimic_slot_empty", slot + 1), true);
            return;
        }

        int castLevel = MimicRuntime.getSlotLevel(data, slot);
        MagicData md = MagicData.getPlayerMagicData(caster);
        ServerLevel level = caster.serverLevel();
        spell.onCast(level, castLevel, caster, CastSource.SPELLBOOK, md);

        data.putInt(AbilityRuntime.TAG_MIMIC_ACTIVE_SLOT, slot);

        level.playSound(null, caster.blockPosition(), SoundEvents.CHORUS_FLOWER_GROW,
                SoundSource.PLAYERS, 0.4F, 1.2F);
        caster.displayClientMessage(Component.translatable(
                "message.corpse_campus.mimic_executed",
                MimicRuntime.displayName(spell.getSpellResource()),
                slot + 1), true);
    }
}

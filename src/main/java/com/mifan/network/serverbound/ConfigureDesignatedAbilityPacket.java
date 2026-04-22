package com.mifan.network.serverbound;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalyBookService.SpellSpec;
import com.mifan.item.DesignatedAbilityItem;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端在指定异能 GUI 中点完"确定"后，把所选 (spellId, level) 发回服务端。
 * 服务端校验玩家手持是 {@link DesignatedAbilityItem} 且未配置，然后把 NBT 写到那把物品上。
 */
public class ConfigureDesignatedAbilityPacket {
    private final ResourceLocation spellId;
    private final int spellLevel;

    public ConfigureDesignatedAbilityPacket(ResourceLocation spellId, int spellLevel) {
        this.spellId = spellId;
        this.spellLevel = spellLevel;
    }

    public static void encode(ConfigureDesignatedAbilityPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.spellId);
        buffer.writeVarInt(packet.spellLevel);
    }

    public static ConfigureDesignatedAbilityPacket decode(FriendlyByteBuf buffer) {
        return new ConfigureDesignatedAbilityPacket(buffer.readResourceLocation(), buffer.readVarInt());
    }

    public static void handle(ConfigureDesignatedAbilityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            // 仅校验主手 + 副手，避免拿玩家其他物品栏中的同类物品被意外覆盖
            ItemStack stack = sender.getItemInHand(InteractionHand.MAIN_HAND);
            if (!(stack.getItem() instanceof DesignatedAbilityItem)) {
                stack = sender.getItemInHand(InteractionHand.OFF_HAND);
            }
            if (!(stack.getItem() instanceof DesignatedAbilityItem)) {
                sender.displayClientMessage(
                        Component.translatable("message.corpse_campus.designated_ability.not_holding")
                                .withStyle(ChatFormatting.RED), true);
                return;
            }
            if (DesignatedAbilityItem.isConfigured(stack)) {
                sender.displayClientMessage(
                        Component.translatable("message.corpse_campus.designated_ability.already_configured")
                                .withStyle(ChatFormatting.RED), true);
                return;
            }

            SpellSpec spec = AnomalyBookService.getSpellSpec(packet.spellId);
            if (spec == null) {
                sender.displayClientMessage(
                        Component.translatable("message.corpse_campus.absorb_spell_missing", packet.spellId.toString())
                                .withStyle(ChatFormatting.RED), true);
                return;
            }
            AbstractSpell spell = AnomalyBookService.getRegisteredSpell(packet.spellId);
            int clamped = spell == null ? Math.max(1, packet.spellLevel)
                    : Mth.clamp(packet.spellLevel, 1, spell.getMaxLevel());

            DesignatedAbilityItem.applyConfiguration(stack, packet.spellId, clamped, spec.schoolId());
            sender.displayClientMessage(
                    Component.translatable("message.corpse_campus.designated_ability.configured",
                            spec.zhName(),
                            Component.translatable("school." + spec.schoolId().getNamespace()
                                    + "." + spec.schoolId().getPath()),
                            clamped).withStyle(ChatFormatting.AQUA), false);
        });
        context.setPacketHandled(true);
    }
}

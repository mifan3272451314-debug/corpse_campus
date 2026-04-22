package com.mifan.network.serverbound;

import com.mifan.anomaly.AnomalyBookService;
import com.mifan.anomaly.AnomalyBookService.UpgradeOutcome;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpgradeAnomalySpellPacket {
    private final ResourceLocation spellId;

    public UpgradeAnomalySpellPacket(ResourceLocation spellId) {
        this.spellId = spellId;
    }

    public static void encode(UpgradeAnomalySpellPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.spellId);
    }

    public static UpgradeAnomalySpellPacket decode(FriendlyByteBuf buffer) {
        return new UpgradeAnomalySpellPacket(buffer.readResourceLocation());
    }

    public static void handle(UpgradeAnomalySpellPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            UpgradeOutcome outcome = AnomalyBookService.upgradeLoadedSpell(sender, packet.spellId);
            String key = switch (outcome) {
                case SUCCESS          -> "message.corpse_campus.upgrade_success";
                case NO_BOOK          -> "message.corpse_campus.upgrade_no_book";
                case SPELL_NOT_LOADED -> "message.corpse_campus.upgrade_not_loaded";
                case ALREADY_MAX_LEVEL -> "message.corpse_campus.upgrade_max_level";
                case NOT_ENOUGH_XP    -> "message.corpse_campus.upgrade_not_enough_xp";
            };
            ChatFormatting color = outcome == UpgradeOutcome.SUCCESS
                    ? ChatFormatting.AQUA
                    : ChatFormatting.RED;
            sender.displayClientMessage(Component.translatable(key).withStyle(color), true);
        });
        context.setPacketHandled(true);
    }
}

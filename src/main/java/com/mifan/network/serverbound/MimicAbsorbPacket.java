package com.mifan.network.serverbound;

import com.mifan.spell.runtime.MimicHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class MimicAbsorbPacket {
    private final UUID targetPlayerId;
    private final String spellId;

    public MimicAbsorbPacket(UUID targetPlayerId, String spellId) {
        this.targetPlayerId = targetPlayerId;
        this.spellId = spellId;
    }

    public static void encode(MimicAbsorbPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.targetPlayerId);
        buffer.writeUtf(packet.spellId);
    }

    public static MimicAbsorbPacket decode(FriendlyByteBuf buffer) {
        return new MimicAbsorbPacket(buffer.readUUID(), buffer.readUtf());
    }

    public static void handle(MimicAbsorbPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                MimicHandler.handleAbsorbChoice(sender, packet.targetPlayerId, packet.spellId);
            }
        });
        context.setPacketHandled(true);
    }
}

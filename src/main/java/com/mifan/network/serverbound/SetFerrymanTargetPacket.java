package com.mifan.network.serverbound;

import com.mifan.spell.AbilityRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class SetFerrymanTargetPacket {
    private final UUID targetPlayerId;
    private final int spellLevel;

    public SetFerrymanTargetPacket(UUID targetPlayerId, int spellLevel) {
        this.targetPlayerId = targetPlayerId;
        this.spellLevel = spellLevel;
    }

    public static void encode(SetFerrymanTargetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.targetPlayerId);
        buffer.writeVarInt(packet.spellLevel);
    }

    public static SetFerrymanTargetPacket decode(FriendlyByteBuf buffer) {
        return new SetFerrymanTargetPacket(buffer.readUUID(), buffer.readVarInt());
    }

    public static void handle(SetFerrymanTargetPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                AbilityRuntime.setFerrymanTargetPlayer(sender, packet.targetPlayerId, packet.spellLevel);
            }
        });
        context.setPacketHandled(true);
    }
}

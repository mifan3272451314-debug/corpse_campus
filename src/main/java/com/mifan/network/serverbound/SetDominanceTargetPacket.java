package com.mifan.network.serverbound;

import com.mifan.spell.AbilityRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class SetDominanceTargetPacket {
    private final UUID targetPlayerId;

    public SetDominanceTargetPacket(UUID targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public static void encode(SetDominanceTargetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.targetPlayerId);
    }

    public static SetDominanceTargetPacket decode(FriendlyByteBuf buffer) {
        return new SetDominanceTargetPacket(buffer.readUUID());
    }

    public static void handle(SetDominanceTargetPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                AbilityRuntime.setDominanceTargetPlayer(sender, packet.targetPlayerId);
            }
        });
        context.setPacketHandled(true);
    }
}

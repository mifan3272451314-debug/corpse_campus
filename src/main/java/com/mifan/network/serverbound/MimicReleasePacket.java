package com.mifan.network.serverbound;

import com.mifan.spell.runtime.MimicHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MimicReleasePacket {
    private final int slot;

    public MimicReleasePacket(int slot) {
        this.slot = slot;
    }

    public static void encode(MimicReleasePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.slot);
    }

    public static MimicReleasePacket decode(FriendlyByteBuf buffer) {
        return new MimicReleasePacket(buffer.readVarInt());
    }

    public static void handle(MimicReleasePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                MimicHandler.handleReleaseChoice(sender, packet.slot);
            }
        });
        context.setPacketHandled(true);
    }
}

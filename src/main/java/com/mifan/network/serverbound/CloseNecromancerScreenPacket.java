package com.mifan.network.serverbound;

import com.mifan.spell.runtime.NecromancerRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CloseNecromancerScreenPacket {

    public CloseNecromancerScreenPacket() {
    }

    public static void encode(CloseNecromancerScreenPacket packet, FriendlyByteBuf buffer) {
    }

    public static CloseNecromancerScreenPacket decode(FriendlyByteBuf buffer) {
        return new CloseNecromancerScreenPacket();
    }

    public static void handle(CloseNecromancerScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            NecromancerRuntime.endSession(sender);
        });
        context.setPacketHandled(true);
    }
}

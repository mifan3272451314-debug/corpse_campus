package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenDominanceScreenPacket {
    public OpenDominanceScreenPacket() {
    }

    public static void encode(OpenDominanceScreenPacket packet, FriendlyByteBuf buffer) {
    }

    public static OpenDominanceScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenDominanceScreenPacket();
    }

    public static void handle(OpenDominanceScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> AbilityClientHandler::openDominanceTargetScreen));
        context.setPacketHandled(true);
    }
}

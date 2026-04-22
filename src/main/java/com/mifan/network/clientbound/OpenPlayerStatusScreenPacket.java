package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenPlayerStatusScreenPacket {
    public OpenPlayerStatusScreenPacket() {
    }

    public static void encode(OpenPlayerStatusScreenPacket packet, FriendlyByteBuf buffer) {
    }

    public static OpenPlayerStatusScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenPlayerStatusScreenPacket();
    }

    public static void handle(OpenPlayerStatusScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> AbilityClientHandler::openPlayerStatusScreen));
        context.setPacketHandled(true);
    }
}

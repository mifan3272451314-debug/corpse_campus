package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenDesignatedAbilityScreenPacket {
    public OpenDesignatedAbilityScreenPacket() {
    }

    public static void encode(OpenDesignatedAbilityScreenPacket packet, FriendlyByteBuf buffer) {
    }

    public static OpenDesignatedAbilityScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenDesignatedAbilityScreenPacket();
    }

    public static void handle(OpenDesignatedAbilityScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> AbilityClientHandler::openDesignatedAbilityScreen));
        context.setPacketHandled(true);
    }
}

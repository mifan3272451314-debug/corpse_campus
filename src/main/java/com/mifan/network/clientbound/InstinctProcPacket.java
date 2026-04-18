package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class InstinctProcPacket {
    private final boolean lastStand;

    public InstinctProcPacket(boolean lastStand) {
        this.lastStand = lastStand;
    }

    public static void encode(InstinctProcPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.lastStand);
    }

    public static InstinctProcPacket decode(FriendlyByteBuf buffer) {
        return new InstinctProcPacket(buffer.readBoolean());
    }

    public static void handle(InstinctProcPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.handleInstinctProc(packet)));
        context.setPacketHandled(true);
    }

    public boolean isLastStand() {
        return lastStand;
    }
}

package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class NecromancerSoulCountPacket {
    private final int totalSouls;

    public NecromancerSoulCountPacket(int totalSouls) {
        this.totalSouls = totalSouls;
    }

    public int getTotalSouls() {
        return totalSouls;
    }

    public static void encode(NecromancerSoulCountPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.totalSouls);
    }

    public static NecromancerSoulCountPacket decode(FriendlyByteBuf buffer) {
        return new NecromancerSoulCountPacket(buffer.readVarInt());
    }

    public static void handle(NecromancerSoulCountPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.updateNecromancerSoulCount(packet.totalSouls)));
        context.setPacketHandled(true);
    }
}

package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DangerSensePingPacket {
    private final int entityId;
    private final int durationTicks;
    private final boolean playAlert;

    public DangerSensePingPacket(int entityId, int durationTicks, boolean playAlert) {
        this.entityId = entityId;
        this.durationTicks = durationTicks;
        this.playAlert = playAlert;
    }

    public static void encode(DangerSensePingPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.entityId);
        buffer.writeVarInt(packet.durationTicks);
        buffer.writeBoolean(packet.playAlert);
    }

    public static DangerSensePingPacket decode(FriendlyByteBuf buffer) {
        return new DangerSensePingPacket(buffer.readInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(DangerSensePingPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.handleDangerPing(packet)));
        context.setPacketHandled(true);
    }

    public int getEntityId() {
        return entityId;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public boolean shouldPlayAlert() {
        return playAlert;
    }
}

package com.mifan.network.serverbound;

import com.mifan.spell.AbilityRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetRecorderOfficerTimerPacket {
    private final int spellLevel;
    private final int targetEntityId;
    private final int timerSeconds;

    public SetRecorderOfficerTimerPacket(int spellLevel, int targetEntityId, int timerSeconds) {
        this.spellLevel = spellLevel;
        this.targetEntityId = targetEntityId;
        this.timerSeconds = timerSeconds;
    }

    public static void encode(SetRecorderOfficerTimerPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.spellLevel);
        buffer.writeVarInt(packet.targetEntityId);
        buffer.writeVarInt(packet.timerSeconds);
    }

    public static SetRecorderOfficerTimerPacket decode(FriendlyByteBuf buffer) {
        return new SetRecorderOfficerTimerPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(SetRecorderOfficerTimerPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                AbilityRuntime.armRecorderOfficerTarget(sender, Math.max(1, packet.spellLevel), packet.targetEntityId,
                        packet.timerSeconds);
            }
        });
        context.setPacketHandled(true);
    }
}

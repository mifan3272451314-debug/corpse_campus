package com.mifan.network.serverbound;

import com.mifan.spell.runtime.MinionMode;
import com.mifan.spell.runtime.NecromancerRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetNecromancerModePacket {
    private final byte modeOrdinal;
    private final boolean cycle;

    public SetNecromancerModePacket(MinionMode mode) {
        this.modeOrdinal = (mode == null ? MinionMode.FOLLOW : mode).toByte();
        this.cycle = false;
    }

    public SetNecromancerModePacket(boolean cycle) {
        this.modeOrdinal = 0;
        this.cycle = cycle;
    }

    private SetNecromancerModePacket(byte mode, boolean cycle) {
        this.modeOrdinal = mode;
        this.cycle = cycle;
    }

    public static void encode(SetNecromancerModePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.cycle);
        buffer.writeByte(packet.modeOrdinal);
    }

    public static SetNecromancerModePacket decode(FriendlyByteBuf buffer) {
        boolean cycle = buffer.readBoolean();
        byte mode = buffer.readByte();
        return new SetNecromancerModePacket(mode, cycle);
    }

    public static void handle(SetNecromancerModePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            MinionMode applied;
            if (packet.cycle) {
                applied = NecromancerRuntime.cycleMode(sender);
            } else {
                applied = MinionMode.fromByte(packet.modeOrdinal);
                NecromancerRuntime.setMode(sender, applied);
            }
            sender.displayClientMessage(
                    Component.translatable("message.corpse_campus.necromancer_mode_changed",
                            Component.translatable(applied.translationKey())),
                    true);
            NecromancerRuntime.pushScreenUpdate(sender);
        });
        context.setPacketHandled(true);
    }
}

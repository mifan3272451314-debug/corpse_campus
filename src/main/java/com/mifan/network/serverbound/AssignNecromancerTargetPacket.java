package com.mifan.network.serverbound;

import com.mifan.spell.runtime.NecromancerRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AssignNecromancerTargetPacket {
    private final boolean clear;

    public AssignNecromancerTargetPacket(boolean clear) {
        this.clear = clear;
    }

    public static void encode(AssignNecromancerTargetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.clear);
    }

    public static AssignNecromancerTargetPacket decode(FriendlyByteBuf buffer) {
        return new AssignNecromancerTargetPacket(buffer.readBoolean());
    }

    public static void handle(AssignNecromancerTargetPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (packet.clear) {
                NecromancerRuntime.clearAssignedTarget(sender);
                sender.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.corpse_campus.necromancer_assign_cleared"),
                        true);
            } else {
                NecromancerRuntime.assignLookedTarget(sender);
            }
            NecromancerRuntime.pushScreenUpdate(sender);
        });
        context.setPacketHandled(true);
    }
}

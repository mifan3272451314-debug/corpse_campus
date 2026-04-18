package com.mifan.network;

import com.mifan.corpsecampus;
import com.mifan.network.clientbound.DangerSensePingPacket;
import com.mifan.network.clientbound.InstinctProcPacket;
import com.mifan.network.clientbound.OpenDominanceScreenPacket;
import com.mifan.network.serverbound.SetDominanceTargetPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(corpsecampus.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int packetId = 0;
    private static boolean registered;

    private ModNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CHANNEL.messageBuilder(DangerSensePingPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DangerSensePingPacket::encode)
                .decoder(DangerSensePingPacket::decode)
                .consumerMainThread(DangerSensePingPacket::handle)
                .add();

        CHANNEL.messageBuilder(InstinctProcPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(InstinctProcPacket::encode)
                .decoder(InstinctProcPacket::decode)
                .consumerMainThread(InstinctProcPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenDominanceScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenDominanceScreenPacket::encode)
                .decoder(OpenDominanceScreenPacket::decode)
                .consumerMainThread(OpenDominanceScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetDominanceTargetPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDominanceTargetPacket::encode)
                .decoder(SetDominanceTargetPacket::decode)
                .consumerMainThread(SetDominanceTargetPacket::handle)
                .add();
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}

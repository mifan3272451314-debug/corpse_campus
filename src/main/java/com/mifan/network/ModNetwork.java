package com.mifan.network;

import com.mifan.corpsecampus;
import com.mifan.network.clientbound.DangerSensePingPacket;
import com.mifan.network.clientbound.InstinctProcPacket;
import com.mifan.network.clientbound.NecromancerSoulCountPacket;
import com.mifan.network.clientbound.OlfactionTrailSyncPacket;
import com.mifan.network.clientbound.OpenDominanceScreenPacket;
import com.mifan.network.clientbound.OpenFerrymanScreenPacket;
import com.mifan.network.clientbound.OpenMidasTouchScreenPacket;
import com.mifan.network.clientbound.OpenMimicAbsorbScreenPacket;
import com.mifan.network.clientbound.OpenMimicReleaseScreenPacket;
import com.mifan.network.clientbound.OpenNecromancerScreenPacket;
import com.mifan.network.clientbound.OpenPlayerStatusScreenPacket;
import com.mifan.network.clientbound.OpenRecorderOfficerScreenPacket;
import com.mifan.network.serverbound.MimicAbsorbPacket;
import com.mifan.network.serverbound.MimicReleasePacket;
import com.mifan.network.serverbound.SetDominanceTargetPacket;
import com.mifan.network.serverbound.SetFerrymanTargetPacket;
import com.mifan.network.serverbound.SetMidasTouchTimerPacket;
import com.mifan.network.serverbound.SetRecorderOfficerTimerPacket;
import com.mifan.network.serverbound.SummonNecromancerMinionPacket;
import com.mifan.network.serverbound.UpgradeAnomalySpellPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "2";

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

        CHANNEL.messageBuilder(OlfactionTrailSyncPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OlfactionTrailSyncPacket::encode)
                .decoder(OlfactionTrailSyncPacket::decode)
                .consumerMainThread(OlfactionTrailSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenDominanceScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenDominanceScreenPacket::encode)
                .decoder(OpenDominanceScreenPacket::decode)
                .consumerMainThread(OpenDominanceScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenMidasTouchScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenMidasTouchScreenPacket::encode)
                .decoder(OpenMidasTouchScreenPacket::decode)
                .consumerMainThread(OpenMidasTouchScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenRecorderOfficerScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenRecorderOfficerScreenPacket::encode)
                .decoder(OpenRecorderOfficerScreenPacket::decode)
                .consumerMainThread(OpenRecorderOfficerScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenPlayerStatusScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenPlayerStatusScreenPacket::encode)
                .decoder(OpenPlayerStatusScreenPacket::decode)
                .consumerMainThread(OpenPlayerStatusScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetDominanceTargetPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDominanceTargetPacket::encode)
                .decoder(SetDominanceTargetPacket::decode)
                .consumerMainThread(SetDominanceTargetPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetMidasTouchTimerPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetMidasTouchTimerPacket::encode)
                .decoder(SetMidasTouchTimerPacket::decode)
                .consumerMainThread(SetMidasTouchTimerPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetRecorderOfficerTimerPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetRecorderOfficerTimerPacket::encode)
                .decoder(SetRecorderOfficerTimerPacket::decode)
                .consumerMainThread(SetRecorderOfficerTimerPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenFerrymanScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenFerrymanScreenPacket::encode)
                .decoder(OpenFerrymanScreenPacket::decode)
                .consumerMainThread(OpenFerrymanScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetFerrymanTargetPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetFerrymanTargetPacket::encode)
                .decoder(SetFerrymanTargetPacket::decode)
                .consumerMainThread(SetFerrymanTargetPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenMimicAbsorbScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenMimicAbsorbScreenPacket::encode)
                .decoder(OpenMimicAbsorbScreenPacket::decode)
                .consumerMainThread(OpenMimicAbsorbScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenMimicReleaseScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenMimicReleaseScreenPacket::encode)
                .decoder(OpenMimicReleaseScreenPacket::decode)
                .consumerMainThread(OpenMimicReleaseScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(MimicAbsorbPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MimicAbsorbPacket::encode)
                .decoder(MimicAbsorbPacket::decode)
                .consumerMainThread(MimicAbsorbPacket::handle)
                .add();

        CHANNEL.messageBuilder(MimicReleasePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MimicReleasePacket::encode)
                .decoder(MimicReleasePacket::decode)
                .consumerMainThread(MimicReleasePacket::handle)
                .add();

        CHANNEL.messageBuilder(UpgradeAnomalySpellPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpgradeAnomalySpellPacket::encode)
                .decoder(UpgradeAnomalySpellPacket::decode)
                .consumerMainThread(UpgradeAnomalySpellPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenNecromancerScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenNecromancerScreenPacket::encode)
                .decoder(OpenNecromancerScreenPacket::decode)
                .consumerMainThread(OpenNecromancerScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(SummonNecromancerMinionPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SummonNecromancerMinionPacket::encode)
                .decoder(SummonNecromancerMinionPacket::decode)
                .consumerMainThread(SummonNecromancerMinionPacket::handle)
                .add();

        CHANNEL.messageBuilder(NecromancerSoulCountPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NecromancerSoulCountPacket::encode)
                .decoder(NecromancerSoulCountPacket::decode)
                .consumerMainThread(NecromancerSoulCountPacket::handle)
                .add();
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}

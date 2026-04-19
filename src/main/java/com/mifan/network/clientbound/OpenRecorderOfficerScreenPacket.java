package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenRecorderOfficerScreenPacket {
    private final int spellLevel;
    private final int targetEntityId;
    private final String targetName;
    private final int defaultSeconds;
    private final int minSeconds;
    private final int maxSeconds;

    public OpenRecorderOfficerScreenPacket(int spellLevel, int targetEntityId, String targetName,
            int defaultSeconds, int minSeconds, int maxSeconds) {
        this.spellLevel = spellLevel;
        this.targetEntityId = targetEntityId;
        this.targetName = targetName;
        this.defaultSeconds = defaultSeconds;
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
    }

    public int getSpellLevel() {
        return spellLevel;
    }

    public int getTargetEntityId() {
        return targetEntityId;
    }

    public String getTargetName() {
        return targetName;
    }

    public int getDefaultSeconds() {
        return defaultSeconds;
    }

    public int getMinSeconds() {
        return minSeconds;
    }

    public int getMaxSeconds() {
        return maxSeconds;
    }

    public static void encode(OpenRecorderOfficerScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.spellLevel);
        buffer.writeVarInt(packet.targetEntityId);
        buffer.writeUtf(packet.targetName);
        buffer.writeVarInt(packet.defaultSeconds);
        buffer.writeVarInt(packet.minSeconds);
        buffer.writeVarInt(packet.maxSeconds);
    }

    public static OpenRecorderOfficerScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenRecorderOfficerScreenPacket(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(32767),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    public static void handle(OpenRecorderOfficerScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.openRecorderOfficerScreen(packet)));
        context.setPacketHandled(true);
    }
}

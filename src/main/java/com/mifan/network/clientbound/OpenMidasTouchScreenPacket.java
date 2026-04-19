package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenMidasTouchScreenPacket {
    private final int spellLevel;
    private final int defaultSeconds;
    private final int minSeconds;
    private final int maxSeconds;
    private final int defaultPowerLevel;
    private final int minPowerLevel;
    private final int maxPowerLevel;

    public OpenMidasTouchScreenPacket(int spellLevel, int defaultSeconds, int minSeconds, int maxSeconds,
            int defaultPowerLevel, int minPowerLevel, int maxPowerLevel) {
        this.spellLevel = spellLevel;
        this.defaultSeconds = defaultSeconds;
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
        this.defaultPowerLevel = defaultPowerLevel;
        this.minPowerLevel = minPowerLevel;
        this.maxPowerLevel = maxPowerLevel;
    }

    public int getSpellLevel() {
        return spellLevel;
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

    public int getDefaultPowerLevel() {
        return defaultPowerLevel;
    }

    public int getMinPowerLevel() {
        return minPowerLevel;
    }

    public int getMaxPowerLevel() {
        return maxPowerLevel;
    }

    public static void encode(OpenMidasTouchScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.spellLevel);
        buffer.writeVarInt(packet.defaultSeconds);
        buffer.writeVarInt(packet.minSeconds);
        buffer.writeVarInt(packet.maxSeconds);
        buffer.writeVarInt(packet.defaultPowerLevel);
        buffer.writeVarInt(packet.minPowerLevel);
        buffer.writeVarInt(packet.maxPowerLevel);
    }

    public static OpenMidasTouchScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenMidasTouchScreenPacket(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    public static void handle(OpenMidasTouchScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.openMidasTouchTimerScreen(packet)));
        context.setPacketHandled(true);
    }
}

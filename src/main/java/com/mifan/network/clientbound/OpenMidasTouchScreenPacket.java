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

    public OpenMidasTouchScreenPacket(int spellLevel, int defaultSeconds, int minSeconds, int maxSeconds) {
        this.spellLevel = spellLevel;
        this.defaultSeconds = defaultSeconds;
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
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

    public static void encode(OpenMidasTouchScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.spellLevel);
        buffer.writeVarInt(packet.defaultSeconds);
        buffer.writeVarInt(packet.minSeconds);
        buffer.writeVarInt(packet.maxSeconds);
    }

    public static OpenMidasTouchScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenMidasTouchScreenPacket(
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

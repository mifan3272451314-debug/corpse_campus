package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenFerrymanScreenPacket {
    private final int spellLevel;

    public OpenFerrymanScreenPacket(int spellLevel) {
        this.spellLevel = spellLevel;
    }

    public int getSpellLevel() {
        return spellLevel;
    }

    public static void encode(OpenFerrymanScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.spellLevel);
    }

    public static OpenFerrymanScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenFerrymanScreenPacket(buffer.readVarInt());
    }

    public static void handle(OpenFerrymanScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.openFerrymanTargetScreen(packet)));
        context.setPacketHandled(true);
    }
}

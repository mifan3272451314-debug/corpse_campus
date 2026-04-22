package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenNecromancerScreenPacket {
    private final List<SoulEntry> souls;
    private final float currentMana;
    private final int enhanceCost;

    public OpenNecromancerScreenPacket(List<SoulEntry> souls, float currentMana, int enhanceCost) {
        this.souls = souls;
        this.currentMana = currentMana;
        this.enhanceCost = enhanceCost;
    }

    public List<SoulEntry> getSouls() {
        return souls;
    }

    public float getCurrentMana() {
        return currentMana;
    }

    public int getEnhanceCost() {
        return enhanceCost;
    }

    public static void encode(OpenNecromancerScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.souls.size());
        for (SoulEntry entry : packet.souls) {
            buffer.writeUtf(entry.typeId);
            buffer.writeVarInt(entry.count);
        }
        buffer.writeFloat(packet.currentMana);
        buffer.writeVarInt(packet.enhanceCost);
    }

    public static OpenNecromancerScreenPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<SoulEntry> souls = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String typeId = buffer.readUtf();
            int count = buffer.readVarInt();
            souls.add(new SoulEntry(typeId, count));
        }
        float mana = buffer.readFloat();
        int enhanceCost = buffer.readVarInt();
        return new OpenNecromancerScreenPacket(souls, mana, enhanceCost);
    }

    public static void handle(OpenNecromancerScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.openNecromancerScreen(packet)));
        context.setPacketHandled(true);
    }

    public static final class SoulEntry {
        private final String typeId;
        private final int count;

        public SoulEntry(String typeId, int count) {
            this.typeId = typeId;
            this.count = count;
        }

        public String typeId() {
            return typeId;
        }

        public int count() {
            return count;
        }
    }
}

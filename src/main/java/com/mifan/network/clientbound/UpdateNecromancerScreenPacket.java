package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class UpdateNecromancerScreenPacket {
    private final List<OpenNecromancerScreenPacket.SoulEntry> souls;
    private final float currentMana;
    private final int enhanceCost;
    private final boolean canCastNormal;
    private final boolean canCastEnhanced;
    private final byte currentMode;

    public UpdateNecromancerScreenPacket(List<OpenNecromancerScreenPacket.SoulEntry> souls, float currentMana,
            int enhanceCost, boolean canCastNormal, boolean canCastEnhanced, byte currentMode) {
        this.souls = souls;
        this.currentMana = currentMana;
        this.enhanceCost = enhanceCost;
        this.canCastNormal = canCastNormal;
        this.canCastEnhanced = canCastEnhanced;
        this.currentMode = currentMode;
    }

    public List<OpenNecromancerScreenPacket.SoulEntry> getSouls() {
        return souls;
    }

    public float getCurrentMana() {
        return currentMana;
    }

    public int getEnhanceCost() {
        return enhanceCost;
    }

    public boolean canCastNormal() {
        return canCastNormal;
    }

    public boolean canCastEnhanced() {
        return canCastEnhanced;
    }

    public byte getCurrentMode() {
        return currentMode;
    }

    public static void encode(UpdateNecromancerScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.souls.size());
        for (OpenNecromancerScreenPacket.SoulEntry entry : packet.souls) {
            buffer.writeUtf(entry.typeId());
            buffer.writeVarInt(entry.count());
        }
        buffer.writeFloat(packet.currentMana);
        buffer.writeVarInt(packet.enhanceCost);
        buffer.writeBoolean(packet.canCastNormal);
        buffer.writeBoolean(packet.canCastEnhanced);
        buffer.writeByte(packet.currentMode);
    }

    public static UpdateNecromancerScreenPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<OpenNecromancerScreenPacket.SoulEntry> souls = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String typeId = buffer.readUtf();
            int count = buffer.readVarInt();
            souls.add(new OpenNecromancerScreenPacket.SoulEntry(typeId, count));
        }
        float mana = buffer.readFloat();
        int enhanceCost = buffer.readVarInt();
        boolean canCastNormal = buffer.readBoolean();
        boolean canCastEnhanced = buffer.readBoolean();
        byte mode = buffer.readByte();
        return new UpdateNecromancerScreenPacket(souls, mana, enhanceCost, canCastNormal, canCastEnhanced, mode);
    }

    public static void handle(UpdateNecromancerScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.updateNecromancerScreen(packet)));
        context.setPacketHandled(true);
    }
}

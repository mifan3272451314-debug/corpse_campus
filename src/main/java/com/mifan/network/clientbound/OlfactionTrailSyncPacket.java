package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OlfactionTrailSyncPacket {
    private final List<Entry> entries;

    public OlfactionTrailSyncPacket(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static void encode(OlfactionTrailSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeVarInt(entry.entityId);
            buffer.writeVarInt(entry.stepIndex);
            buffer.writeDouble(entry.x);
            buffer.writeDouble(entry.y);
            buffer.writeDouble(entry.z);
            buffer.writeVarInt(entry.remainingTicks);
        }
    }

    public static OlfactionTrailSyncPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readVarInt()));
        }
        return new OlfactionTrailSyncPacket(entries);
    }

    public static void handle(OlfactionTrailSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.handleOlfactionTrailSync(packet)));
        context.setPacketHandled(true);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public static final class Entry {
        private final int entityId;
        private final int stepIndex;
        private final double x;
        private final double y;
        private final double z;
        private final int remainingTicks;

        public Entry(int entityId, int stepIndex, double x, double y, double z, int remainingTicks) {
            this.entityId = entityId;
            this.stepIndex = stepIndex;
            this.x = x;
            this.y = y;
            this.z = z;
            this.remainingTicks = remainingTicks;
        }

        public int getEntityId() {
            return entityId;
        }

        public int getStepIndex() {
            return stepIndex;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public int getRemainingTicks() {
            return remainingTicks;
        }
    }
}

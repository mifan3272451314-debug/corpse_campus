package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenMimicReleaseScreenPacket {
    public record SlotEntry(int slot, String spellId) {
    }

    private final List<SlotEntry> entries;

    public OpenMimicReleaseScreenPacket(List<SlotEntry> entries) {
        this.entries = entries;
    }

    public List<SlotEntry> entries() {
        return entries;
    }

    public static void encode(OpenMimicReleaseScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entries.size());
        for (SlotEntry e : packet.entries) {
            buffer.writeVarInt(e.slot());
            buffer.writeUtf(e.spellId());
        }
    }

    public static OpenMimicReleaseScreenPacket decode(FriendlyByteBuf buffer) {
        int n = buffer.readVarInt();
        List<SlotEntry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int slot = buffer.readVarInt();
            String id = buffer.readUtf();
            entries.add(new SlotEntry(slot, id));
        }
        return new OpenMimicReleaseScreenPacket(entries);
    }

    public static void handle(OpenMimicReleaseScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.openMimicReleaseScreen(packet)));
        context.setPacketHandled(true);
    }
}

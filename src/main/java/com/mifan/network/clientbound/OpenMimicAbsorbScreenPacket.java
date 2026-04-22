package com.mifan.network.clientbound;

import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class OpenMimicAbsorbScreenPacket {
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    private final List<String> spellIds;

    public OpenMimicAbsorbScreenPacket(UUID targetPlayerId, String targetPlayerName, List<String> spellIds) {
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        this.spellIds = spellIds;
    }

    public UUID targetPlayerId() {
        return targetPlayerId;
    }

    public String targetPlayerName() {
        return targetPlayerName;
    }

    public List<String> spellIds() {
        return spellIds;
    }

    public static void encode(OpenMimicAbsorbScreenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.targetPlayerId);
        buffer.writeUtf(packet.targetPlayerName);
        buffer.writeVarInt(packet.spellIds.size());
        for (String id : packet.spellIds) {
            buffer.writeUtf(id);
        }
    }

    public static OpenMimicAbsorbScreenPacket decode(FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        String name = buffer.readUtf();
        int n = buffer.readVarInt();
        List<String> spells = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            spells.add(buffer.readUtf());
        }
        return new OpenMimicAbsorbScreenPacket(id, name, spells);
    }

    public static void handle(OpenMimicAbsorbScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.openMimicAbsorbScreen(packet)));
        context.setPacketHandled(true);
    }
}

package com.mifan.network.serverbound;

import com.mifan.spell.MidasBombRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetMidasTouchTimerPacket {
    private final int spellLevel;
    private final int timerSeconds;
    private final int powerLevel;

    public SetMidasTouchTimerPacket(int spellLevel, int timerSeconds, int powerLevel) {
        this.spellLevel = spellLevel;
        this.timerSeconds = timerSeconds;
        this.powerLevel = powerLevel;
    }

    public static void encode(SetMidasTouchTimerPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.spellLevel);
        buffer.writeVarInt(packet.timerSeconds);
        buffer.writeVarInt(packet.powerLevel);
    }

    public static SetMidasTouchTimerPacket decode(FriendlyByteBuf buffer) {
        return new SetMidasTouchTimerPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(SetMidasTouchTimerPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                io.redspace.ironsspellbooks.api.magic.MagicData magicData = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(sender);
                if (com.mifan.spell.rizhao.MidasTouchSpell.consumeMidasMana(sender, magicData, packet.powerLevel)) {
                    MidasBombRuntime.armFromPlayerSelection(sender, Math.max(1, packet.spellLevel), packet.timerSeconds, packet.powerLevel);
                }
            }
        });
        context.setPacketHandled(true);
    }
}

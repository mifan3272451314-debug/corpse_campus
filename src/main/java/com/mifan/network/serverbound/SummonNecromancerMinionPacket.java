package com.mifan.network.serverbound;

import com.mifan.spell.runtime.NecromancerRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SummonNecromancerMinionPacket {
    private final String typeId;
    private final boolean forceEnhanced;

    public SummonNecromancerMinionPacket(String typeId, boolean forceEnhanced) {
        this.typeId = typeId;
        this.forceEnhanced = forceEnhanced;
    }

    public static void encode(SummonNecromancerMinionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.typeId);
        buffer.writeBoolean(packet.forceEnhanced);
    }

    public static SummonNecromancerMinionPacket decode(FriendlyByteBuf buffer) {
        return new SummonNecromancerMinionPacket(buffer.readUtf(), buffer.readBoolean());
    }

    public static void handle(SummonNecromancerMinionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ResourceLocation typeId = ResourceLocation.tryParse(packet.typeId);
            if (typeId == null) {
                return;
            }
            NecromancerRuntime.SummonResult result = NecromancerRuntime.summon(sender, typeId, packet.forceEnhanced);
            if (!result.success()) {
                sender.displayClientMessage(Component.translatable(result.failKey()), true);
                return;
            }
            EntityType<?> type = result.type();
            Component name = type != null
                    ? type.getDescription()
                    : Component.literal(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
            String key = result.enhanced()
                    ? "message.corpse_campus.necromancer_summoned_enhanced"
                    : "message.corpse_campus.necromancer_summoned";
            sender.displayClientMessage(Component.translatable(key, name), false);
            sender.level().playSound(null, sender.blockPosition(), SoundEvents.WITHER_SPAWN,
                    SoundSource.PLAYERS, 0.35F, result.enhanced() ? 1.4F : 1.0F);
        });
        context.setPacketHandled(true);
    }
}

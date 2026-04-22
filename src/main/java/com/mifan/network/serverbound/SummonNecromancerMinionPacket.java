package com.mifan.network.serverbound;

import com.mifan.spell.AbilityRuntime;
import com.mifan.spell.runtime.EnhancementType;
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
    private final byte enhancementOrdinal;

    public SummonNecromancerMinionPacket(String typeId, EnhancementType enhancement) {
        this.typeId = typeId;
        this.enhancementOrdinal = (enhancement == null ? EnhancementType.NONE : enhancement).toByte();
    }

    private SummonNecromancerMinionPacket(String typeId, byte enhancementOrdinal) {
        this.typeId = typeId;
        this.enhancementOrdinal = enhancementOrdinal;
    }

    public static void encode(SummonNecromancerMinionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.typeId);
        buffer.writeByte(packet.enhancementOrdinal);
    }

    public static SummonNecromancerMinionPacket decode(FriendlyByteBuf buffer) {
        String typeId = buffer.readUtf();
        byte enhancement = buffer.readByte();
        return new SummonNecromancerMinionPacket(typeId, enhancement);
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
            EnhancementType enhancement = EnhancementType.fromByte(packet.enhancementOrdinal);
            NecromancerRuntime.Session session = NecromancerRuntime.getSession(sender);
            boolean enhancedCost = enhancement.isEnhanced();
            int cost = session == null
                    ? (enhancedCost ? AbilityRuntime.NECROMANCER_ENHANCE_MANA_COST : 0)
                    : (enhancedCost ? session.enhancedCost() : session.normalCost());

            NecromancerRuntime.SummonResult result = NecromancerRuntime.summon(sender, typeId, enhancement, cost);
            if (!result.success()) {
                sender.displayClientMessage(Component.translatable(result.failKey()), true);
                NecromancerRuntime.pushScreenUpdate(sender);
                return;
            }
            EntityType<?> type = result.type();
            Component name = type != null
                    ? type.getDescription()
                    : Component.literal(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
            String key = buildMessageKey(result.enhancement());
            sender.displayClientMessage(Component.translatable(key, name), false);
            sender.level().playSound(null, sender.blockPosition(), SoundEvents.WITHER_SPAWN,
                    SoundSource.PLAYERS, 0.35F, result.enhanced() ? 1.4F : 1.0F);
            NecromancerRuntime.pushScreenUpdate(sender);
        });
        context.setPacketHandled(true);
    }

    private static String buildMessageKey(EnhancementType enhancement) {
        return switch (enhancement) {
            case SPEED -> "message.corpse_campus.necromancer_summoned_speed";
            case ATTACK -> "message.corpse_campus.necromancer_summoned_attack";
            case DEFENSE -> "message.corpse_campus.necromancer_summoned_defense";
            case HEALTH -> "message.corpse_campus.necromancer_summoned_health";
            default -> "message.corpse_campus.necromancer_summoned";
        };
    }
}

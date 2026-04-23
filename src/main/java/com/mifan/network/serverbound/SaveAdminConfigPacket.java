package com.mifan.network.serverbound;

import com.mifan.admin.AdminPanelRegistry;
import com.mifan.anomaly.AnomalyConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 客户端在 {@link com.mifan.client.screen.AdminPanelScreen} 点保存时发送。
 * 携带被修改字段的 (path → stringified value) 差异 map,服务端 op2 check 后批量写入 + 落盘。
 */
public class SaveAdminConfigPacket {

    private final Map<String, String> changes;

    public SaveAdminConfigPacket(Map<String, String> changes) {
        this.changes = changes;
    }

    public static void encode(SaveAdminConfigPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.changes.size());
        for (Map.Entry<String, String> e : packet.changes.entrySet()) {
            buffer.writeUtf(e.getKey());
            buffer.writeUtf(e.getValue());
        }
    }

    public static SaveAdminConfigPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        Map<String, String> changes = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            String k = buffer.readUtf();
            String v = buffer.readUtf();
            changes.put(k, v);
        }
        return new SaveAdminConfigPacket(changes);
    }

    public static void handle(SaveAdminConfigPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) {
                return;
            }
            if (!sender.hasPermissions(2)) {
                sender.displayClientMessage(
                        Component.translatable("message.corpse_campus.admin.no_permission")
                                .withStyle(ChatFormatting.RED), true);
                return;
            }

            int ok = 0;
            int fail = 0;
            for (Map.Entry<String, String> e : packet.changes.entrySet()) {
                if (AdminPanelRegistry.writeField(AnomalyConfig.SPEC, e.getKey(), e.getValue())) {
                    ok++;
                } else {
                    fail++;
                }
            }
            final int okFinal = ok;
            final int failFinal = fail;
            if (failFinal == 0) {
                sender.displayClientMessage(
                        Component.translatable("message.corpse_campus.admin.config_saved", okFinal)
                                .withStyle(ChatFormatting.GREEN), false);
            } else {
                sender.displayClientMessage(
                        Component.translatable("message.corpse_campus.admin.config_partial", okFinal, failFinal)
                                .withStyle(ChatFormatting.YELLOW), false);
            }
        });
        ctx.setPacketHandled(true);
    }
}

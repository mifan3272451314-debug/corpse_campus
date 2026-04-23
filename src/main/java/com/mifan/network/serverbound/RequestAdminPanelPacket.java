package com.mifan.network.serverbound;

import com.mifan.admin.AdminPanelRegistry;
import com.mifan.admin.CommandDescriptor;
import com.mifan.admin.ConfigFieldDescriptor;
import com.mifan.anomaly.AnomalyConfig;
import com.mifan.network.ModNetwork;
import com.mifan.network.clientbound.OpenAdminPanelPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端按下"打开管理员面板"键时发送。服务端 op2 check 后回 {@link OpenAdminPanelPacket},
 * 携带最新的 config 和 command 扫描快照。
 */
public class RequestAdminPanelPacket {

    public RequestAdminPanelPacket() {
    }

    public static void encode(RequestAdminPanelPacket packet, FriendlyByteBuf buffer) {
    }

    public static RequestAdminPanelPacket decode(FriendlyByteBuf buffer) {
        return new RequestAdminPanelPacket();
    }

    public static void handle(RequestAdminPanelPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
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

            List<ConfigFieldDescriptor> fields = AdminPanelRegistry.scanConfigSpec(AnomalyConfig.SPEC);
            List<CommandDescriptor> commands = AdminPanelRegistry.scanCommandTree(
                    sender.getServer().getCommands().getDispatcher(), "magic");

            ModNetwork.sendToPlayer(new OpenAdminPanelPacket(fields, commands), sender);
        });
        ctx.setPacketHandled(true);
    }
}

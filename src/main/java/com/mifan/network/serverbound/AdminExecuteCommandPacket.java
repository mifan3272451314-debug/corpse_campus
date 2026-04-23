package com.mifan.network.serverbound;

import com.mifan.admin.AdminPanelRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端在管理员 GUI 指令 tab 点"执行"时发送。
 * 服务端 op2 check 后走 {@link AdminPanelRegistry#executeCommand}——经 Brigadier 标准管道,
 * 原指令的 .requires() 会自然生效,即使伪造包也会被原链上的权限判断拒绝。
 */
public class AdminExecuteCommandPacket {

    private final String command;

    public AdminExecuteCommandPacket(String command) {
        this.command = command;
    }

    public static void encode(AdminExecuteCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.command, 512);
    }

    public static AdminExecuteCommandPacket decode(FriendlyByteBuf buffer) {
        return new AdminExecuteCommandPacket(buffer.readUtf(512));
    }

    public static void handle(AdminExecuteCommandPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
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
            AdminPanelRegistry.executeCommand(sender.getServer(),
                    sender.createCommandSourceStack(), packet.command);
        });
        ctx.setPacketHandled(true);
    }
}

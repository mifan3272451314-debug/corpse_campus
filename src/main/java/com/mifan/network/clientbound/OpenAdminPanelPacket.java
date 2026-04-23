package com.mifan.network.clientbound;

import com.mifan.admin.CommandDescriptor;
import com.mifan.admin.ConfigFieldDescriptor;
import com.mifan.spell.AbilityClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端在 {@link com.mifan.network.serverbound.RequestAdminPanelPacket} 校验权限通过后下发。
 * 携带 config 字段快照和 command 描述符列表,客户端直接用于构建 {@link com.mifan.client.screen.AdminPanelScreen}。
 */
public class OpenAdminPanelPacket {

    private final List<ConfigFieldDescriptor> fields;
    private final List<CommandDescriptor> commands;

    public OpenAdminPanelPacket(List<ConfigFieldDescriptor> fields, List<CommandDescriptor> commands) {
        this.fields = fields;
        this.commands = commands;
    }

    public List<ConfigFieldDescriptor> fields() {
        return fields;
    }

    public List<CommandDescriptor> commands() {
        return commands;
    }

    public static void encode(OpenAdminPanelPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.fields.size());
        for (ConfigFieldDescriptor d : packet.fields) {
            buffer.writeUtf(d.path());
            buffer.writeByte(d.type().ordinal());
            buffer.writeUtf(d.comment() == null ? "" : d.comment());
            buffer.writeUtf(String.valueOf(d.defaultValue()));
            buffer.writeUtf(String.valueOf(d.currentValue()));
            buffer.writeBoolean(d.rangeMin() != null);
            if (d.rangeMin() != null) {
                buffer.writeDouble(d.rangeMin().doubleValue());
            }
            buffer.writeBoolean(d.rangeMax() != null);
            if (d.rangeMax() != null) {
                buffer.writeDouble(d.rangeMax().doubleValue());
            }
            List<String> enumOpts = d.enumOptions() == null ? List.of() : d.enumOptions();
            buffer.writeVarInt(enumOpts.size());
            for (String opt : enumOpts) {
                buffer.writeUtf(opt);
            }
        }

        buffer.writeVarInt(packet.commands.size());
        for (CommandDescriptor c : packet.commands) {
            buffer.writeUtf(c.fullPath());
            buffer.writeUtf(c.category());
            buffer.writeUtf(c.description() == null ? "" : c.description());
            buffer.writeVarInt(c.arguments().size());
            for (CommandDescriptor.ArgumentInfo a : c.arguments()) {
                buffer.writeUtf(a.name());
                buffer.writeUtf(a.type());
            }
            buffer.writeBoolean(c.hasExecutable());
            buffer.writeVarInt(c.requiredPermLvl());
        }
    }

    public static OpenAdminPanelPacket decode(FriendlyByteBuf buffer) {
        int fieldCount = buffer.readVarInt();
        List<ConfigFieldDescriptor> fields = new ArrayList<>(fieldCount);
        ConfigFieldDescriptor.FieldType[] typeValues = ConfigFieldDescriptor.FieldType.values();
        for (int i = 0; i < fieldCount; i++) {
            String path = buffer.readUtf();
            int typeIdx = buffer.readByte();
            ConfigFieldDescriptor.FieldType type = typeValues[Math.max(0, Math.min(typeIdx, typeValues.length - 1))];
            String comment = buffer.readUtf();
            String def = buffer.readUtf();
            String cur = buffer.readUtf();
            Number min = buffer.readBoolean() ? buffer.readDouble() : null;
            Number max = buffer.readBoolean() ? buffer.readDouble() : null;
            int optCount = buffer.readVarInt();
            List<String> opts = optCount == 0 ? null : new ArrayList<>(optCount);
            for (int j = 0; j < optCount; j++) {
                opts.add(buffer.readUtf());
            }
            fields.add(new ConfigFieldDescriptor(path, type, comment, def, cur, min, max, opts));
        }

        int cmdCount = buffer.readVarInt();
        List<CommandDescriptor> commands = new ArrayList<>(cmdCount);
        for (int i = 0; i < cmdCount; i++) {
            String fullPath = buffer.readUtf();
            String category = buffer.readUtf();
            String description = buffer.readUtf();
            int argCount = buffer.readVarInt();
            List<CommandDescriptor.ArgumentInfo> args = new ArrayList<>(argCount);
            for (int j = 0; j < argCount; j++) {
                args.add(new CommandDescriptor.ArgumentInfo(buffer.readUtf(), buffer.readUtf()));
            }
            boolean hasExec = buffer.readBoolean();
            int perm = buffer.readVarInt();
            commands.add(new CommandDescriptor(fullPath, category, description, args, hasExec, perm));
        }

        return new OpenAdminPanelPacket(fields, commands);
    }

    public static void handle(OpenAdminPanelPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityClientHandler.openAdminPanelScreen(packet)));
        ctx.setPacketHandled(true);
    }
}

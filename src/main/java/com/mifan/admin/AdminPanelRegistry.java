package com.mifan.admin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员面板的"自省扫描"基础类。
 *
 * 两条职责，都是为 AdminPanelScreen 和 /magic rules 指令共同服务：
 *   1. {@link #scanConfigSpec(ForgeConfigSpec)} 扫 ForgeConfigSpec 的字段树，产出扁平化的
 *      {@link ConfigFieldDescriptor} 列表。新增任何 config 字段无需改 GUI。
 *   2. {@link #scanCommandTree(CommandDispatcher, String)} 递归遍历 Brigadier 的指令树，
 *      产出 {@link CommandDescriptor} 列表，GUI 用第二级 literal 做分类键自动归组。
 *
 * 还提供 {@link #executeCommand(MinecraftServer, CommandSourceStack, String)} 作为
 * 安全执行入口，内部走 dispatcher 的正常管道，原指令的 .requires() 会自然生效。
 */
public final class AdminPanelRegistry {

    private AdminPanelRegistry() {
    }

    // ────────────────────────── Config 扫描 ──────────────────────────

    public static List<ConfigFieldDescriptor> scanConfigSpec(ForgeConfigSpec spec) {
        List<ConfigFieldDescriptor> out = new ArrayList<>();
        com.electronwill.nightconfig.core.UnmodifiableConfig specRoot = spec.getSpec();
        com.electronwill.nightconfig.core.UnmodifiableConfig valuesRoot = spec.getValues();
        walkConfig("", specRoot, valuesRoot, out);
        return out;
    }

    public static ConfigFieldDescriptor findField(ForgeConfigSpec spec, String path) {
        for (ConfigFieldDescriptor d : scanConfigSpec(spec)) {
            if (d.path().equals(path)) {
                return d;
            }
        }
        return null;
    }

    /** 按路径反查底层 ConfigValue，供写入用。返回 null 表示路径不存在。 */
    @SuppressWarnings("unchecked")
    public static ForgeConfigSpec.ConfigValue<Object> resolveValue(ForgeConfigSpec spec, String path) {
        String[] segs = path.split("\\.");
        com.electronwill.nightconfig.core.UnmodifiableConfig cur = spec.getValues();
        for (int i = 0; i < segs.length - 1; i++) {
            Object next = cur.valueMap().get(segs[i]);
            if (!(next instanceof com.electronwill.nightconfig.core.UnmodifiableConfig sub)) {
                return null;
            }
            cur = sub;
        }
        Object leaf = cur.valueMap().get(segs[segs.length - 1]);
        if (leaf instanceof ForgeConfigSpec.ConfigValue<?> cv) {
            return (ForgeConfigSpec.ConfigValue<Object>) cv;
        }
        return null;
    }

    private static void walkConfig(String prefix,
            com.electronwill.nightconfig.core.UnmodifiableConfig specNode,
            com.electronwill.nightconfig.core.UnmodifiableConfig valuesNode,
            List<ConfigFieldDescriptor> out) {
        for (Map.Entry<String, Object> entry : specNode.valueMap().entrySet()) {
            String key = entry.getKey();
            Object specVal = entry.getValue();
            Object valVal = valuesNode.valueMap().get(key);
            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;

            if (specVal instanceof com.electronwill.nightconfig.core.UnmodifiableConfig subSpec
                    && valVal instanceof com.electronwill.nightconfig.core.UnmodifiableConfig subVal) {
                walkConfig(fullPath, subSpec, subVal, out);
                continue;
            }

            if (!(specVal instanceof ForgeConfigSpec.ValueSpec vs)
                    || !(valVal instanceof ForgeConfigSpec.ConfigValue<?> cv)) {
                continue;
            }

            Object defaultValue = vs.getDefault();
            Object currentValue;
            try {
                currentValue = cv.get();
            } catch (Exception ignored) {
                currentValue = defaultValue;
            }
            ConfigFieldDescriptor.FieldType type = inferType(defaultValue, vs);
            Number[] range = extractRange(vs);
            out.add(new ConfigFieldDescriptor(
                    fullPath,
                    type,
                    String.valueOf(vs.getComment()),
                    defaultValue,
                    currentValue,
                    range[0],
                    range[1],
                    null));
        }
    }

    private static ConfigFieldDescriptor.FieldType inferType(Object defaultValue, ForgeConfigSpec.ValueSpec vs) {
        if (defaultValue instanceof Boolean) return ConfigFieldDescriptor.FieldType.BOOL;
        if (defaultValue instanceof Integer) return ConfigFieldDescriptor.FieldType.INT;
        if (defaultValue instanceof Long) return ConfigFieldDescriptor.FieldType.LONG;
        if (defaultValue instanceof Double || defaultValue instanceof Float) return ConfigFieldDescriptor.FieldType.DOUBLE;
        if (defaultValue instanceof String) return ConfigFieldDescriptor.FieldType.STRING;
        if (defaultValue instanceof Enum<?>) return ConfigFieldDescriptor.FieldType.ENUM;
        Class<?> clazz = vs.getClazz();
        if (clazz == Boolean.class) return ConfigFieldDescriptor.FieldType.BOOL;
        if (clazz == Integer.class) return ConfigFieldDescriptor.FieldType.INT;
        return ConfigFieldDescriptor.FieldType.UNKNOWN;
    }

    /**
     * 取 ValueSpec 的 range(min/max),不同 Forge 版本 API 不一致,用反射兜底。
     * 拿不到即返回 {null, null}。
     */
    private static Number[] extractRange(ForgeConfigSpec.ValueSpec vs) {
        try {
            var method = vs.getClass().getMethod("getRange");
            Object range = method.invoke(vs);
            if (range == null) {
                return new Number[]{null, null};
            }
            Number min = null;
            Number max = null;
            try {
                var getMin = range.getClass().getMethod("getMin");
                getMin.setAccessible(true);
                Object v = getMin.invoke(range);
                if (v instanceof Number n) min = n;
            } catch (Exception ignored) {
            }
            try {
                var getMax = range.getClass().getMethod("getMax");
                getMax.setAccessible(true);
                Object v = getMax.invoke(range);
                if (v instanceof Number n) max = n;
            } catch (Exception ignored) {
            }
            return new Number[]{min, max};
        } catch (Exception ignored) {
            return new Number[]{null, null};
        }
    }

    /**
     * 写入单个字段。返回是否成功。成功后调用方需另行保存 config 到磁盘。
     * 字符串 value 会按 type 自动解析(true/false/int/double 等)。
     */
    public static boolean writeField(ForgeConfigSpec spec, String path, String value) {
        ForgeConfigSpec.ConfigValue<Object> cv = resolveValue(spec, path);
        if (cv == null) {
            return false;
        }
        ConfigFieldDescriptor descriptor = findField(spec, path);
        if (descriptor == null) {
            return false;
        }
        Object parsed;
        try {
            parsed = switch (descriptor.type()) {
                case BOOL -> Boolean.parseBoolean(value);
                case INT -> Integer.parseInt(value);
                case LONG -> Long.parseLong(value);
                case DOUBLE -> Double.parseDouble(value);
                case STRING -> value;
                case ENUM, UNKNOWN -> value;
            };
        } catch (NumberFormatException e) {
            return false;
        }
        try {
            cv.set(parsed);
            cv.save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ────────────────────────── 指令扫描 ──────────────────────────

    /** 按手册分组顺序排序,同组内按 fullPath 字典序。 */
    private static final List<String> CATEGORY_ORDER = List.of(
            "book", "spell", "awaken", "state", "mana", "trait",
            "limit", "seal", "rewind", "refresh", "list", "rules", "help", "other");

    public static List<CommandDescriptor> scanCommandTree(CommandDispatcher<CommandSourceStack> dispatcher,
            String rootLiteral) {
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild(rootLiteral);
        if (root == null) {
            return List.of();
        }
        List<CommandDescriptor> out = new ArrayList<>();
        walkCommand(root, rootLiteral, new ArrayList<>(), out);
        out.sort((a, b) -> {
            int oa = CATEGORY_ORDER.indexOf(a.category());
            int ob = CATEGORY_ORDER.indexOf(b.category());
            if (oa < 0) oa = CATEGORY_ORDER.size();
            if (ob < 0) ob = CATEGORY_ORDER.size();
            if (oa != ob) return Integer.compare(oa, ob);
            return a.fullPath().compareTo(b.fullPath());
        });
        return out;
    }

    private static void walkCommand(CommandNode<CommandSourceStack> node, String fullPath,
            List<CommandDescriptor.ArgumentInfo> accumulatedArgs, List<CommandDescriptor> out) {
        boolean isExecutable = node.getCommand() != null;
        if (isExecutable) {
            CommandMetadata.Meta meta = CommandMetadata.lookup(fullPath);
            String category = meta.category().equals("other") ? deriveCategory(fullPath) : meta.category();
            out.add(new CommandDescriptor(fullPath, category, meta.description(),
                    List.copyOf(accumulatedArgs), true, 2));
        }

        Collection<CommandNode<CommandSourceStack>> children = node.getChildren();
        for (CommandNode<CommandSourceStack> child : children) {
            if (child instanceof LiteralCommandNode<CommandSourceStack> lit) {
                String nextPath = fullPath + " " + lit.getLiteral();
                walkCommand(child, nextPath, accumulatedArgs, out);
            } else if (child instanceof ArgumentCommandNode<?, ?> arg) {
                List<CommandDescriptor.ArgumentInfo> nextArgs = new ArrayList<>(accumulatedArgs);
                nextArgs.add(new CommandDescriptor.ArgumentInfo(arg.getName(),
                        arg.getType().getClass().getSimpleName()));
                walkCommand(child, fullPath, nextArgs, out);
            }
        }
    }

    /**
     * fallback 分类:元数据表未命中时使用。
     * 只有一段(magic X)→ "other";否则取 fullPath 第二段。
     */
    private static String deriveCategory(String fullPath) {
        String[] parts = fullPath.split(" ");
        if (parts.length < 3) return "other";
        return parts[1];
    }

    /** 把指令列表按 category 分组。 */
    public static Map<String, List<CommandDescriptor>> groupByCategory(List<CommandDescriptor> commands) {
        Map<String, List<CommandDescriptor>> grouped = new HashMap<>();
        for (CommandDescriptor cd : commands) {
            grouped.computeIfAbsent(cd.category(), k -> new ArrayList<>()).add(cd);
        }
        return grouped;
    }

    // ────────────────────────── 指令执行 ──────────────────────────

    /**
     * 通过 dispatcher 重新执行指令字符串。走 Brigadier 标准管道,原指令的 .requires() 会自然跑。
     * 命令文本不应以 "/" 开头。
     */
    public static int executeCommand(MinecraftServer server, CommandSourceStack source, String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        return server.getCommands().performPrefixedCommand(source, cmd);
    }
}

package com.mifan.client.screen;

import com.mifan.admin.CommandDescriptor;
import com.mifan.admin.ConfigFieldDescriptor;
import com.mifan.network.ModNetwork;
import com.mifan.network.serverbound.AdminExecuteCommandPacket;
import com.mifan.network.serverbound.SaveAdminConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员控制面板:左列表(可切 tab 配置/指令) + 右详情编辑 + 底部[恢复默认][保存][关闭]。
 *
 * 列表分类键取自 {@link ConfigFieldDescriptor#group()} 和 {@link CommandDescriptor#category()},
 * 由 {@link com.mifan.admin.AdminPanelRegistry} 扫描得出——新增 config 字段 / 指令会自动出现。
 *
 * 编辑值先暂存在 {@link #pendingChanges},点"保存"才发 {@link SaveAdminConfigPacket}。
 * 指令执行发 {@link AdminExecuteCommandPacket},服务端走 Brigadier 标准管道。
 */
public class AdminPanelScreen extends Screen {

    private static final int PANEL_W = 520;
    private static final int PANEL_H = 320;
    private static final int LIST_W = 240;
    private static final int ROW_H = 16;

    /** 分类 key → 中文标签。未命中则显示 key 本身。 */
    private static final Map<String, String> CATEGORY_LABEL = Map.ofEntries(
            Map.entry("book", "书管理"),
            Map.entry("spell", "法术管理"),
            Map.entry("awaken", "觉醒状态"),
            Map.entry("state", "状态与重算"),
            Map.entry("mana", "法力/属性"),
            Map.entry("trait", "异常特性"),
            Map.entry("limit", "上限系统"),
            Map.entry("seal", "封印"),
            Map.entry("rewind", "回溯之虫"),
            Map.entry("refresh", "刷新"),
            Map.entry("list", "查询"),
            Map.entry("rules", "规则"),
            Map.entry("help", "帮助"),
            Map.entry("other", "其它"),
            Map.entry("limit_group", "上限配置"),
            Map.entry("rules_group", "核心规则"));

    private static String labelFor(String cat) {
        return CATEGORY_LABEL.getOrDefault(cat, cat);
    }

    private final List<ConfigFieldDescriptor> configFields;
    private final List<CommandDescriptor> commands;

    /** path → 用户当前编辑值(字符串形式)。空即未修改。 */
    private final Map<String, String> pendingChanges = new HashMap<>();

    private enum Tab { CONFIG, COMMAND }
    private Tab tab = Tab.CONFIG;

    private int scrollOffset;
    private int selectedRow = -1;

    /** 指令 tab 用:argName → EditBox */
    private final Map<String, EditBox> argEditors = new LinkedHashMap<>();
    /** 配置 tab 用:当前编辑框(单个) */
    private EditBox configValueEditor;
    /** 配置 tab bool 快捷切换按钮 */
    private Button configBoolToggle;

    private Button saveButton;
    private Button resetButton;
    private Button closeButton;
    private Button tabConfigButton;
    private Button tabCommandButton;
    private Button executeCommandButton;

    public AdminPanelScreen(List<ConfigFieldDescriptor> configFields, List<CommandDescriptor> commands) {
        super(Component.translatable("screen.corpse_campus.admin_panel.title"));
        this.configFields = configFields;
        this.commands = commands;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }
    private int panelTop() { return (this.height - PANEL_H) / 2; }

    @Override
    protected void init() {
        int pl = panelLeft();
        int pt = panelTop();

        tabConfigButton = Button.builder(
                Component.translatable("screen.corpse_campus.admin_panel.tab_config", configFields.size()),
                b -> switchTab(Tab.CONFIG))
                .bounds(pl + 10, pt + 10, 110, 18).build();
        tabCommandButton = Button.builder(
                Component.translatable("screen.corpse_campus.admin_panel.tab_command", commands.size()),
                b -> switchTab(Tab.COMMAND))
                .bounds(pl + 125, pt + 10, 110, 18).build();

        resetButton = Button.builder(
                Component.translatable("screen.corpse_campus.admin_panel.reset"),
                b -> resetPending())
                .bounds(pl + 10, pt + PANEL_H - 26, 100, 18).build();
        saveButton = Button.builder(
                Component.translatable("screen.corpse_campus.admin_panel.save"),
                b -> saveAndClose())
                .bounds(pl + 120, pt + PANEL_H - 26, 100, 18).build();
        closeButton = Button.builder(
                Component.translatable("screen.corpse_campus.admin_panel.close"),
                b -> this.onClose())
                .bounds(pl + PANEL_W - 110, pt + PANEL_H - 26, 100, 18).build();

        this.addRenderableWidget(tabConfigButton);
        this.addRenderableWidget(tabCommandButton);
        this.addRenderableWidget(resetButton);
        this.addRenderableWidget(saveButton);
        this.addRenderableWidget(closeButton);

        rebuildDetailWidgets();
    }

    private void switchTab(Tab newTab) {
        this.tab = newTab;
        this.selectedRow = -1;
        this.scrollOffset = 0;
        rebuildDetailWidgets();
    }

    private void rebuildDetailWidgets() {
        if (configValueEditor != null) this.removeWidget(configValueEditor);
        if (configBoolToggle != null) this.removeWidget(configBoolToggle);
        if (executeCommandButton != null) this.removeWidget(executeCommandButton);
        for (EditBox eb : argEditors.values()) this.removeWidget(eb);
        configValueEditor = null;
        configBoolToggle = null;
        executeCommandButton = null;
        argEditors.clear();

        if (selectedRow < 0) return;

        int pl = panelLeft();
        int pt = panelTop();
        int detailX = pl + LIST_W + 20;
        int detailY = pt + 60;

        if (tab == Tab.CONFIG) {
            ConfigFieldDescriptor d = configFields.get(selectedRow);
            String currentText = pendingChanges.getOrDefault(d.path(), String.valueOf(d.currentValue()));
            if (d.type() == ConfigFieldDescriptor.FieldType.BOOL) {
                boolean cur = Boolean.parseBoolean(currentText);
                configBoolToggle = Button.builder(
                        Component.literal(cur ? "§a■ 开启 (true)" : "§c□ 关闭 (false)"),
                        b -> {
                            String next = cur ? "false" : "true";
                            pendingChanges.put(d.path(), next);
                            rebuildDetailWidgets();
                        })
                        .bounds(detailX, detailY + 40, 200, 20).build();
                this.addRenderableWidget(configBoolToggle);
            } else {
                configValueEditor = new EditBox(this.font, detailX, detailY + 40, 240, 18,
                        Component.literal("value"));
                configValueEditor.setMaxLength(256);
                configValueEditor.setValue(currentText);
                configValueEditor.setResponder(s -> pendingChanges.put(d.path(), s));
                this.addRenderableWidget(configValueEditor);
            }
        } else {
            CommandDescriptor c = commands.get(selectedRow);
            int argY = detailY + 40;
            for (CommandDescriptor.ArgumentInfo arg : c.arguments()) {
                EditBox eb = new EditBox(this.font, detailX, argY, 240, 16,
                        Component.literal(arg.name()));
                eb.setHint(Component.literal(arg.name() + " : " + arg.type()));
                eb.setMaxLength(128);
                argEditors.put(arg.name(), eb);
                this.addRenderableWidget(eb);
                argY += 20;
            }
            executeCommandButton = Button.builder(
                    Component.translatable("screen.corpse_campus.admin_panel.execute"),
                    b -> executeSelectedCommand())
                    .bounds(detailX, argY + 4, 140, 18).build();
            this.addRenderableWidget(executeCommandButton);
        }
    }

    private void executeSelectedCommand() {
        if (selectedRow < 0) return;
        CommandDescriptor c = commands.get(selectedRow);
        StringBuilder sb = new StringBuilder(c.fullPath());
        for (CommandDescriptor.ArgumentInfo arg : c.arguments()) {
            EditBox eb = argEditors.get(arg.name());
            String val = eb == null ? "" : eb.getValue().trim();
            if (val.isEmpty()) {
                return; // 参数必填
            }
            sb.append(' ').append(val);
        }
        ModNetwork.CHANNEL.sendToServer(new AdminExecuteCommandPacket(sb.toString()));
    }

    private void resetPending() {
        pendingChanges.clear();
        rebuildDetailWidgets();
    }

    private void saveAndClose() {
        if (!pendingChanges.isEmpty()) {
            ModNetwork.CHANNEL.sendToServer(new SaveAdminConfigPacket(new HashMap<>(pendingChanges)));
            pendingChanges.clear();
        }
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int pl = panelLeft();
        int pt = panelTop();

        // 外框
        g.fill(pl - 2, pt - 2, pl + PANEL_W + 2, pt + PANEL_H + 2, 0xFF202020);
        g.fill(pl, pt, pl + PANEL_W, pt + PANEL_H, 0xFF101018);

        // 标题
        g.drawString(this.font, this.title, pl + 10, pt + 36, 0xFFFFD700, false);

        // tab 高亮
        int tabHighlight = 0xFF3A3A60;
        if (tab == Tab.CONFIG) g.fill(pl + 10, pt + 28, pl + 120, pt + 30, tabHighlight);
        else g.fill(pl + 125, pt + 28, pl + 235, pt + 30, tabHighlight);

        // 左列表区
        int listTop = pt + 55;
        int listBottom = pt + PANEL_H - 35;
        g.fill(pl + 8, listTop - 2, pl + 8 + LIST_W, listBottom + 2, 0xFF1A1A24);

        int visibleRows = (listBottom - listTop) / ROW_H;
        List<String> rowTexts = buildRowTexts();
        int total = rowTexts.size();
        int maxScroll = Math.max(0, total - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        for (int i = 0; i < visibleRows && (i + scrollOffset) < total; i++) {
            int idx = i + scrollOffset;
            int rowY = listTop + i * ROW_H;
            // selectedRow 存真实 descriptor 索引,渲染时把当前 visible 行也 map 到 real 再比较
            int realOfThisRow = mapVisibleToReal(idx);
            boolean isSelected = realOfThisRow >= 0 && realOfThisRow == selectedRow;
            if (isSelected) {
                g.fill(pl + 8, rowY, pl + 8 + LIST_W, rowY + ROW_H, 0xFF4060A0);
            } else if (mouseX >= pl + 8 && mouseX < pl + 8 + LIST_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H) {
                g.fill(pl + 8, rowY, pl + 8 + LIST_W, rowY + ROW_H, 0x40FFFFFF);
            }
            g.drawString(this.font, rowTexts.get(idx), pl + 12, rowY + 4, 0xFFEEEEEE, false);
        }

        // 右详情
        renderDetails(g, pl + LIST_W + 20, pt + 55);

        // 待保存提示
        if (!pendingChanges.isEmpty()) {
            g.drawString(this.font,
                    Component.translatable("screen.corpse_campus.admin_panel.pending", pendingChanges.size()),
                    pl + 230, pt + PANEL_H - 22, 0xFFFFAA00, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private List<String> buildRowTexts() {
        List<String> out = new ArrayList<>();
        if (tab == Tab.CONFIG) {
            String lastGroup = null;
            for (ConfigFieldDescriptor d : configFields) {
                if (!d.group().equals(lastGroup)) {
                    out.add("§b§l[" + labelFor(d.group()) + "]");
                    lastGroup = d.group();
                }
                String cur = pendingChanges.getOrDefault(d.path(), String.valueOf(d.currentValue()));
                String tag = pendingChanges.containsKey(d.path()) ? "§e* " : "§7  ";
                out.add(tag + "§f" + d.path() + " §8= §a" + cur);
            }
        } else {
            String lastCat = null;
            for (CommandDescriptor c : commands) {
                if (!c.category().equals(lastCat)) {
                    out.add("§b§l[" + labelFor(c.category()) + "]");
                    lastCat = c.category();
                }
                // 无参指令:行尾加 "▶" 提示单击即执行
                String suffix = c.arguments().isEmpty() ? " §a§l▶" : " §7(" + c.arguments().size() + "p)";
                out.add("§7  §f/" + c.fullPath() + suffix);
            }
        }
        return out;
    }

    private void renderDetails(GuiGraphics g, int x, int y) {
        if (selectedRow < 0) {
            g.drawString(this.font,
                    Component.translatable("screen.corpse_campus.admin_panel.hint_select"),
                    x, y, 0xFF888888, false);
            return;
        }

        if (tab == Tab.CONFIG) {
            if (selectedRow >= configFields.size()) return;
            ConfigFieldDescriptor d = configFields.get(selectedRow);
            g.drawString(this.font, "§f" + d.path(), x, y, 0xFFFFD700, false);
            g.drawString(this.font, "§7类型: §e" + d.type().name(), x, y + 12, 0xFFCCCCCC, false);
            g.drawString(this.font, "§7默认: §f" + d.defaultValue(), x, y + 24, 0xFFCCCCCC, false);
            if (d.rangeMin() != null || d.rangeMax() != null) {
                g.drawString(this.font,
                        "§7范围: §f[" + d.rangeMin() + ", " + d.rangeMax() + "]",
                        x, y + 60, 0xFFCCCCCC, false);
            }
            String comment = d.comment() == null ? "" : d.comment();
            int commentY = y + 80;
            for (String line : wrapText(comment, 240)) {
                g.drawString(this.font, "§8" + line, x, commentY, 0xFF999999, false);
                commentY += 10;
            }
        } else {
            if (selectedRow >= commands.size()) return;
            CommandDescriptor c = commands.get(selectedRow);
            g.drawString(this.font, "§f/" + c.fullPath(), x, y, 0xFFFFD700, false);
            // 功能说明(来自 CommandMetadata)——一句话描述
            String desc = c.description() == null ? "" : c.description();
            int descY = y + 14;
            if (!desc.isEmpty()) {
                for (String line : wrapText(desc, 240)) {
                    g.drawString(this.font, "§7" + line, x, descY, 0xFFCCCCCC, false);
                    descY += 10;
                }
                descY += 4;
            }
            g.drawString(this.font, "§8模板: §e" + c.usageTemplate(), x, descY, 0xFFCCCCCC, false);
            descY += 12;
            g.drawString(this.font, c.arguments().isEmpty()
                    ? "§a无参数 — 单击列表行即可直接执行"
                    : "§7参数:", x, descY, 0xFFCCCCCC, false);
        }
    }

    /** 根据 buildRowTexts 里分组行的偏移,把 visible index 映射回 configFields / commands 的真实索引。 */
    private int mapVisibleToReal(int visibleIdx) {
        int realCursor = 0;
        String lastGroup = null;
        int visCursor = 0;
        if (tab == Tab.CONFIG) {
            for (ConfigFieldDescriptor d : configFields) {
                if (!d.group().equals(lastGroup)) {
                    if (visCursor == visibleIdx) return -1;
                    visCursor++;
                    lastGroup = d.group();
                }
                if (visCursor == visibleIdx) return realCursor;
                visCursor++;
                realCursor++;
            }
        } else {
            for (CommandDescriptor c : commands) {
                if (!c.category().equals(lastGroup)) {
                    if (visCursor == visibleIdx) return -1;
                    visCursor++;
                    lastGroup = c.category();
                }
                if (visCursor == visibleIdx) return realCursor;
                visCursor++;
                realCursor++;
            }
        }
        return -1;
    }

    private List<String> wrapText(String raw, int maxWidthPx) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        String[] words = raw.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String next = line.length() == 0 ? w : line + " " + w;
            if (this.font.width(next) > maxWidthPx && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(w);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int pl = panelLeft();
        int pt = panelTop();
        int listTop = pt + 55;
        int listBottom = pt + PANEL_H - 35;

        if (button == 0
                && mouseX >= pl + 8 && mouseX < pl + 8 + LIST_W
                && mouseY >= listTop && mouseY < listBottom) {
            int rowI = (int) ((mouseY - listTop) / ROW_H);
            int idx = rowI + scrollOffset;
            List<String> rowTexts = buildRowTexts();
            if (idx >= 0 && idx < rowTexts.size()) {
                // 跳过分组标签(含 §b§l[ 前缀)
                if (rowTexts.get(idx).startsWith("§b§l[")) {
                    return true;
                }
                int real = mapVisibleToRealStatic(idx);
                if (real >= 0) {
                    // 指令 tab 下,无参指令点一次即执行,不进详情页
                    if (tab == Tab.COMMAND && real < commands.size()
                            && commands.get(real).arguments().isEmpty()) {
                        CommandDescriptor c = commands.get(real);
                        ModNetwork.CHANNEL.sendToServer(new AdminExecuteCommandPacket(c.fullPath()));
                        selectedRow = real; // 视觉高亮反馈
                        return true;
                    }
                    selectedRow = real;
                    rebuildDetailWidgets();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 静态工具:visible 索引 → 真实 descriptor 索引(selectedRow 改为存真实索引)。 */
    private int mapVisibleToRealStatic(int visibleIdx) {
        return mapVisibleToReal(visibleIdx);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(delta) * 2);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

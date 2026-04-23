package com.mifan.admin;

import java.util.List;

/**
 * 指令元描述符。由 {@link AdminPanelRegistry#scanCommandTree} 遍历 Brigadier 树产出。
 *
 * @param fullPath        完整路径，如 "magic rules set"
 * @param category        分类键，取自 fullPath 第二段（如 "magic rules set" → "rules"）
 * @param arguments       末端 argument 节点列表（名称 + usage 提示）
 * @param hasExecutable   末端是否可直接执行（有 executes 绑定）
 * @param requiredPermLvl 该末端的 permission 要求（读 .requires() 判断；拿不到时默认 2）
 */
public record CommandDescriptor(
        String fullPath,
        String category,
        List<ArgumentInfo> arguments,
        boolean hasExecutable,
        int requiredPermLvl) {

    /** Argument 参数的简要元信息，供 GUI 生成输入框用。 */
    public record ArgumentInfo(String name, String type) {
    }

    /** GUI 展示用的建议命令模板，形如 "magic rules set <key> <value>"。 */
    public String usageTemplate() {
        StringBuilder sb = new StringBuilder(fullPath);
        for (ArgumentInfo arg : arguments) {
            sb.append(" <").append(arg.name()).append('>');
        }
        return sb.toString();
    }
}

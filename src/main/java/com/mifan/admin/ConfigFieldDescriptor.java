package com.mifan.admin;

import java.util.List;

/**
 * Config 字段元描述符。由 {@link AdminPanelRegistry#scanConfigSpec} 扫描 ForgeConfigSpec 产出。
 *
 * @param path         点分路径,如 "rules.requireBForA"
 * @param type         字段类型,驱动 GUI 控件选择
 * @param comment      注释(Forge 注解)——面向管理员的说明
 * @param defaultValue 默认值(spec 里声明的)
 * @param currentValue 当前运行时值
 * @param rangeMin     数值类字段的最小值（非数值类为 null）
 * @param rangeMax     数值类字段的最大值（非数值类为 null）
 * @param enumOptions  ENUM 类型时的候选值列表（其它类型为 null）
 */
public record ConfigFieldDescriptor(
        String path,
        FieldType type,
        String comment,
        Object defaultValue,
        Object currentValue,
        Number rangeMin,
        Number rangeMax,
        List<String> enumOptions) {

    public enum FieldType {
        BOOL,
        INT,
        LONG,
        DOUBLE,
        STRING,
        ENUM,
        UNKNOWN
    }

    /** 路径第一段作为分组键（如 "limit" / "rules"），没有点号时归到 "misc"。 */
    public String group() {
        int dot = path.indexOf('.');
        return dot < 0 ? "misc" : path.substring(0, dot);
    }
}

package com.mifan.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 流派石板：通过 /magic Slate <schoolId> [player] 绑定流派后，
 * 物品名渲染为「<流派名>石板」并七彩动画，tooltip 列出该流派的能力描述。
 *
 * 未绑定状态显示「空白石板」+ 引导提示。
 */
public class SlateItem extends Item {
    public static final String TAG_BOUND_SCHOOL = "BoundSchool";

    public SlateItem() {
        super(new Item.Properties().stacksTo(64));
    }

    @Nullable
    public static String getBoundSchoolPath(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BOUND_SCHOOL)) {
            return null;
        }
        String raw = tag.getString(TAG_BOUND_SCHOOL);
        if (raw.isEmpty()) {
            return null;
        }
        int colon = raw.indexOf(':');
        String path = colon >= 0 ? raw.substring(colon + 1) : raw;
        return SlateDescriptions.isKnownSchool(path) ? path : null;
    }

    public static void bindSchool(ItemStack stack, String schoolPath) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_BOUND_SCHOOL, "corpse_campus:" + schoolPath);
    }

    @Override
    public Component getName(ItemStack stack) {
        String schoolPath = getBoundSchoolPath(stack);
        if (schoolPath == null) {
            return Component.translatable("item.corpse_campus.slate.empty")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true));
        }
        String schoolName = SlateDescriptions.SCHOOL_DISPLAY_NAME.getOrDefault(schoolPath, schoolPath);
        String fullName = schoolName + "石板";
        return rainbow(fullName);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String schoolPath = getBoundSchoolPath(stack);
        if (schoolPath == null) {
            tooltip.add(Component.translatable("tooltip.corpse_campus.slate.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        List<String> lines = SlateDescriptions.LINES.get(schoolPath);
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            if (line.isEmpty()) {
                tooltip.add(Component.literal(""));
            } else {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /**
     * 逐字符 HSV 动画着色，hue 随 wall-clock 时间与字符索引漂移。
     * 同栈调用此方法每帧返回不同 RGB，因此鼠标 hover 物品时 GUI 会看到颜色流动。
     */
    private static MutableComponent rainbow(String text) {
        MutableComponent root = Component.empty();
        long t = System.currentTimeMillis();
        for (int i = 0; i < text.length(); i++) {
            float hue = ((t / 30L) % 360L + i * 18L) % 360L / 360.0F;
            int rgb = hsvToRgb(hue, 0.85F, 1.0F);
            root.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
        }
        return root;
    }

    private static int hsvToRgb(float h, float s, float v) {
        int hi = (int) Math.floor(h * 6) % 6;
        float f = h * 6 - (float) Math.floor(h * 6);
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r;
        float g;
        float b;
        switch (hi) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        int ri = Math.round(r * 255);
        int gi = Math.round(g * 255);
        int bi = Math.round(b * 255);
        return (ri << 16) | (gi << 8) | bi;
    }
}

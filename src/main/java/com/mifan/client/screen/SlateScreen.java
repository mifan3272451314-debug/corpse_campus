package com.mifan.client.screen;

import com.mifan.item.SlateDescriptions;
import com.mifan.item.SlateItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 流派石板查看界面：右键石板时打开。
 *
 * 顶部展示「<流派>石板」（七彩动画标题），主体为可滚动文本面板，
 * 内容来自 {@link SlateDescriptions#LINES}。
 */
public class SlateScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 240;
    private static final int CONTENT_PADDING = 12;
    private static final int TITLE_HEIGHT = 22;
    private static final int FOOTER_HEIGHT = 16;
    private static final int LINE_GAP = 2;

    private static final int COLOR_PANEL_BG = 0xE8101018;
    private static final int COLOR_PANEL_BORDER = 0xFF8888AA;
    private static final int COLOR_TITLE_DIVIDER = 0x80FFFFFF;
    private static final int COLOR_TEXT = 0xFFCCCCCC;
    private static final int COLOR_SCROLL_TRACK = 0x40FFFFFF;
    private static final int COLOR_SCROLL_THUMB = 0xFFAAAAAA;
    private static final int COLOR_FOOTER = 0xFF666688;

    private final String schoolPath;
    private final String schoolName;

    private List<FormattedCharSequence> wrappedLines = new ArrayList<>();
    private int contentHeight;
    private int viewHeight;
    private double scrollPx;
    private boolean draggingScrollbar;
    private double dragYOffset;

    public SlateScreen(String schoolPath) {
        super(Component.literal(SlateDescriptions.SCHOOL_DISPLAY_NAME.getOrDefault(schoolPath, schoolPath) + "石板"));
        this.schoolPath = schoolPath;
        this.schoolName = SlateDescriptions.SCHOOL_DISPLAY_NAME.getOrDefault(schoolPath, schoolPath);
    }

    @Override
    protected void init() {
        super.init();
        rebuildLines();
        scrollPx = 0;
    }

    private void rebuildLines() {
        wrappedLines.clear();
        Font font = this.font;
        int contentWidth = PANEL_W - CONTENT_PADDING * 2 - 6; // 留出滚动条宽度
        List<String> raw = SlateDescriptions.LINES.get(schoolPath);
        if (raw == null) {
            wrappedLines.add(Component.translatable("tooltip.corpse_campus.slate.empty")
                    .withStyle(ChatFormatting.RED).getVisualOrderText());
        } else {
            int lineH = font.lineHeight + LINE_GAP;
            for (String line : raw) {
                if (line.isEmpty()) {
                    wrappedLines.add(FormattedCharSequence.EMPTY);
                    continue;
                }
                Component cmp = Component.literal(line).withStyle(ChatFormatting.GRAY);
                List<FormattedCharSequence> parts = font.split(cmp, contentWidth);
                if (parts.isEmpty()) {
                    wrappedLines.add(FormattedCharSequence.EMPTY);
                } else {
                    wrappedLines.addAll(parts);
                }
            }
            // 一个估算的总高（用于滚动 clamp）
            contentHeight = wrappedLines.size() * lineH;
        }
        viewHeight = PANEL_H - TITLE_HEIGHT - FOOTER_HEIGHT - CONTENT_PADDING * 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);

        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int right = left + PANEL_W;
        int bottom = top + PANEL_H;

        // 面板底 + 边
        g.fill(left, top, right, bottom, COLOR_PANEL_BG);
        drawBorder(g, left, top, right, bottom, COLOR_PANEL_BORDER);

        // 七彩标题（每帧重算，颜色流动）
        Component title = SlateItem.rainbow(schoolName + "石板");
        int titleY = top + (TITLE_HEIGHT - font.lineHeight) / 2;
        g.drawString(font, title, left + (PANEL_W - font.width(title)) / 2, titleY, 0xFFFFFFFF, false);
        // 标题下分隔线
        g.fill(left + CONTENT_PADDING, top + TITLE_HEIGHT, right - CONTENT_PADDING, top + TITLE_HEIGHT + 1, COLOR_TITLE_DIVIDER);

        // 文本区
        int contentLeft = left + CONTENT_PADDING;
        int contentTop = top + TITLE_HEIGHT + CONTENT_PADDING;
        int contentRight = right - CONTENT_PADDING - 6; // 给滚动条留位
        int contentBottom = contentTop + viewHeight;

        g.enableScissor(contentLeft, contentTop, contentRight + 6, contentBottom);
        int lineH = font.lineHeight + LINE_GAP;
        int firstVisible = (int) Math.floor(scrollPx / lineH);
        int yStart = contentTop - (int) (scrollPx - firstVisible * lineH);
        for (int i = firstVisible; i < wrappedLines.size(); i++) {
            int y = yStart + (i - firstVisible) * lineH;
            if (y >= contentBottom) break;
            FormattedCharSequence seq = wrappedLines.get(i);
            g.drawString(font, seq, contentLeft, y, COLOR_TEXT, false);
        }
        g.disableScissor();

        // 滚动条
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        if (maxScroll > 0) {
            int trackX = contentRight + 1;
            int trackTop = contentTop;
            int trackBottom = contentBottom;
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(20, (int) ((long) trackH * viewHeight / contentHeight));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (scrollPx / maxScroll));
            g.fill(trackX, trackTop, trackX + 4, trackBottom, COLOR_SCROLL_TRACK);
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, COLOR_SCROLL_THUMB);
        }

        // 底部提示
        Component foot = Component.translatable("screen.corpse_campus.slate.footer");
        int footY = bottom - FOOTER_HEIGHT + (FOOTER_HEIGHT - font.lineHeight) / 2;
        g.drawString(font, foot, left + (PANEL_W - font.width(foot)) / 2, footY, COLOR_FOOTER, false);

        super.render(g, mouseX, mouseY, partial);
    }

    private static void drawBorder(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int lineH = font.lineHeight + LINE_GAP;
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        scrollPx = Mth.clamp(scrollPx - delta * lineH * 3, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = (this.width - PANEL_W) / 2;
            int top = (this.height - PANEL_H) / 2;
            int right = left + PANEL_W;
            int contentTop = top + TITLE_HEIGHT + CONTENT_PADDING;
            int contentBottom = contentTop + viewHeight;
            int contentRight = right - CONTENT_PADDING - 6;
            int trackX = contentRight + 1;
            int maxScroll = Math.max(0, contentHeight - viewHeight);
            if (maxScroll > 0
                    && mouseX >= trackX && mouseX <= trackX + 4
                    && mouseY >= contentTop && mouseY <= contentBottom) {
                int trackH = contentBottom - contentTop;
                int thumbH = Math.max(20, (int) ((long) trackH * viewHeight / contentHeight));
                int thumbY = contentTop + (int) ((trackH - thumbH) * (scrollPx / maxScroll));
                if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                    draggingScrollbar = true;
                    dragYOffset = mouseY - thumbY;
                } else {
                    // 点击轨道空白处：thumb 中心对齐到点击位置
                    double centerY = mouseY - thumbH / 2.0;
                    double ratio = Mth.clamp((centerY - contentTop) / Math.max(1, trackH - thumbH), 0.0, 1.0);
                    scrollPx = ratio * maxScroll;
                    draggingScrollbar = true;
                    dragYOffset = thumbH / 2.0;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            int top = (this.height - PANEL_H) / 2;
            int contentTop = top + TITLE_HEIGHT + CONTENT_PADDING;
            int contentBottom = contentTop + viewHeight;
            int trackH = contentBottom - contentTop;
            int maxScroll = Math.max(0, contentHeight - viewHeight);
            int thumbH = maxScroll > 0
                    ? Math.max(20, (int) ((long) trackH * viewHeight / contentHeight))
                    : trackH;
            double thumbTop = mouseY - dragYOffset;
            double ratio = Mth.clamp((thumbTop - contentTop) / Math.max(1, trackH - thumbH), 0.0, 1.0);
            scrollPx = ratio * maxScroll;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

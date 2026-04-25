package com.mifan.client.screen;

import net.minecraft.client.Minecraft;

/**
 * 客户端专用入口：把 SlateItem 的右键打开 Screen 行为隔离在客户端类里，
 * 避免专用服务端加载到 Minecraft / Screen 类。
 */
public final class SlateScreenLauncher {
    private SlateScreenLauncher() {
    }

    public static void open(String schoolPath) {
        Minecraft.getInstance().setScreen(new SlateScreen(schoolPath));
    }
}

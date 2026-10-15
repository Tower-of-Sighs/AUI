package com.sighs.apricityui.client;

import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.client.Minecraft;

/**
 * 开发期调试用：{@code -Dapricityui.startPage=<逻辑路径>} 让客户端启动后自动打开指定页面，
 * 省掉每次重启手动开页面的步骤。不设这个属性时完全惰性，对正式包没有影响。
 */
public final class DebugStartupPage {
    private static final String PROPERTY = "apricityui.startPage";
    private static final int DELAY_TICKS = 60;

    private static int ticks;
    private static boolean resolved;
    private static String openedPath;

    private DebugStartupPage() {
    }

    public static void tick() {
        if (resolved) return;
        String path = System.getProperty(PROPERTY);
        if (path == null || path.isBlank()) {
            resolved = true;
            return;
        }
        if (++ticks < DELAY_TICKS) return;
        resolved = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        openedPath = path.trim();
        minecraft.execute(() -> minecraft.setScreen(new ApricityScreen(openedPath)));
    }
}

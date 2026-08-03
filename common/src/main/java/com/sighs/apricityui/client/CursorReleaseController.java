package com.sighs.apricityui.client;

import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.Cursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

/** Keeps the native cursor released while the configured hold key is down. */
public final class CursorReleaseController {
    private static boolean active;
    private static boolean restoreMouseGrab;

    private CursorReleaseController() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        MouseHandler mouseHandler = minecraft.mouseHandler;
        boolean available = minecraft.level != null
                && minecraft.screen == null
                && minecraft.getOverlay() == null
                && minecraft.isWindowActive();

        update(
                AuiServices.keys().isReleaseMouseDown(),
                available,
                mouseHandler.isMouseGrabbed(),
                mouseHandler::releaseMouse,
                () -> {
                    Cursor.resetToDefault();
                    mouseHandler.grabMouse();
                }
        );
    }

    public static boolean isActive() {
        return active;
    }

    public static void update(boolean requested, boolean available, boolean mouseGrabbed,
                       Runnable releaseMouse, Runnable grabMouse) {
        if (requested && available) {
            if (!active) {
                active = true;
                restoreMouseGrab = mouseGrabbed;
                if (mouseGrabbed) releaseMouse.run();
            } else if (mouseGrabbed) {
                restoreMouseGrab = true;
                releaseMouse.run();
            }
            return;
        }

        if (!active) return;

        boolean shouldRestore = restoreMouseGrab && available && !mouseGrabbed;
        active = false;
        restoreMouseGrab = false;
        if (shouldRestore) grabMouse.run();
    }

    public static void resetForTest() {
        active = false;
        restoreMouseGrab = false;
    }
}

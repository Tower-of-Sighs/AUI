package com.sighs.apricityui.dev;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class Test {
    private static final String PAGE_PATH_WITH_SLASH = "test/cursor_demo.html";
    private static boolean jPressedLastTick = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        boolean jPressed = window != 0L && InputConstants.isKeyDown(window, GLFW.GLFW_KEY_J);
        if (jPressed && !jPressedLastTick) {
            toggleCursorDemo();
        }

        jPressedLastTick = jPressed;
    }

    private static void toggleCursorDemo() {
        boolean isOpened = !ApricityUI.getDocument(PAGE_PATH_WITH_SLASH).isEmpty();
        if (isOpened) {
            ApricityUI.removeDocument(PAGE_PATH_WITH_SLASH);
            return;
        }
        if (ApricityUI.createDocument(PAGE_PATH_WITH_SLASH) != null) {
            return;
        }
    }
}

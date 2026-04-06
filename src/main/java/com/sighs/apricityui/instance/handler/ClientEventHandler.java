package com.sighs.apricityui.instance.handler;

import cc.sighs.oelib.event.EventSide;
import cc.sighs.oelib.event.Subscribe;
import cc.sighs.oelib.event.events.InputEvent;
import cc.sighs.oelib.event.events.ScreenEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Operation;
import com.sighs.apricityui.instance.WorldWindow;
import com.sighs.apricityui.style.Position;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

public class ClientEventHandler {
    public ClientEventHandler() {}

    @Subscribe(side = EventSide.CLIENT, receiveCanceled = true)
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (Minecraft.getInstance().screen != null) return;
        boolean consumed = Operation.scroll(event.getScrollDelta());
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                MouseEvent mouseEvent = new MouseEvent("scroll", realPos);
                mouseEvent.scrollDelta = -event.getScrollDelta() * 50;
                consumed |= MouseEvent.tiggerEvent(mouseEvent, window.document);
            }
        }
        if (consumed) event.setCanceled(true);
    }

    @Subscribe(side = EventSide.CLIENT, receiveCanceled = true)
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (Operation.scroll(event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    @Subscribe(side = EventSide.CLIENT, receiveCanceled = true)
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (SharedConstants.isAllowedChatCharacter(event.getCodePoint())) {
            if (Operation.onCharTyped(event.getCodePoint())) event.setCanceled(true);
        }
    }

    @Subscribe(side = EventSide.CLIENT, receiveCanceled = true)
    public static void mouseButton(InputEvent.MouseButton.Pre event) {
        boolean consumed = false;
        if (event.getAction() == InputConstants.PRESS) consumed = Operation.onMouseDown(event.getButton());
        if (event.getAction() == InputConstants.RELEASE) consumed = Operation.onMouseUp(event.getButton());
        if (Minecraft.getInstance().screen != null) {
            if (consumed) event.setCanceled(true);
            return;
        }
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                if (event.getAction() == InputConstants.PRESS) {
                    consumed |= MouseEvent.tiggerEvent(new MouseEvent("mousedown", realPos, event.getButton()), window.document);
                } else {
                    consumed |= MouseEvent.tiggerEvent(new MouseEvent("mouseup", realPos, event.getButton()), window.document);
                }
            }
        }
        if (consumed) event.setCanceled(true);
    }

    @Subscribe(side = EventSide.CLIENT)
    public static void onKeyPressed(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && event.getAction() == InputConstants.PRESS && event.getKey() == GLFW.GLFW_KEY_K) {
            DevTools.toggleExample();
        }
        if (minecraft.screen != null) {
            return;
        }
        int action = event.getAction();
        if (action != InputConstants.PRESS && action != InputConstants.REPEAT && action != InputConstants.RELEASE) return;
        boolean canceled = Operation.handleKeyInput(
                event.getKey(),
                event.getScanCode(),
                action,
                event.getModifiers(),
                action == InputConstants.REPEAT,
                KeyEvent.Source.INPUT_EVENT
        );
//        if (canceled) event.setCanceled(true);
    }

    @Subscribe(side = EventSide.CLIENT)
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() == GLFW.GLFW_KEY_K) {
            DevTools.toggleExample();
        }
        int action = InputConstants.PRESS;
        boolean canceled = Operation.handleKeyInput(
                event.getKeyCode(),
                event.getScanCode(),
                action,
                event.getModifiers(),
                false,
                KeyEvent.Source.SCREEN_EVENT
        );
//        if (canceled) event.setCanceled(true);
    }

    @Subscribe(side = EventSide.CLIENT)
    public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        int action = InputConstants.RELEASE;
        Operation.handleKeyInput(
                event.getKeyCode(),
                event.getScanCode(),
                action,
                event.getModifiers(),
                false,
                KeyEvent.Source.SCREEN_EVENT
        );
    }
}

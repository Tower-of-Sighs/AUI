package com.sighs.apricityui.instance.handler;

import cc.sighs.oelib.event.EventSide;
import cc.sighs.oelib.event.Subscribe;
import cc.sighs.oelib.event.events.InputEvent;
import cc.sighs.oelib.event.events.ScreenEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Operation;
import com.sighs.apricityui.instance.WorldWindow;
import com.sighs.apricityui.style.Position;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

public class ClientEventHandler {
    @Subscribe(side = EventSide.CLIENT)
    public static void scroll(ScreenEvent.MouseScrolled.Post event) {
        Operation.scroll(event.getScrollDelta());
    }

    @Subscribe(side = EventSide.CLIENT, receiveCanceled = true)
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (SharedConstants.isAllowedChatCharacter(event.getCodePoint())) {
            if (Operation.onCharTyped(event.getCodePoint())) event.setCanceled(true);
        }
    }

    @Subscribe(side = EventSide.CLIENT, receiveCanceled = true)
    public static void mouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == InputConstants.PRESS) Operation.onMouseDown();
        if (event.getAction() == InputConstants.RELEASE) Operation.onMouseUp();
        if (Minecraft.getInstance().screen != null) return;
        for (WorldWindow window : WorldWindow.windows) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                if (event.getAction() == InputConstants.PRESS) {
                    MouseEvent.tiggerEvent(new MouseEvent("mousedown", realPos), window.document);
                } else {
                    MouseEvent.tiggerEvent(new MouseEvent("mouseup", realPos), window.document);
                }
                event.setCanceled(true);
            }
        }
    }

    @Subscribe(side = EventSide.CLIENT)
    public static void onKeyPressed(InputEvent.Key event) {
        if (Minecraft.getInstance().screen != null) {
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
                com.sighs.apricityui.event.KeyEvent.Source.INPUT_EVENT
        );
//        if (canceled) event.setCanceled(true);
    }

    @Subscribe(side = EventSide.CLIENT)
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        int action = InputConstants.PRESS;
        boolean canceled = Operation.handleKeyInput(
                event.getKeyCode(),
                event.getScanCode(),
                action,
                event.getModifiers(),
                false,
                com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT
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
                com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT
        );
    }
}

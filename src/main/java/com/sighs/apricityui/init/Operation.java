package com.sighs.apricityui.init;

import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.style.Position;
import org.lwjgl.glfw.GLFW;

public class Operation {
    public static Position cachedMousePosition = null;

    public static void onMouseDown() {
        onMouseDown(-1);
    }
    public static void onMouseDown(int button) {
        MouseEvent.tiggerEvent(new MouseEvent("mousedown", getMousePosition()));
    }
    public static void onMouseUp() {
        onMouseUp(-1);
    }
    public static void onMouseUp(int button) {
        MouseEvent.tiggerEvent(new MouseEvent("mouseup", getMousePosition()));
    }
    public static void onMouseMove(Position currentMousePosition) {
        if (cachedMousePosition != null) {
            MouseEvent mouseEvent = new MouseEvent("mousemove", getMousePosition());
            mouseEvent.movementX = currentMousePosition.x - cachedMousePosition.x;
            mouseEvent.movementY = currentMousePosition.y - cachedMousePosition.y;
            MouseEvent.tiggerEvent(mouseEvent);
        }
        cachedMousePosition = currentMousePosition;
    }

    public static void scroll(double delta) {
        MouseEvent mouseEvent = new MouseEvent("scroll", getMousePosition());
        mouseEvent.scrollDelta = -delta * 50;
        MouseEvent.tiggerEvent(mouseEvent);
    }

    public static boolean onCharTyped(char code) {
        boolean shouldCancel = false;
        for (Document document : Document.getAll()) {
            if (document.getFocusedElement() instanceof Input input) {
                input.insertText(Character.toString(code));
                shouldCancel = true;
            }
        }
        return shouldCancel;
    }

    public static boolean onKeyPressed(int key) {
        boolean cancel = false;
        for (Document document : Document.getAll()) {
            Element focusedElement = document.getFocusedElement();
            if (focusedElement instanceof Input input) {
                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    input.sliceText(input.getCursor() - 1, input.getCursor());
                } else if (key == GLFW.GLFW_KEY_LEFT) {
                    input.moveCursor(-1);
                } else if (key == GLFW.GLFW_KEY_RIGHT) {
                    input.moveCursor(1);
                } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                    document.clearFocus();
                }
                cancel = true;
            }
        }
        if (key == GLFW.GLFW_KEY_LEFT_ALT) {
            DevTools.toggle();
        }
        if (key == Keybindings.RELOAD.getKey().getValue()) {
            Loader.reload();
        }
        return cancel;
    }

    public static Position getMousePosition() {
        return cachedMousePosition;
    }

    public static boolean isKeyPressed(String key) {
        return Client.isKeyPressed(key);
    }
}

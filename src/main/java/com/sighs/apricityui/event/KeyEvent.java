package com.sighs.apricityui.event;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.init.Operation;
import org.lwjgl.glfw.GLFW;

public class KeyEvent extends Event {
    public enum Source {
        INPUT_EVENT,
        SCREEN_EVENT
    }

    public final int keyCode;
    public final int scanCode;
    public final int modifiers;
    public final String key;
    public final String code;
    public final boolean repeat;
    public final Source source;
    public boolean altKey;
    public boolean shiftKey;
    public boolean controlKey;
    public boolean metaKey;

    public KeyEvent(Element target, String type, int keyCode, int scanCode, int modifiers, boolean repeat, Source source) {
        super(target, type, null, true);
        this.keyCode = keyCode;
        this.scanCode = scanCode;
        this.modifiers = modifiers;
        this.repeat = repeat;
        this.key = resolveKey(keyCode, scanCode);
        this.code = resolveCode(keyCode);
        this.source = source == null ? Source.INPUT_EVENT : source;
        this.altKey = ((modifiers & GLFW.GLFW_MOD_ALT) != 0)
                || Operation.isKeyPressed("key.keyboard.left.alt")
                || Operation.isKeyPressed("key.keyboard.right.alt");
        this.shiftKey = ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0)
                || Operation.isKeyPressed("key.keyboard.left.shift")
                || Operation.isKeyPressed("key.keyboard.right.shift");
        this.controlKey = ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0)
                || Operation.isKeyPressed("key.keyboard.left.control")
                || Operation.isKeyPressed("key.keyboard.right.control");
        this.metaKey = ((modifiers & GLFW.GLFW_MOD_SUPER) != 0)
                || Operation.isKeyPressed("key.keyboard.left.win")
                || Operation.isKeyPressed("key.keyboard.right.win");
    }

    public static void triggerEvent(Document document, String type, int keyCode, int scanCode, int modifiers, boolean repeat, Source source) {
        if (document == null) return;
        Element target = document.getFocusedElement();
        if (target == null) target = document.getActiveElement();
        if (target == null) target = document.body;
        if (target == null) return;
        Event.tiggerEvent(new KeyEvent(target, type, keyCode, scanCode, modifiers, repeat, source));
    }

    public static void triggerEvent(Document document, String type, int keyCode, boolean repeat) {
        triggerEvent(document, type, keyCode, 0, 0, repeat, Source.INPUT_EVENT);
    }

    private static String resolveKey(int keyCode, int scanCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_ESCAPE -> "Escape";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_SPACE -> " ";
            case GLFW.GLFW_KEY_LEFT -> "ArrowLeft";
            case GLFW.GLFW_KEY_RIGHT -> "ArrowRight";
            case GLFW.GLFW_KEY_UP -> "ArrowUp";
            case GLFW.GLFW_KEY_DOWN -> "ArrowDown";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_PAGE_UP -> "PageUp";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PageDown";
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Control";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "Alt";
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> "Meta";
            default -> {
                String glfwName = GLFW.glfwGetKeyName(keyCode, scanCode);
                if (glfwName == null || glfwName.isBlank()) yield "Unidentified";
                yield glfwName.length() == 1 ? glfwName.toLowerCase() : glfwName;
            }
        };
    }

    private static String resolveCode(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_A -> "KeyA";
            case GLFW.GLFW_KEY_B -> "KeyB";
            case GLFW.GLFW_KEY_C -> "KeyC";
            case GLFW.GLFW_KEY_D -> "KeyD";
            case GLFW.GLFW_KEY_E -> "KeyE";
            case GLFW.GLFW_KEY_F -> "KeyF";
            case GLFW.GLFW_KEY_G -> "KeyG";
            case GLFW.GLFW_KEY_H -> "KeyH";
            case GLFW.GLFW_KEY_I -> "KeyI";
            case GLFW.GLFW_KEY_J -> "KeyJ";
            case GLFW.GLFW_KEY_K -> "KeyK";
            case GLFW.GLFW_KEY_L -> "KeyL";
            case GLFW.GLFW_KEY_M -> "KeyM";
            case GLFW.GLFW_KEY_N -> "KeyN";
            case GLFW.GLFW_KEY_O -> "KeyO";
            case GLFW.GLFW_KEY_P -> "KeyP";
            case GLFW.GLFW_KEY_Q -> "KeyQ";
            case GLFW.GLFW_KEY_R -> "KeyR";
            case GLFW.GLFW_KEY_S -> "KeyS";
            case GLFW.GLFW_KEY_T -> "KeyT";
            case GLFW.GLFW_KEY_U -> "KeyU";
            case GLFW.GLFW_KEY_V -> "KeyV";
            case GLFW.GLFW_KEY_W -> "KeyW";
            case GLFW.GLFW_KEY_X -> "KeyX";
            case GLFW.GLFW_KEY_Y -> "KeyY";
            case GLFW.GLFW_KEY_Z -> "KeyZ";
            case GLFW.GLFW_KEY_0 -> "Digit0";
            case GLFW.GLFW_KEY_1 -> "Digit1";
            case GLFW.GLFW_KEY_2 -> "Digit2";
            case GLFW.GLFW_KEY_3 -> "Digit3";
            case GLFW.GLFW_KEY_4 -> "Digit4";
            case GLFW.GLFW_KEY_5 -> "Digit5";
            case GLFW.GLFW_KEY_6 -> "Digit6";
            case GLFW.GLFW_KEY_7 -> "Digit7";
            case GLFW.GLFW_KEY_8 -> "Digit8";
            case GLFW.GLFW_KEY_9 -> "Digit9";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_ESCAPE -> "Escape";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_LEFT -> "ArrowLeft";
            case GLFW.GLFW_KEY_RIGHT -> "ArrowRight";
            case GLFW.GLFW_KEY_UP -> "ArrowUp";
            case GLFW.GLFW_KEY_DOWN -> "ArrowDown";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "ShiftLeft";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "ShiftRight";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "ControlLeft";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "ControlRight";
            case GLFW.GLFW_KEY_LEFT_ALT -> "AltLeft";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "AltRight";
            case GLFW.GLFW_KEY_LEFT_SUPER -> "MetaLeft";
            case GLFW.GLFW_KEY_RIGHT_SUPER -> "MetaRight";
            default -> "Unidentified";
        };
    }
}

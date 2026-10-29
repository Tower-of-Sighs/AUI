package com.sighs.apricityui.event;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.render.Operation;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLScancode;

import java.util.Locale;

/**
 * AUI keyboard event (26.3 / SDL3 variant).
 *
 * <p>Minecraft 26.3 replaced GLFW with SDL3, so the numeric key space changed:
 * {@code keyCode} is now an <em>SDL scancode</em> ({@code InputConstants.KEY_A == 4}) — the
 * value Minecraft matches {@code KeyMapping}s against and the one every AUI comparison in
 * {@link Operation} uses — while {@code scanCode} carries the <em>SDL keycode</em> ({@code SDLK_*},
 * {@code 'a' == 97}), which is layout dependent and only used to derive the DOM {@code key} string.
 * The field names are kept so {@link Operation}'s call sites (and the loader targets) do not move;
 * {@code modifiers} is the {@code SDL_KMOD_*} mask exposed as {@code InputConstants.MOD_*}.</p>
 */
public class KeyEvent extends Event {
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

    /**
     * @param keyCode   host input code, i.e. the SDL scancode on 26.3 (was the GLFW keycode)
     * @param scanCode  the SDL keycode ({@code SDLK_*}); only used to name printable keys
     * @param modifiers {@code SDL_KMOD_*} modifier mask (was the {@code GLFW_MOD_*} mask)
     */
    public KeyEvent(Element target, String type, int keyCode, int scanCode, int modifiers, boolean repeat, Source source) {
        super(target, type, true);
        // 键盘事件必须是可取消的：宿主（如 <iframe> 背后的 web view）依赖 preventDefault
        // 来声明"这个按键归我"，Operation.onKeyPressed 也据此让 Minecraft 不再处理它。
        // 此前 cancelable 一直是 false，preventDefault 对键盘事件是空操作，那条分支从未生效。
        this.cancelable = true;
        this.keyCode = keyCode;
        this.scanCode = scanCode;
        this.modifiers = modifiers;
        this.repeat = repeat;
        this.key = resolveKey(keyCode, scanCode);
        this.code = resolveCode(keyCode);
        this.source = source == null ? Source.INPUT_EVENT : source;
        this.altKey = ((modifiers & InputConstants.MOD_ALT) != 0)
                || isModifierPressed("key.keyboard.left.alt")
                || isModifierPressed("key.keyboard.right.alt");
        this.shiftKey = ((modifiers & InputConstants.MOD_SHIFT) != 0)
                || isModifierPressed("key.keyboard.left.shift")
                || isModifierPressed("key.keyboard.right.shift");
        this.controlKey = ((modifiers & InputConstants.MOD_CONTROL) != 0)
                || isModifierPressed("key.keyboard.left.control")
                || isModifierPressed("key.keyboard.right.control");
        this.metaKey = ((modifiers & InputConstants.MOD_SUPER) != 0)
                || isModifierPressed("key.keyboard.left.win")
                || isModifierPressed("key.keyboard.right.win");
    }

    public static KeyEvent triggerEvent(Document document, String type, int keyCode, int scanCode, int modifiers, boolean repeat, Source source) {
        if (document == null) return null;
        Element target = document.getFocusedElement();
        if (target == null) target = document.getPressedElement();
        if (target == null) target = document.body;
        if (target == null) return null;
        KeyEvent event = new KeyEvent(target, type, keyCode, scanCode, modifiers, repeat, source);
        event.setTrusted(true);
        Event.tiggerEvent(event);
        return event;
    }

    public static KeyEvent triggerEvent(Document document, String type, int keyCode, boolean repeat) {
        return triggerEvent(document, type, keyCode, 0, 0, repeat, Source.INPUT_EVENT);
    }

    /**
     * DOM {@code KeyboardEvent.key}.
     *
     * <p>Named keys (Enter, arrows, modifiers…) are derived from the physical scancode, so they are
     * layout independent. Printable keys are named through SDL ({@code SDL_GetKeyName}) because DOM
     * {@code key} is defined to be layout dependent; when the caller only supplied a scancode the
     * keycode is recovered with {@code SDL_GetKeyFromScancode(scancode, 0, false)} — the same call
     * vanilla uses for {@code InputConstants.Type.KEYBOARD}'s display name.</p>
     */
    private static String resolveKey(int scanCode, int keyCode) {
        return switch (scanCode) {
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> "Enter";
            case InputConstants.KEY_ESCAPE -> "Escape";
            case InputConstants.KEY_BACKSPACE -> "Backspace";
            case InputConstants.KEY_TAB -> "Tab";
            case InputConstants.KEY_SPACE -> " ";
            case InputConstants.KEY_LEFT -> "ArrowLeft";
            case InputConstants.KEY_RIGHT -> "ArrowRight";
            case InputConstants.KEY_UP -> "ArrowUp";
            case InputConstants.KEY_DOWN -> "ArrowDown";
            case InputConstants.KEY_DELETE -> "Delete";
            case InputConstants.KEY_INSERT -> "Insert";
            case InputConstants.KEY_HOME -> "Home";
            case InputConstants.KEY_END -> "End";
            case InputConstants.KEY_PAGEUP -> "PageUp";
            case InputConstants.KEY_PAGEDOWN -> "PageDown";
            case InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT -> "Shift";
            case InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL -> "Control";
            case InputConstants.KEY_LALT, InputConstants.KEY_RALT -> "Alt";
            case InputConstants.KEY_LGUI, InputConstants.KEY_RGUI -> "Meta";
            case InputConstants.KEY_CAPSLOCK -> "CapsLock";
            case InputConstants.KEY_NUMLOCK -> "NumLock";
            // Keypad keys produce a character while NumLock is on (the usual state, and the only one
            // the scancode can describe): DOM reports that character, not SDL's "Keypad 1".
            case InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2, InputConstants.KEY_NUMPAD3,
                 InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5, InputConstants.KEY_NUMPAD6,
                 InputConstants.KEY_NUMPAD7, InputConstants.KEY_NUMPAD8, InputConstants.KEY_NUMPAD9 ->
                    String.valueOf((scanCode - InputConstants.KEY_NUMPAD1 + 1) % 10);
            case InputConstants.KEY_NUMPAD0 -> "0";
            case InputConstants.KEY_ADD -> "+";
            case InputConstants.KEY_MULTIPLY -> "*";
            case InputConstants.KEY_NUMPADEQUALS -> "=";
            case SDLScancode.SDL_SCANCODE_KP_COMMA -> ",";
            case SDLScancode.SDL_SCANCODE_KP_MINUS -> "-";
            case SDLScancode.SDL_SCANCODE_KP_DIVIDE -> "/";
            // 220 is SDL_SCANCODE_KP_DECIMAL, which InputConstants also names KEY_NUMPADCOMMA.
            case InputConstants.KEY_NUMPADCOMMA -> ".";
            case SDLScancode.SDL_SCANCODE_KP_PERIOD -> ".";
            default -> {
                // was GLFW.glfwGetKeyName(keyCode, scanCode)
                String name = keyName(scanCode, keyCode);
                if (name == null || name.isBlank()) yield "Unidentified";
                yield name.codePointCount(0, name.length()) == 1 ? name.toLowerCase(Locale.ROOT) : name;
            }
        };
    }

    /**
     * SDL keycode for a scancode: prefer the one the host handed us, else resolve it with no modifier
     * state (so Shift+A still reports {@code SDLK_a}). {@code 0} when SDL cannot resolve it.
     */
    private static int sdlKeycode(int scanCode, int keyCode) {
        try {
            return keyCode != 0 ? keyCode : SDLKeyboard.SDL_GetKeyFromScancode(scanCode, (short) 0, false);
        } catch (Throwable ignored) {
            // SDL is not initialised off the client thread / during shutdown.
            return 0;
        }
    }

    /**
     * SDL name of the key behind a scancode, used for printable keys so {@code key} stays
     * layout dependent (which is what DOM {@code key} means).
     */
    private static String keyName(int scanCode, int keyCode) {
        int resolved = sdlKeycode(scanCode, keyCode);
        if (resolved == 0) return null;
        try {
            return SDLKeyboard.SDL_GetKeyName(resolved);
        } catch (Throwable ignored) {
            // A missing name is not worth failing an event dispatch over.
            return null;
        }
    }

    /**
     * DOM {@code KeyboardEvent.code} — derived purely from the physical SDL scancode, so it is
     * identical on every keyboard layout (that is the whole point of {@code code}).
     */
    private static String resolveCode(int scanCode) {
        // SDL scancode ranges: A..Z = 4..29, 1..9 then 0 = 30..39, F1..F12 = 58..69,
        // Numpad 1..9 then 0 = 89..98. All contiguous.
        if (scanCode >= InputConstants.KEY_A && scanCode <= InputConstants.KEY_Z) {
            return "Key" + (char) ('A' + (scanCode - InputConstants.KEY_A));
        }
        if (scanCode >= InputConstants.KEY_1 && scanCode <= InputConstants.KEY_0) {
            return "Digit" + ((scanCode - InputConstants.KEY_1 + 1) % 10);
        }
        if (scanCode >= InputConstants.KEY_F1 && scanCode <= InputConstants.KEY_F12) {
            return "F" + (scanCode - InputConstants.KEY_F1 + 1);
        }
        if (scanCode >= InputConstants.KEY_NUMPAD1 && scanCode <= InputConstants.KEY_NUMPAD0) {
            return "Numpad" + ((scanCode - InputConstants.KEY_NUMPAD1 + 1) % 10);
        }
        return switch (scanCode) {
            case InputConstants.KEY_RETURN -> "Enter";
            case InputConstants.KEY_NUMPADENTER -> "NumpadEnter";
            case InputConstants.KEY_ESCAPE -> "Escape";
            case InputConstants.KEY_BACKSPACE -> "Backspace";
            case InputConstants.KEY_TAB -> "Tab";
            case InputConstants.KEY_SPACE -> "Space";
            case InputConstants.KEY_LEFT -> "ArrowLeft";
            case InputConstants.KEY_RIGHT -> "ArrowRight";
            case InputConstants.KEY_UP -> "ArrowUp";
            case InputConstants.KEY_DOWN -> "ArrowDown";
            case InputConstants.KEY_DELETE -> "Delete";
            case InputConstants.KEY_INSERT -> "Insert";
            case InputConstants.KEY_HOME -> "Home";
            case InputConstants.KEY_END -> "End";
            case InputConstants.KEY_PAGEUP -> "PageUp";
            case InputConstants.KEY_PAGEDOWN -> "PageDown";
            case InputConstants.KEY_MINUS -> "Minus";
            case InputConstants.KEY_EQUALS -> "Equal";
            case InputConstants.KEY_LBRACKET -> "BracketLeft";
            case InputConstants.KEY_RBRACKET -> "BracketRight";
            case InputConstants.KEY_BACKSLASH -> "Backslash";
            case InputConstants.KEY_SEMICOLON -> "Semicolon";
            case InputConstants.KEY_APOSTROPHE -> "Quote";
            case InputConstants.KEY_GRAVE -> "Backquote";
            case InputConstants.KEY_COMMA -> "Comma";
            case InputConstants.KEY_PERIOD -> "Period";
            case InputConstants.KEY_SLASH -> "Slash";
            case InputConstants.KEY_CAPSLOCK -> "CapsLock";
            case InputConstants.KEY_NUMLOCK -> "NumLock";
            case InputConstants.KEY_PRINTSCREEN -> "PrintScreen";
            case InputConstants.KEY_SCROLLLOCK -> "ScrollLock";
            case InputConstants.KEY_PAUSE -> "Pause";
            case InputConstants.KEY_LSHIFT -> "ShiftLeft";
            case InputConstants.KEY_RSHIFT -> "ShiftRight";
            case InputConstants.KEY_LCONTROL -> "ControlLeft";
            case InputConstants.KEY_RCONTROL -> "ControlRight";
            case InputConstants.KEY_LALT -> "AltLeft";
            case InputConstants.KEY_RALT -> "AltRight";
            case InputConstants.KEY_LGUI -> "MetaLeft";
            case InputConstants.KEY_RGUI -> "MetaRight";
            case InputConstants.KEY_ADD -> "NumpadAdd";
            case InputConstants.KEY_MULTIPLY -> "NumpadMultiply";
            case InputConstants.KEY_NUMPADEQUALS -> "NumpadEqual";
            case InputConstants.KEY_NUMPADCOMMA -> "NumpadComma";
            case SDLScancode.SDL_SCANCODE_KP_MINUS -> "NumpadSubtract";
            case SDLScancode.SDL_SCANCODE_KP_DIVIDE -> "NumpadDivide";
            case SDLScancode.SDL_SCANCODE_KP_PERIOD -> "NumpadDecimal";
            default -> "Unidentified";
        };
    }

    private static boolean isModifierPressed(String key) {
        try {
            return Operation.isKeyPressed(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 浏览器名称别名：JS 里 e.ctrlKey 访问 controlKey。
     */
    public boolean getCtrlKey() {
        return controlKey;
    }

    @Override
    public KeyEvent clone() {
        KeyEvent copy = new KeyEvent(target instanceof Element element ? element : null, type, keyCode, scanCode, modifiers, repeat, source);
        copyTo(copy);
        copy.altKey = altKey;
        copy.shiftKey = shiftKey;
        copy.controlKey = controlKey;
        copy.metaKey = metaKey;
        return copy;
    }

    public enum Source {
        INPUT_EVENT,
        SCREEN_EVENT
    }
}

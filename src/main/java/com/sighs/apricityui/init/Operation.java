package com.sighs.apricityui.init;

import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.style.Position;
import net.minecraft.client.Minecraft;
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
            if (document.getFocusedElement() instanceof AbstractText textElement && textElement.canEditText()) {
                textElement.insertText(Character.toString(code));
                shouldCancel = true;
            }
        }
        return shouldCancel;
    }

    public static boolean onKeyPressed(int key) {
        return onKeyPressed(key, false);
    }

    public static boolean onKeyPressed(int key, boolean repeat) {
        boolean cancel = false;
        for (Document document : Document.getAll()) {
            KeyEvent.triggerEvent(document, "keydown", key, repeat);
            Element focusedElement = document.getFocusedElement();
            String selectedText = resolveSelectedText(document, focusedElement);

            if (focusedElement instanceof AbstractText textElement) {
                if (isCtrlDown()) {
                    if (key == GLFW.GLFW_KEY_A) {
                        if (textElement.canSelectText()) {
                            textElement.selectAll();
                            cancel = true;
                            continue;
                        }
                    }
                    if (key == GLFW.GLFW_KEY_C) {
                        if (textElement.canSelectText() && !selectedText.isEmpty()) {
                            setClipboardText(selectedText);
                            cancel = true;
                            continue;
                        }
                    }
                    if (key == GLFW.GLFW_KEY_X) {
                        if (textElement.canEditText() && textElement.hasSelection()) {
                            if (!selectedText.isEmpty()) setClipboardText(selectedText);
                            textElement.replaceSelection("");
                            cancel = true;
                            continue;
                        }
                    }
                    if (key == GLFW.GLFW_KEY_V) {
                        if (textElement.canEditText()) {
                            textElement.insertText(getClipboardText());
                            cancel = true;
                            continue;
                        }
                    }
                    if (key == GLFW.GLFW_KEY_Z) {
                        if (textElement.canEditText() && textElement.undo()) {
                            cancel = true;
                            continue;
                        }
                    }
                }

                if (focusedElement instanceof Input input && key == GLFW.GLFW_KEY_SPACE && input.handleSpaceKey()) {
                    cancel = true;
                    continue;
                }

                if (!textElement.canEditText()) continue;

                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    textElement.deleteBackward();
                    cancel = true;
                } else if (key == GLFW.GLFW_KEY_DELETE) {
                    textElement.deleteForward();
                    cancel = true;
                } else if (key == GLFW.GLFW_KEY_LEFT) {
                    textElement.moveCursor(-1, isShiftDown() && textElement.canSelectText());
                    cancel = true;
                } else if (key == GLFW.GLFW_KEY_RIGHT) {
                    textElement.moveCursor(1, isShiftDown() && textElement.canSelectText());
                    cancel = true;
                } else if (key == GLFW.GLFW_KEY_ENTER) {
                    if (focusedElement instanceof TextArea) {
                        textElement.insertText("\n");
                    } else {
                        document.clearFocus();
                    }
                    cancel = true;
                } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                    document.clearFocus();
                    cancel = true;
                }
            } else if (focusedElement != null) {
                if (isCtrlDown()) {
                    if (key == GLFW.GLFW_KEY_A && focusedElement.canSelectInnerText()) {
                        focusedElement.selectAllInnerText();
                        cancel = true;
                        continue;
                    }
                    if (key == GLFW.GLFW_KEY_C && focusedElement.canSelectInnerText() && !selectedText.isEmpty()) {
                        setClipboardText(selectedText);
                        cancel = true;
                        continue;
                    }
                }
                if (key == GLFW.GLFW_KEY_ESCAPE && focusedElement.canSelectInnerText()) {
                    focusedElement.clearTextSelection();
                    document.clearFocus();
                    cancel = true;
                }
            }
        }
        if (!repeat && key == GLFW.GLFW_KEY_LEFT_ALT) {
            DevTools.toggle();
        }
        if (!repeat && key == Keybindings.RELOAD.getKey().getValue()) {
            Loader.reload();
        }
        return cancel;
    }

    public static void onKeyReleased(int key) {
        for (Document document : Document.getAll()) {
            KeyEvent.triggerEvent(document, "keyup", key, false);
        }
    }

    private static String getSelectedTextFromElement(Element element) {
        if (element == null) return "";
        if (element instanceof AbstractText textElement) {
            String selected = textElement.getSelectedText();
            return selected == null ? "" : selected;
        }
        if (element.canSelectInnerText()) {
            String selected = element.getSelectedInnerText();
            return selected == null ? "" : selected;
        }
        return "";
    }

    private static String resolveSelectedText(Document document, Element focusedElement) {
        String selected = getSelectedTextFromElement(focusedElement);
        if (!selected.isEmpty()) return selected;

        Element active = document.getActiveElement();
        if (active != focusedElement) {
            selected = getSelectedTextFromElement(active);
            if (!selected.isEmpty()) return selected;
        }

        for (Element element : document.getElements()) {
            selected = getSelectedTextFromElement(element);
            if (!selected.isEmpty()) return selected;
        }
        return "";
    }

    public static Position getMousePosition() {
        return cachedMousePosition;
    }

    private static boolean isCtrlDown() {
        return isKeyPressed("key.keyboard.left.control") || isKeyPressed("key.keyboard.right.control");
    }

    private static boolean isShiftDown() {
        return isKeyPressed("key.keyboard.left.shift") || isKeyPressed("key.keyboard.right.shift");
    }

    public static String getClipboardText() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.keyboardHandler == null) return "";
        String text = minecraft.keyboardHandler.getClipboard();
        return text == null ? "" : text;
    }

    public static void setClipboardText(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.keyboardHandler == null) return;
        minecraft.keyboardHandler.setClipboard(text == null ? "" : text);
    }

    public static boolean isKeyPressed(String key) {
        return Client.isKeyPressed(key);
    }
}

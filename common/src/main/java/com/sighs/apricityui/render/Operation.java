package com.sighs.apricityui.render;

import com.sighs.apricityui.behavior.richtext.RichTextEditing;
import com.sighs.apricityui.behavior.richtext.RichTextRange;
import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.dev.ResourceManager;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.layout.Position;
import org.lwjgl.glfw.GLFW;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.List;

public class Operation {
    public static Position cachedMousePosition = null;
    private static int mouseButtons = 0;
    private static final long KEY_DEDUP_WINDOW_NS = 5_000_000L; // 5ms
    private static long lastKeyEventTimeNs = 0L;
    private static int lastKeyCode = -1;
    private static int lastScanCode = -1;
    private static int lastAction = -1;
    private static int lastModifiers = -1;
    private static boolean lastDevToolsInspectConsumed;

    public static boolean onMouseDown() {
        return onMouseDown(-1);
    }

    public static boolean onMouseDown(int button) {
        lastDevToolsInspectConsumed = false;
        mouseButtons |= buttonMask(button);
        Position mousePosition = getMousePositionDirectly();
        if (DevTools.handleInspectMouseDown(mousePosition, button)) {
            lastDevToolsInspectConsumed = true;
            return true;
        }
        MouseEvent event = new MouseEvent("mousedown", mousePosition, button);
        event.setTrusted(true);
        MouseEvent.tiggerEvent(event);
        return event.isNativeConsumed();
    }

    public static boolean onMouseUp() {
        return onMouseUp(-1);
    }

    public static boolean onMouseUp(int button) {
        lastDevToolsInspectConsumed = false;
        mouseButtons &= ~buttonMask(button);
        if (DevTools.handleInspectMouseUp(button)) {
            lastDevToolsInspectConsumed = true;
            return true;
        }
        MouseEvent event = new MouseEvent("mouseup", getMousePositionDirectly(), button);
        event.setTrusted(true);
        MouseEvent.tiggerEvent(event);
        return event.isNativeConsumed();
    }

    /** Returns whether the most recent mouse press/release was consumed by DevTools picking. */
    public static boolean wasDevToolsInspectConsumed() {
        return lastDevToolsInspectConsumed;
    }

    public static void onMouseMove(Position currentMousePosition) {
        if (currentMousePosition == null) {
            currentMousePosition = getMousePositionDirectly();
        }
        if (currentMousePosition == null) {
            return;
        }
        if (cachedMousePosition != null) {
            if (Double.compare(currentMousePosition.x, cachedMousePosition.x) == 0
                    && Double.compare(currentMousePosition.y, cachedMousePosition.y) == 0) {
                return;
            }
            MouseEvent mouseEvent = new MouseEvent("mousemove", currentMousePosition);
            mouseEvent.movementX = currentMousePosition.x - cachedMousePosition.x;
            mouseEvent.movementY = currentMousePosition.y - cachedMousePosition.y;
            mouseEvent.setTrusted(true);
            MouseEvent.tiggerEvent(mouseEvent);
        }
        cachedMousePosition = currentMousePosition;
    }

    public static boolean scroll(double delta) {
        MouseEvent mouseEvent = new MouseEvent("wheel", getMousePositionDirectly());
        mouseEvent.deltaY = -delta * 50;
        mouseEvent.scrollDelta = mouseEvent.deltaY;
        mouseEvent.cancelable = true;
        mouseEvent.setTrusted(true);
        MouseEvent.tiggerEvent(mouseEvent);
        return mouseEvent.isNativeConsumed();
    }

    public static int getMouseButtons() {
        return mouseButtons;
    }

    private static int buttonMask(int button) {
        return switch (button) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 8;
            case 4 -> 16;
            default -> 0;
        };
    }

    public static boolean onCharTyped(char code) {
        return onCharTyped((int) code);
    }

    public static boolean onCharTyped(int codePoint) {
        if (!Character.isValidCodePoint(codePoint)) return false;
        String content = new String(Character.toChars(codePoint));
        boolean shouldCancel = false;
        for (Document document : Document.getAll()) {
            Element focusedElement = document.getFocusedElement();
            if (focusedElement instanceof AbstractText textElement && textElement.canEditText()) {
                Event.runTrustedAction(() -> textElement.insertText(content));
                shouldCancel = true;
            } else if (focusedElement instanceof RichText richText && richText.canEditText()) {
                Event.runTrustedAction(() -> RichTextEditing.insertText(richText, content));
                shouldCancel = true;
            }
        }
        return shouldCancel;
    }

    public static boolean onKeyPressed(int key, int scanCode, int modifiers, boolean repeat, KeyEvent.Source source) {
        // Ctrl+A 只作用于一个目标文档，避免所有文档同时全选
        Document selectionTargetDocument = resolveSelectionTargetDocument();
        boolean cancel = false;
        for (Document document : Document.getAll()) {
            final boolean[] documentCanceled = {false};
            Event.runTrustedAction(() -> {
                KeyEvent keyEvent = KeyEvent.triggerEvent(document, "keydown", key, scanCode, modifiers, repeat, source);
                if (keyEvent != null && keyEvent.defaultPrevented) {
                    documentCanceled[0] = true;
                    return;
                }
                Element focusedElement = document.getFocusedElement();
                String selectedText = resolveSelectedText(document, focusedElement);
                boolean ctrlDown = isCtrlDown();

                if (focusedElement instanceof Input input && input.handleRangeKey(key)) {
                    documentCanceled[0] = true;
                    return;
                }

                // 富文本可编辑元素：方向键/Home/End 移动、Shift 扩展、编辑键、Esc 清焦点
                if (focusedElement instanceof RichText) {
                    RichTextSelection selection = document.getRichTextSelection();
                    if (selection == null || !selection.hasAnchor()) {
                        selection.setCollapsed(focusedElement, 0);
                    }
                    boolean keepSelection = isShiftDown();
                    boolean handled = true;
                    if (key == GLFW.GLFW_KEY_LEFT) {
                        selection.moveLeft(keepSelection);
                    } else if (key == GLFW.GLFW_KEY_RIGHT) {
                        selection.moveRight(keepSelection);
                    } else if (key == GLFW.GLFW_KEY_UP) {
                        selection.moveUp(keepSelection);
                    } else if (key == GLFW.GLFW_KEY_DOWN) {
                        selection.moveDown(keepSelection);
                    } else if (key == GLFW.GLFW_KEY_HOME) {
                        selection.moveToHome(keepSelection);
                    } else if (key == GLFW.GLFW_KEY_END) {
                        selection.moveToEnd(keepSelection);
                    } else if (key == GLFW.GLFW_KEY_A && ctrlDown) {
                        selection.selectAll(focusedElement);
                    } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                        document.clearFocus();
                    } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                        RichTextEditing.deleteBackward((RichText) focusedElement);
                    } else if (key == GLFW.GLFW_KEY_DELETE) {
                        RichTextEditing.deleteForward((RichText) focusedElement);
                    } else if (key == GLFW.GLFW_KEY_ENTER) {
                        RichTextEditing.insertParagraph((RichText) focusedElement);
                    } else if (ctrlDown && key == GLFW.GLFW_KEY_Z && !isShiftDown()) {
                        RichTextEditing.undo((RichText) focusedElement);
                    } else if (ctrlDown && (key == GLFW.GLFW_KEY_Y || (key == GLFW.GLFW_KEY_Z && isShiftDown()))) {
                        RichTextEditing.redo((RichText) focusedElement);
                    } else if (ctrlDown && key == GLFW.GLFW_KEY_V) {
                        Event clipboard = new Event(focusedElement, "paste", true);
                        clipboard.cancelable = true;
                        clipboard.clipboardData = new ClipboardDataBridge();
                        Event.markTrustedFromCurrentDispatch(clipboard);
                        Event.tiggerEvent(clipboard);
                        if (!clipboard.defaultPrevented) {
                            String internalHtml = getInternalClipboardHtml();
                            if (internalHtml != null) {
                                RichTextEditing.pasteHtml((RichText) focusedElement, internalHtml);
                            } else {
                                RichTextEditing.pasteText((RichText) focusedElement, getClipboardText());
                            }
                        }
                    } else if (ctrlDown && key == GLFW.GLFW_KEY_C && !selection.collapsed()) {
                        Event clipboard = new Event(focusedElement, "copy", true);
                        clipboard.cancelable = true;
                        clipboard.clipboardData = new ClipboardDataBridge();
                        Event.markTrustedFromCurrentDispatch(clipboard);
                        Event.tiggerEvent(clipboard);
                        if (clipboard.defaultPrevented) {
                            handled = false;
                        } else {
                            setClipboardText(selection.getSelectedText());
                            RichTextRange copyRange = selection.toRange();
                            setInternalClipboardHtml(copyRange == null ? null : copyRange.toHtml());
                        }
                    } else if (ctrlDown && key == GLFW.GLFW_KEY_X && !selection.collapsed()) {
                        Event clipboard = new Event(focusedElement, "cut", true);
                        clipboard.cancelable = true;
                        clipboard.clipboardData = new ClipboardDataBridge();
                        Event.markTrustedFromCurrentDispatch(clipboard);
                        Event.tiggerEvent(clipboard);
                        if (clipboard.defaultPrevented) {
                            handled = false;
                        } else {
                            setClipboardText(selection.getSelectedText());
                            RichTextRange cutRange = selection.toRange();
                            setInternalClipboardHtml(cutRange == null ? null : cutRange.toHtml());
                            RichTextEditing.deleteSelection((RichText) focusedElement);
                        }
                    } else if (shouldConsumeTextEntryKey(focusedElement, key)) {
                        // 字母/数字/符号键：消费 keydown，真正字符经 onCharTyped 插入
                        handled = true;
                    } else {
                        handled = false;
                    }
                    if (handled) {
                        documentCanceled[0] = true;
                        return;
                    }
                }

                if (focusedElement != null && ("BUTTON".equalsIgnoreCase(focusedElement.tagName)
                        || (focusedElement instanceof Input input
                        && ("submit".equalsIgnoreCase(input.getType())
                        || "reset".equalsIgnoreCase(input.getType())
                        || "button".equalsIgnoreCase(input.getType()))))
                        && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE)) {
                    focusedElement.click();
                    documentCanceled[0] = true;
                    return;
                }

                if (focusedElement instanceof Select select && select.handleKeyDownDefault(keyEvent)) {
                    documentCanceled[0] = true;
                    return;
                }

                if (focusedElement instanceof AbstractText textElement) {
                    if (ctrlDown) {
                        if (key == GLFW.GLFW_KEY_A) {
                            if (textElement.canSelectText()) {
                                textElement.selectAll();
                                documentCanceled[0] = true;
                                return;
                            }
                        }
                        if (key == GLFW.GLFW_KEY_C) {
                            if (textElement.canSelectText() && !selectedText.isEmpty()) {
                                Event clipboard = new Event(focusedElement, "copy", true);
                                clipboard.cancelable = true;
                                Event.markTrustedFromCurrentDispatch(clipboard);
                                Event.tiggerEvent(clipboard);
                                if (!clipboard.defaultPrevented) {
                                    setClipboardText(selectedText);
                                    documentCanceled[0] = true;
                                    return;
                                }
                            }
                        }
                        if (key == GLFW.GLFW_KEY_X) {
                            if (textElement.canEditText() && textElement.hasSelection()) {
                                Event clipboard = new Event(focusedElement, "cut", true);
                                clipboard.cancelable = true;
                                Event.markTrustedFromCurrentDispatch(clipboard);
                                Event.tiggerEvent(clipboard);
                                if (!clipboard.defaultPrevented) {
                                    if (!selectedText.isEmpty()) setClipboardText(selectedText);
                                    textElement.replaceSelection("");
                                    documentCanceled[0] = true;
                                    return;
                                }
                            }
                        }
                        if (key == GLFW.GLFW_KEY_V) {
                            if (textElement.canEditText()) {
                                Event clipboard = new Event(focusedElement, "paste", true);
                                clipboard.cancelable = true;
                                clipboard.clipboardData = new ClipboardDataBridge();
                                Event.markTrustedFromCurrentDispatch(clipboard);
                                Event.tiggerEvent(clipboard);
                                if (!clipboard.defaultPrevented) {
                                    textElement.insertText(getClipboardText());
                                    documentCanceled[0] = true;
                                    return;
                                }
                            }
                        }
                        if (key == GLFW.GLFW_KEY_Z) {
                            if (textElement.canEditText() && textElement.undo()) {
                                documentCanceled[0] = true;
                                return;
                            }
                        }
                    }

                    if (focusedElement instanceof Input input && key == GLFW.GLFW_KEY_SPACE && input.handleSpaceKey()) {
                        documentCanceled[0] = true;
                        return;
                    }

                    if (!textElement.canEditText()) return;

                    if (key == GLFW.GLFW_KEY_BACKSPACE) {
                        textElement.deleteBackward();
                        documentCanceled[0] = true;
                    } else if (key == GLFW.GLFW_KEY_DELETE) {
                        textElement.deleteForward();
                        documentCanceled[0] = true;
                    } else if (key == GLFW.GLFW_KEY_LEFT) {
                        textElement.moveCursor(-1, isShiftDown() && textElement.canSelectText());
                        documentCanceled[0] = true;
                    } else if (key == GLFW.GLFW_KEY_RIGHT) {
                        textElement.moveCursor(1, isShiftDown() && textElement.canSelectText());
                        documentCanceled[0] = true;
                    } else if (key == GLFW.GLFW_KEY_HOME) {
                        textElement.moveCursorToHome(isShiftDown() && textElement.canSelectText());
                        documentCanceled[0] = true;
                    } else if (key == GLFW.GLFW_KEY_END) {
                        textElement.moveCursorToEnd(isShiftDown() && textElement.canSelectText());
                        documentCanceled[0] = true;
                    } else if (key == GLFW.GLFW_KEY_UP) {
                        textElement.moveCursorByLine(-1, isShiftDown() && textElement.canSelectText());
                        documentCanceled[0] = true;
                    } else if (key == GLFW.GLFW_KEY_DOWN) {
                        textElement.moveCursorByLine(1, isShiftDown() && textElement.canSelectText());
                        documentCanceled[0] = true;
                    } else if (key == GLFW.GLFW_KEY_ENTER) {
                        if (textElement.isMultiline()) {
                            textElement.insertText("\n");
                        } else {
                            if (!focusedElement.submitEnclosingForm()) {
                                document.clearFocus();
                            }
                        }
                        documentCanceled[0] = true;
                    } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                        document.clearFocus();
                        documentCanceled[0] = true;
                    } else if (shouldConsumeTextEntryKey(focusedElement, key)) {
                        // Character insertion arrives through CharacterTyped. Consume the
                        // preceding key press so Minecraft shortcuts do not run first.
                        documentCanceled[0] = true;
                    }
                } else {
                    // 非输入控件（或无焦点）：文档级选择快捷键
                    if (ctrlDown) {
                        if (key == GLFW.GLFW_KEY_A && document == selectionTargetDocument && document.selectAllDocumentText()) {
                            documentCanceled[0] = true;
                            return;
                        }
                        if (key == GLFW.GLFW_KEY_C && !selectedText.isEmpty()) {
                            setClipboardText(selectedText);
                            documentCanceled[0] = true;
                            return;
                        }
                    }
                    if (key == GLFW.GLFW_KEY_ESCAPE && document.hasDocumentSelection()) {
                        document.clearDocumentSelection();
                        document.clearFocus();
                        documentCanceled[0] = true;
                    }
                }
            });
            cancel |= documentCanceled[0];
        }
        if (!repeat && handleFrameworkShortcut(key, modifiers)) {
            return true;
        }
        return cancel;
    }

    /**
     * 解析 Ctrl+A 的唯一目标文档：
     * 1. 最上层（front-to-back）持有文档级选区的文档（正在编辑的选区所在文档）；
     * 2. 否则最上层鼠标指针命中的文档；
     * 3. 否则回退到上下文文档（可能为 null）。
     */
    public static Document resolveSelectionTargetDocument() {
        List<Document> documents = DocumentLayerOrder.frontToBack(Document.getAll());
        for (Document document : documents) {
            if (document != null && document.hasDocumentSelection()) return document;
        }
        if (cachedMousePosition != null) {
            for (Document document : documents) {
                if (document != null && document.interceptsMouseEventsAt(cachedMousePosition)) {
                    return document;
                }
            }
        }
        return Document.getContextDocument();
    }

    public static boolean shouldConsumeTextEntryKey(Element focusedElement, int key) {
        if (focusedElement instanceof RichText richText && richText.canEditText()) {
            return isTextEntryKey(key);
        }
        if (!(focusedElement instanceof AbstractText textElement) || !textElement.canEditText()) {
            return false;
        }
        return isTextEntryKey(key);
    }

    private static boolean isTextEntryKey(int key) {
        return (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
                || (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)
                || (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_EQUAL)
                || key == GLFW.GLFW_KEY_SPACE
                || key == GLFW.GLFW_KEY_APOSTROPHE
                || key == GLFW.GLFW_KEY_COMMA
                || key == GLFW.GLFW_KEY_MINUS
                || key == GLFW.GLFW_KEY_PERIOD
                || key == GLFW.GLFW_KEY_SLASH
                || key == GLFW.GLFW_KEY_SEMICOLON
                || key == GLFW.GLFW_KEY_EQUAL
                || key == GLFW.GLFW_KEY_LEFT_BRACKET
                || key == GLFW.GLFW_KEY_BACKSLASH
                || key == GLFW.GLFW_KEY_RIGHT_BRACKET
                || key == GLFW.GLFW_KEY_GRAVE_ACCENT
                || key == GLFW.GLFW_KEY_WORLD_1
                || key == GLFW.GLFW_KEY_WORLD_2;
    }

    private static boolean handleFrameworkShortcut(int key, int modifiers) {
        boolean devToolsShortcut = key == AuiServices.keys().devToolsKey()
                || (key == GLFW.GLFW_KEY_I
                && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT))
                == (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT));
        if (devToolsShortcut) {
            DevTools.toggle();
            return true;
        }
        if (key == AuiServices.keys().resourceManagerKey()) {
            ResourceManager.toggle();
            return true;
        }
        if (key == AuiServices.keys().reloadKey()) {
            ClientLoader.reload();
            return true;
        }
        return false;
    }

    public static void onKeyReleased(int key) {
        onKeyReleased(key, 0, 0, KeyEvent.Source.INPUT_EVENT);
    }

    public static void onKeyReleased(int key, int scanCode, int modifiers, KeyEvent.Source source) {
        for (Document document : Document.getAll()) {
            KeyEvent.triggerEvent(document, "keyup", key, scanCode, modifiers, false, source);
        }
    }

    private static String resolveSelectedText(Document document, Element focusedElement) {
        // 文档级选择优先（非可编辑文本）
        if (document != null) {
            String docSelection = document.getDocumentSelectedText();
            if (docSelection != null && !docSelection.isEmpty()) return docSelection;
        }
        // 输入控件保留自己的选区
        if (focusedElement instanceof AbstractText textElement) {
            String selected = textElement.getSelectedText();
            if (selected != null && !selected.isEmpty()) return selected;
        }
        return "";
    }

    /**
     * FIXME:
     * 如果在某些情况（如窗口拖动等）鼠标位置缓存为空或者是读到旧的缓存值时请参考{@link #getMousePositionDirectly()}
     * 未来建议重构，统一输入源，或在输入更新链中保证鼠标坐标始终同步。
     *
     * @see Client#getMousePosition()
     */
    public static Position getMousePosition() {
        return cachedMousePosition;
    }

    public static Position getMousePositionDirectly() {
        Position live = AuiServices.client().getMousePositionDirectly();
        if (live != null) {
            if (cachedMousePosition == null) {
                cachedMousePosition = live;
            }
            return live;
        }
        return cachedMousePosition;
    }

    private static boolean isCtrlDown() {
        return isKeyPressed("key.keyboard.left.control") || isKeyPressed("key.keyboard.right.control");
    }

    private static boolean isShiftDown() {
        return isKeyPressed("key.keyboard.left.shift") || isKeyPressed("key.keyboard.right.shift");
    }

    public static String getClipboardText() {
        return Base.getClipboardText();
    }

    public static void setClipboardText(String text) {
        Base.setClipboardText(text);
    }

    // 应用内富文本剪贴板：系统剪贴板只有纯文本（GLFW），富文本 HTML 走内存。
    private static String internalClipboardHtml = null;

    public static String getInternalClipboardHtml() {
        return internalClipboardHtml;
    }

    public static void setInternalClipboardHtml(String html) {
        internalClipboardHtml = (html == null || html.isEmpty()) ? null : html;
    }

    public static boolean isKeyPressed(String key) {
        return AuiServices.client().isKeyPressed(key);
    }

    public static boolean handleKeyInput(int key, int scanCode, int action, int modifiers, boolean repeat, KeyEvent.Source source) {
        if (isDuplicateKeyEvent(key, scanCode, action, modifiers)) {
            return false;
        }
        if (action == GLFW.GLFW_RELEASE) {
            onKeyReleased(key, scanCode, modifiers, source);
            return false;
        }
        return onKeyPressed(key, scanCode, modifiers, repeat, source);
    }

    private static boolean isDuplicateKeyEvent(int key, int scanCode, int action, int modifiers) {
        long now = System.nanoTime();
        if (key == lastKeyCode
                && scanCode == lastScanCode
                && action == lastAction
                && modifiers == lastModifiers
                && (now - lastKeyEventTimeNs) <= KEY_DEDUP_WINDOW_NS) {
            return true;
        }
        lastKeyEventTimeNs = now;
        lastKeyCode = key;
        lastScanCode = scanCode;
        lastAction = action;
        lastModifiers = modifiers;
        return false;
    }

}

package com.sighs.apricityui.element;

import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.*;
import com.sighs.apricityui.util.TextMetrics;

import java.util.*;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Text;

public abstract class AbstractText extends Element {
    protected int maxLength = 256;
    protected int cursor = 0;
    protected long lastBlinkTime = 0;
    protected String placeholder = "";
    protected String cachedValue = "";

    protected int selectionStart = 0;
    protected int selectionEnd = 0;
    protected String selectionDirection = "none";
    protected boolean selecting = false;
    protected int selectionAnchor = 0;
    protected final Deque<TextState> undoStack = new ArrayDeque<>();
    protected boolean restoringUndo = false;
    protected boolean composing = false;
    protected static final int MAX_UNDO_STACK = 128;
    private String focusValueSnapshot = "";

    protected AbstractText(Document document, String tagName) {
        super(document, tagName);
        ensureValue();
        focusValueSnapshot = value;
        clearSelection();
        addSelectionEventListeners();
        addInternalEventListener("focus", event -> focusValueSnapshot = getValue());
        addInternalEventListener("blur", event -> {
            String currentValue = getValue();
            if (!Objects.equals(focusValueSnapshot, currentValue)) {
                dispatchChangeEvent();
                focusValueSnapshot = currentValue;
            }
        });
    }

    private void addSelectionEventListeners() {
        addEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouseEvent)) return;
            if (!canEditText() && !canSelectText()) return;
            if (mouseEvent.button == 2) {
                // 中键粘贴：Linux 主选区语义的跨平台等效实现 —— 把当前文档选区文本
                // 插入到光标处（若输入控件自身有选区则替换之），不清除文档选区、
                // 不进入输入控件自己的选区流程。
                if (canEditText() && document != null) {
                    String primary = document.getDocumentSelectedText();
                    if (primary != null && !primary.isEmpty()) {
                        replaceSelection(primary);
                    }
                }
                return;
            }
            if (document != null) {
                document.clearAllTextSelectionsExcept(this);
            }

            if (canSelectText() && Interaction.isUserSelectAll(this)) {
                selectAll();
                selecting = false;
                clampScroll();
                return;
            }

            locateCursor(mouseEvent.offsetX, mouseEvent.offsetY);
            if (canSelectText() && mouseEvent.shiftKey) {
                if (!hasSelection()) selectionAnchor = selectionStart;
                selectionStart = selectionAnchor;
                selectionEnd = cursor;
                updateSelectionDirection();
            } else {
                selectionAnchor = cursor;
                if (canSelectText()) clearSelection();
            }
            selecting = canSelectText();
            clampScroll();
        });

        addEventListener("mousemove", event -> {
            if (!(event instanceof MouseEvent mouseEvent) || !canSelectText()) return;
            if (!selecting || document.getPressedElement() != this) return;

            locateCursor(mouseEvent.offsetX, mouseEvent.offsetY);
            selectionStart = selectionAnchor;
            selectionEnd = cursor;
            updateSelectionDirection();
            clampScroll();
        });

        addEventListener("mouseup", event -> selecting = false);
    }

    @Override
    protected void onInitFromDom(Element origin) {
        placeholder = getAttribute("placeholder");
        String maxLengthAttr = getAttribute("maxlength");
        int parsed = Size.parse(maxLengthAttr);
        if (parsed > 0) maxLength = parsed;

        ensureValue();
        cursor = Math.min(cursor, value.length());
        selectionAnchor = cursor;
        clearSelection();
    }

    @Override
    public void setAttribute(String name, String value) {
        super.setAttribute(name, value);
        syncTextAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        super.removeAttribute(name);
        syncTextAttribute(name, null);
    }

    private void syncTextAttribute(String name, String attrValue) {
        if (name.equals("placeholder")) {
            placeholder = attrValue == null ? "" : attrValue;
            return;
        }

        if (name.equals("maxlength")) {
            int parsed = Size.parse(attrValue == null ? "" : attrValue);
            maxLength = parsed > 0 ? parsed : 256;
            return;
        }

        if (name.equals("value")) {
            ensureValue();
            cursor = Math.min(cursor, value.length());
            selectionAnchor = cursor;
            clearSelection();
            undoStack.clear();
            getRenderer().text.clear();
        }
    }

    protected void ensureValue() {
        if (value == null) value = "";
    }

    public boolean canEditText() {
        return true;
    }

    public boolean canSelectText() {
        return Interaction.isUserSelectable(this);
    }

    public boolean isMultiline() {
        return supportsMultilineInput();
    }

    protected boolean supportsMultilineInput() {
        return false;
    }

    public int getCursor() {
        return cursor;
    }

    public int getSelectionStart() {
        return Math.min(selectionStart, value == null ? 0 : value.length());
    }

    public int getSelectionEnd() {
        return Math.min(selectionEnd, value == null ? 0 : value.length());
    }

    public String getSelectionDirection() {
        return selectionDirection;
    }

    public void setSelectionRange(int start, int end) {
        setSelectionRange(start, end, "none");
    }

    public void setSelectionRange(int start, int end, String direction) {
        ensureValue();
        int length = value.length();
        int safeStart = clamp(start, 0, length);
        int safeEnd = clamp(end, 0, length);
        selectionStart = safeStart;
        selectionEnd = safeEnd;
        selectionDirection = normalizeSelectionDirection(direction);
        selectionAnchor = "backward".equals(selectionDirection) ? safeEnd : safeStart;
        cursor = safeEnd;
        clampScroll();
    }

    public void select() {
        ensureValue();
        setSelectionRange(0, value.length(), "forward");
    }

    public void setRangeText(String replacement) {
        setRangeText(replacement, getSelectionStart(), getSelectionEnd(), "preserve");
    }

    public void setRangeText(String replacement, int start, int end, String selectionMode) {
        ensureValue();
        if (!canEditText()) return;
        int safeStart = clamp(Math.min(start, end), 0, value.length());
        int safeEnd = clamp(Math.max(start, end), 0, value.length());
        String normalized = normalizeInsertedText(replacement == null ? "" : replacement);
        if (!dispatchBeforeInputEvent("insertReplacementText", normalized)) return;
        pushUndoState();
        value = value.substring(0, safeStart) + normalized + value.substring(safeEnd);
        int nextStart = safeStart;
        int nextEnd = safeStart + normalized.length();
        if ("select".equalsIgnoreCase(selectionMode)) {
            selectionStart = nextStart;
            selectionEnd = nextEnd;
            selectionDirection = "forward";
            cursor = nextEnd;
        } else if ("start".equalsIgnoreCase(selectionMode)) {
            clearSelection();
            cursor = nextStart;
            clearSelection();
        } else if ("end".equalsIgnoreCase(selectionMode)) {
            clearSelection();
            cursor = nextEnd;
            clearSelection();
        } else {
            int delta = normalized.length() - (safeEnd - safeStart);
            int cursorValue = clamp(cursor + delta, 0, value.length());
            cursor = cursorValue;
            clearSelection();
        }
        clampScroll();
        getRenderer().text.clear();
        dispatchInputEvent("insertReplacementText", normalized);
    }

    public void beginComposition(String data) {
        Event composition = new Event.CompositionEvent(this, "compositionstart", true, data);
        ((Event.CompositionEvent) composition).isComposing = true;
        composing = true;
        Event.tiggerEvent(composition);
    }

    public void updateComposition(String data) {
        Event composition = new Event.CompositionEvent(this, "compositionupdate", true, data);
        ((Event.CompositionEvent) composition).isComposing = true;
        Event.tiggerEvent(composition);
    }

    public void endComposition(String data) {
        Event composition = new Event.CompositionEvent(this, "compositionend", true, data);
        ((Event.CompositionEvent) composition).isComposing = false;
        Event.tiggerEvent(composition);
        composing = false;
    }

    public boolean hasSelection() {
        return selectionStart != selectionEnd;
    }

    protected int selMin() {
        return Math.min(selectionStart, selectionEnd);
    }

    protected int selMax() {
        return Math.max(selectionStart, selectionEnd);
    }

    public void clearSelection() {
        selectionStart = cursor;
        selectionEnd = cursor;
        selectionDirection = "none";
        addDirtyFlags(Drawer.REPAINT);
    }

    public void selectAll() {
        ensureValue();
        cursor = value.length();
        selectionAnchor = 0;
        selectionStart = 0;
        selectionEnd = cursor;
        selectionDirection = "forward";
        clampScroll();
    }

    public String getSelectedText() {
        ensureValue();
        if (!hasSelection()) return "";
        return value.substring(selMin(), selMax());
    }

    public void replaceSelection(String str) {
        if (!canEditText()) return;
        String normalized = str == null ? "" : normalizeInsertedText(str);
        if (normalized.isEmpty()) {
            if (!hasSelection()) return;
            if (!dispatchBeforeInputEvent("deleteContentBackward", null)) return;
            pushUndoState();
            sliceText(selMin(), selMax(), "deleteContentBackward", false);
            return;
        }
        insertText(normalized);
    }

    public void insertText(String str) {
        if (!canEditText()) return;
        if (str == null || str.isEmpty()) return;

        ensureValue();
        str = normalizeInsertedText(str);
        if (str.isEmpty()) return;
        if (!dispatchBeforeInputEvent("insertText", str)) return;
        pushUndoState();

        if (hasSelection()) {
            int min = selMin();
            int max = selMax();
            value = value.substring(0, min) + value.substring(max);
            cursor = min;
        }

        int allowed = maxLength - value.length();
        if (allowed <= 0) {
            selectionAnchor = cursor;
            clearSelection();
            clampScroll();
            return;
        }
        if (str.length() > allowed) {
            str = str.substring(0, allowed);
        }

        String before = value.substring(0, cursor);
        String after = value.substring(cursor);
        value = before + str + after;
        cursor += str.length();
        selectionAnchor = cursor;
        clearSelection();
        clampScroll();
        getRenderer().text.clear();
        dispatchInputEvent("insertText", str);
    }

    private String normalizeInsertedText(String str) {
        if (supportsMultilineInput()) {
            return str.replace("\r\n", "\n").replace('\r', '\n');
        }
        return str.replace("\r", "").replace("\n", "");
    }

    public void moveCursor(int offset) {
        moveCursor(offset, false);
    }

    public void moveCursor(int offset, boolean keepSelection) {
        ensureValue();
        if (keepSelection && !hasSelection()) {
            selectionAnchor = cursor;
        }

        cursor += offset;
        cursor = clamp(cursor, 0, value.length());

        if (keepSelection) {
            selectionStart = selectionAnchor;
            selectionEnd = cursor;
            updateSelectionDirection();
        } else {
            selectionAnchor = cursor;
            clearSelection();
        }
        clampScroll();
    }

    /**
     * 光标移动到当前行首（多行）或文本开头（单行），对应浏览器 Home 键。
     */
    public void moveCursorToHome(boolean keepSelection) {
        ensureValue();
        applyNavigationMove(lineStartIndex(), keepSelection);
    }

    /**
     * 光标移动到当前行尾（多行）或文本末尾（单行），对应浏览器 End 键。
     */
    public void moveCursorToEnd(boolean keepSelection) {
        ensureValue();
        applyNavigationMove(lineEndIndex(), keepSelection);
    }

    /**
     * 光标按视觉行上下移动（多行），保持视觉列（以字符宽度度量），对应浏览器 ↑/↓ 键。
     * 单行输入控件不消费该操作（浏览器单行 input 的 ↑/↓ 为 no-op）。
     */
    public void moveCursorByLine(int delta, boolean keepSelection) {
        if (!supportsMultilineInput() || delta == 0) return;
        ensureValue();

        Text.WrappedText wrapped = wrapForNavigation();
        List<String> lines = wrapped.lines();
        int[] starts = wrapped.starts();
        if (lines.isEmpty()) return;

        int line = resolveNavigationLine(lines, starts, cursor);
        int targetLine = clamp(line + delta, 0, lines.size() - 1);
        if (targetLine == line) return;

        int columnStart = starts[line];
        int column = clamp(cursor - columnStart, 0, lines.get(line).length());
        double currentX = column == 0 ? 0 : Size.measureText(this, lines.get(line).substring(0, column));

        // 在目标行中选取与当前光标 X 距离最近的字符列。
        String targetText = lines.get(targetLine);
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        double acc = 0;
        for (int i = 0; i <= targetText.length(); i++) {
            double distance = Math.abs(acc - currentX);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
            if (i < targetText.length()) {
                acc += Size.measureText(this, String.valueOf(targetText.charAt(i)));
            }
        }
        applyNavigationMove(starts[targetLine] + best, keepSelection);
    }

    private Text.WrappedText wrapForNavigation() {
        Text text = Text.of(this);
        text.content = getRenderText();
        return Text.wrap(text, Box.of(this).innerSize().width());
    }

    private int resolveNavigationLine(List<String> lines, int[] starts, int index) {
        int line = 0;
        while (line < lines.size() - 1 && index > starts[line] + lines.get(line).length()) {
            line++;
        }
        return line;
    }

    private int lineStartIndex() {
        if (!supportsMultilineInput()) return 0;
        Text.WrappedText wrapped = wrapForNavigation();
        List<String> lines = wrapped.lines();
        int[] starts = wrapped.starts();
        if (lines.isEmpty()) return 0;
        return starts[resolveNavigationLine(lines, starts, cursor)];
    }

    private int lineEndIndex() {
        ensureValue();
        if (!supportsMultilineInput()) return value.length();
        Text.WrappedText wrapped = wrapForNavigation();
        List<String> lines = wrapped.lines();
        int[] starts = wrapped.starts();
        if (lines.isEmpty()) return value.length();
        int line = resolveNavigationLine(lines, starts, cursor);
        return starts[line] + lines.get(line).length();
    }

    private void applyNavigationMove(int target, boolean keepSelection) {
        ensureValue();
        target = clamp(target, 0, value.length());
        if (keepSelection && !hasSelection()) {
            selectionAnchor = cursor;
        }
        cursor = target;
        if (keepSelection) {
            selectionStart = selectionAnchor;
            selectionEnd = cursor;
            updateSelectionDirection();
        } else {
            selectionAnchor = cursor;
            clearSelection();
        }
        clampScroll();
    }

    public boolean deleteBackward() {
        ensureValue();
        if (hasSelection()) {
            if (!dispatchBeforeInputEvent("deleteContentBackward", null)) return false;
            pushUndoState();
            sliceText(selMin(), selMax(), "deleteContentBackward", false);
            return true;
        }
        if (cursor <= 0) return false;
        if (!dispatchBeforeInputEvent("deleteContentBackward", null)) return false;
        pushUndoState();
        sliceText(cursor - 1, cursor, "deleteContentBackward", false);
        return true;
    }

    public boolean deleteForward() {
        ensureValue();
        if (hasSelection()) {
            if (!dispatchBeforeInputEvent("deleteContentForward", null)) return false;
            pushUndoState();
            sliceText(selMin(), selMax(), "deleteContentForward", false);
            return true;
        }
        if (cursor >= value.length()) return false;
        if (!dispatchBeforeInputEvent("deleteContentForward", null)) return false;
        pushUndoState();
        sliceText(cursor, cursor + 1, "deleteContentForward", false);
        return true;
    }

    public void sliceText(int start, int end) {
        sliceText(start, end, "deleteContentBackward", true);
    }

    private void sliceText(int start, int end, String inputType, boolean dispatchInputEvent) {
        ensureValue();
        if (start < 0) start = 0;
        if (end > value.length()) end = value.length();
        if (start >= end) return;

        String before = value.substring(0, start);
        String after = value.substring(end);
        value = before + after;
        cursor = start;
        selectionAnchor = cursor;
        clearSelection();
        clampScroll();
        getRenderer().text.clear();
        if (dispatchInputEvent) {
            dispatchInputEvent(inputType, null);
        }
    }

    public boolean undo() {
        if (!canEditText()) return false;
        if (undoStack.isEmpty()) return false;
        if (!dispatchBeforeInputEvent("historyUndo", null)) return false;
        TextState state = undoStack.pop();
        restoringUndo = true;
        try {
            value = state.value;
            cursor = clamp(state.cursor, 0, value.length());
            selectionStart = clamp(state.selectionStart, 0, value.length());
            selectionEnd = clamp(state.selectionEnd, 0, value.length());
            selectionAnchor = clamp(state.selectionAnchor, 0, value.length());
            selectionDirection = normalizeSelectionDirection(state.selectionDirection);
            clampScroll();
            getRenderer().text.clear();
            dispatchInputEvent("historyUndo", null);
        } finally {
            restoringUndo = false;
        }
        return true;
    }

    protected void dispatchInputEvent(String inputType, String data) {
        Event.InputEvent event = new Event.InputEvent(this, "input", true, inputType, data);
        event.isComposing = composing;
        Event.markTrustedFromCurrentDispatch(event);
        Event.tiggerEvent(event);
    }

    protected boolean dispatchBeforeInputEvent(String inputType, String data) {
        Event.InputEvent event = new Event.InputEvent(this, "beforeinput", true, inputType, data);
        event.cancelable = true;
        event.isComposing = composing;
        Event.markTrustedFromCurrentDispatch(event);
        Event.tiggerEvent(event);
        return !event.defaultPrevented;
    }

    protected void dispatchChangeEvent() {
        Event event = new Event(this, "change", true);
        Event.markTrustedFromCurrentDispatch(event);
        Event.tiggerEvent(event);
    }

    protected void pushUndoState() {
        if (restoringUndo) return;
        ensureValue();
        TextState current = new TextState(value, cursor, selectionStart, selectionEnd, selectionAnchor, selectionDirection);
        TextState top = undoStack.peek();
        if (top != null && top.equals(current)) return;
        undoStack.push(current);
        while (undoStack.size() > MAX_UNDO_STACK) {
            undoStack.removeLast();
        }
    }

    protected void locateCursor(double mouseOffsetX, double mouseOffsetY) {
        locateCursor(mouseOffsetX);
    }

    protected void locateCursor(double mouseOffsetX) {
        Box box = Box.of(this);
        double contentStartX = box.getBorderLeft() + box.getPaddingLeft();
        double relativeX = mouseOffsetX - contentStartX + scrollLeft - resolveTextAlignX(getRenderText());

        String text = getRenderText();
        if (text.isEmpty()) {
            cursor = 0;
            return;
        }

        double currentWidth = 0;
        int newCursor = 0;
        for (int i = 0; i < text.length(); i++) {
            String charStr = String.valueOf(text.charAt(i));
            double charWidth = Size.measureText(this, charStr);
            if (relativeX <= currentWidth + charWidth / 2.0) {
                break;
            }
            currentWidth += charWidth;
            newCursor++;
        }

        cursor = newCursor;
        clampScroll();
    }

    /**
     * 单行输入文字的水平起点（相对内容区左缘）：整行放得下时应用 text-align/text-indent
     * （RTL 由 computeAlignedX 的 start/end 映射处理），溢出时按浏览器左对齐可见区，
     * 保证渲染（Input.drawTextInput）与光标映射（locateCursor）使用同一套偏移。
     */
    protected double resolveTextAlignX(String content) {
        if (content == null || content.isEmpty()) return 0;
        double lineWidth = Size.measureText(this, content);
        double contentWidth = Math.max(0, Box.of(this).innerSize().width());
        if (lineWidth > contentWidth) return 0;
        return TextMetrics.computeAlignedX(Text.of(this), contentWidth, lineWidth, true);
    }

    private void updateSelectionDirection() {
        if (selectionStart == selectionEnd) selectionDirection = "none";
        else selectionDirection = selectionEnd < selectionStart ? "backward" : "forward";
    }

    private static String normalizeSelectionDirection(String direction) {
        if ("backward".equalsIgnoreCase(direction)) return "backward";
        if ("forward".equalsIgnoreCase(direction)) return "forward";
        return "none";
    }

    protected void clampScroll() {
        String text = getRenderText();
        if (cursor > text.length()) cursor = text.length();
        if (cursor < 0) cursor = 0;

        String textBeforeCursor = text.substring(0, cursor);
        double cursorX = Size.measureText(this, textBeforeCursor);
        this.scrollWidth = Size.measureText(this, text);
        double visibleWidth = Math.max(0, Box.of(this).innerSize().width());
        double maxScrollLeft = Math.max(0, scrollWidth - visibleWidth);
        double desiredScrollLeft = scrollLeft;

        if (cursorX < desiredScrollLeft) desiredScrollLeft = cursorX;
        else if (cursorX > desiredScrollLeft + visibleWidth) desiredScrollLeft = cursorX - visibleWidth + 2;

        // Native text controls keep their internal scroll position synchronous.
        // Do not route caret visibility through the page scroll model's easing
        // and overscroll, which causes a one-frame horizontal jump on mousedown.
        setTextScrollLeftImmediate(Math.max(0, Math.min(maxScrollLeft, desiredScrollLeft)));
        this.addDirtyFlags(Drawer.REPAINT);
    }

    protected final void setTextScrollLeftImmediate(double value) {
        double visibleWidth = Math.max(0, Box.of(this).innerSize().width());
        double maxScrollLeft = Math.max(0, scrollWidth - visibleWidth);
        double clamped = Math.max(0, Math.min(maxScrollLeft, value));
        scrollLeft = clamped;
        targetScrollLeft = clamped;
    }

    protected final void setTextScrollTopImmediate(double value) {
        double visibleHeight = Math.max(0, Box.of(this).innerSize().height());
        double maxScrollTop = Math.max(0, scrollHeight - visibleHeight);
        double clamped = Math.max(0, Math.min(maxScrollTop, value));
        scrollTop = clamped;
        targetScrollTop = clamped;
    }

    protected String getRenderText() {
        ensureValue();
        return value;
    }

    protected void drawSingleLineSelection(PoseStack poseStack, Rect rectRenderer, String renderText, float drawY, double lineHeight) {
        if (!canSelectText()) return;
        if (!hasSelection()) return;
        int min = clamp(selMin(), 0, renderText.length());
        int max = clamp(selMax(), 0, renderText.length());
        if (min >= max) return;

        Position contentPos = rectRenderer.getContentPosition();
        double alignX = resolveTextAlignX(renderText);
        double startX = alignX + Size.measureText(this, renderText.substring(0, min)) - scrollLeft;
        double endX = alignX + Size.measureText(this, renderText.substring(0, max)) - scrollLeft;

        float x0 = (float) (contentPos.x + startX);
        float x1 = (float) (contentPos.x + endX);
        float y0 = drawY;
        float y1 = y0 + (float) lineHeight;
        Graph.drawFillRect(poseStack.last().pose(), x0, y0, x1, y1, Text.getSelectionColor(this));
    }

    protected void drawSingleLineCursor(PoseStack poseStack, String renderText, float drawX, float drawY, float lineHeight) {
        if (!Element.isElementFocusing(this)) return;
        String textBefore = renderText.substring(0, Math.min(cursor, renderText.length()));
        double cursorXOffset = Size.measureText(this, textBefore);
        float renderX = (float) (drawX + cursorXOffset);
        Graph.drawCursor(poseStack.last().pose(), renderX, drawY, lineHeight, Text.getFontColor(this), this.lastBlinkTime);
    }

    protected List<String> splitLines(String text) {
        return new ArrayList<>(List.of(text.split("\n", -1)));
    }

    protected int[] buildLineStarts(List<String> lines) {
        int[] starts = new int[lines.size()];
        int offset = 0;
        for (int i = 0; i < lines.size(); i++) {
            starts[i] = offset;
            offset += lines.get(i).length();
            if (i < lines.size() - 1) offset++;
        }
        return starts;
    }

    protected int clamp(int value, int min, int max) {
        if (value < min) return min;
        return Math.min(value, max);
    }

    @Override
    public void tick() {
        if (!Objects.equals(cachedValue, value) && value != null) {
            cachedValue = value;
            getRenderer().text.clear();
            getRenderer().size.clear();
            if (document != null) {
                document.markDirty(this, Drawer.RELAYOUT | Drawer.REPAINT);
                if (parentElement != null) {
                    parentElement.getRenderer().size.clear();
                    document.markDirty(parentElement, Drawer.RELAYOUT | Drawer.REPAINT);
                }
            }
        }
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    protected record TextState(String value, int cursor, int selectionStart, int selectionEnd,
                               int selectionAnchor, String selectionDirection) {
    }
}

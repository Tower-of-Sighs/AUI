package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public abstract class AbstractText extends Element {
    protected int maxLength = 256;
    protected int cursor = 0;
    protected long lastBlinkTime = 0;
    protected String placeholder = "";
    protected String cachedValue = "";

    protected int selectionStart = 0;
    protected int selectionEnd = 0;
    protected boolean selecting = false;
    protected int selectionAnchor = 0;
    protected final Deque<TextState> undoStack = new ArrayDeque<>();
    protected boolean restoringUndo = false;
    protected static final int MAX_UNDO_STACK = 128;

    protected AbstractText(Document document, String tagName) {
        super(document, tagName);
        ensureValue();
        clearSelection();
        addSelectionEventListeners();
    }

    private void addSelectionEventListeners() {
        addEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouseEvent)) return;
            if (!canEditText() && !canSelectText()) return;

            locateCursor(mouseEvent.offsetX, mouseEvent.offsetY);
            if (canSelectText() && mouseEvent.shiftKey) {
                if (!hasSelection()) selectionAnchor = selectionStart;
                selectionStart = selectionAnchor;
                selectionEnd = cursor;
            } else {
                selectionAnchor = cursor;
                if (canSelectText()) clearSelection();
            }
            selecting = canSelectText();
            clampScroll();
        });

        addEventListener("mousemove", event -> {
            if (!(event instanceof MouseEvent mouseEvent) || !canSelectText()) return;
            if (!selecting || document.getActiveElement() != this) return;

            locateCursor(mouseEvent.offsetX, mouseEvent.offsetY);
            selectionStart = selectionAnchor;
            selectionEnd = cursor;
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
        return Style.isUserSelectable(this);
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
        addDirtyFlags(Drawer.REPAINT);
    }

    public void selectAll() {
        ensureValue();
        cursor = value.length();
        selectionAnchor = 0;
        selectionStart = 0;
        selectionEnd = cursor;
        clampScroll();
    }

    public String getSelectedText() {
        ensureValue();
        if (!hasSelection()) return "";
        return value.substring(selMin(), selMax());
    }

    public void replaceSelection(String str) {
        insertText(str);
    }

    public void insertText(String str) {
        if (!canEditText()) return;
        if (str == null || str.isEmpty()) return;
        pushUndoState();

        ensureValue();
        str = normalizeInsertedText(str);
        if (str.isEmpty()) return;

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
        } else {
            selectionAnchor = cursor;
            clearSelection();
        }
        clampScroll();
    }

    public boolean deleteBackward() {
        ensureValue();
        if (hasSelection()) {
            pushUndoState();
            sliceText(selMin(), selMax());
            return true;
        }
        if (cursor <= 0) return false;
        pushUndoState();
        sliceText(cursor - 1, cursor);
        return true;
    }

    public boolean deleteForward() {
        ensureValue();
        if (hasSelection()) {
            pushUndoState();
            sliceText(selMin(), selMax());
            return true;
        }
        if (cursor >= value.length()) return false;
        pushUndoState();
        sliceText(cursor, cursor + 1);
        return true;
    }

    public void sliceText(int start, int end) {
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
    }

    public boolean undo() {
        if (!canEditText()) return false;
        if (undoStack.isEmpty()) return false;
        TextState state = undoStack.pop();
        restoringUndo = true;
        try {
            value = state.value;
            cursor = clamp(state.cursor, 0, value.length());
            selectionStart = clamp(state.selectionStart, 0, value.length());
            selectionEnd = clamp(state.selectionEnd, 0, value.length());
            selectionAnchor = clamp(state.selectionAnchor, 0, value.length());
            clampScroll();
            getRenderer().text.clear();
        } finally {
            restoringUndo = false;
        }
        return true;
    }

    protected void pushUndoState() {
        if (restoringUndo) return;
        ensureValue();
        TextState current = new TextState(value, cursor, selectionStart, selectionEnd, selectionAnchor);
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
        double relativeX = mouseOffsetX - contentStartX + scrollLeft;

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

    protected void clampScroll() {
        String text = getRenderText();
        if (cursor > text.length()) cursor = text.length();
        if (cursor < 0) cursor = 0;

        String textBeforeCursor = text.substring(0, cursor);
        double cursorX = Size.measureText(this, textBeforeCursor);

        Size size = Size.getContentSize(this);
        double visibleWidth = size.width();

        if (cursorX < scrollLeft) setScrollLeft(cursorX);
        else if (cursorX > scrollLeft + visibleWidth) setScrollLeft(cursorX - visibleWidth + 2);

        this.scrollWidth = Size.measureText(this, text);
        this.addDirtyFlags(Drawer.REPAINT);
    }

    protected String getRenderText() {
        ensureValue();
        return value;
    }

    protected void drawSingleLineSelection(PoseStack poseStack, Rect rectRenderer, String renderText, double lineHeight) {
        if (!canSelectText()) return;
        if (!hasSelection()) return;
        int min = clamp(selMin(), 0, renderText.length());
        int max = clamp(selMax(), 0, renderText.length());
        if (min >= max) return;

        Position contentPos = rectRenderer.getContentPosition();
        double startX = Size.measureText(this, renderText.substring(0, min)) - scrollLeft;
        double endX = Size.measureText(this, renderText.substring(0, max)) - scrollLeft;

        float x0 = (float) (contentPos.x + startX);
        float x1 = (float) (contentPos.x + endX);
        float y0 = (float) contentPos.y;
        float y1 = y0 + (float) lineHeight;
        Graph.drawFillRect(poseStack.last().pose(), x0, y0, x1, y1, Style.getSelectionColor(this));
    }

    protected void drawSingleLineCursor(PoseStack poseStack, String renderText, float drawX, float drawY, float lineHeight) {
        if (!Element.isElementFocusing(this)) return;
        String textBefore = renderText.substring(0, Math.min(cursor, renderText.length()));
        double cursorXOffset = Size.measureText(this, textBefore);
        float renderX = (float) (drawX + cursorXOffset);
        Graph.drawCursor(poseStack.last().pose(), renderX, drawY, lineHeight, Style.getFontColor(this), this.lastBlinkTime);
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
        }
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    protected record TextState(String value, int cursor, int selectionStart, int selectionEnd, int selectionAnchor) {}
}

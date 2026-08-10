package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.*;

import java.util.List;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.util.TextMetrics;

@ElementRegister(TextArea.TAG_NAME)
public class TextArea extends AbstractText {
    public static final String TAG_NAME = "TEXTAREA";
    private boolean textAreaValueDirty;
    private boolean resizing;
    private double resizeStartX;
    private double resizeStartY;
    private double resizeStartWidth;
    private double resizeStartHeight;

    public TextArea(Document document) {
        super(document, TAG_NAME);
        addInternalEventListener("mousedown", this::beginResize);
        addInternalEventListener("mousemove", this::continueResize);
        addInternalEventListener("mouseup", event -> resizing = false);
    }

    @Override
    protected boolean supportsMultilineInput() {
        return true;
    }

    @Override
    protected void onInitFromDom(Element origin) {
        super.onInitFromDom(origin);

        if (!hasAttribute("value") && (value == null || value.isEmpty())) {
            String inlineText = origin == null ? "" : origin.getTextContent();
            if (inlineText == null) inlineText = "";
            value = inlineText.replace("\r\n", "\n").replace('\r', '\n');
            textAreaValueDirty = false;
            cursor = Math.min(cursor, value.length());
            selectionAnchor = cursor;
            clearSelection();
            getRenderer().text.clear();
        }
    }

    @Override
    public String getDefaultValue() {
        String text = getTextContent();
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    @Override
    public void setDefaultValue(String value) {
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        setTextContent(normalized);
        if (!textAreaValueDirty) {
            this.value = normalized;
            cursor = Math.min(cursor, normalized.length());
            selectionAnchor = cursor;
            clearSelection();
            clampScroll();
            getRenderer().text.clear();
        }
    }

    @Override
    public void setValue(String value) {
        super.setValue(value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n'));
        textAreaValueDirty = true;
    }

    @Override
    protected void restoreFormValue(String restored) {
        String normalized = restored == null ? "" : restored.replace("\r\n", "\n").replace('\r', '\n');
        value = normalized;
        textAreaValueDirty = false;
        cursor = Math.min(cursor, normalized.length());
        selectionAnchor = cursor;
        clearSelection();
        clampScroll();
        getRenderer().text.clear();
        getRenderer().wrappedText.clear();
        invalidateStyle();
    }

    @Override
    protected void locateCursor(double mouseOffsetX, double mouseOffsetY) {
        String renderText = getRenderText();
        Text text = Text.of(this);
        text.content = renderText;
        Text.WrappedText wrapped = Text.wrap(text, Box.of(this).innerSize().width());
        List<String> lines = wrapped.lines();
        int[] starts = wrapped.starts();

        Box box = Box.of(this);
        double contentStartX = box.getBorderLeft() + box.getPaddingLeft();
        double contentStartY = box.getBorderTop() + box.getPaddingTop();
        double lineHeight = text.lineHeight;
        if (lineHeight <= 0) lineHeight = Size.DEFAULT_LINE_HEIGHT;

        double relativeY = mouseOffsetY - contentStartY + getScrollTop();
        int line = clamp((int) Math.floor(relativeY / lineHeight), 0, Math.max(0, lines.size() - 1));

        String lineText = lines.get(line);
        double alignX = textAlignX(text, lineText, line);
        double relativeX = mouseOffsetX - contentStartX + scrollLeft - alignX;
        double currentWidth = 0;
        int column = 0;
        for (int i = 0; i < lineText.length(); i++) {
            double charWidth = Size.measureText(this, String.valueOf(lineText.charAt(i)));
            if (relativeX <= currentWidth + charWidth / 2.0) break;
            currentWidth += charWidth;
            column++;
        }

        cursor = starts[line] + column;
        clampScroll();
    }

    /**
     * 文本某行相对内容区左缘的对齐偏移：行放得下时应用 text-align/text-indent
     * （RTL 由 computeAlignedX 的 start/end 映射处理），溢出时按浏览器左对齐可见区，
     * 渲染（drawPhase/drawSelection）与光标映射（locateCursor）共用同一套偏移。
     */
    private double textAlignX(Text text, String line, int lineIndex) {
        double lineWidth = Size.measureText(this, line);
        double contentWidth = Math.max(0, Box.of(this).innerSize().width());
        if (lineWidth > contentWidth) return 0;
        return TextMetrics.computeAlignedX(text, contentWidth, lineWidth, lineIndex == 0);
    }

    @Override
    protected void clampScroll() {
        String renderText = getRenderText();
        Text text = Text.of(this);
        text.content = renderText;
        Text.WrappedText wrapped = Text.wrap(text, Box.of(this).innerSize().width());
        List<String> lines = wrapped.lines();
        int[] starts = wrapped.starts();

        cursor = clamp(cursor, 0, renderText.length());

        double lineHeight = text.lineHeight;
        int cursorLine = resolveCursorLine(lines, starts, cursor);
        int lineStart = starts[cursorLine];
        int column = clamp(cursor - lineStart, 0, lines.get(cursorLine).length());
        double cursorX = Size.measureText(this, lines.get(cursorLine).substring(0, column));
        double cursorY = cursorLine * lineHeight;

        Size visibleSize = Box.of(this).innerSize();
        double visibleWidth = Math.max(0, visibleSize.width());
        double visibleHeight = Math.max(0, visibleSize.height());

        scrollWidth = wrapped.width();
        scrollHeight = wrapped.height(lineHeight);
        double desiredScrollLeft = scrollLeft;
        if (cursorX < desiredScrollLeft) desiredScrollLeft = cursorX;
        else if (cursorX > desiredScrollLeft + visibleWidth) desiredScrollLeft = cursorX - visibleWidth + 2;
        setTextScrollLeftImmediate(desiredScrollLeft);

        double desiredScrollTop = scrollTop;
        if (cursorY < desiredScrollTop) desiredScrollTop = cursorY;
        else if (cursorY + lineHeight > desiredScrollTop + visibleHeight) {
            desiredScrollTop = cursorY + lineHeight - visibleHeight + 2;
        }
        setTextScrollTopImmediate(desiredScrollTop);
        addDirtyFlags(Drawer.REPAINT);
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        if (phase == Base.RenderPhase.SHADOW) rectRenderer.drawShadow(poseStack);
        if (phase == Base.RenderPhase.BORDER) {
            rectRenderer.drawBorder(poseStack);
            drawResizeHandle(poseStack, rectRenderer);
        }
        if (phase != Base.RenderPhase.BODY) return;

        rectRenderer.drawBody(poseStack);

        String renderText = getRenderText();
        boolean isPlaceholder = renderText.isEmpty() && !placeholder.isEmpty();

        Text text = Text.of(this);
        double lineHeight = text.lineHeight;
        Position contentPos = rectRenderer.getContentPosition();
        double currentScrollLeft = scrollLeft;
        double currentScrollTop = getScrollTop();
        float baseX = (float) (contentPos.x - currentScrollLeft);
        float baseY = (float) (contentPos.y - currentScrollTop);

        if (isPlaceholder) {
            text.content = placeholder;
            text.color = new Color("#888888");
            float placeholderX = (float) (baseX + textAlignX(text, placeholder, 0));
            FontDrawer.drawFont(poseStack, text, new Position(placeholderX, baseY));
            if (Element.isElementFocusing(this)) {
                Graph.drawCursor(poseStack.last().pose(), placeholderX, baseY, (float) lineHeight, Text.getFontColor(this), lastBlinkTime);
            }
            return;
        }

        text.content = renderText;
        Text.WrappedText wrapped = Text.wrap(text, Box.of(this).innerSize().width());
        List<String> lines = wrapped.lines();
        int[] starts = wrapped.starts();

        drawSelection(poseStack, text, lines, starts, baseX, baseY, lineHeight);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float y = (float) (baseY + i * lineHeight);
            float lineX = (float) (baseX + textAlignX(text, line, i));
            if (!canSelectText() || !hasSelection()) {
                text.content = line;
                text.color = new Color(Text.getFontColor(this));
                FontDrawer.drawFont(poseStack, text, new Position(lineX, y));
                continue;
            }

            int lineStart = starts[i];
            int lineEnd = lineStart + line.length();
            int min = Math.max(selMin(), lineStart);
            int max = Math.min(selMax(), lineEnd);
            if (min >= max) {
                text.content = line;
                text.color = new Color(Text.getFontColor(this));
                FontDrawer.drawFont(poseStack, text, new Position(lineX, y));
                continue;
            }

            String before = line.substring(0, min - lineStart);
            String selected = line.substring(min - lineStart, max - lineStart);
            String after = line.substring(max - lineStart);

            float segmentX = lineX;
            if (!before.isEmpty()) {
                text.content = before;
                text.color = new Color(Text.getFontColor(this));
                FontDrawer.drawFont(poseStack, text, new Position(segmentX, y));
                segmentX += (float) Size.measureText(this, before);
            }
            if (!selected.isEmpty()) {
                text.content = selected;
                text.color = new Color("#FFFFFF");
                FontDrawer.drawFont(poseStack, text, new Position(segmentX, y));
                segmentX += (float) Size.measureText(this, selected);
            }
            if (!after.isEmpty()) {
                text.content = after;
                text.color = new Color(Text.getFontColor(this));
                FontDrawer.drawFont(poseStack, text, new Position(segmentX, y));
            }
        }

        if (!Element.isElementFocusing(this)) return;
        int cursorLine = resolveCursorLine(lines, starts, cursor);
        int lineStart = starts[cursorLine];
        int column = clamp(cursor - lineStart, 0, lines.get(cursorLine).length());
        double cursorOffset = Size.measureText(this, lines.get(cursorLine).substring(0, column));
        float cursorX = (float) (baseX + textAlignX(text, lines.get(cursorLine), cursorLine) + cursorOffset);
        float cursorY = (float) (baseY + cursorLine * lineHeight);
        Graph.drawCursor(poseStack.last().pose(), cursorX, cursorY, (float) lineHeight, Text.getFontColor(this), lastBlinkTime);
    }

    public boolean isResizeHandleAt(Position documentPosition) {
        if (documentPosition == null || !canResize()) return false;
        Box box = Box.of(this);
        Position position = Position.of(this);
        double localX = documentPosition.x - position.x - box.getMarginLeft();
        double localY = documentPosition.y - position.y - box.getMarginTop();
        return isResizeHandleOffset(localX, localY);
    }

    public String getResizeCursor() {
        String resize = normalizedResize();
        if ("vertical".equals(resize) || "block".equals(resize)) return "ns-resize";
        if ("horizontal".equals(resize) || "inline".equals(resize)) return "ew-resize";
        return "se-resize";
    }

    private void beginResize(Event event) {
        if (!(event instanceof MouseEvent mouseEvent) || !isResizeHandleOffset(mouseEvent.offsetX, mouseEvent.offsetY)) return;
        Box box = Box.of(this);
        resizing = true;
        resizeStartX = mouseEvent.clientX;
        resizeStartY = mouseEvent.clientY;
        resizeStartWidth = box.elementSize().width();
        resizeStartHeight = box.elementSize().height();
        clearSelection();
        event.preventDefault();
    }

    private void continueResize(Event event) {
        if (!resizing || !(event instanceof MouseEvent mouseEvent)) return;
        String resize = normalizedResize();
        Box box = Box.of(this);
        boolean borderBox = box.isBorderBox();
        if ("both".equals(resize) || "horizontal".equals(resize) || "inline".equals(resize)) {
            double width = Math.max(16, resizeStartWidth + mouseEvent.clientX - resizeStartX);
            if (!borderBox) width -= box.getBorderHorizontal() + box.getPaddingHorizontal();
            setInlineStyleProperty("width", px(Math.max(0, width)));
        }
        if ("both".equals(resize) || "vertical".equals(resize) || "block".equals(resize)) {
            double height = Math.max(16, resizeStartHeight + mouseEvent.clientY - resizeStartY);
            if (!borderBox) height -= box.getBorderVertical() + box.getPaddingVertical();
            setInlineStyleProperty("height", px(Math.max(0, height)));
        }
        event.preventDefault();
    }

    private void drawResizeHandle(PoseStack poseStack, Rect rectRenderer) {
        if (!canResize()) return;
        Box box = rectRenderer.box;
        float right = (float) (rectRenderer.position.x + box.getMarginLeft() + box.elementSize().width() - 3);
        float bottom = (float) (rectRenderer.position.y + box.getMarginTop() + box.elementSize().height() - 3);
        int color = new Color(isDisabled() ? "#777777" : "#A9A9A9").getValue();
        for (int i = 0; i < 3; i++) {
            float length = 3 + i * 3;
            for (int step = 0; step < length; step += 2) {
                float x = right - step;
                // Each stroke terminates on the same bottom/right corner. The
                // previous extra per-stroke offset shifted the longer strokes
                // upward, producing a detached triangular mark instead of the
                // browser's three parallel diagonal grip lines.
                float y = bottom - (length - step - 1);
                Graph.drawFillRect(poseStack.last().pose(), x, y, x + 1, y + 1, color);
            }
        }
    }

    private boolean isResizeHandleOffset(double offsetX, double offsetY) {
        if (!canResize()) return false;
        Size size = Box.of(this).elementSize();
        return offsetX >= size.width() - 14 && offsetY >= size.height() - 14;
    }

    private boolean canResize() {
        return !isDisabled() && !"none".equals(normalizedResize());
    }

    private String normalizedResize() {
        String resize = getComputedStyle().resize;
        if (resize == null) return "none";
        resize = resize.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (resize) {
            case "both", "horizontal", "vertical", "block", "inline" -> resize;
            default -> "none";
        };
    }

    private static String px(double value) {
        return String.format(java.util.Locale.ROOT, "%.2fpx", value);
    }

    private void drawSelection(PoseStack poseStack, Text text, List<String> lines, int[] starts, float baseX, float baseY, double lineHeight) {
        if (!canSelectText()) return;
        if (!hasSelection()) return;

        int min = selMin();
        int max = selMax();
        if (min == max) return;

        for (int i = 0; i < lines.size(); i++) {
            String lineText = lines.get(i);
            int lineStart = starts[i];
            int lineEnd = lineStart + lineText.length();

            int drawStart = Math.max(min, lineStart);
            int drawEnd = Math.min(max, lineEnd);
            if (drawStart >= drawEnd) continue;

            double alignX = textAlignX(text, lineText, i);
            double startX = alignX + Size.measureText(this, lineText.substring(0, drawStart - lineStart));
            double endX = alignX + Size.measureText(this, lineText.substring(0, drawEnd - lineStart));
            float x0 = (float) (baseX + startX);
            float x1 = (float) (baseX + endX);
            float y0 = (float) (baseY + i * lineHeight);
            float y1 = (float) (y0 + lineHeight);
            Graph.drawFillRect(poseStack.last().pose(), x0, y0, x1, y1, Text.getSelectionColor(this));
        }
    }

    private int resolveCursorLine(List<String> lines, int[] starts, int cursorIndex) {
        int line = 0;
        while (line < lines.size() - 1 && cursorIndex > starts[line] + lines.get(line).length()) {
            line++;
        }
        return line;
    }
}

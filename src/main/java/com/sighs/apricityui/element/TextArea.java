package com.sighs.apricityui.element;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.*;
import com.sighs.apricityui.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@ElementRegister(TextArea.TAG_NAME)
public class TextArea extends AbstractText {
    public static final String TAG_NAME = "TEXTAREA";

    public TextArea(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    protected boolean supportsMultilineInput() {
        return true;
    }

    @Override
    protected void onInitFromDom(Element origin) {
        super.onInitFromDom(origin);

        if (!hasAttribute("value")) {
            String inlineText = origin == null ? "" : origin.innerText;
            if (inlineText == null) inlineText = "";
            value = inlineText.replace("\r\n", "\n").replace('\r', '\n');
            cursor = Math.min(cursor, value.length());
            selectionAnchor = cursor;
            clearSelection();
            clampScroll();
            getRenderer().text.clear();
        }
    }

    @Override
    protected void locateCursor(double mouseOffsetX, double mouseOffsetY) {
        String renderText = getRenderText();
        WrapResult wrapped = wrapLines(renderText);
        List<String> lines = wrapped.lines;
        int[] starts = wrapped.starts;

        Box box = Box.of(this);
        double contentStartX = box.getBorderLeft() + box.getPaddingLeft();
        double contentStartY = box.getBorderTop() + box.getPaddingTop();
        Text text = Text.of(this);
        double lineHeight = text.lineHeight;
        if (lineHeight <= 0) lineHeight = Size.DEFAULT_LINE_HEIGHT;

        double relativeY = mouseOffsetY - contentStartY + getScrollTop();
        int line = clamp((int) Math.floor(relativeY / lineHeight), 0, Math.max(0, lines.size() - 1));

        String lineText = lines.get(line);
        double relativeX = mouseOffsetX - contentStartX + scrollLeft;
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

    @Override
    protected void clampScroll() {
        String renderText = getRenderText();
        WrapResult wrapped = wrapLines(renderText);
        List<String> lines = wrapped.lines;
        int[] starts = wrapped.starts;

        cursor = clamp(cursor, 0, renderText.length());

        Text text = Text.of(this);
        double lineHeight = text.lineHeight;

        int cursorLine = resolveCursorLine(lines, starts, cursor);
        int lineStart = starts[cursorLine];
        int column = clamp(cursor - lineStart, 0, lines.get(cursorLine).length());
        double cursorX = Size.measureText(this, lines.get(cursorLine).substring(0, column));
        double cursorY = cursorLine * lineHeight;

        Size visibleSize = Box.of(this).innerSize();
        double visibleWidth = Math.max(0, visibleSize.width());
        double visibleHeight = Math.max(0, visibleSize.height());

        if (cursorX < scrollLeft) setScrollLeft(cursorX);
        else if (cursorX > scrollLeft + visibleWidth) setScrollLeft(cursorX - visibleWidth + 2);

        if (cursorY < scrollTop) setScrollTop(cursorY);
        else if (cursorY + lineHeight > scrollTop + visibleHeight) {
            setScrollTop(cursorY + lineHeight - visibleHeight + 2);
        }

        double maxLineWidth = 0;
        for (String lineText : lines) {
            maxLineWidth = Math.max(maxLineWidth, Size.measureText(this, lineText));
        }
        scrollWidth = maxLineWidth;
        scrollHeight = Math.max(lineHeight, lines.size() * lineHeight);
        addDirtyFlags(Drawer.REPAINT);
    }

    @Override
    public void drawPhase(MatrixStack stack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        if (phase == Base.RenderPhase.SHADOW) rectRenderer.drawShadow(stack);
        if (phase == Base.RenderPhase.BORDER) rectRenderer.drawBorder(stack);
        if (phase != Base.RenderPhase.BODY) return;

        rectRenderer.drawBody(stack);

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
            FontDrawer.drawFont(stack, text, new Position(baseX, baseY));
            if (Element.isElementFocusing(this)) {
                Graph.drawCursor(stack.last().pose(), baseX, baseY, (float) lineHeight, Style.getFontColor(this), lastBlinkTime);
            }
            return;
        }

        WrapResult wrapped = wrapLines(renderText);
        List<String> lines = wrapped.lines;
        int[] starts = wrapped.starts;

        drawSelection(stack, lines, starts, baseX, baseY, lineHeight);

        text.color = new Color(Style.getFontColor(this));
        for (int i = 0; i < lines.size(); i++) {
            text.content = lines.get(i);
            float y = (float) (baseY + i * lineHeight);
            FontDrawer.drawFont(stack, text, new Position(baseX, y));
        }

        if (!Element.isElementFocusing(this)) return;
        int cursorLine = resolveCursorLine(lines, starts, cursor);
        int lineStart = starts[cursorLine];
        int column = clamp(cursor - lineStart, 0, lines.get(cursorLine).length());
        double cursorOffset = Size.measureText(this, lines.get(cursorLine).substring(0, column));
        float cursorX = (float) (baseX + cursorOffset);
        float cursorY = (float) (baseY + cursorLine * lineHeight);
        Graph.drawCursor(stack.last().pose(), cursorX, cursorY, (float) lineHeight, Style.getFontColor(this), lastBlinkTime);
    }

    private void drawSelection(MatrixStack stack, List<String> lines, int[] starts, float baseX, float baseY, double lineHeight) {
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

            double startX = Size.measureText(this, lineText.substring(0, drawStart - lineStart));
            double endX = Size.measureText(this, lineText.substring(0, drawEnd - lineStart));
            float x0 = (float) (baseX + startX);
            float x1 = (float) (baseX + endX);
            float y0 = (float) (baseY + i * lineHeight);
            float y1 = (float) (y0 + lineHeight);
            Graph.drawFillRect(stack.last().pose(), x0, y0, x1, y1, Style.getSelectionColor(this));
        }
    }

    private int resolveCursorLine(List<String> lines, int[] starts, int cursorIndex) {
        int line = 0;
        while (line < lines.size() - 1 && cursorIndex > starts[line] + lines.get(line).length()) {
            line++;
        }
        return line;
    }

    private WrapResult wrapLines(String renderText) {
        double wrapWidth = Box.of(this).innerSize().width();
        String whiteSpace = getComputedStyle().whiteSpace;
        if (StringUtils.isNotNullOrEmptyEx(whiteSpace) && "nowrap".equalsIgnoreCase(whiteSpace.trim())) {
            wrapWidth = Double.MAX_VALUE;
        }

        if (wrapWidth <= 2) {
            List<String> hard = splitLines(renderText);
            return new WrapResult(hard, buildLineStarts(hard));
        }

        List<String> lines = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();

        int globalIndex = 0;
        StringBuilder current = new StringBuilder();
        double currentWidth = 0;

        starts.add(0);

        for (int i = 0; i < renderText.length(); i++) {
            char c = renderText.charAt(i);

            // 硬换行
            if (c == '\n') {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0;
                globalIndex++;
                starts.add(globalIndex);
                continue;
            }

            String charStr = String.valueOf(c);
            double charWidth = Size.measureText(this, charStr);

            // 软换行
            if (current.length() > 0 && currentWidth + charWidth > wrapWidth) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0;
                starts.add(globalIndex);
            }

            current.append(c);
            currentWidth += charWidth;
            globalIndex++;
        }

        lines.add(current.toString());

        int[] startArr = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) startArr[i] = starts.get(i);
        return new WrapResult(lines, startArr);
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static class WrapResult {
        private List<String> lines;
        private int[] starts;
    }
}

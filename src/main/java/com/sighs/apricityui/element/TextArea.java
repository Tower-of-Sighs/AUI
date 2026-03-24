package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
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

        if (cursorX < scrollLeft) setScrollLeft(cursorX);
        else if (cursorX > scrollLeft + visibleWidth) setScrollLeft(cursorX - visibleWidth + 2);

        if (cursorY < scrollTop) setScrollTop(cursorY);
        else if (cursorY + lineHeight > scrollTop + visibleHeight) {
            setScrollTop(cursorY + lineHeight - visibleHeight + 2);
        }

        scrollWidth = wrapped.width();
        scrollHeight = wrapped.height(lineHeight);
        addDirtyFlags(Drawer.REPAINT);
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        if (phase == Base.RenderPhase.SHADOW) rectRenderer.drawShadow(poseStack);
        if (phase == Base.RenderPhase.BORDER) rectRenderer.drawBorder(poseStack);
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
            FontDrawer.drawFont(poseStack, text, new Position(baseX, baseY));
            if (Element.isElementFocusing(this)) {
                Graph.drawCursor(poseStack.last().pose(), baseX, baseY, (float) lineHeight, Style.getFontColor(this), lastBlinkTime);
            }
            return;
        }

        text.content = renderText;
        Text.WrappedText wrapped = Text.wrap(text, Box.of(this).innerSize().width());
        List<String> lines = wrapped.lines();
        int[] starts = wrapped.starts();

        drawSelection(poseStack, lines, starts, baseX, baseY, lineHeight);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float y = (float) (baseY + i * lineHeight);
            if (!canSelectText() || !hasSelection()) {
                text.content = line;
                text.color = new Color(Style.getFontColor(this));
                FontDrawer.drawFont(poseStack, text, new Position(baseX, y));
                continue;
            }

            int lineStart = starts[i];
            int lineEnd = lineStart + line.length();
            int min = Math.max(selMin(), lineStart);
            int max = Math.min(selMax(), lineEnd);
            if (min >= max) {
                text.content = line;
                text.color = new Color(Style.getFontColor(this));
                FontDrawer.drawFont(poseStack, text, new Position(baseX, y));
                continue;
            }

            String before = line.substring(0, min - lineStart);
            String selected = line.substring(min - lineStart, max - lineStart);
            String after = line.substring(max - lineStart);

            float segmentX = baseX;
            if (!before.isEmpty()) {
                text.content = before;
                text.color = new Color(Style.getFontColor(this));
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
                text.color = new Color(Style.getFontColor(this));
                FontDrawer.drawFont(poseStack, text, new Position(segmentX, y));
            }
        }

        if (!Element.isElementFocusing(this)) return;
        int cursorLine = resolveCursorLine(lines, starts, cursor);
        int lineStart = starts[cursorLine];
        int column = clamp(cursor - lineStart, 0, lines.get(cursorLine).length());
        double cursorOffset = Size.measureText(this, lines.get(cursorLine).substring(0, column));
        float cursorX = (float) (baseX + cursorOffset);
        float cursorY = (float) (baseY + cursorLine * lineHeight);
        Graph.drawCursor(poseStack.last().pose(), cursorX, cursorY, (float) lineHeight, Style.getFontColor(this), lastBlinkTime);
    }

    private void drawSelection(PoseStack poseStack, List<String> lines, int[] starts, float baseX, float baseY, double lineHeight) {
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

            double startX = Size.measureText(this, lineText.substring(0, drawStart - lineStart));
            double endX = Size.measureText(this, lineText.substring(0, drawEnd - lineStart));
            float x0 = (float) (baseX + startX);
            float x1 = (float) (baseX + endX);
            float y0 = (float) (baseY + i * lineHeight);
            float y1 = (float) (y0 + lineHeight);
            Graph.drawFillRect(poseStack.last().pose(), x0, y0, x1, y1, Style.getSelectionColor(this));
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

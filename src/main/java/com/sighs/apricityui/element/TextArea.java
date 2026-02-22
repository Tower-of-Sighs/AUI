package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.*;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Color;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.Text;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class TextArea extends AbstractTextElement {
    public static final String TAG_NAME = "TEXTAREA";

    static {
        Element.register(TAG_NAME, (document, string) -> new TextArea(document));
    }

    public TextArea(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    protected boolean supportsMultilineInput() {
        return true;
    }

    @Override
    protected void locateCursor(double mouseOffsetX, double mouseOffsetY) {
        String renderText = getRenderText();
        List<String> lines = splitLines(renderText);
        int[] starts = buildLineStarts(lines);

        Rect rect = Rect.of(this);
        Position contentPos = rect.getContentPosition();
        Text text = Text.of(this);
        double lineHeight = text.lineHeight;

        double relativeY = mouseOffsetY - contentPos.y + scrollTop;
        int line = clamp((int) Math.floor(relativeY / lineHeight), 0, lines.size() - 1);

        String lineText = lines.get(line);
        double relativeX = mouseOffsetX - contentPos.x + scrollLeft;
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
        List<String> lines = splitLines(renderText);
        int[] starts = buildLineStarts(lines);

        cursor = clamp(cursor, 0, renderText.length());

        Text text = Text.of(this);
        double lineHeight = text.lineHeight;

        int cursorLine = resolveCursorLine(lines, starts, cursor);
        int lineStart = starts[cursorLine];
        int column = clamp(cursor - lineStart, 0, lines.get(cursorLine).length());
        double cursorX = Size.measureText(this, lines.get(cursorLine).substring(0, column));
        double cursorY = cursorLine * lineHeight;

        Size contentSize = Size.getContentSize(this);
        double visibleWidth = contentSize.width();
        double visibleHeight = contentSize.height();

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
        float baseX = (float) (contentPos.x - scrollLeft);
        float baseY = (float) (contentPos.y - getScrollTop());

        if (isPlaceholder) {
            text.content = placeholder;
            text.color = new Color("#888888");
            FontDrawer.drawFont(poseStack, text, new Position(baseX, baseY));
            if (Element.isElementFocusing(this)) {
                Graph.drawCursor(poseStack.last().pose(), baseX, baseY, (float) lineHeight, Style.getFontColor(this), lastBlinkTime);
            }
            return;
        }

        List<String> lines = splitLines(renderText);
        int[] starts = buildLineStarts(lines);

        drawSelection(poseStack, lines, starts, baseX, baseY, lineHeight);

        text.color = new Color(Style.getFontColor(this));
        for (int i = 0; i < lines.size(); i++) {
            text.content = lines.get(i);
            float y = (float) (baseY + i * lineHeight);
            FontDrawer.drawFont(poseStack, text, new Position(baseX, y));
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

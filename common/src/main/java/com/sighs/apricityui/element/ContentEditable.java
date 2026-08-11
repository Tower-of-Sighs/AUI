package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.util.TextMetrics;

import java.util.List;
import java.util.Objects;

/**
 * HTML {@code contenteditable} 属性的元素级实现。
 * <p>
 * 任何标签在 HTML 中声明 {@code contenteditable} 后，解析器会经
 * {@link Element#init(Element)} 把通用元素替换为本类（保留原 tagName，
 * 从而维持 {@code div} 等类型选择器与样式的语义）。编辑行为复用
 * {@link AbstractText} 的多行文本内核：点击定位光标、拖拽/Shift 选区、
 * 键盘编辑、剪贴板、撤销、IME、beforeinput/input/change 事件。
 * <p>
 * 语义为纯文本编辑（等价 {@code contenteditable="plaintext-only"}）：
 * 内容扁平化为一个字符串存于 {@code value}，初始化时子节点被吸收进
 * {@code innerText}（驱动布局与 JS 读取），不再保留可编辑区内的嵌套元素。
 * {@code contenteditable="false"} 时退化为纯展示，不聚焦、不可编辑、
 * 不可选择。
 */
public class ContentEditable extends AbstractText {
    private boolean contentEditableEnabled;

    public ContentEditable(Document document, String tagName) {
        super(document, tagName);
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
        }
        if (!hasAttribute("maxlength")) {
            // 浏览器 contenteditable 没有默认长度上限；无显式 maxlength 时不限制。
            maxLength = Integer.MAX_VALUE;
        }

        contentEditableEnabled = resolveContentEditableEnabled();

        // 扁平化：内容已并入 value，清掉子节点，防止子元素拥有独立的渲染节点
        // 与绘制叠加；innerText 与 value 对齐，驱动布局尺寸与 JS 文本读取。
        if (!childNodes.isEmpty()) {
            childNodes.clear();
            if (children == null) {
                children = new java.util.ArrayList<>();
            } else {
                children.clear();
            }
        }
        innerText = value == null ? "" : value;
        cursor = 0;
        selectionAnchor = cursor;
        clearSelection();
        getRenderer().text.clear();
    }

    @Override
    public void setAttribute(String name, String value) {
        super.setAttribute(name, value);
        syncContentEditableState(name);
    }

    @Override
    public void removeAttribute(String name) {
        super.removeAttribute(name);
        syncContentEditableState(name);
    }

    private void syncContentEditableState(String name) {
        if (!"contenteditable".equalsIgnoreCase(name)) return;
        contentEditableEnabled = resolveContentEditableEnabled();
        if (!contentEditableEnabled) {
            clearSelection();
        }
        getRenderer().text.clear();
        addDirtyFlags(Drawer.REPAINT);
    }

    private boolean resolveContentEditableEnabled() {
        if (!hasAttribute("contenteditable")) return false;
        String raw = getAttribute("contenteditable");
        return raw == null || !"false".equalsIgnoreCase(raw.trim());
    }

    /** 是否声明为可编辑（对应浏览器 isContentEditable 语义）。 */
    public boolean isContentEditable() {
        return contentEditableEnabled;
    }

    @Override
    public boolean canEditText() {
        return contentEditableEnabled;
    }

    @Override
    public boolean canSelectText() {
        return contentEditableEnabled && super.canSelectText();
    }

    @Override
    public boolean canFocus() {
        return contentEditableEnabled;
    }

    @Override
    public void tick() {
        super.tick();
        if (value != null && !Objects.equals(innerText, value)) {
            innerText = value;
        }
    }

    @Override
    public void setTextContent(String value) {
        super.setTextContent(value);
        String current = getTextContent();
        String normalized = current == null ? "" : current.replace("\r\n", "\n").replace('\r', '\n');
        this.value = normalized;
        cursor = Math.min(cursor, normalized.length());
        selectionAnchor = cursor;
        clearSelection();
        undoStack.clear();
        getRenderer().text.clear();
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
     * 渲染与光标映射共用同一套偏移。实现与 TextArea 保持一致。
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
        if (phase == Base.RenderPhase.BORDER) rectRenderer.drawBorder(poseStack);
        if (phase != Base.RenderPhase.BODY) return;

        rectRenderer.drawBody(poseStack);

        String renderText = getRenderText();
        if (renderText.isEmpty()) {
            if (canEditText() && Element.isElementFocusing(this)) {
                Text text = Text.of(this);
                Position contentPos = rectRenderer.getContentPosition();
                Graph.drawCursor(poseStack.last().pose(), (float) contentPos.x, (float) contentPos.y,
                        (float) Math.max(text.lineHeight, Size.DEFAULT_LINE_HEIGHT),
                        Text.getFontColor(this), lastBlinkTime);
            }
            return;
        }

        Text text = Text.of(this);
        double lineHeight = text.lineHeight;
        Position contentPos = rectRenderer.getContentPosition();
        double currentScrollLeft = scrollLeft;
        double currentScrollTop = getScrollTop();
        float baseX = (float) (contentPos.x - currentScrollLeft);
        float baseY = (float) (contentPos.y - currentScrollTop);

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

        if (!canEditText() || !Element.isElementFocusing(this)) return;
        int cursorLine = resolveCursorLine(lines, starts, cursor);
        int lineStart = starts[cursorLine];
        int column = clamp(cursor - lineStart, 0, lines.get(cursorLine).length());
        double cursorOffset = Size.measureText(this, lines.get(cursorLine).substring(0, column));
        float cursorX = (float) (baseX + textAlignX(text, lines.get(cursorLine), cursorLine) + cursorOffset);
        float cursorY = (float) (baseY + cursorLine * lineHeight);
        Graph.drawCursor(poseStack.last().pose(), cursorX, cursorY, (float) lineHeight, Text.getFontColor(this), lastBlinkTime);
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

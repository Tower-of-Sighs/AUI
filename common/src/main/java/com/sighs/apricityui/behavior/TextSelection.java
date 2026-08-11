package com.sighs.apricityui.behavior;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Flex;
import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.layout.NormalFlow;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.util.TextMetrics;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;

import java.util.List;

/**
 * 文档级文字选择在单个元素上的视图：负责鼠标事件、命中测试与绘制，
 * 但不再持有选区状态 —— 选区统一存放在 {@link DocumentSelection} 中。
 * <p>
 * 输入控件（AbstractText）保留自己的选区，不经过这里。
 */
public final class TextSelection {
    private final Element owner;

    public TextSelection(Element owner) {
        this.owner = owner;
    }

    public void addEventListeners() {
        owner.addInternalEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouseEvent)) return;
            // 只有命中元素（路径最深处）处理，避免祖先元素重复执行同一套规则
            if (event.target != owner) return;
            handleMouseDown(mouseEvent);
        });

        owner.addInternalEventListener("mousemove", event -> {
            if (!(event instanceof MouseEvent mouseEvent)) return;
            if (event.target != owner) return;
            handleMouseMove(mouseEvent);
        });

        owner.addInternalEventListener("mouseup", event -> {
            if (owner.document == null) return;
            handleMouseUp(event);
        });
    }

    // ------------------------------------------------------------------
    // 鼠标事件：命中 → （单元, 偏移），再操作文档级选择
    // ------------------------------------------------------------------

    private void handleMouseDown(MouseEvent event) {
        if (isInsideRichTextEditor(owner)) return;
        Document document = owner.document;
        if (document == null) return;
        // 中键不参与文档级选择：Linux 主选区语义下由输入控件负责中键粘贴，这里保持 no-op
        if (event.button == 2) return;
        // 任何新的按下都取消尚未完成的文本拖拽（拖拽过程中按下其他按钮/元素时）
        document.endTextDrag();
        SelectionUnits.UnitOffset hit = resolveUnitOffset(owner, event.clientX, event.clientY);
        DocumentSelection selection = document.getDocumentSelection();
        if (hit == null) {
            // 点击不可选区域（含输入控件内部）：清空文档选择
            document.clearDocumentSelection();
            return;
        }

        if (Interaction.isUserSelectAll(owner)) {
            // user-select:all：直接选中整个单元（不进入拖拽状态）
            Element unit = hit.unit();
            selection.selectUnit(unit);
            selection.setSelecting(false);
            document.setFocusedElement(unit);
            return;
        }

        int clickCount = event.clickCount;
        if (clickCount >= 3) {
            // 三击：选择整个段落（单元）
            Element unit = hit.unit();
            selection.selectUnit(unit);
            selection.setSelecting(true);
            document.setFocusedElement(unit);
            return;
        }
        if (clickCount == 2) {
            // 双击：按空白分词选择单词，拖拽从词边界继续扩展
            Element unit = hit.unit();
            String text = SelectionUnits.flattenedSelectableText(unit);
            int[] word = wordRange(text, hit.offset());
            if (word != null) {
                selection.collapse(unit, word[0]);
                selection.extendTo(unit, word[1]);
            } else {
                selection.collapse(unit, hit.offset());
            }
            selection.setSelecting(true);
            document.setFocusedElement(unit);
            return;
        }

        if (event.shiftKey && selection.hasAnchor()) {
            // shift+点击：从既有锚点扩展
            selection.extendTo(hit.unit(), hit.offset());
        } else if (clickCount == 1 && isInsideSelection(selection, hit)) {
            // 单击落在选区内部：保留选区（不折叠/不扩展），登记为潜在的文本拖拽，
            // 拖拽文本快照取当前文档选区；移出阈值后进入真正的拖拽（COPY 语义）。
            document.beginTextDrag(document.getDocumentSelectedText(), event.clientX, event.clientY);
        } else {
            selection.collapse(hit.unit(), hit.offset());
        }
        selection.setSelecting(true);
        document.setFocusedElement(hit.unit());
    }

    /** 命中偏移是否落在该单元当前的文档选区范围内（含端点），单击保留选区用。 */
    private static boolean isInsideSelection(DocumentSelection selection, SelectionUnits.UnitOffset hit) {
        int[] range = selection.localRangeForUnit(hit.unit());
        if (range == null) return false;
        return hit.offset() >= range[0] && hit.offset() <= range[1];
    }

    private void handleMouseMove(MouseEvent event) {
        Document document = owner.document;
        if (isInsideRichTextEditor(owner)) return;
        if (document == null) return;
        // 重派发到按下元素的事件（指针已移到其他元素上）不参与扩展：悬停元素自身的
        // 监听器会按指针位置扩展，按下元素的重复处理会用其自身几何算出错误偏移并覆盖
        // 正确的跨元素扩展终点（块级边界拖拽失效的根因）。
        if (event.activeElementRedirect) return;
        DocumentSelection selection = document.getDocumentSelection();
        if (!selection.isSelecting()) return;
        // 文本拖拽候选：先推进拖拽判定，越过阈值后选区冻结，不再扩展
        if (document.isTextDragPending()) {
            document.updateTextDrag(event.clientX, event.clientY);
            if (document.isTextDragging()) return;
        }
        // 事件始终派发到指针当前悬停的元素，无需再依赖 pressedElement 冻结
        SelectionUnits.UnitOffset hit = resolveUnitOffset(owner, event.clientX, event.clientY);
        if (hit == null) {
            // 指针悬停在不可选内容上：保持终点不动（自然冻结）
            return;
        }
        selection.extendTo(hit.unit(), hit.offset());
    }

    /**
     * mouseup：若文本拖拽结束时指针落在可编辑输入控件上，把拖拽文本复制进去
     * （COPY 语义，源选区保持不动）；否则仅取消拖拽。每次 mouseup 都清理拖拽状态。
     */
    private void handleMouseUp(Event rawEvent) {
        Document document = owner.document;
        if (rawEvent instanceof MouseEvent mouseEvent) {
            // 重派发到按下元素的事件不处理落下/清理：悬停元素自身的 mouseup 已完成
            if (mouseEvent.activeElementRedirect) return;
        }
        // 只有最深命中元素（target == owner）执行落下，避免路由上的祖先重复处理
        if (document.isTextDragging() && rawEvent.target == owner
                && rawEvent.target instanceof AbstractText input && input.canEditText()) {
            String draggedText = document.getDraggedText();
            if (draggedText != null && !draggedText.isEmpty()) {
                input.insertText(draggedText);
            }
        }
        document.endTextDrag();
        document.getDocumentSelection().setSelecting(false);
    }

    // ------------------------------------------------------------------
    // 命中测试：指针 → （单元, 偏移）
    // ------------------------------------------------------------------

    public static SelectionUnits.UnitOffset resolveUnitOffset(Element hit, double x, double y) {
        if (hit == null) return null;
        Element unit = SelectionUnits.resolveUnit(hit);
        if (unit == null) return null;
        return new SelectionUnits.UnitOffset(unit, locateOffsetInUnit(unit, x, y));
    }

    private static int locateOffsetInUnit(Element unit, double x, double y) {
        if (unit == null) return 0;
        String display = unit.getComputedStyle().display;
        if (Layout.isFlexDisplay(display)) {
            return locateFlexDirect(unit, x, y);
        }
        if (Layout.isGridDisplay(display)) {
            return locateLeaf(unit, x, y);
        }
        if (SelectionUnits.paintsTextViaRuns(unit)) {
            return locateRuns(unit, x, y);
        }
        return locateLeaf(unit, x, y);
    }

    /** 普通流 run 行的命中：先查原子对象盒子，再逐 run 逐行找包含指针的行，按字符宽度定位。 */
    private static int locateRuns(Element unit, double x, double y) {
        // 阶段零：原子对象（img/hr）盒子命中 → 对象哨兵偏移
        int objectHit = locateObjectHit(unit, x, y);
        if (objectHit >= 0) return objectHit;

        List<NormalFlow.TextRunLayout> runs = NormalFlow.computeTextRuns(unit);
        if (runs.isEmpty()) return 0;
        Rect rect = Rect.of(unit);
        Position contentPos = rect.getContentPosition();
        boolean alignDirect = shouldAlignDirectTextRuns(unit);
        double contentWidth = alignDirect ? Box.of(unit).innerSize().width() : 0;
        String flattened = SelectionUnits.flattenedSelectableText(unit);

        // 阶段一：按 Y 距离选出最近的文本行。同一视觉行的多个 run 因基线对齐可能
        // 有不同 y，但距离都落在同一行内；严格小于才更新，避免“距离相等时最后一个
        // run 恒胜”导致偏移恒落到行尾（拖拽无效果、双击选到最后一个词）。
        int bestRunIndex = -1;
        int bestLineIndex = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int r = 0; r < runs.size(); r++) {
            NormalFlow.TextRunLayout run = runs.get(r);
            if (run == null || run.text() == null || run.lines() == null) continue;
            double lineHeight = run.text().lineHeight;
            for (int i = 0; i < run.lines().size(); i++) {
                String line = run.lines().get(i);
                if (line == null || line.isEmpty()) continue;
                double lineY0 = contentPos.y + run.y() + i * lineHeight;
                double distance = y < lineY0 ? lineY0 - y
                        : (y > lineY0 + lineHeight ? y - (lineY0 + lineHeight) : 0);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestRunIndex = r;
                    bestLineIndex = i;
                }
            }
        }
        if (bestRunIndex < 0) return 0;

        // 阶段二：在选中的视觉行内按 X 定位。收集与该行纵向跨度重叠的所有 run 行
        // （垂直重叠判定兼容混合字号与基线偏移），找出包含指针 X 的那个映射字符偏移。
        NormalFlow.TextRunLayout chosenRun = runs.get(bestRunIndex);
        double chosenLineY0 = contentPos.y + chosenRun.y() + bestLineIndex * chosenRun.text().lineHeight;
        double chosenLineY1 = chosenLineY0 + chosenRun.text().lineHeight;
        int bestOffset = 0;
        double bestXDistance = Double.MAX_VALUE;
        for (int r = 0; r < runs.size(); r++) {
            NormalFlow.TextRunLayout run = runs.get(r);
            if (run == null || run.text() == null || run.lines() == null) continue;
            Text runText = run.text();
            double lineHeight = runText.lineHeight;
            int runBase = SelectionUnits.baseOffsetOfDescendant(unit, run.node() != null ? run.node() : run.owner());
            for (int i = 0; i < run.lines().size(); i++) {
                String line = run.lines().get(i);
                if (line == null || line.isEmpty()) continue;
                double lineY0 = contentPos.y + run.y() + i * lineHeight;
                double lineY1 = lineY0 + lineHeight;
                // 与该行纵向跨度无重叠（上一行/下一行）则跳过
                if (lineY1 <= chosenLineY0 || lineY0 >= chosenLineY1) continue;
                double lineWidth = Text.measureLine(runText, line);
                double alignOffset = (alignDirect && run.owner() == unit)
                        ? TextMetrics.computeAlignedX(runText, contentWidth, lineWidth, i == 0)
                        : 0;
                double lineX0 = contentPos.x + (i == 0 ? run.x() : 0) + alignOffset - unit.scrollLeft;
                double lineX1 = lineX0 + lineWidth;
                double xDistance = x < lineX0 ? lineX0 - x : (x > lineX1 ? x - lineX1 : 0);
                if (xDistance > bestXDistance) continue;
                bestXDistance = xDistance;
                int lineLocal;
                double relativeX = x - lineX0;
                if (relativeX <= 0) {
                    lineLocal = 0;
                } else if (relativeX >= lineWidth) {
                    lineLocal = line.length();
                } else {
                    double currentWidth = 0;
                    lineLocal = 0;
                    for (int c = 0; c < line.length(); c++) {
                        double charWidth = Text.measureLine(runText, line.substring(c, c + 1));
                        if (relativeX <= currentWidth + charWidth / 2.0) break;
                        currentWidth += charWidth;
                        lineLocal++;
                    }
                }
                int global = runBase + SelectionUnits.runLineStart(run, i) + lineLocal;
                bestOffset = Math.min(global, flattened.length());
            }
        }
        return bestOffset;
    }

    /** 原子对象（img/hr）盒子命中：指针落入对象盒子时返回其哨兵偏移，否则 -1。 */
    private static int locateObjectHit(Element unit, double x, double y) {
        if (unit == null) return -1;
        int[] hit = {-1};
        locateObjectHitRecursive(unit, unit, x, y, hit);
        return hit[0];
    }

    private static void locateObjectHitRecursive(Element unit, Element current, double x, double y, int[] hit) {
        if (hit[0] >= 0) return;
        for (Node child : current.getRenderChildNodes()) {
            if (hit[0] >= 0) return;
            if (!(child instanceof Element childElement)) continue;
            if (SelectionUnits.isAtomicObject(childElement)) {
                Element.DOMRect rect = childElement.getBoundingClientRect();
                if (rect != null && rect.width > 0 && rect.height > 0
                        && x >= rect.x && x <= rect.x + rect.width
                        && y >= rect.y && y <= rect.y + rect.height) {
                    hit[0] = SelectionUnits.baseOffsetOfDescendant(unit, childElement);
                    return;
                }
                continue;
            }
            if (childElement instanceof com.sighs.apricityui.element.AbstractText) continue;
            locateObjectHitRecursive(unit, childElement, x, y, hit);
        }
    }

    /** flex 直接文本布局的命中：按布局位置找最近的文本块，再按字符宽度定位。 */    private static int locateFlexDirect(Element unit, double x, double y) {
        List<Flex.DirectTextLayout> layouts = Flex.computeDirectTextLayouts(unit);
        if (layouts.isEmpty()) return 0;
        List<String> fragments = SelectionUnits.flexTextFragments(unit);
        String flattened = SelectionUnits.flattenedSelectableText(unit);
        Position origin = Position.of(unit);
        int bestOffset = 0;
        double bestDistance = Double.MAX_VALUE;
        int fragmentIndex = 0;
        int accumulatedBase = 0;
        for (Flex.DirectTextLayout layout : layouts) {
            Text text = layout.text();
            if (text == null || text.content == null || text.content.isEmpty()) continue;
            int base = accumulatedBase;
            if (fragmentIndex < fragments.size() && fragments.get(fragmentIndex).equals(text.content)) {
                accumulatedBase += text.content.length();
                fragmentIndex++;
            } else {
                // 片段对不上（order 参与方等特殊情况）：放弃后续分段
                fragmentIndex = fragments.size();
            }
            double px = origin.x + layout.position().x - unit.scrollLeft;
            double py = origin.y + layout.position().y - unit.scrollTop;
            double distance = y < py ? py - y
                    : (y > py + text.lineHeight ? y - (py + text.lineHeight) : 0);
            if (distance > bestDistance) continue;
            bestDistance = distance;
            double lineWidth = Text.measureLine(text, text.content);
            int local;
            double relativeX = x - px;
            if (relativeX <= 0) {
                local = 0;
            } else if (relativeX >= lineWidth) {
                local = text.content.length();
            } else {
                double currentWidth = 0;
                local = 0;
                for (int c = 0; c < text.content.length(); c++) {
                    double charWidth = Text.measureLine(text, text.content.substring(c, c + 1));
                    if (relativeX <= currentWidth + charWidth / 2.0) break;
                    currentWidth += charWidth;
                    local++;
                }
            }
            bestOffset = Math.min(base + local, flattened.length());
        }
        return bestOffset;
    }

    /** 叶子单元（文本由 drawInnerText 绘制）的命中：与 drawInnerText 的排版坐标一致。 */
    private static int locateLeaf(Element unit, double x, double y) {
        Text text = selectableTextFor(unit);
        if (text.content == null || text.content.isEmpty()) return 0;
        Box box = Box.of(unit);
        double contentWidth = box.innerSize().width();
        double contentHeight = box.innerSize().height();
        Text.WrappedText wrapped = Text.wrapCached(unit, text);
        List<String> lines = unit.resolveRenderedLines(text, contentWidth, contentHeight);
        if (lines.isEmpty()) return 0;
        int[] starts = wrapped.starts();
        double lineHeight = text.lineHeight;
        double textHeight = Math.max(lineHeight, lines.size() * lineHeight);
        String display = unit.getComputedStyle().display;
        boolean flexLike = Layout.isFlexDisplay(display) || Layout.isGridDisplay(display);
        Position flexTextOffset = flexLike ? unit.getFlexTextOffset() : Position.ZERO;
        Position contentPos = Rect.of(unit).getContentPosition();
        double drawY = contentPos.y + (flexLike ? flexTextOffset.y
                : TextMetrics.computeVerticalOffset(text, contentHeight, textHeight));

        int lineIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            double lineY = drawY + i * lineHeight;
            double distance = y < lineY ? lineY - y : (y > lineY + lineHeight ? y - (lineY + lineHeight) : 0);
            if (distance == 0) {
                lineIndex = i;
                break;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                lineIndex = i;
            }
        }

        String line = lines.get(lineIndex);
        double lineWidth = Text.measureLine(text, line);
        double drawX = contentPos.x + (flexLike
                ? TextMetrics.computeFlexTextAlignedX(unit, text, contentWidth, lineWidth)
                : TextMetrics.computeAlignedX(text, contentWidth, lineWidth, lineIndex == 0));
        int lineStart = starts[lineIndex];
        double relativeX = x - (drawX - unit.scrollLeft);
        if (relativeX <= 0) return lineStart;
        double currentWidth = 0;
        int cursor = 0;
        for (int i = 0; i < line.length(); i++) {
            double charWidth = measureSegment(unit, line.substring(i, i + 1));
            if (relativeX <= currentWidth + charWidth / 2.0) break;
            currentWidth += charWidth;
            cursor++;
        }
        return Math.min(lineStart + cursor, lineStart + line.length());
    }

    /**
     * 浏览器式单词边界：围绕命中偏移的最大连续非空白片段。
     * 偏移落在空白内（或文本为空）时返回 null，此时不做词选择。
     */
    /** 按空白分词：返回 offset 所在词的 [start, end)；无词时返回 null。 */
    public static int[] wordRange(String text, int offset) {
        if (text == null || text.isEmpty()) return null;
        int clamped = Math.max(0, Math.min(offset, text.length()));
        int start = clamped;
        while (start > 0 && !isWordSeparator(text.charAt(start - 1))) start--;
        int end = clamped;
        while (end < text.length() && !isWordSeparator(text.charAt(end))) end++;
        if (start == end) return null;
        return new int[]{start, end};
    }

    /** 元素是否位于富文本编辑区（richtext 子树）内：编辑区的选词/选中由 RichTextSelection 管理。 */
    private static boolean isInsideRichTextEditor(Element element) {
        for (Element e = element; e != null; e = e.parentElement) {
            if (e instanceof com.sighs.apricityui.element.RichText) return true;
        }
        return false;
    }

    private static boolean isWordSeparator(char c) {
        return Character.isWhitespace(c);
    }

    /** 与 Element.drawChildTextRuns 的 shouldAlignDirectNormalFlowTextRuns 保持一致。 */
    private static boolean shouldAlignDirectTextRuns(Element element) {
        boolean hasText = false;
        for (Node child : element.getRenderChildNodes()) {
            if (child instanceof com.sighs.apricityui.dom.CommentNode) continue;
            if (child instanceof TextNode textNode) {
                hasText |= textNode.getTextContent() != null && !textNode.getTextContent().isEmpty();
                continue;
            }
            if (child instanceof Element childElement && !Layout.isInFlow(childElement.getComputedStyle())) {
                continue;
            }
            return false;
        }
        return hasText;
    }

    // ------------------------------------------------------------------
    // 公开视图 API（委托给文档级选择）
    // ------------------------------------------------------------------

    public boolean canSelectInnerText() {
        return SelectionUnits.resolveUnitContext(owner) != null;
    }

    public boolean hasInnerTextSelection() {
        SelectionUnits.UnitContext context = SelectionUnits.resolveUnitContext(owner);
        if (context == null) return false;
        return viewLocalRange(context) != null;
    }

    public String getSelectedInnerText() {
        SelectionUnits.UnitContext context = SelectionUnits.resolveUnitContext(owner);
        if (context == null) return "";
        int[] range = viewLocalRange(context);
        if (range == null) return "";
        // 选区偏移在归一化空间；取原始文本时映射回单元扁平文本的全局区间（子元素视图需加基偏移）
        return SelectionUnits.rawRangeForNormalizedRange(context.unit(),
                context.baseOffset() + range[0], context.baseOffset() + range[1]);
    }

    public void selectAllInnerText() {
        if (owner.document == null) return;
        SelectionUnits.UnitContext context = SelectionUnits.resolveUnitContext(owner);
        if (context == null) return;
        DocumentSelection selection = owner.document.getDocumentSelection();
        int start = context.baseOffset();
        selection.collapse(context.unit(), start);
        selection.extendTo(context.unit(), start + context.text().length());
        owner.addDirtyFlags(Drawer.REPAINT);
    }

    public void clearTextSelection() {
        if (owner.document == null) return;
        SelectionUnits.UnitContext context = SelectionUnits.resolveUnitContext(owner);
        if (context == null) return;
        DocumentSelection selection = owner.document.getDocumentSelection();
        // 只清当前元素所在单元的选区，不影响其他单元的选区
        if (selection.getAnchorUnit() != context.unit() && selection.getEndUnit() != context.unit()) {
            return;
        }
        selection.clear();
        owner.addDirtyFlags(Drawer.REPAINT);
    }

    private int[] viewLocalRange(SelectionUnits.UnitContext context) {
        if (owner.document == null) return null;
        int[] unitRange = owner.document.resolveUnitSelectionRange(context.unit());
        if (unitRange == null) return null;
        int start = Math.max(unitRange[0] - context.baseOffset(), 0);
        int end = Math.min(unitRange[1] - context.baseOffset(), context.text().length());
        if (start >= end) return null;
        return new int[]{start, end};
    }

    // ------------------------------------------------------------------
    // 绘制（叶子单元路径；富文本单元由 drawChildTextRuns 分段绘制）
    // ------------------------------------------------------------------

    public void drawInnerTextSelection(PoseStack poseStack, Rect rectRenderer) {
        if (owner == null || owner.document == null) return;
        // 无选区时直接返回：高亮绘制只在选区存在时才有意义，避免每元素每帧的空跑
        if (!owner.document.hasAnyActiveSelection()) return;
        if (SelectionUnits.paintsTextViaRuns(owner)) return;
        SelectionUnits.UnitContext context = SelectionUnits.resolveUnitContext(owner);
        if (context == null) return;
        int[] range = viewLocalRange(context);
        if (range == null) return;
        Text baseText = selectableText();
        if (!hasDrawableText(baseText.content)) return;
        Text.WrappedText wrapped = Text.wrapCached(owner, baseText);
        int[] starts = wrapped.starts();
        int min = range[0];
        int max = range[1];
        if (min >= max) return;

        Position contentPos = rectRenderer.getContentPosition();
        double contentWidth = Box.of(owner).innerSize().width();
        double contentHeight = Box.of(owner).innerSize().height();
        // 高亮跟随实际绘制的渲染行（可能被 line-clamp 截断或 text-overflow 省略），
        // 垂直偏移也以渲染行数为准，与 drawInnerText 的绘制坐标保持一致
        List<String> lines = owner.resolveRenderedLines(baseText, contentWidth, contentHeight);
        double textHeight = Math.max(baseText.lineHeight, lines.size() * baseText.lineHeight);
        double baseY = contentPos.y + TextMetrics.computeVerticalOffset(baseText, contentHeight, textHeight);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineStart = starts[i];
            // 与 drawInnerText 一致：可选中终点取渲染行与换行行的公共前缀，排除行尾省略号
            int selectableEnd = lineStart + commonPrefixLength(line, wrapped.lines().get(i));
            int drawStart = Math.max(min, lineStart);
            int drawEnd = Math.min(max, selectableEnd);
            if (drawStart >= drawEnd) continue;

            double lineWidth = Text.measureLine(baseText, line);
            double drawX = contentPos.x + TextMetrics.computeAlignedX(baseText, contentWidth, lineWidth, i == 0);
            double startX = measureTextSegmentWidth(line.substring(0, drawStart - lineStart)) - owner.scrollLeft;
            double endX = measureTextSegmentWidth(line.substring(0, drawEnd - lineStart)) - owner.scrollLeft;
            float x0 = (float) (drawX + startX);
            float x1 = (float) (drawX + endX);
            float y0 = (float) (baseY + i * baseText.lineHeight);
            float y1 = y0 + (float) baseText.lineHeight;
            Graph.drawFillRect(poseStack.last().pose(), x0, y0, x1, y1, Text.getSelectionColor(owner));
        }
    }

    public void drawInnerText(PoseStack poseStack, Rect rectRenderer) {
        if (owner == null || owner.document == null) return;
        if (SelectionUnits.paintsTextViaRuns(owner)) return;
        Text text = selectableText();
        Position contentPos = rectRenderer.getContentPosition();
        text.color = new Color(Text.getFontColor(owner));

        if (!hasDrawableText(text.content)) return;

        double contentWidth = Box.of(owner).innerSize().width();
        double contentHeight = Box.of(owner).innerSize().height();
        List<String> lines = owner.resolveRenderedLines(text, contentWidth, contentHeight);
        double textHeight = Math.max(text.lineHeight, lines.size() * text.lineHeight);
        boolean flexLike = com.sighs.apricityui.layout.Layout.isFlexDisplay(owner.getComputedStyle().display)
                || com.sighs.apricityui.layout.Layout.isGridDisplay(owner.getComputedStyle().display);
        Position flexTextOffset = flexLike ? owner.getFlexTextOffset() : Position.ZERO;
        double drawY = contentPos.y + (flexLike ? flexTextOffset.y : TextMetrics.computeVerticalOffset(text, contentHeight, textHeight));
        // 选中文字与普通文字同色（高亮矩形由 drawInnerTextSelection 先行绘制），
        // 整行一次绘制，不做按段光栅化：非锚定路径的 glyphAnchorTexel 依赖各段
        // 内容的光栅 ink 统计，分段会让各段垂直锚定不同，导致选中区域后面的文字上浮。
        Position linePos = new Position(0, 0);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            double lineWidth = Text.measureLine(text, line);
            double drawX = contentPos.x + (flexLike
                    ? TextMetrics.computeFlexTextAlignedX(owner, text, contentWidth, lineWidth)
                    : TextMetrics.computeAlignedX(text, contentWidth, lineWidth, i == 0));
            text.content = line;
            linePos.x = drawX - owner.scrollLeft;
            linePos.y = drawY + i * text.lineHeight;
            FontDrawer.drawFont(poseStack, text, linePos);
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private Text selectableText() {
        Text base = Text.of(owner);
        Text copy = new Text();
        copy.fontSize = base.fontSize;
        copy.fontWeight = base.fontWeight;
        copy.oblique = base.oblique;
        copy.strokeWidth = base.strokeWidth;
        copy.strokeColor = base.strokeColor;
        copy.color = base.color;
        copy.textDecoration = base.textDecoration;
        copy.fontFamily = base.fontFamily;
        copy.lineHeight = base.lineHeight;
        copy.direction = base.direction;
        copy.textAlign = base.textAlign;
        copy.verticalAlign = base.verticalAlign;
        copy.whiteSpace = base.whiteSpace;
        copy.fontMode = base.fontMode;
        copy.textIndent = base.textIndent;
        copy.letterSpacing = base.letterSpacing;
        copy.rasterBackgroundColor = base.rasterBackgroundColor;
        copy.content = SelectionUnits.flattenedSelectableText(owner);
        return copy;
    }

    private static Text selectableTextFor(Element unit) {
        Text base = Text.of(unit);
        Text copy = new Text();
        copy.fontSize = base.fontSize;
        copy.fontWeight = base.fontWeight;
        copy.oblique = base.oblique;
        copy.strokeWidth = base.strokeWidth;
        copy.strokeColor = base.strokeColor;
        copy.color = base.color;
        copy.textDecoration = base.textDecoration;
        copy.fontFamily = base.fontFamily;
        copy.lineHeight = base.lineHeight;
        copy.direction = base.direction;
        copy.textAlign = base.textAlign;
        copy.verticalAlign = base.verticalAlign;
        copy.whiteSpace = base.whiteSpace;
        copy.fontMode = base.fontMode;
        copy.textIndent = base.textIndent;
        copy.letterSpacing = base.letterSpacing;
        copy.rasterBackgroundColor = base.rasterBackgroundColor;
        copy.content = SelectionUnits.flattenedSelectableText(unit);
        return copy;
    }

    private double measureTextSegmentWidth(String segment) {
        if (segment == null || segment.isEmpty()) return 0;
        Text base = Text.of(owner);
        Text copy = TextMetrics.cloneTextForSegment(base, segment, Color.BLACK);
        return Text.measureLine(copy, segment);
    }

    private static double measureSegment(Element unit, String segment) {
        if (segment == null || segment.isEmpty()) return 0;
        Text base = Text.of(unit);
        Text copy = TextMetrics.cloneTextForSegment(base, segment, Color.BLACK);
        return Text.measureLine(copy, segment);
    }

    /** 两个字符串的公共前缀长度；渲染行是换行行的前缀或省略号变体。 */
    private static int commonPrefixLength(String a, String b) {
        if (a == null || b == null) return 0;
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    /** U+FFFC 只表示编辑模型中的对象位置，不应作为普通字形绘制。 */
    private static boolean hasDrawableText(String content) {
        if (content == null || content.isEmpty()) return false;
        for (int i = 0; i < content.length(); i++) {
            char value = content.charAt(i);
            if (value != '\uFFFC' && !Character.isWhitespace(value) && !Character.isSpaceChar(value)) {
                return true;
            }
        }
        return false;
    }
}

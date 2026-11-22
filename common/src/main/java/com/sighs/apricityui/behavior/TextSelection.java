package com.sighs.apricityui.behavior;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.element.Iframe;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.layout.TextFlowGeometry;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.PoseMatrices;
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
    private static final int WORD_CLASS_SEPARATOR = 0;
    private static final int WORD_CLASS_WORD = 1;
    private static final int WORD_CLASS_PUNCTUATION = 2;
    private final Element owner;
    // ------------------------------------------------------------------
    // 鼠标事件：命中 → （单元, 偏移），再操作文档级选择
    // ------------------------------------------------------------------
    private Position reusableLinePos;

    public TextSelection(Element owner) {
        this.owner = owner;
    }

    /**
     * 命中偏移是否落在该单元当前的文档选区范围内（含端点），单击保留选区用。
     */
    private static boolean isInsideSelection(DocumentSelection selection, SelectionUnits.UnitOffset hit) {
        int[] range = selection.localRangeForUnit(hit.unit());
        if (range == null) return false;
        return hit.offset() >= range[0] && hit.offset() <= range[1];
    }

    /**
     * 按拖拽粒度扩展终点：字符粒度直接扩展；词/段落粒度（双击/三击后的拖拽）把终点
     * 吸附到所在词/段落的边界，锚点固定在初始词/段落背离拖拽方向的一侧——反向拖拽
     * 时锚点翻转到区间另一端，保证初始词/段落始终完整选中（浏览器行为）。
     */
    private static void extendWithGranularity(Document document, DocumentSelection selection,
                                              SelectionUnits.UnitOffset hit) {
        DocumentSelection.Granularity granularity = selection.getGranularity();
        Element anchorRangeUnit = selection.getGranularityUnit();
        if (granularity == DocumentSelection.Granularity.CHARACTER || anchorRangeUnit == null) {
            selection.extendTo(hit.unit(), hit.offset());
            return;
        }
        boolean draggingBackward = hit.unit() != anchorRangeUnit
                ? selection.unitOrderedBefore(document, hit.unit(), anchorRangeUnit)
                : hit.offset() < selection.getGranularityStart();
        int anchorBoundary = draggingBackward ? selection.getGranularityEnd() : selection.getGranularityStart();
        if (selection.getAnchorUnit() != anchorRangeUnit || selection.getAnchorOffset() != anchorBoundary) {
            selection.moveAnchorTo(anchorRangeUnit, anchorBoundary);
        }
        String text = SelectionUnits.flattenedSelectableText(hit.unit());
        int snapped = hit.offset();
        if (granularity == DocumentSelection.Granularity.WORD) {
            int[] word = wordRange(text, hit.offset());
            if (word != null) snapped = draggingBackward ? word[0] : word[1];
        } else {
            int[] paragraph = paragraphRange(text, hit.offset());
            if (paragraph != null) snapped = draggingBackward ? paragraph[0] : paragraph[1];
        }
        selection.extendTo(hit.unit(), snapped);
    }

    public static SelectionUnits.UnitOffset resolveUnitOffset(Element hit, double x, double y) {
        if (hit == null) return null;
        Element unit = SelectionUnits.resolveUnit(hit);
        if (unit == null) return null;
        double[] local = com.sighs.apricityui.render.CommittedGeometry.toLocalPoint(unit, x, y);
        return new SelectionUnits.UnitOffset(unit, locateOffsetInUnit(unit, local[0], local[1]));
    }

    private static int locateOffsetInUnit(Element unit, double x, double y) {
        if (unit == null) return 0;
        String display = unit.getComputedStyle().display;
        if (Layout.isFlexDisplay(display) || Layout.isGridDisplay(display)) {
            return locateDirectLayoutText(unit, x, y);
        }
        if (SelectionUnits.paintsTextViaRuns(unit)) {
            return locateRuns(unit, x, y);
        }
        return locateLeaf(unit, x, y);
    }

    // ------------------------------------------------------------------
    // 命中测试：指针 → （单元, 偏移）
    // ------------------------------------------------------------------

    /**
     * 普通流 run 行的命中：先查原子对象盒子，再逐 run 逐行找包含指针的行，按字符宽度定位。
     */
    private static int locateRuns(Element unit, double x, double y) {
        // 阶段零：原子对象（img/hr）盒子命中 → 对象哨兵偏移
        int objectHit = locateObjectHit(unit, x, y);
        if (objectHit >= 0) return objectHit;

        var lines = TextFlowGeometry.of(unit).lines();
        if (lines.isEmpty()) return 0;
        Position origin = Rect.of(unit).getContentPosition();
        double localX = x - origin.x + unit.scrollLeft;
        double localY = y - origin.y;
        TextFlowGeometry.Line chosen = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (var line : lines) {
            double distance = distanceFromInterval(localY, line.y(), line.y() + line.height());
            if (distance < bestDistance) {
                bestDistance = distance;
                chosen = line;
            }
        }
        if (chosen == null) return 0;
        int bestOffset = 0;
        double bestXDistance = Double.POSITIVE_INFINITY;
        for (var line : lines) {
            if (line.y() + line.height() <= chosen.y() || line.y() >= chosen.y() + chosen.height()) continue;
            double distance = distanceFromInterval(localX, line.x(), line.x() + line.width());
            if (distance >= bestXDistance) continue;
            bestXDistance = distance;
            int local = line.advances().offsetAt(localX - line.x());
            bestOffset = SelectionUnits.baseOffsetOfDescendant(unit, line.source()) + line.start() + local;
        }
        return Math.min(bestOffset, SelectionUnits.flattenedSelectableText(unit).length());
    }

    private static double distanceFromInterval(double value, double min, double max) {
        return Math.max(0, Math.max(min - value, value - max));
    }

    /**
     * 原子对象（img/hr）盒子命中：指针落入对象盒子时返回其哨兵偏移，否则 -1。
     */
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

    /**
     * Flex/Grid 直接文本布局的命中：按布局位置找最近的文本块，再按字符宽度定位。
     */
    private static int locateDirectLayoutText(Element unit, double x, double y) {
        TextFlowGeometry flow = TextFlowGeometry.of(unit);
        List<Layout.DirectTextLayout> layouts = flow.direct();
        if (layouts.isEmpty()) return 0;
        List<String> fragments = SelectionUnits.flexTextFragments(unit);
        String flattened = SelectionUnits.flattenedSelectableText(unit);
        // 与 flex 直接文本的绘制坐标（forRender + margin）保持一致。
        Position origin = Position.forRender(unit);
        Box box = Box.of(unit);
        double originX = origin.x + box.getMarginLeft();
        double originY = origin.y + box.getMarginTop();
        int bestOffset = 0;
        double bestDistance = Double.MAX_VALUE;
        int fragmentIndex = 0;
        int accumulatedBase = 0;
        int directLineIndex = 0;
        for (Layout.DirectTextLayout layout : layouts) {
            if (layout == null || layout.text() == null) continue;
            int layoutLineBase = directLineIndex;
            if (layout.text().content != null) {
                directLineIndex += layout.lines().size();
            }
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
            double px = originX + layout.position().x - unit.scrollLeft;
            double py = originY + layout.position().y - unit.scrollTop;
            List<String> lines = layout.lines();
            int[] starts = layout.lineStarts();
            for (int i = 0; i < lines.size(); i++) {
                TextFlowGeometry.DirectLine line = flow.directLines().get(layoutLineBase + i);
                double lineY = py + i * text.lineHeight;
                double distance = distanceFromInterval(y, lineY, lineY + text.lineHeight);
                if (distance > bestDistance) continue;
                bestDistance = distance;
                int local = line.advances().offsetAt(x - px);
                bestOffset = Math.min(base + line.start() + local, flattened.length());
            }
        }
        return bestOffset;
    }

    /**
     * 鼠标位置是否落在单元的可选文本（行盒）上。供光标/命中判定使用，
     * 几何与 {@link #locateOffsetInUnit} 完全一致，避免光标与选区错位。
     */
    public static boolean isPositionOverSelectableText(Element unit, double x, double y) {
        if (unit == null) return false;
        String display = unit.getComputedStyle().display;
        if (Layout.isFlexDisplay(display) || Layout.isGridDisplay(display)) return overDirectLayoutText(unit, x, y);
        if (SelectionUnits.paintsTextViaRuns(unit)) return overRunsText(unit, x, y);
        return overLeafText(unit, x, y);
    }

    private static boolean overDirectLayoutText(Element unit, double x, double y) {
        Position origin = Position.forRender(unit);
        Box box = Box.of(unit);
        double ox = origin.x + box.getMarginLeft();
        double oy = origin.y + box.getMarginTop();
        TextFlowGeometry flow = TextFlowGeometry.of(unit);
        int directLineIndex = 0;
        for (Layout.DirectTextLayout layout : flow.direct()) {
            int layoutLineBase = directLineIndex;
            if (layout != null && layout.text() != null && layout.text().content != null) {
                directLineIndex += layout.lines().size();
            }
            if (layout == null || layout.text() == null || layout.position() == null) continue;
            Text text = layout.text();
            if (text.content == null || text.content.isEmpty()) continue;
            double px = ox + layout.position().x - unit.scrollLeft;
            double py = oy + layout.position().y - unit.scrollTop;
            // 软换行后逐行判定：y 落在某一行内且 x 不超出该行宽度。
            for (int i = 0; i < layout.lines().size(); i++) {
                TextFlowGeometry.DirectLine line = flow.directLines().get(layoutLineBase + i);
                double lineY = py + i * text.lineHeight;
                if (y >= lineY && y < lineY + text.lineHeight
                        && x >= px && x <= px + line.width()) return true;
            }
        }
        return false;
    }

    private static boolean overRunsText(Element unit, double x, double y) {
        var lines = TextFlowGeometry.of(unit).lines();
        if (lines.isEmpty()) return false;
        Position origin = Rect.of(unit).getContentPosition();
        double localX = x - origin.x + unit.scrollLeft;
        double localY = y - origin.y;
        for (var line : lines) {
            if (localY >= line.y() && localY < line.y() + line.height()
                    && localX >= line.x() && localX <= line.x() + line.width()) return true;
        }
        return false;
    }

    private static boolean overLeafText(Element unit, double x, double y) {
        var lines = TextFlowGeometry.of(unit).leaf();
        if (lines.isEmpty()) return false;
        Position origin = Rect.of(unit).getContentPosition();
        double px = x - origin.x + unit.scrollLeft;
        double py = y - origin.y;
        for (var line : lines) {
            if (py >= line.y() && py < line.y() + line.height()
                    && px >= line.x() && px <= line.x() + line.width()) return true;
        }
        return false;
    }

    private static int locateLeaf(Element unit, double x, double y) {
        var lines = TextFlowGeometry.of(unit).leaf();
        if (lines.isEmpty()) return 0;
        Position origin = Rect.of(unit).getContentPosition();
        double py = y - origin.y;
        var chosen = lines.get(0);
        double nearest = Double.POSITIVE_INFINITY;
        for (var line : lines) {
            double distance = distanceFromInterval(py, line.y(), line.y() + line.height());
            if (distance >= nearest) continue;
            nearest = distance;
            chosen = line;
        }
        int offset = chosen.advances().offsetAt(x - origin.x + unit.scrollLeft - chosen.x());
        return chosen.start() + Math.min(offset, chosen.selectableLength());
    }

    /**
     * 按浏览器词边界分词：返回 offset 所在词的 [start, end)；无词时返回 null。
     */
    public static int[] wordRange(String text, int offset) {
        if (text == null || text.isEmpty()) return null;
        int clamped = Math.max(0, Math.min(offset, text.length()));
        int anchor = -1;
        if (clamped > 0 && wordClassOf(text.charAt(clamped - 1)) != WORD_CLASS_SEPARATOR) {
            anchor = clamped - 1;
        } else if (clamped < text.length() && wordClassOf(text.charAt(clamped)) != WORD_CLASS_SEPARATOR) {
            anchor = clamped;
        }
        if (anchor < 0) return null;
        int wordClass = wordClassOf(text.charAt(anchor));
        int start = anchor;
        while (start > 0 && wordClassOf(text.charAt(start - 1)) == wordClass) start--;
        int end = anchor + 1;
        while (end < text.length() && wordClassOf(text.charAt(end)) == wordClass) end++;
        if (start >= end) return null;
        return new int[]{start, end};
    }

    /**
     * 段落范围：扁平文本中硬换行（\n，来自 <br>）之间的片段 [start, end)，
     * 不含换行符本身。浏览器三击选择段落；无硬换行的单元段落即整个单元文本。
     */
    public static int[] paragraphRange(String text, int offset) {
        if (text == null || text.isEmpty()) return null;
        int clamped = Math.max(0, Math.min(offset, text.length()));
        int start = clamped <= 0 ? 0 : text.lastIndexOf('\n', clamped - 1) + 1;
        int newline = text.indexOf('\n', clamped);
        int end = newline < 0 ? text.length() : newline;
        return new int[]{start, end};
    }

    /**
     * 浏览器式词边界（UAX #29 的简化版）：字符分三类——空白（不可成词）、词字符
     * （字母/数字/CJK/连接符如 _/组合符）、标点符号。标点同样断词：双击 "foo.bar"
     * 选中 "foo"；连续同类标点作为一个整体成词（双击 "..." 选中整段标点）。
     * 偏移落在两字符之间时优先取左侧字符的词（双击词尾选中该词）。
     * 偏移落在空白内（或文本为空）时返回 null，此时不做词选择。
     */

    private static int wordClassOf(char c) {
        if (Character.isWhitespace(c)) return WORD_CLASS_SEPARATOR;
        // CJK 表意文字在 isLetterOrDigit 的 Letter 范畴内，与拉丁词同属词字符
        if (Character.isLetterOrDigit(c)) return WORD_CLASS_WORD;
        int type = Character.getType(c);
        // 连接符（_ 等）与组合符并入词字符，与浏览器词边界一致
        if (type == Character.CONNECTOR_PUNCTUATION
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK) {
            return WORD_CLASS_WORD;
        }
        return WORD_CLASS_PUNCTUATION;
    }

    /**
     * 元素是否位于富文本编辑区（richtext 子树）内：编辑区的选词/选中由 RichTextSelection 管理。
     */
    private static boolean isInsideRichTextEditor(Element element) {
        for (Element e = element; e != null; e = e.parentElement) {
            if (e instanceof com.sighs.apricityui.element.RichText) return true;
        }
        return false;
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
    // 公开视图 API（委托给文档级选择）
    // ------------------------------------------------------------------

    private void handleMouseDown(MouseEvent event) {
        // The embedded document owns its selection. Resolving an iframe to an outer text
        // unit would move focus to BODY after MouseEvent had already focused the iframe,
        // leaving mouse forwarding intact but routing every key away from the web view.
        if (owner instanceof Iframe) return;
        if (isInsideRichTextEditor(owner)) return;
        Document document = owner.document;
        if (document == null) return;
        // 中键不参与文档级选择：Linux 主选区语义下由输入控件负责中键粘贴，这里保持 no-op
        if (event.button == 2) return;
        // 右键：选区内保留（contextmenu 作用于当前选区），选区外折叠到点击点，均不进入拖拽
        if (event.button == 1) {
            handleRightMouseDown(document, event);
            return;
        }
        // 侧键等其他按键不参与文本选择
        if (event.button != 0) return;
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
            // 三击：选择段落（硬换行 <br> 之间的片段；无硬换行时即整个单元），
            // 拖拽从段落边界继续按段落扩展
            Element unit = hit.unit();
            String text = SelectionUnits.flattenedSelectableText(unit);
            int[] paragraph = paragraphRange(text, hit.offset());
            if (paragraph != null && paragraph[0] < paragraph[1]) {
                selection.collapse(unit, paragraph[0]);
                selection.extendTo(unit, paragraph[1]);
                selection.setGranularity(DocumentSelection.Granularity.PARAGRAPH, unit, paragraph[0], paragraph[1]);
            } else {
                selection.collapse(unit, hit.offset());
            }
            selection.setSelecting(true);
            document.setFocusedElement(unit);
            return;
        }
        if (clickCount == 2) {
            // 双击：按词边界选词（空白与标点均断词），拖拽从词边界继续按词扩展
            Element unit = hit.unit();
            String text = SelectionUnits.flattenedSelectableText(unit);
            int[] word = wordRange(text, hit.offset());
            if (word != null) {
                selection.collapse(unit, word[0]);
                selection.extendTo(unit, word[1]);
                selection.setGranularity(DocumentSelection.Granularity.WORD, unit, word[0], word[1]);
            } else {
                selection.collapse(unit, hit.offset());
            }
            selection.setSelecting(true);
            document.setFocusedElement(unit);
            return;
        }

        if (event.shiftKey && selection.hasAnchor()) {
            // shift+点击：从既有锚点按字符粒度扩展
            selection.setGranularity(DocumentSelection.Granularity.CHARACTER, null, 0, 0);
            selection.extendTo(hit.unit(), hit.offset());
        } else if (clickCount == 1 && isInsideSelection(selection, hit)) {
            // 单击落在选区内部：保留选区（不折叠/不扩展），登记为潜在的文本拖拽，
            // 拖拽文本快照取当前文档选区；移出阈值后进入真正的拖拽（COPY 语义）。
            // 若未形成拖拽，mouseup 时按浏览器行为折叠到按下点（pending collapse）。
            document.beginTextDrag(document.getDocumentSelectedText(), event.clientX, event.clientY);
            selection.markPendingCollapse(hit.unit(), hit.offset());
        } else {
            selection.collapse(hit.unit(), hit.offset());
        }
        selection.setSelecting(true);
        document.setFocusedElement(hit.unit());
    }

    /**
     * 右键按下（浏览器语义）：命中选区内部时保留选区（随后的 contextmenu 作用于它）；
     * 命中选区外的可选文本时折叠到点击点；命中不可选区域时清空选择。不进入拖拽状态。
     */
    private void handleRightMouseDown(Document document, MouseEvent event) {
        document.endTextDrag();
        DocumentSelection selection = document.getDocumentSelection();
        SelectionUnits.UnitOffset hit = resolveUnitOffset(owner, event.clientX, event.clientY);
        if (hit == null) {
            document.clearDocumentSelection();
            return;
        }
        if (isInsideSelection(selection, hit)) return;
        selection.collapse(hit.unit(), hit.offset());
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
        // 文本拖拽候选：先推进拖拽判定；候选期间（含越阈值后的拖拽中）选区冻结不再扩展，
        // 未越阈值时由 mouseup 折叠到按下点，越阈值后进入真正的拖拽
        if (document.isTextDragPending()) {
            document.updateTextDrag(event.clientX, event.clientY);
            return;
        }
        // 事件始终派发到指针当前悬停的元素，无需再依赖 pressedElement 冻结
        SelectionUnits.UnitOffset hit = resolveUnitOffset(owner, event.clientX, event.clientY);
        if (hit == null) {
            // 指针悬停在不可选内容上：保持终点不动（自然冻结）
            return;
        }
        extendWithGranularity(document, selection, hit);
    }

    /**
     * mouseup：若文本拖拽结束时指针落在可编辑输入控件上，把拖拽文本复制进去
     * （COPY 语义，源选区保持不动）；否则仅取消拖拽。每次 mouseup 都清理拖拽状态。
     * 选区内按下但未形成拖拽时，按浏览器行为把选区折叠到按下点（pending collapse）。
     */
    private void handleMouseUp(Event rawEvent) {
        Document document = owner.document;
        if (rawEvent instanceof MouseEvent mouseEvent) {
            // 重派发到按下元素的事件不处理落下/清理：悬停元素自身的 mouseup 已完成
            if (mouseEvent.activeElementRedirect) return;
        }
        boolean dragging = document.isTextDragging();
        // 只有最深命中元素（target == owner）执行落下，避免路由上的祖先重复处理
        if (dragging && rawEvent.target == owner
                && rawEvent.target instanceof AbstractText input && input.canEditText()) {
            String draggedText = document.getDraggedText();
            if (draggedText != null && !draggedText.isEmpty()) {
                input.insertText(draggedText, "insertFromDrop");
            }
        }
        document.endTextDrag();
        DocumentSelection selection = document.getDocumentSelection();
        selection.setSelecting(false);
        // 待折叠只消费一次：路由上多个元素的 mouseup 监听器共享同一份文档选择
        SelectionUnits.UnitOffset pending = selection.consumePendingCollapse();
        if (pending != null && !dragging) {
            selection.collapse(pending.unit(), pending.offset());
        }
    }

    public boolean canSelectInnerText() {
        return SelectionUnits.resolveUnitContext(owner) != null;
    }

    public boolean hasInnerTextSelection() {
        SelectionUnits.UnitContext context = SelectionUnits.resolveUnitContext(owner);
        if (context == null) return false;
        return viewLocalRange(context) != null;
    }

    // ------------------------------------------------------------------
    // 绘制（叶子单元路径；富文本单元由 drawChildTextRuns 分段绘制）
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

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

    public void drawInnerTextSelection(PoseStack poseStack, Rect rectRenderer) {
        if (owner == null || owner.document == null) return;
        // 无选区时直接返回：高亮绘制只在选区存在时才有意义，避免每元素每帧的空跑
        if (!owner.document.hasAnyActiveSelection()) return;
        if (SelectionUnits.paintsTextViaRuns(owner)) return;
        SelectionUnits.UnitContext context = SelectionUnits.resolveUnitContext(owner);
        if (context == null) return;
        int[] range = viewLocalRange(context);
        if (range == null) return;
        Position origin = rectRenderer.getContentPosition();
        for (var line : TextFlowGeometry.of(owner).leaf()) {
            int lo = Math.max(range[0], line.start());
            int hi = Math.min(range[1], line.start() + line.selectableLength());
            if (lo >= hi) continue;
            double x = origin.x + line.x() - owner.scrollLeft;
            double y = origin.y + line.y();
            Graph.drawFillRect(PoseMatrices.of(poseStack),
                    (float) (x + line.advances().xAt(lo - line.start())), (float) y,
                    (float) (x + line.advances().xAt(hi - line.start())), (float) (y + line.height()),
                    Text.getSelectionColor(owner));
        }
    }

    public void drawInnerText(PoseStack poseStack, Rect rectRenderer) {
        if (owner == null || owner.document == null) return;
        var lines = TextFlowGeometry.of(owner).leaf();
        if (lines.isEmpty()) return;
        Position origin = rectRenderer.getContentPosition();
        Color color = new Color(Text.getFontColor(owner));
        if (reusableLinePos == null) reusableLinePos = new Position(0, 0);
        for (var line : lines) {
            Text text = TextMetrics.cloneTextForSegment(line.text(), line.content(), Color.BLACK);
            text.color = color;
            reusableLinePos.x = origin.x + line.x() - owner.scrollLeft;
            reusableLinePos.y = origin.y + line.y();
            FontDrawer.drawFont(poseStack, text, reusableLinePos);
        }
    }

}

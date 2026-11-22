package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.*;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.util.TextMetrics;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.element.Translation;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.parser.CSS;

public final class NormalFlow {
    // FlowState 池：computeFlow 可经子元素位置查询重入，所以用栈式池而非单例。
    // 动画驱动的逐帧布局失效让每个 flow 容器每帧重建 3 个 IdentityHashMap
    // （内部 Object[] 数组重新扩容，JFR 归因约 102MB）；池化后 clear 保容复用。
    private static final ThreadLocal<java.util.ArrayDeque<FlowState>> FLOW_STATE_POOL =
            ThreadLocal.withInitial(java.util.ArrayDeque::new);

    private static final ThreadLocal<IdentityHashMap<Element, FloatInput>> FLOAT_INPUTS =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final int FLOAT_PROBE = 10;
    private static final FloatInput NO_FLOATS = new FloatInput(List.of(), null);

    private NormalFlow() {
    }

    public static Position computeChildPosition(Element element, Element parent, List<Element> siblings) {
        Box parentBox = Box.of(parent);
        double lineLimit = parentBox.innerSize().width();
        FlowResult result = getOrComputeFlowLayout(parent, lineLimit);
        Position childPosition = result.childPositions().get(element);
        if (childPosition != null) {
            return new Position(parentBox.offset("left") + childPosition.x, parentBox.offset("top") + childPosition.y);
        }
        result = computeFlow(parent, parent.getRenderChildNodes(), lineLimit, element);
        FlowMetrics metrics = result.metrics();
        return new Position(
                parentBox.offset("left") + metrics.targetX,
                parentBox.offset("top") + metrics.targetY
        );
    }

    public static Size computeContentSize(Element element) {
        double lineLimit = resolveLineLimit(element);
        // FlowResult owns both line placement and content extents. Keeping a
        // second size-only cache made those two views invalidate separately.
        return getOrComputeFlowLayout(element, lineLimit).metrics().contentSize();
    }

    static Size contentAtWidth(Element element, double width) {
        return getOrComputeFlowLayout(element, Math.max(0.000001, width)).metrics().contentSize();
    }

    static Double floatAvoidanceWidth(Element element) {
        return floatInput(element).avoidanceWidth();
    }

    public static boolean hasFloatExclusions(Element element) {
        return !floatInput(element).exclusions().isEmpty();
    }

    static boolean usesFloatLines(Element element) {
        return hasFloatExclusions(element) || containsFloats(element);
    }

    private static boolean containsFloats(Element owner) {
        Object memo = LayoutMeasureCache.getObject(FLOAT_PROBE, owner, 0, 0, false);
        if (memo != null) return (boolean) memo;
        boolean result = false;
        for (Element child : owner.getRenderChildren()) {
            if (!child.getComputedStyle().isInFlow()) continue;
            if (FloatFlow.floats(child) || !FloatFlow.isolates(child) && containsFloats(child)) {
                result = true;
                break;
            }
        }
        LayoutMeasureCache.putObject(FLOAT_PROBE, owner, 0, 0, false, result);
        return result;
    }

    private static FloatInput floatInput(Element element) {
        var active = FLOAT_INPUTS.get();
        FloatInput input = active.get(element);
        if (input != null) return input;
        if (Size.isNaturalMeasurementContext()) return NO_FLOATS;
        Element parent = element.parentElement;
        if (parent == null || active.containsKey(parent)) return NO_FLOATS;
        if (!containsFloats(parent) && !hasFloatExclusions(parent)) return NO_FLOATS;
        return getOrComputeFlowLayout(parent, resolveLineLimit(parent)).floatInputs().getOrDefault(element, NO_FLOATS);
    }

    public static Position computeStaticBlockPosition(Element element) {
        Element parent = element.parentElement;
        if (parent == null) return Position.ZERO;
        Box box = Box.of(parent);
        FlowState state = obtainState(box.innerSize().width(), null);
        state.floats.addAll(floatInput(parent).exclusions());
        state.floatAware = !state.floats.isEmpty() || containsFloats(parent);
        try {
            List<Node> nodes = parent.getRenderChildNodes();
            for (int i = 0; i < nodes.size() && nodes.get(i) != element; i++) layoutChild(parent, nodes, i, state);
            if (state.cursorX > 0 || state.lineHeight > 0) commitLineBreak(state);
            return new Position(box.offset("left"), box.offset("top") + state.cursorY);
        } finally {
            releaseState(state);
        }
    }

    public static List<TextRunLayout> computeTextRuns(Element element) {
        return getOrComputeFlowLayout(element, resolveLineLimit(element)).textRuns();
    }

    private static FlowResult getOrComputeFlowLayout(Element element, double lineLimit) {
        boolean natural = Size.isNaturalMeasurementContext();
        FloatInput input = floatInput(element);
        boolean cacheable = input.exclusions().isEmpty();
        FlowResult cached = cacheable ? (FlowResult) LayoutMeasureCache.getObject(
                LayoutMeasureCache.LAYOUT_NORMAL_FLOW, element, lineLimit, Double.NaN, natural) : null;
        if (cached != null) return cached;
        LayoutMeasureCache.recordMeasurement();
        FlowResult result = computeFlow(element, element.getRenderChildNodes(), lineLimit, null);
        if (cacheable) LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_NORMAL_FLOW,
                element, lineLimit, Double.NaN, natural, result);
        return result;
    }

    public static boolean isInlineTextPaintedByAncestor(Element element) {
        if (element == null) return false;
        Element current = element;
        while (current != null) {
            Element parent = current.parentElement;
            if (parent == null) return false;

            Style currentStyle = current.getComputedStyle();
            if (!"inline".equals(normalizeDisplay(currentStyle.display))) return false;
            if (isReplacedLikeElement(current)) return false;
            if (requiresIndependentInlinePaint(current)) return false;

            Style parentStyle = parent.getComputedStyle();
            if (Layout.isFlexDisplay(parentStyle.display) || Layout.isGridDisplay(parentStyle.display)) {
                return false;
            }

            // The ancestor's inline formatting context recursively flattens
            // style-only wrappers. This includes wrappers whose text lives in
            // a nested inline child, for example <u><strong>text</strong></u>.
            // Letting such a wrapper paint itself would draw the same run once
            // here and once from the ancestor, at different local origins.
            if (shouldFragmentInlineElement(current)) return true;
            if (shouldFragmentInlineElement(parent)) return true;
            current = parent;
        }
        return false;
    }

    private static double resolveLineLimit(Element element) {
        if (Size.isMinContentMeasurementContext()) return 0.000001;
        Style style = element.getComputedStyle();
        Box box = Box.of(element);
        double horizontalBox = box.getBorderHorizontal() + box.getPaddingHorizontal();
        boolean natural = Size.isNaturalMeasurementContext();
        Size used = natural || "inline".equals(style.display) ? null : element.getLayout().size.get();
        if (used != null) return Math.max(0, used.width() - horizontalBox);
        double parentWidth = Size.getScaleWidth(element);
        Double avoidanceWidth = floatAvoidanceWidth(element);
        if (!natural && explicitAutoWidth(style, parentWidth) && avoidanceWidth != null) {
            parentWidth = Math.max(0, avoidanceWidth - box.getMarginHorizontal());
        }
        Double explicitWidth = style.widthLength().resolve(parentWidth);
        Double maxWidth = style.maxWidthLength().resolve(parentWidth);
        if (natural && explicitWidth == null && maxWidth == null && !Size.hasNaturalWidthConstraint(element)) {
            return 0;
        }
        double resolved = explicitWidth == null ? parentWidth : explicitWidth;
        if (explicitWidth != null && style.isBorderBox()) resolved -= horizontalBox;
        if (maxWidth != null) {
            double maxContent = Math.max(0, maxWidth - (style.isBorderBox() ? horizontalBox : 0));
            resolved = Math.min(resolved, maxContent);
        }
        Double minWidth = style.minWidthLength().resolve(parentWidth);
        if (minWidth != null) {
            resolved = Math.max(resolved, minWidth - (style.isBorderBox() ? horizontalBox : 0));
        }
        return Math.max(0, resolved);
    }

    private static boolean explicitAutoWidth(Style style, double width) {
        return style.widthLength().resolve(width) == null;
    }

    private static FlowState obtainState(double lineLimit, Element target) {
        FlowState state = FLOW_STATE_POOL.get().poll();
        if (state == null) return new FlowState(lineLimit, target);
        state.reset(lineLimit, target);
        return state;
    }

    private static void releaseState(FlowState state) {
        state.childPositions.clear();
        state.lineAtomicAscents.clear();
        state.lineTextRunAscents.clear();
        state.textRuns.clear();
        state.lineChildren.clear();
        state.floats.clear();
        state.floatInputs.clear();
        state.lineAlignmentOwner = null;
        java.util.ArrayDeque<FlowState> pool = FLOW_STATE_POOL.get();
        if (pool.size() < 16) pool.push(state);
    }

    private static FlowResult computeFlow(Element owner, List<Node> children, double lineLimit, Element target) {
        FlowState state = obtainState(lineLimit, target);
        var active = FLOAT_INPUTS.get();
        FloatInput input = floatInput(owner);
        FloatInput previous = active.put(owner, input);
        state.floats.addAll(input.exclusions());
        state.floatAware = !state.floats.isEmpty() || containsFloats(owner);
        if (state.floatAware && lineLimit > 0 && !Size.isNaturalMeasurementContext()) {
            state.lineAlignmentOwner = owner;
        }
        try {
            if (children.isEmpty() && owner.innerText != null && !owner.innerText.isEmpty() && state.floatAware) {
                placeInlineText(owner, owner.innerText, state);
            } else {
                layoutChildren(owner, children, state);
            }

            if (!state.foundTarget) {
                state.targetX = 0;
                state.targetY = state.cursorY;
            }

            double contentWidth = Math.max(state.maxLineWidth, state.cursorX);
            double contentHeight = state.cursorY + state.lineHeight;
            alignFloatLine(state);
            List<FloatFlow.Exclusion> local = List.copyOf(state.floats.subList(input.exclusions().size(), state.floats.size()));
            if (!local.isEmpty() && FloatFlow.isolates(owner)) {
                for (var box : local) contentHeight = Math.max(contentHeight, box.bottom());
            }
            return new FlowResult(
                    new FlowMetrics(state.targetX, state.targetY, new Size(contentWidth, contentHeight)),
                    new IdentityHashMap<>(state.childPositions),
                    List.copyOf(state.textRuns), new IdentityHashMap<>(state.floatInputs), local
            );
        } finally {
            if (previous == null) active.remove(owner);
            else active.put(owner, previous);
            releaseState(state);
        }
    }

    private static void layoutChildren(Element owner, List<Node> children, FlowState state) {
        // 索引循环：flow 布局逐帧高频，for-each 迭代器分配 JFR 归因约 8MB。
        for (int i = 0; i < children.size(); i++) {
            if (state.foundTarget) return;
            layoutChild(owner, children, i, state);
        }
    }

    private static void layoutChild(Element owner, List<Node> children, int index, FlowState state) {
        Node child = children.get(index);
        if (child instanceof TextNode textNode) {
            layoutTextChild(owner, children, index, textNode, state);
            return;
        }
        if (!(child instanceof Element childElement)) return;

        if (isLineBreak(childElement) && Layout.isInFlow(childElement.getComputedStyle())) {
            placeLineBreak(owner, state);
            return;
        }

        Style style = childElement.getComputedStyle();
        if (!Layout.isInFlow(style)) return;
        if (FloatFlow.floats(childElement)) {
            placeFloat(childElement, state);
            return;
        }

        if (isInlineLevel(style.display)) {
            if (!requiresIndependentInlinePaint(childElement) && shouldFragmentInlineElement(childElement)) {
                if (state.floatAware) {
                    prepareLine(state, 0, 1);
                    state.lineChildren.add(childElement);
                }
                state.childPositions.put(childElement, new Position(state.cursorX, state.cursorY));
                if (state.target != null && childElement == state.target) {
                    state.targetX = state.cursorX;
                    state.targetY = state.cursorY;
                    state.foundTarget = true;
                    return;
                }
                layoutInlineContent(childElement, state);
                state.previousFlowWasBlock = false;
                return;
            }
            placeAtomicInline(owner, childElement, state);
            return;
        }

        placeBlock(childElement, state);
    }

    private static void placeTextRun(Element owner, TextNode node, FlowState state) {
        if (state.floatAware && !Size.isMinContentMeasurementContext() && state.lineLimit > 0) {
            placeTextAroundFloats(owner, node, node.getTextContent(), state);
            return;
        }
        TextRunLayout run = layoutTextRun(owner, node, node.getTextContent(), state.lineLimit, state.cursorX, state.cursorY);
        placeTextRun(run, owner, node, node.getTextContent(), state);
    }

    /**
     * 文本节点入场前的 white-space 边界处理（CSS 2.1 §16.6.1，white-space 折叠模式）：
     * 纯空白文本节点塌缩成单个空格，但只有在"当前行已有内容、且同一内联格式化
     * 上下文中后面还跟着行内内容"时才真正占位——行首/行尾（块级边界、容器
     * 边缘、<br> 前）的空白会被移除。行首空格本身由 layoutTextRun 剥离兜底。
     * pre 系模式保留原始空白，直接放置。
     */
    private static void layoutTextChild(Element owner, List<Node> siblings, int index, TextNode node, FlowState state) {
        String whiteSpace = Text.getWhiteSpace(owner);
        if (collapsesWhiteSpace(whiteSpace)) {
            String normalized = normalizeInlineTextFragment(node.getTextContent(), whiteSpace);
            if (normalized.isEmpty()) return;
            if (normalized.isBlank() && !hasFollowingInlineContent(siblings, index + 1, whiteSpace)) return;
        }
        placeTextRun(owner, node, state);
    }

    private static boolean collapsesWhiteSpace(String whiteSpace) {
        String value = whiteSpace == null ? "normal" : whiteSpace.trim().toLowerCase(java.util.Locale.ROOT);
        return !"pre".equals(value) && !"pre-wrap".equals(value) && !"break-spaces".equals(value);
    }

    /**
     * 同一容器后续兄弟中是否还有会在本行产生内容的行内级节点（决定行尾空白是否保留）。
     */
    private static boolean hasFollowingInlineContent(List<Node> siblings, int fromIndex, String whiteSpace) {
        for (int i = fromIndex; i < siblings.size(); i++) {
            Node next = siblings.get(i);
            if (next instanceof TextNode textNode) {
                String normalized = normalizeInlineTextFragment(textNode.getTextContent(), whiteSpace);
                // 纯空白节点自身也可能被边界规则移除，不构成本行内容，继续向后看。
                if (normalized.isEmpty() || normalized.isBlank()) continue;
                return true;
            }
            if (!(next instanceof Element nextElement)) continue;
            Style nextStyle = nextElement.getComputedStyle();
            if (!Layout.isInFlow(nextStyle) || FloatFlow.floats(nextElement)) continue;
            if (isLineBreak(nextElement)) return false;
            return isInlineLevel(nextStyle.display);
        }
        return false;
    }

    /**
     * <br> 的换行：当前行有内容时提交断行；连续 <br>（空行）也要推进至少一行高度，避免折叠。
     */
    private static void placeLineBreak(Element owner, FlowState state) {
        if (state.cursorX > 0 || state.lineHeight > 0) {
            commitLineBreak(state);
        } else if (state.cursorY > 0 || !state.textRuns.isEmpty()) {
            double emptyLineHeight = owner == null ? 0 : Text.of(owner).lineHeight;
            state.cursorY += Math.max(0, emptyLineHeight);
        }
        state.previousFlowWasBlock = false;
    }

    private static boolean isLineBreak(Element element) {
        return element != null && "BR".equals(element.tagName);
    }

    private static void placeInlineText(Element owner, String content, FlowState state) {
        if (state.floatAware && !Size.isMinContentMeasurementContext() && state.lineLimit > 0) {
            placeTextAroundFloats(owner, null, content, state);
            return;
        }
        TextRunLayout run = layoutTextRun(owner, null, content, state.lineLimit, state.cursorX, state.cursorY);
        placeTextRun(run, owner, null, content, state);
    }

    private static void placeTextRun(TextRunLayout run, Element owner, TextNode node, String content, FlowState state) {
        if (run == null) return;
        if (run.startedOnNewLine() && (state.cursorX > 0 || state.lineHeight > 0)) {
            commitLineBreak(state);
            run = layoutTextRun(owner, node, content, state.lineLimit, state.cursorX, state.cursorY);
            if (run == null) return;
        }

        // Text runs on the same line share a painted baseline (the paint
        // backends anchor at Text.renderedBaselineOffset), so each run
        // participates in the line's ascent the same way atomic inlines do.
        double runAscent = Text.renderedBaselineOffset(run.text());
        double runDescent = Math.max(0, run.text().lineHeight - runAscent);
        includeBaselineMetrics(state, runAscent, runDescent);
        run = run.withY(state.cursorY + Math.max(0, state.lineAscent - runAscent));
        state.textRuns.add(run);
        state.lineTextRunAscents.put(run, runAscent);
        if (run.lineCount() > 1) {
            state.maxLineWidth = Math.max(state.maxLineWidth, run.maxWidth());
            state.cursorY = run.y() + (run.lineCount() - 1) * run.text().lineHeight;
            state.cursorX = run.lastLineWidth();
            state.lineHeight = Math.max(state.lineHeight, run.text().lineHeight);
            // The continuation line starts with fresh baseline context; entries
            // from the run's first line must not be re-shifted against the new
            // cursorY.
            state.lineAscent = 0;
            state.lineDescent = 0;
            state.lineStrutIncluded = false;
            state.lineAtomicAscents.clear();
            state.lineTextRunAscents.clear();
        } else {
            state.cursorX += run.lastLineWidth();
            state.lineHeight = Math.max(state.lineHeight, run.text().lineHeight);
        }
        state.previousFlowWasBlock = false;
    }

    private static void layoutInlineContent(Element element, FlowState state) {
        if (element == null) return;
        if (element instanceof Translation translation) {
            placeInlineText(element, translation.getTranslatedText(), state);
            return;
        }
        if (element.getRenderChildNodes().isEmpty()) {
            placeInlineText(element, element.innerText, state);
            return;
        }
        layoutChildren(element, element.getRenderChildNodes(), state);
    }

    private static void placeAtomicInline(Element owner, Element childElement, FlowState state) {
        Box childBox = Box.of(childElement);
        Size size;
        if (Size.isNaturalMeasurementContext()) {
            Size natural = Size.natural(childElement);
            size = new Size(
                    natural.width() + childBox.getMarginHorizontal(),
                    natural.height() + childBox.getMarginVertical()
            );
        } else {
            size = childBox.size();
        }
        if (state.floatAware && state.lineLimit > 0) {
            prepareLine(state, size.width(), Math.max(0.000001, size.height()));
            state.lineChildren.add(childElement);
        } else if (state.lineLimit > 0 && state.cursorX > 0 && state.cursorX + size.width() > state.lineLimit) {
            commitLineBreak(state);
        }

        double childY = state.cursorY;
        if (usesBaselineAlignment(childElement)) {
            includeLineStrut(owner, state);
            double childAscent = atomicInlineBaseline(childElement, childBox, size);
            includeBaselineMetrics(state, childAscent, Math.max(0, size.height() - childAscent));
            childY += Math.max(0, state.lineAscent - childAscent);
            state.lineAtomicAscents.put(childElement, childAscent);
        }

        state.childPositions.put(childElement, new Position(state.cursorX, childY));
        if (state.target != null && childElement == state.target) {
            state.targetX = state.cursorX;
            state.targetY = childY;
            state.foundTarget = true;
            return;
        }

        state.cursorX += size.width();
        state.lineHeight = Math.max(state.lineHeight, size.height());
        state.previousFlowWasBlock = false;
    }

    private static boolean usesBaselineAlignment(Element element) {
        if (element == null) return false;
        String value = element.getComputedStyle().verticalAlign;
        return value == null || value.isBlank() || "unset".equalsIgnoreCase(value)
                || "baseline".equalsIgnoreCase(value);
    }

    private static void includeLineStrut(Element owner, FlowState state) {
        if (owner == null || state.lineStrutIncluded) return;
        Text text = Text.of(owner);
        double ascent = Text.baselineOffset(text);
        includeBaselineMetrics(state, ascent, Math.max(0, text.lineHeight - ascent));
        state.lineStrutIncluded = true;
    }

    private static void includeBaselineMetrics(FlowState state, double ascent, double descent) {
        double previousAscent = state.lineAscent;
        state.lineAscent = Math.max(state.lineAscent, Math.max(0, ascent));
        state.lineDescent = Math.max(state.lineDescent, Math.max(0, descent));
        state.lineHeight = Math.max(state.lineHeight, state.lineAscent + state.lineDescent);
        if (state.lineAscent <= previousAscent) return;
        for (var entry : state.lineAtomicAscents.entrySet()) {
            Position existing = state.childPositions.get(entry.getKey());
            if (existing == null) continue;
            state.childPositions.put(entry.getKey(), new Position(
                    existing.x,
                    state.cursorY + state.lineAscent - entry.getValue()
            ));
        }
        if (state.lineTextRunAscents.isEmpty()) return;
        // Records are immutable: rebuild the already-placed runs of this line
        // with their baseline re-aligned to the grown line ascent.
        var shiftedRuns = new IdentityHashMap<TextRunLayout, Double>();
        for (var entry : state.lineTextRunAscents.entrySet()) {
            TextRunLayout shifted = entry.getKey().withY(state.cursorY + Math.max(0, state.lineAscent - entry.getValue()));
            int index = indexOfIdentity(state.textRuns, entry.getKey());
            if (index >= 0) state.textRuns.set(index, shifted);
            shiftedRuns.put(shifted, entry.getValue());
        }
        state.lineTextRunAscents.clear();
        state.lineTextRunAscents.putAll(shiftedRuns);
    }

    private static int indexOfIdentity(List<TextRunLayout> runs, TextRunLayout target) {
        for (int i = 0; i < runs.size(); i++) {
            if (runs.get(i) == target) return i;
        }
        return -1;
    }

    private static double atomicInlineBaseline(Element element, Box box, Size outerSize) {
        boolean textControl = isTextBaselineControl(element);
        if (isReplacedLikeElement(element) && !textControl) {
            return Math.max(0, outerSize.height());
        }
        Text text = Text.of(element);
        if (textControl || text.content != null && !text.content.isBlank()) {
            double contentHeight = Math.max(0,
                    outerSize.height() - box.getMarginVertical() - box.getBorderVertical() - box.getPaddingVertical());
            double crossOffset = 0;
            // A button centers its text within a taller minimum-height box;
            // anchoring its baseline at the top adds spurious descent to the parent line.
            if ((textControl && "BUTTON".equalsIgnoreCase(element.tagName))
                    || (Layout.isFlexDisplay(element.getComputedStyle().display)
                    && Flex.of(element).flexDirection.contains("row")
                    && Flex.of(element).alignItems.is("center"))) {
                crossOffset = Math.max(0, (contentHeight - text.lineHeight) / 2.0d);
            }
            double baseline = box.getMarginTop() + box.getBorderTop() + box.getPaddingTop()
                    + crossOffset + Text.baselineOffset(text);
            return Math.max(0, Math.min(outerSize.height(), baseline));
        }
        return Math.max(0, outerSize.height());
    }

    private static boolean isTextBaselineControl(Element element) {
        if (element == null || element.tagName == null) return false;
        return switch (element.tagName.trim().toUpperCase()) {
            case "BUTTON", "INPUT", "SELECT", "TEXTAREA" -> true;
            default -> false;
        };
    }

    private static void prepareLine(FlowState state, double minimum, double height) {
        for (;;) {
            var span = FloatFlow.span(state.floats, state.cursorY, Math.max(height, state.lineHeight), state.lineLimit);
            double used = state.cursorX - state.lineStart;
            if (span.left() + used + minimum <= span.right() + 0.000001) {
                shiftLine(state, span.left());
                return;
            }
            if (state.lineHeight > 0 || used > 0) {
                commitLineBreak(state);
            } else if (Double.isFinite(span.nextY())) {
                state.cursorY = span.nextY();
            } else {
                shiftLine(state, span.left());
                return;
            }
        }
    }

    private static void shiftLine(FlowState state, double left) {
        double delta = left - state.lineStart;
        if (delta == 0) return;
        state.lineStart = left;
        state.cursorX += delta;
        for (Element child : state.lineChildren) {
            Position position = state.childPositions.get(child);
            if (position != null) state.childPositions.put(child, new Position(position.x + delta, position.y));
        }
        for (int i = state.firstLineRun; i < state.textRuns.size(); i++) {
            TextRunLayout run = state.textRuns.get(i);
            TextRunLayout shifted = run.withX(run.x() + delta);
            state.textRuns.set(i, shifted);
            Double ascent = state.lineTextRunAscents.remove(run);
            if (ascent != null) state.lineTextRunAscents.put(shifted, ascent);
        }
    }

    private static void placeTextAroundFloats(Element owner, TextNode node, String raw, FlowState state) {
        Element textOwner = node != null && node.parentNode instanceof Element parent ? parent : owner;
        Text text = new Text();
        Text base = Text.of(textOwner);
        TextMetrics.copyTextForRun(base, text);
        text.color = base.color;
        text.strokeColor = base.strokeColor;
        text.content = com.sighs.apricityui.dom.TextTransform.apply(normalizeInlineTextFragment(raw, text.whiteSpace), textOwner);
        if (text.content == null || text.content.isEmpty()) return;
        boolean collapse = collapsesWhiteSpace(text.whiteSpace);
        double height = Math.max(0.000001, text.lineHeight);
        String content = text.content;
        int start = 0;
        while (start < content.length()) {
            if (collapse && state.cursorX <= state.lineStart && content.charAt(start) == ' ') {
                start++;
                continue;
            }
            prepareLine(state, Text.minimumFirstLineWidth(text, content, start), height);
            if (collapse && state.cursorX <= state.lineStart && content.charAt(start) == ' ') {
                start++;
                continue;
            }
            var span = FloatFlow.span(state.floats, state.cursorY, Math.max(height, state.lineHeight), state.lineLimit);
            Text.LineBreak line = Text.firstLine(text, content, start, Math.max(0.000001, span.right() - state.cursorX));
            String lineText = content.substring(start, line.end());
            if (collapse && line.next() < content.length()) lineText = lineText.stripTrailing();
            double width = Text.measureLine(text, lineText);
            double ascent = Text.renderedBaselineOffset(text);
            includeBaselineMetrics(state, ascent, Math.max(0, text.lineHeight - ascent));
            TextRunLayout run = new TextRunLayout(node, textOwner, text, state.cursorX,
                    state.cursorY + Math.max(0, state.lineAscent - ascent), List.of(lineText), width, width, false, start);
            state.textRuns.add(run);
            state.lineTextRunAscents.put(run, ascent);
            state.cursorX += width;
            state.lineHeight = Math.max(state.lineHeight, height);
            state.previousFlowWasBlock = false;
            start = line.next();
            if (start < content.length()) commitLineBreak(state);
        }
    }

    private static void placeFloat(Element element, FlowState state) {
        Box box = Box.of(element);
        Size size = Size.isNaturalMeasurementContext()
                ? Size.natural(element).add(new Size(box.getMarginHorizontal(), box.getMarginVertical())) : box.size();
        boolean left = "left".equals(element.getComputedStyle().cssFloat);
        double y = clearY(state.floats, element.getComputedStyle().clear,
                Math.max(state.previousBlockTop, state.cursorY - (state.previousFlowWasBlock ? state.previousBlockMarginBottom : 0)));
        for (var earlier : state.floats) y = Math.max(y, earlier.top());
        double width = state.lineLimit > 0 ? state.lineLimit : size.width();
        double x;
        for (;;) {
            var span = FloatFlow.span(state.floats, y, Math.max(0.000001, size.height()), width);
            double used = y == state.cursorY ? state.cursorX - state.lineStart : 0;
            if (size.width() + used <= span.right() - span.left() + 0.000001
                    || !Double.isFinite(span.nextY()) && used == 0) {
                x = left ? span.left() : span.right() - size.width();
                break;
            }
            double nextLine = used > 0 ? y + Math.max(0.000001, state.lineHeight) : Double.POSITIVE_INFINITY;
            y = Math.min(nextLine, span.nextY());
        }
        state.floats.add(new FloatFlow.Exclusion(left, x, x + size.width(), y, y + size.height()));
        state.childPositions.put(element, new Position(x, y));
        state.maxLineWidth = Math.max(state.maxLineWidth, x + size.width());
        if (y == state.cursorY) {
            shiftLine(state, FloatFlow.span(state.floats, y, Math.max(0.000001, state.lineHeight), width).left());
        }
    }

    private static double clearY(List<FloatFlow.Exclusion> floats, String clear, double top) {
        if ("none".equals(clear)) return top;
        for (var box : floats) {
            if ("both".equals(clear) || (box.leftSide() ? "left".equals(clear) : "right".equals(clear))) {
                top = Math.max(top, box.bottom());
            }
        }
        return top;
    }

    private static void placeBlock(Element childElement, FlowState state) {
        if (state.cursorX > state.lineStart || state.lineHeight > 0) {
            commitLineBreak(state);
            state.previousFlowWasBlock = false;
        } else {
            shiftLine(state, 0);
        }

        Box childBox = Box.of(childElement);
        double blockY = state.cursorY;
        if (state.previousFlowWasBlock) {
            double collapsed = collapseAdjacentMargins(state.previousBlockMarginBottom, childBox.getMarginTop());
            blockY += collapsed - state.previousBlockMarginBottom - childBox.getMarginTop();
        }
        double borderTop = blockY + childBox.getMarginTop();
        blockY += clearY(state.floats, childElement.getComputedStyle().clear, borderTop) - borderTop;

        boolean isolated = FloatFlow.isolates(childElement);
        double baseX = 0;
        double available = state.lineLimit;
        FloatInput input = NO_FLOATS;
        if (state.floatAware && !state.floats.isEmpty() && state.lineLimit > 0) {
            if (isolated) {
                Size minimum = Size.unconstrainedNatural(childElement, true);
                double needed = minimum.width() + childBox.getMarginHorizontal();
                for (;;) {
                    var span = FloatFlow.span(state.floats, blockY + childBox.getMarginTop(),
                            Math.max(0.000001, minimum.height()), state.lineLimit);
                    if (needed <= span.right() - span.left() || !Double.isFinite(span.nextY())) {
                        baseX = span.left();
                        available = Math.max(0, span.right() - baseX);
                        if (Double.isFinite(span.nextY())) input = new FloatInput(List.of(), available);
                        break;
                    }
                    blockY = span.nextY() - childBox.getMarginTop();
                }
            } else {
                double dx = childBox.getMarginLeft() + childBox.offset("left");
                double dy = blockY + childBox.getMarginTop() + childBox.offset("top");
                input = new FloatInput(translateFloats(state.floats, -dx, -dy), null);
            }
        }
        var active = FLOAT_INPUTS.get();
        FloatInput previous = active.put(childElement, input);
        if (input != NO_FLOATS) state.floatInputs.put(childElement, input);
        Size blockSize;
        try {
            if (Size.isNaturalMeasurementContext()) {
                Size naturalSize = measureNaturalBlockSize(childElement, childBox, state.lineLimit);
                blockSize = new Size(
                        naturalSize.width() + childBox.getMarginHorizontal(),
                        naturalSize.height() + childBox.getMarginVertical()
                );
            } else {
                blockSize = childBox.size();
            }
            if (!isolated && state.floatAware && containsFloats(childElement)) {
                double width = Math.max(0.000001, blockSize.width() - childBox.getMarginHorizontal()
                        - childBox.getBorderHorizontal() - childBox.getPaddingHorizontal());
                var result = getOrComputeFlowLayout(childElement, width);
                double dx = childBox.getMarginLeft() + childBox.offset("left");
                double dy = blockY + childBox.getMarginTop() + childBox.offset("top");
                state.floats.addAll(translateFloats(result.localFloats(), dx, dy));
            }
        } finally {
            if (previous == null) active.remove(childElement);
            else active.put(childElement, previous);
        }
        boolean hasHorizontalAutoMargin = childBox.isMarginAuto("left") || childBox.isMarginAuto("right");
        HorizontalBlockMargins horizontalMargins = hasHorizontalAutoMargin
                ? resolveHorizontalBlockMargins(childBox, available)
                : new HorizontalBlockMargins(childBox.getMarginLeft(), childBox.getMarginRight());
        double blockOuterWidth = hasHorizontalAutoMargin
                ? horizontalMargins.left() + childBox.elementSize().width() + horizontalMargins.right()
                : blockSize.width();
        state.childPositions.put(childElement, new Position(baseX + horizontalMargins.left() - childBox.getMarginLeft(), blockY));
        if (state.target != null && childElement == state.target) {
            state.targetX = baseX + horizontalMargins.left() - childBox.getMarginLeft();
            state.targetY = blockY;
            state.foundTarget = true;
            return;
        }

        state.cursorY = blockY + blockSize.height();
        state.maxLineWidth = Math.max(state.maxLineWidth, baseX + blockOuterWidth);
        state.previousBlockTop = blockY;
        state.previousBlockMarginBottom = childBox.getMarginBottom();
        state.previousFlowWasBlock = true;
    }

    private static List<FloatFlow.Exclusion> translateFloats(List<FloatFlow.Exclusion> boxes, double x, double y) {
        return boxes.stream().map(box -> box.translate(x, y)).toList();
    }

    private static Size measureNaturalBlockSize(Element childElement, Box childBox, double containingBlockWidth) {
        if (containingBlockWidth <= 0 || !Size.hasNaturalWidthConstraint(childElement)) {
            return Size.natural(childElement);
        }

        double availableOuterWidth = Math.max(0, containingBlockWidth - childBox.getMarginHorizontal());
        double availableContentWidth = Math.max(0, availableOuterWidth
                - childBox.getBorderHorizontal()
                - childBox.getPaddingHorizontal());
        return Size.naturalAtContentWidth(childElement, availableContentWidth);
    }

    private static HorizontalBlockMargins resolveHorizontalBlockMargins(Box childBox, double containingBlockWidth) {
        if (childBox == null) return new HorizontalBlockMargins(0, 0);

        double left = childBox.getMarginLeft();
        double right = childBox.getMarginRight();
        boolean leftAuto = childBox.isMarginAuto("left");
        boolean rightAuto = childBox.isMarginAuto("right");
        if (!leftAuto && !rightAuto) return new HorizontalBlockMargins(left, right);
        if (containingBlockWidth <= 0) return new HorizontalBlockMargins(left, right);

        double remaining = containingBlockWidth
                - childBox.elementSize().width()
                - (leftAuto ? 0 : left)
                - (rightAuto ? 0 : right);
        remaining = Math.max(0, remaining);

        if (leftAuto && rightAuto) {
            double split = remaining / 2.0;
            return new HorizontalBlockMargins(split, split);
        }
        if (leftAuto) {
            return new HorizontalBlockMargins(remaining, right);
        }
        return new HorizontalBlockMargins(left, remaining);
    }

    private static void alignFloatLine(FlowState state) {
        if (state.lineAlignmentOwner == null || state.lineHeight <= 0) return;
        var span = FloatFlow.span(state.floats, state.cursorY, state.lineHeight, state.lineLimit);
        Text text = Text.of(state.lineAlignmentOwner);
        double dx = TextMetrics.computeAlignedX(text, span.right() - span.left(), state.cursorX - state.lineStart, false);
        shiftLine(state, state.lineStart + dx);
    }

    private static void commitLineBreak(FlowState state) {
        state.maxLineWidth = Math.max(state.maxLineWidth, state.cursorX);
        alignFloatLine(state);
        state.cursorY += state.lineHeight;
        state.cursorX = 0;
        state.lineStart = 0;
        state.firstLineRun = state.textRuns.size();
        state.lineChildren.clear();
        state.lineHeight = 0;
        state.lineAscent = 0;
        state.lineDescent = 0;
        state.lineStrutIncluded = false;
        state.lineAtomicAscents.clear();
        state.lineTextRunAscents.clear();
    }

    private static double collapseAdjacentMargins(double previousBottom, double currentTop) {
        if (previousBottom >= 0 && currentTop >= 0) {
            return Math.max(previousBottom, currentTop);
        }
        if (previousBottom <= 0 && currentTop <= 0) {
            return Math.min(previousBottom, currentTop);
        }
        return Math.max(previousBottom, currentTop) + Math.min(previousBottom, currentTop);
    }

    private static TextRunLayout layoutTextRun(Element owner, TextNode node, String content, double lineLimit, double cursorX, double cursorY) {
        if (owner == null) return null;
        Element textOwner = node != null && node.parentNode instanceof Element parent ? parent : owner;
        Text base = Text.of(textOwner);
        Text text = new Text();
        TextMetrics.copyTextForRun(base, text);
        text.color = base.color;
        text.strokeColor = base.strokeColor;
        text.content = com.sighs.apricityui.dom.TextTransform.apply(
                normalizeInlineTextFragment(content, text.whiteSpace), textOwner);
        if (text.content == null || text.content.isEmpty()) return null;
        // CSS：折叠模式下位于行首（cursorX<=0）的空格被移除；折叠后至多一个前导空格。
        if (cursorX <= 0 && collapsesWhiteSpace(text.whiteSpace) && text.content.startsWith(" ")) {
            text.content = text.content.substring(1);
            if (text.content.isEmpty()) return null;
        }

        boolean startOnNewLine = false;
        if (Text.allowsSoftWrap(text.whiteSpace)
                && lineLimit > 0 && cursorX > 0
                && Text.measureText(text) > Math.max(0, lineLimit - cursorX)) {
            startOnNewLine = true;
            cursorX = 0;
        }

        Text.WrappedText wrapped = Text.wrap(text, lineLimit > 0 ? lineLimit : 0);
        List<String> lines = wrapped.lines();
        if (lines.isEmpty()) return null;
        double lastLineWidth = Text.measureLine(text, lines.get(lines.size() - 1));
        return new TextRunLayout(node, textOwner, text, cursorX, cursorY, List.copyOf(lines), wrapped.width(), lastLineWidth, startOnNewLine);
    }

    private static String normalizeInlineTextFragment(String content, String whiteSpace) {
        if (content == null || content.isEmpty()) return "";
        String value = whiteSpace == null ? "normal" : whiteSpace;
        return switch (value) {
            case "pre", "pre-wrap", "break-spaces" -> content.replace("\r\n", "\n").replace('\r', '\n');
            case "pre-line" -> collapseInlineSpacesPreserveNewlines(content);
            case "nowrap", "normal" -> collapseInlineSpaces(content);
            default -> collapseInlineSpaces(content);
        };
    }

    private static String collapseInlineSpaces(String content) {
        // 原实现 replace×2 + replaceAll 正则每次调用重新编译 Pattern
        // （int[] NFA 数组，JFR 归因约 58MB）。快路径：无需折叠时原样返回；
        // 否则单遍手工折叠，语义与 "\r\n/\r→\n→空格，[\t\x0B\f ]+→单空格" 完全一致。
        int len = content.length();
        boolean simple = true;
        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t' || c == '\u000B' || c == '\f') {
                simple = false;
                break;
            }
            if (c == ' ' && i + 1 < len) {
                char next = content.charAt(i + 1);
                if (next == ' ' || next == '\t' || next == '\u000B' || next == '\f'
                        || next == '\r' || next == '\n') {
                    simple = false;
                    break;
                }
            }
        }
        if (simple) return content;
        StringBuilder sb = new StringBuilder(len);
        boolean pendingSpace = false;
        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (c == '\r') {
                if (i + 1 < len && content.charAt(i + 1) == '\n') i++;
                c = ' ';
            } else if (c == '\n') {
                c = ' ';
            }
            if (c == ' ' || c == '\t' || c == '\u000B' || c == '\f') {
                if (pendingSpace) continue;
                pendingSpace = true;
                sb.append(' ');
            } else {
                pendingSpace = false;
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String collapseInlineSpacesPreserveNewlines(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) builder.append('\n');
            builder.append(lines[i].replaceAll("[\\t\\x0B\\f ]+", " "));
        }
        return builder.toString();
    }

    private static boolean isInlineLevel(String display) {
        String value = normalizeDisplay(display);
        return "inline".equals(value) || "inline-block".equals(value) || "inline-flex".equals(value) || "inline-grid".equals(value);
    }

    private static String normalizeDisplay(String display) {
        return display == null ? "" : display.trim().toLowerCase();
    }

    private static boolean shouldFragmentInlineElement(Element element) {
        if (element == null) return false;
        Style style = element.getComputedStyle();
        String display = normalizeDisplay(style.display);
        if (!"inline".equals(display)) return false;
        if (isReplacedLikeElement(element)) return false;
        return containsFragmentableInlineContent(element);
    }

    private static boolean containsFragmentableInlineContent(Element element) {
        if (hasRenderableInlineText(element)) {
            return true;
        }
        for (Node child : element.getRenderChildNodes()) {
            if (child instanceof TextNode textNode) {
                if (!normalizeInlineTextFragment(textNode.getTextContent(), Text.getWhiteSpace(element)).isEmpty()) {
                    return true;
                }
                continue;
            }
            if (!(child instanceof Element childElement)) continue;
            Style childStyle = childElement.getComputedStyle();
            if (!Layout.isInFlow(childStyle)) continue;
            if (!isInlineLevel(childStyle.display)) return false;
            if (isAtomicInlineElement(childElement)) return false;
            if (containsFragmentableInlineContent(childElement)) return true;
        }
        return false;
    }

    private static boolean hasRenderableInlineText(Element element) {
        if (element == null) return false;
        if (element.innerText != null && !normalizeInlineTextFragment(element.innerText, Text.getWhiteSpace(element)).isEmpty()) {
            return true;
        }
        for (Node child : element.getRenderChildNodes()) {
            if (child instanceof TextNode textNode
                    && !normalizeInlineTextFragment(textNode.getTextContent(), Text.getWhiteSpace(element)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAtomicInlineElement(Element element) {
        if (element == null) return true;
        Style style = element.getComputedStyle();
        String display = normalizeDisplay(style.display);
        if (!"inline".equals(display)) return true;
        if (isReplacedLikeElement(element)) return true;
        for (Node child : element.getRenderChildNodes()) {
            if (child instanceof TextNode) continue;
            if (!(child instanceof Element childElement)) continue;
            Style childStyle = childElement.getComputedStyle();
            if (!Layout.isInFlow(childStyle)) continue;
            if (!isInlineLevel(childStyle.display)) return true;
            if (isAtomicInlineElement(childElement)) return true;
        }
        return false;
    }

    private static boolean isReplacedLikeElement(Element element) {
        return Layout.isReplacedElement(element);
    }

    /**
     * An inline element can be folded into an ancestor text run only when it
     * contributes text styling alone. CSS box decorations belong to the
     * inline box itself; suppressing that box drops its padding, border,
     * background and hit area instead of producing the browser's inline box.
     */
    private static boolean requiresIndependentInlinePaint(Element element) {
        if (element == null) return false;
        Style style = element.getComputedStyle();
        String position = style.position == null ? "static" : style.position.trim().toLowerCase();
        String zIndex = style.zIndex == null ? "auto" : style.zIndex.trim().toLowerCase();
        if (!"static".equals(position) || !"auto".equals(zIndex)) return true;

        Box box = Box.of(element);
        if (box.getPaddingHorizontal() != 0 || box.getPaddingVertical() != 0
                || box.getBorderHorizontal() != 0 || box.getBorderVertical() != 0
                || box.getMarginHorizontal() != 0 || box.getMarginVertical() != 0
                || box.isMarginAuto("left") || box.isMarginAuto("right")
                || box.isMarginAuto("top") || box.isMarginAuto("bottom")) {
            return true;
        }

        return hasPaintValue(style.backgroundColor, "transparent")
                || hasPaintValue(style.backgroundImage, "none")
                || hasPaintValue(style.boxShadow, "none")
                || hasPaintValue(style.transform, "none")
                || hasPaintValue(style.rotate, "none")
                || hasPaintValue(style.filter, "none")
                || hasPaintValue(style.clipPath, "none")
                || !isDefaultOpacity(style.opacity);
    }

    private static boolean hasPaintValue(String raw, String emptyValue) {
        if (raw == null || raw.isBlank()) return false;
        String value = raw.trim().toLowerCase();
        return !"unset".equals(value) && !"initial".equals(value) && !emptyValue.equals(value);
    }

    private static boolean isDefaultOpacity(String raw) {
        if (raw == null || raw.isBlank() || "unset".equalsIgnoreCase(raw) || "initial".equalsIgnoreCase(raw)) {
            return true;
        }
        Double opacity = Size.parseNumber(raw);
        return opacity != null && Math.abs(opacity - 1.0d) < 0.0001d;
    }

    public record TextRunLayout(TextNode node, Element owner, Text text, double x, double y, List<String> lines,
                                double maxWidth, double lastLineWidth, boolean startedOnNewLine, int sourceStart) {
        public TextRunLayout(TextNode node, Element owner, Text text, double x, double y, List<String> lines,
                             double maxWidth, double lastLineWidth, boolean startedOnNewLine) {
            this(node, owner, text, x, y, lines, maxWidth, lastLineWidth, startedOnNewLine, 0);
        }

        TextRunLayout withX(double newX) {
            return new TextRunLayout(node, owner, text, newX, y, lines, maxWidth, lastLineWidth, startedOnNewLine, sourceStart);
        }
        public int lineCount() {
            return lines == null ? 0 : lines.size();
        }

        public TextRunLayout withY(double newY) {
            return new TextRunLayout(node, owner, text, x, newY, lines, maxWidth, lastLineWidth, startedOnNewLine, sourceStart);
        }
    }

    private static final class FlowState {
        private final ArrayList<TextRunLayout> textRuns = new ArrayList<>();
        private final IdentityHashMap<Element, Position> childPositions = new IdentityHashMap<>();
        private final IdentityHashMap<Element, Double> lineAtomicAscents = new IdentityHashMap<>();
        private final IdentityHashMap<TextRunLayout, Double> lineTextRunAscents = new IdentityHashMap<>();
        private final ArrayList<Element> lineChildren = new ArrayList<>();
        private final ArrayList<FloatFlow.Exclusion> floats = new ArrayList<>();
        private final IdentityHashMap<Element, FloatInput> floatInputs = new IdentityHashMap<>();
        private boolean floatAware;
        private Element lineAlignmentOwner;
        private double lineStart;
        private int firstLineRun;
        private double previousBlockTop;
        private double lineLimit;
        private Element target;
        private double cursorX = 0;
        private double cursorY = 0;
        private double lineHeight = 0;
        private double lineAscent = 0;
        private double lineDescent = 0;
        private boolean lineStrutIncluded = false;
        private double maxLineWidth = 0;
        private double targetX = 0;
        private double targetY = 0;
        private boolean foundTarget = false;
        private double previousBlockMarginBottom = 0;
        private boolean previousFlowWasBlock = false;

        private FlowState(double lineLimit, Element target) {
            reset(lineLimit, target);
        }

        private void reset(double lineLimit, Element target) {
            this.lineLimit = Math.max(0, lineLimit);
            this.target = target;
            this.cursorX = 0;
            this.lineStart = 0;
            this.firstLineRun = 0;
            this.floatAware = false;
            this.previousBlockTop = 0;
            this.cursorY = 0;
            this.lineHeight = 0;
            this.lineAscent = 0;
            this.lineDescent = 0;
            this.lineStrutIncluded = false;
            this.maxLineWidth = 0;
            this.targetX = 0;
            this.targetY = 0;
            this.foundTarget = false;
            this.previousBlockMarginBottom = 0;
            this.previousFlowWasBlock = false;
        }
    }

    private record FlowMetrics(double targetX, double targetY, Size contentSize) {
    }

    private record FlowResult(FlowMetrics metrics, IdentityHashMap<Element, Position> childPositions,
                              List<TextRunLayout> textRuns, IdentityHashMap<Element, FloatInput> floatInputs,
                              List<FloatFlow.Exclusion> localFloats) {
    }

    private record FloatInput(List<FloatFlow.Exclusion> exclusions, Double avoidanceWidth) {
    }

    private record HorizontalBlockMargins(double left, double right) {
    }
}

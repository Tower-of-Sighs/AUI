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

    public static List<TextRunLayout> computeTextRuns(Element element) {
        return getOrComputeFlowLayout(element, resolveLineLimit(element)).textRuns();
    }

    private static FlowResult getOrComputeFlowLayout(Element element, double lineLimit) {
        boolean natural = Size.isNaturalMeasurementContext();
        FlowResult cached = (FlowResult) LayoutMeasureCache.getObject(LayoutMeasureCache.LAYOUT_NORMAL_FLOW, element, lineLimit, Double.NaN, natural);
        if (cached != null) return cached;
        FlowResult result = computeFlow(element, element.getRenderChildNodes(), lineLimit, null);
        LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_NORMAL_FLOW, element, lineLimit, Double.NaN, natural, result);
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
        Style style = element.getComputedStyle();
        if (Size.isNaturalMeasurementContext() && Size.parseNumber(style.width) == null
                && !Size.hasNaturalWidthConstraint(element)) {
            return 0;
        }
        Double explicitWidth = Size.parseNumber(style.width);
        if (explicitWidth != null) {
            double percentBasis = Size.isPercent(style.width) ? Size.getScaleWidth(element) : 0;
            double resolved = Size.resolveLength(style.width, percentBasis, explicitWidth);
            if (Box.BOX_SIZING_BORDER_BOX.equals(Box.normalizeBoxSizing(style.boxSizing))) {
                Box box = Box.of(element);
                resolved -= box.getBorderHorizontal() + box.getPaddingHorizontal();
            }
            return Math.max(0, resolved);
        }
        return Math.max(0, Size.getScaleWidth(element));
    }

    private static FlowResult computeFlow(Element owner, List<Node> children, double lineLimit, Element target) {
        FlowState state = new FlowState(lineLimit, target);
        layoutChildren(owner, children, state);

        if (!state.foundTarget) {
            state.targetX = 0;
            state.targetY = state.cursorY;
        }

        double contentWidth = Math.max(state.maxLineWidth, state.cursorX);
        double contentHeight = state.cursorY + state.lineHeight;
        return new FlowResult(
                new FlowMetrics(state.targetX, state.targetY, new Size(contentWidth, contentHeight)),
                new IdentityHashMap<>(state.childPositions),
                List.copyOf(state.textRuns)
        );
    }

    private static void layoutChildren(Element owner, List<Node> children, FlowState state) {
        for (Node child : children) {
            if (state.foundTarget) return;
            layoutChild(owner, child, state);
        }
    }

    private static void layoutChild(Element owner, Node child, FlowState state) {
        if (child instanceof TextNode textNode) {
            placeTextRun(owner, textNode, state);
            return;
        }
        if (!(child instanceof Element childElement)) return;

        if (isLineBreak(childElement) && Layout.isInFlow(childElement.getComputedStyle())) {
            placeLineBreak(owner, state);
            return;
        }

        Style style = childElement.getComputedStyle();
        if (!Layout.isInFlow(style)) return;

        if (isInlineLevel(style.display)) {
            if (!requiresIndependentInlinePaint(childElement) && shouldFragmentInlineElement(childElement)) {
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
        TextRunLayout run = layoutTextRun(owner, node, node.getTextContent(), state.lineLimit, state.cursorX, state.cursorY);
        placeTextRun(run, owner, node, node.getTextContent(), state);
    }

    /** <br> 的换行：当前行有内容时提交断行；连续 <br>（空行）也要推进至少一行高度，避免折叠。 */
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
        if (state.lineLimit > 0 && state.cursorX > 0 && state.cursorX + size.width() > state.lineLimit) {
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
            if (Layout.isFlexDisplay(element.getComputedStyle().display)
                    && Flex.of(element).flexDirection.contains("row")
                    && Flex.of(element).alignItems.is("center")) {
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

    private static void placeBlock(Element childElement, FlowState state) {
        if (state.cursorX > 0 || state.lineHeight > 0) {
            commitLineBreak(state);
            state.previousFlowWasBlock = false;
        }

        Box childBox = Box.of(childElement);
        Size blockSize;
        if (Size.isNaturalMeasurementContext()) {
            Size naturalSize = measureNaturalBlockSize(childElement, childBox, state.lineLimit);
            blockSize = new Size(
                    naturalSize.width() + childBox.getMarginHorizontal(),
                    naturalSize.height() + childBox.getMarginVertical()
            );
        } else {
            blockSize = childBox.size();
        }
        boolean hasHorizontalAutoMargin = childBox.isMarginAuto("left") || childBox.isMarginAuto("right");
        HorizontalBlockMargins horizontalMargins = hasHorizontalAutoMargin
                ? resolveHorizontalBlockMargins(childBox, state.lineLimit)
                : new HorizontalBlockMargins(childBox.getMarginLeft(), childBox.getMarginRight());
        double blockOuterWidth = hasHorizontalAutoMargin
                ? horizontalMargins.left() + childBox.elementSize().width() + horizontalMargins.right()
                : blockSize.width();
        double blockY = state.cursorY;
        if (state.previousFlowWasBlock) {
            double collapsedMargin = collapseAdjacentMargins(state.previousBlockMarginBottom, childBox.getMarginTop());
            blockY = state.cursorY - state.previousBlockMarginBottom - childBox.getMarginTop() + collapsedMargin;
        }

        state.childPositions.put(childElement, new Position(horizontalMargins.left(), blockY));
        if (state.target != null && childElement == state.target) {
            state.targetX = horizontalMargins.left();
            state.targetY = blockY;
            state.foundTarget = true;
            return;
        }

        state.cursorY = blockY + blockSize.height();
        state.maxLineWidth = Math.max(state.maxLineWidth, blockOuterWidth);
        state.previousBlockMarginBottom = childBox.getMarginBottom();
        state.previousFlowWasBlock = true;
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

    private static void commitLineBreak(FlowState state) {
        state.maxLineWidth = Math.max(state.maxLineWidth, state.cursorX);
        state.cursorY += state.lineHeight;
        state.cursorX = 0;
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
        text.content = normalizeInlineTextFragment(content, text.whiteSpace);
        if (text.content == null || text.content.isEmpty()) return null;

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
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replace('\n', ' ');
        return normalized.replaceAll("[\\t\\x0B\\f ]+", " ");
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
        if (element == null) return true;
        String tagName = element.tagName == null ? "" : element.tagName.trim().toUpperCase();
        return switch (tagName) {
            case "IMG", "INPUT", "TEXTAREA", "SELECT", "CANVAS", "VIDEO", "AUDIO", "IFRAME", "BUTTON" -> true;
            default -> false;
        };
    }

    public record TextRunLayout(TextNode node, Element owner, Text text, double x, double y, List<String> lines,
                                double maxWidth, double lastLineWidth, boolean startedOnNewLine) {
        public int lineCount() {
            return lines == null ? 0 : lines.size();
        }

        public TextRunLayout withY(double newY) {
            return new TextRunLayout(node, owner, text, x, newY, lines, maxWidth, lastLineWidth, startedOnNewLine);
        }
    }

    private static final class FlowState {
        private final double lineLimit;
        private final Element target;
        private final ArrayList<TextRunLayout> textRuns = new ArrayList<>();
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
        private final IdentityHashMap<Element, Position> childPositions = new IdentityHashMap<>();
        private final IdentityHashMap<Element, Double> lineAtomicAscents = new IdentityHashMap<>();
        private final IdentityHashMap<TextRunLayout, Double> lineTextRunAscents = new IdentityHashMap<>();
        private double previousBlockMarginBottom = 0;
        private boolean previousFlowWasBlock = false;

        private FlowState(double lineLimit, Element target) {
            this.lineLimit = Math.max(0, lineLimit);
            this.target = target;
        }
    }

    private record FlowMetrics(double targetX, double targetY, Size contentSize) {
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

    private record FlowResult(FlowMetrics metrics, IdentityHashMap<Element, Position> childPositions, List<TextRunLayout> textRuns) {
    }

    private record HorizontalBlockMargins(double left, double right) {
    }
}

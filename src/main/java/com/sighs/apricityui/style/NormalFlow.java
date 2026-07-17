package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.init.TextNode;

import java.util.ArrayList;
import java.util.List;

public final class NormalFlow {
    private NormalFlow() {
    }

    public static Position computeChildPosition(Element element, Element parent, List<Element> siblings) {
        Box parentBox = Box.of(parent);
        FlowResult result = computeFlow(parent, parent.getRenderChildNodes(), parentBox.innerSize().width(), element);
        FlowMetrics metrics = result.metrics();
        return new Position(
                parentBox.offset("left") + metrics.targetX,
                parentBox.offset("top") + metrics.targetY
        );
    }

    public static Size computeContentSize(Element element) {
        double lineLimit = resolveLineLimit(element);
        boolean natural = Size.isNaturalMeasurementContext();
        Size cached = LayoutMeasureCache.getSize(LayoutMeasureCache.CONTENT_NORMAL_FLOW, element, lineLimit, Double.NaN, natural);
        if (cached != null) return cached;
        Size result = computeFlow(element, element.getRenderChildNodes(), lineLimit, null).metrics().contentSize();
        LayoutMeasureCache.putSize(LayoutMeasureCache.CONTENT_NORMAL_FLOW, element, lineLimit, Double.NaN, natural, result);
        return result;
    }

    public static List<TextRunLayout> computeTextRuns(Element element) {
        return computeFlow(element, element.getRenderChildNodes(), resolveLineLimit(element), null).textRuns();
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

            Style parentStyle = parent.getComputedStyle();
            if (Layout.isFlexDisplay(parentStyle.display) || Layout.isGridDisplay(parentStyle.display)) {
                return false;
            }

            if (hasRenderableInlineText(current)) return true;
            if (shouldFragmentInlineElement(parent)) return true;
            current = parent;
        }
        return false;
    }

    private static double resolveLineLimit(Element element) {
        Style style = element.getComputedStyle();
        if (Size.isNaturalMeasurementContext() && Size.parseNumber(style.width) == null) {
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

        Style style = childElement.getComputedStyle();
        if (!Layout.isInFlow(style)) return;

        if (isInlineLevel(style.display)) {
            if (shouldFragmentInlineElement(childElement)) {
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
            placeAtomicInline(childElement, state);
            return;
        }

        placeBlock(childElement, state);
    }

    private static void placeTextRun(Element owner, TextNode node, FlowState state) {
        TextRunLayout run = layoutTextRun(owner, node, node.getTextContent(), state.lineLimit, state.cursorX, state.cursorY);
        placeTextRun(run, owner, node, node.getTextContent(), state);
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

        state.textRuns.add(run);
        if (run.lineCount() > 1) {
            state.maxLineWidth = Math.max(state.maxLineWidth, run.maxWidth());
            state.cursorY = run.y() + (run.lineCount() - 1) * run.text().lineHeight;
            state.cursorX = run.lastLineWidth();
            state.lineHeight = Math.max(state.lineHeight, run.text().lineHeight);
        } else {
            state.cursorX += run.lastLineWidth();
            state.lineHeight = Math.max(state.lineHeight, run.text().lineHeight);
        }
        state.previousFlowWasBlock = false;
    }

    private static void layoutInlineContent(Element element, FlowState state) {
        if (element == null) return;
        if (element.getRenderChildNodes().isEmpty()) {
            placeInlineText(element, element.innerText, state);
            return;
        }
        layoutChildren(element, element.getRenderChildNodes(), state);
    }

    private static void placeAtomicInline(Element childElement, FlowState state) {
        Size size = Size.box(childElement);
        if (state.lineLimit > 0 && state.cursorX > 0 && state.cursorX + size.width() > state.lineLimit) {
            commitLineBreak(state);
        }

        if (state.target != null && childElement == state.target) {
            state.targetX = state.cursorX;
            state.targetY = state.cursorY;
            state.foundTarget = true;
            return;
        }

        state.cursorX += size.width();
        state.lineHeight = Math.max(state.lineHeight, size.height());
        state.previousFlowWasBlock = false;
    }

    private static void placeBlock(Element childElement, FlowState state) {
        if (state.cursorX > 0 || state.lineHeight > 0) {
            commitLineBreak(state);
            state.previousFlowWasBlock = false;
        }

        Box childBox = Box.of(childElement);
        Size blockSize;
        if (Size.isNaturalMeasurementContext()) {
            Size naturalSize = Size.natural(childElement);
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
        Element.copyTextForRun(base, text);
        text.color = base.color;
        text.strokeColor = base.strokeColor;
        text.content = normalizeInlineTextFragment(content, text.whiteSpace);
        if (text.content == null || text.content.isEmpty()) return null;

        boolean startOnNewLine = false;
        if (lineLimit > 0 && cursorX > 0 && Text.measureText(text) > Math.max(0, lineLimit - cursorX)) {
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
    }

    private static final class FlowState {
        private final double lineLimit;
        private final Element target;
        private final ArrayList<TextRunLayout> textRuns = new ArrayList<>();
        private double cursorX = 0;
        private double cursorY = 0;
        private double lineHeight = 0;
        private double maxLineWidth = 0;
        private double targetX = 0;
        private double targetY = 0;
        private boolean foundTarget = false;
        private double previousBlockMarginBottom = 0;
        private boolean previousFlowWasBlock = false;

        private FlowState(double lineLimit, Element target) {
            this.lineLimit = Math.max(0, lineLimit);
            this.target = target;
        }
    }

    private record FlowMetrics(double targetX, double targetY, Size contentSize) {
    }

    private record FlowResult(FlowMetrics metrics, List<TextRunLayout> textRuns) {
    }

    private record HorizontalBlockMargins(double left, double right) {
    }
}

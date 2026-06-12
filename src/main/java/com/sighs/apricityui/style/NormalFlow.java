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
        FlowResult result = computeFlow(parent, parent.childNodes, parentBox.innerSize().width(), element);
        FlowMetrics metrics = result.metrics();
        return new Position(
                parentBox.offset("left") + metrics.targetX,
                parentBox.offset("top") + metrics.targetY
        );
    }

    public static Size computeContentSize(Element element) {
        return computeFlow(element, element.childNodes, resolveLineLimit(element), null).metrics().contentSize();
    }

    public static List<TextRunLayout> computeTextRuns(Element element) {
        return computeFlow(element, element.childNodes, resolveLineLimit(element), null).textRuns();
    }

    private static double resolveLineLimit(Element element) {
        Style style = element.getComputedStyle();
        Double explicitWidth = Size.parseNumber(style.width);
        if (explicitWidth != null) {
            double resolved = Size.resolveLength(style.width, Size.getScaleWidth(element), explicitWidth);
            if (Box.BOX_SIZING_BORDER_BOX.equals(Box.normalizeBoxSizing(style.boxSizing))) {
                Box box = Box.of(element);
                resolved -= box.getBorderHorizontal() + box.getPaddingHorizontal();
            }
            return Math.max(0, resolved);
        }
        return Math.max(0, Size.getScaleWidth(element));
    }

    private static FlowResult computeFlow(Element owner, List<Node> children, double lineLimit, Element target) {
        double cursorX = 0;
        double cursorY = 0;
        double lineHeight = 0;
        double maxLineWidth = 0;
        double targetX = 0;
        double targetY = 0;
        boolean foundTarget = false;
        ArrayList<TextRunLayout> textRuns = new ArrayList<>();

        for (Node child : children) {
            if (child instanceof TextNode textNode) {
                TextRunLayout run = layoutTextRun(owner, textNode, lineLimit, cursorX, cursorY);
                if (run == null) continue;
                if (run.startedOnNewLine() && (cursorX > 0 || lineHeight > 0)) {
                    maxLineWidth = Math.max(maxLineWidth, cursorX);
                    cursorY += lineHeight;
                    cursorX = 0;
                    lineHeight = 0;
                    run = layoutTextRun(owner, textNode, lineLimit, cursorX, cursorY);
                    if (run == null) continue;
                }

                textRuns.add(run);
                if (run.lineCount() > 1) {
                    maxLineWidth = Math.max(maxLineWidth, run.maxWidth());
                    cursorY = run.y() + (run.lineCount() - 1) * run.text().lineHeight;
                    cursorX = run.lastLineWidth();
                    lineHeight = Math.max(lineHeight, run.text().lineHeight);
                } else {
                    cursorX += run.lastLineWidth();
                    lineHeight = Math.max(lineHeight, run.text().lineHeight);
                }
                continue;
            }

            if (!(child instanceof Element childElement)) continue;
            Style style = childElement.getComputedStyle();
            if (!Layout.isInFlow(style)) continue;

            Size size = Size.box(childElement);
            boolean inlineLevel = isInlineLevel(style.display);

            if (inlineLevel) {
                if (lineLimit > 0 && cursorX > 0 && cursorX + size.width() > lineLimit) {
                    maxLineWidth = Math.max(maxLineWidth, cursorX);
                    cursorY += lineHeight;
                    cursorX = 0;
                    lineHeight = 0;
                }

                if (target != null && childElement == target) {
                    targetX = cursorX;
                    targetY = cursorY;
                    foundTarget = true;
                    break;
                }

                cursorX += size.width();
                lineHeight = Math.max(lineHeight, size.height());
                continue;
            }

            if (cursorX > 0 || lineHeight > 0) {
                maxLineWidth = Math.max(maxLineWidth, cursorX);
                cursorY += lineHeight;
                cursorX = 0;
                lineHeight = 0;
            }

            if (target != null && childElement == target) {
                targetX = 0;
                targetY = cursorY;
                foundTarget = true;
                break;
            }

            cursorY += size.height();
            maxLineWidth = Math.max(maxLineWidth, size.width());
        }

        if (!foundTarget) {
            targetX = 0;
            targetY = cursorY;
        }

        double contentWidth = Math.max(maxLineWidth, cursorX);
        double contentHeight = cursorY + lineHeight;
        return new FlowResult(
                new FlowMetrics(targetX, targetY, new Size(contentWidth, contentHeight)),
                List.copyOf(textRuns)
        );
    }

    private static TextRunLayout layoutTextRun(Element owner, TextNode node, double lineLimit, double cursorX, double cursorY) {
        if (owner == null || node == null) return null;
        Text base = Text.of(owner);
        Text text = new Text();
        Element.copyTextForRun(base, text);
        text.color = base.color;
        text.strokeColor = base.strokeColor;
        text.content = normalizeInlineTextFragment(node.getTextContent(), text.whiteSpace);
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
        return new TextRunLayout(node, text, cursorX, cursorY, List.copyOf(lines), wrapped.width(), lastLineWidth, startOnNewLine);
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
        if (display == null) return false;
        String value = display.trim().toLowerCase();
        return "inline".equals(value) || "inline-block".equals(value);
    }

    public record TextRunLayout(TextNode node, Text text, double x, double y, List<String> lines,
                                double maxWidth, double lastLineWidth, boolean startedOnNewLine) {
        public int lineCount() {
            return lines == null ? 0 : lines.size();
        }
    }

    private record FlowMetrics(double targetX, double targetY, Size contentSize) {
    }

    private record FlowResult(FlowMetrics metrics, List<TextRunLayout> textRuns) {
    }
}

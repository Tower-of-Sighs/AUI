package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.List;

public final class NormalFlow {
    private NormalFlow() {
    }

    public static Position computeChildPosition(Element element, Element parent, List<Element> siblings) {
        Box parentBox = Box.of(parent);
        FlowMetrics metrics = computeFlow(siblings, parentBox.innerSize().width(), element);
        return new Position(
                parentBox.offset("left") + metrics.targetX,
                parentBox.offset("top") + metrics.targetY
        );
    }

    public static Size computeContentSize(Element element) {
        FlowMetrics metrics = computeFlow(element.children, resolveLineLimit(element), null);
        return new Size(metrics.contentWidth, metrics.contentHeight);
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

    private static FlowMetrics computeFlow(List<Element> children, double lineLimit, Element target) {
        double cursorX = 0;
        double cursorY = 0;
        double lineHeight = 0;
        double maxLineWidth = 0;
        double targetX = 0;
        double targetY = 0;
        boolean foundTarget = false;

        for (Element child : children) {
            Style style = child.getComputedStyle();
            if (!Layout.isInFlow(style)) continue;

            Size size = Size.box(child);
            boolean inlineLevel = isInlineLevel(style.display);

            if (inlineLevel) {
                if (lineLimit > 0 && cursorX > 0 && cursorX + size.width() > lineLimit) {
                    maxLineWidth = Math.max(maxLineWidth, cursorX);
                    cursorY += lineHeight;
                    cursorX = 0;
                    lineHeight = 0;
                }

                if (target != null && child == target) {
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

            if (target != null && child == target) {
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
        return new FlowMetrics(targetX, targetY, contentWidth, contentHeight);
    }

    private static boolean isInlineLevel(String display) {
        if (display == null) return false;
        String value = display.trim().toLowerCase();
        return "inline".equals(value) || "inline-block".equals(value);
    }

    private record FlowMetrics(double targetX, double targetY, double contentWidth, double contentHeight) {
    }
}

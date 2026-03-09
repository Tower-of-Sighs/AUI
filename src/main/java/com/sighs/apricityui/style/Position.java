package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.List;

public class Position {
    public static final Position ZERO = new Position(0, 0);

    public double x;
    public double y;

    public Position(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Position add(Position position) {
        return new Position(x + position.x, y + position.y);
    }

    public static Position getOffset(Element element) {
        if (element == null) return ZERO;

        Position cache = element.getRenderer().position.get();
        if (cache != null) return cache;

        Style style = element.getComputedStyle();
        Position resultPosition = ZERO;
        Element parent = element.parentElement;
        String positionType = style.position == null ? "static" : style.position;

        // 基础流式位置（absolute/fixed 不参与常规流布局）
        if (!"absolute".equals(positionType) && !"fixed".equals(positionType) && parent != null) {
            resultPosition = computeNormalFlowChildPosition(element, parent, parent.children);
        }

        // relative: 在原流位置上偏移
        if ("relative".equals(positionType)) {
            resultPosition = resultPosition.add(resolveRelativeShift(element));
        }

        // absolute/fixed: 以 containing block 进行偏移定位
        if ("absolute".equals(positionType) || "fixed".equals(positionType)) {
            resultPosition = resolveOutOfFlowOffset(element, positionType);
        }

        element.getRenderer().position.set(resultPosition);
        return resultPosition;
    }

    public static Position of(Element element) {
        if (element == null) return ZERO;
        Position resultPosition = new Position(0, 0);
        for (Element e : element.getRoute()) {
            resultPosition = resultPosition.add(Position.getOffset(e));
            if (!e.uuid.equals(element.uuid))
                resultPosition = resultPosition.add(new Position(-e.getScrollLeft(), -e.getScrollTop()));
            if ("fixed".equals(e.getComputedStyle().position)) break;
        }
        return resultPosition;
    }

    private static Position computeNormalFlowChildPosition(Element element, Element parent, List<Element> siblings) {
        Style ps = parent.getComputedStyle();
        if ("grid".equals(ps.display)) {
            return Grid.computeChildPosition(element, parent, siblings);
        }
        return Flex.computeChildPosition(element, parent, siblings);
    }

    public static int parseSignedInt(String str) {
        if (str == null || str.isEmpty() || "unset".equals(str)) {
            return 0;
        }

        StringBuilder numberBuilder = new StringBuilder();
        boolean foundNumber = false;

        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (c == '-' && !foundNumber && i + 1 < chars.length && Character.isDigit(chars[i + 1])) {
                numberBuilder.append(c);
                foundNumber = true;
            } else if (Character.isDigit(c)) {
                numberBuilder.append(c);
                foundNumber = true;
            } else if (foundNumber) {
                break;
            }
        }

        if (!numberBuilder.isEmpty()) {
            try {
                return Integer.parseInt(numberBuilder.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        return 0;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank() && !"unset".equals(value);
    }

    private static Position resolveRelativeShift(Element element) {
        Style style = element.getComputedStyle();
        double basisW = Size.getScaleWidth(element);
        double basisH = Size.getScaleHeight(element);
        double left = isSet(style.left) ? Size.resolveLength(style.left, basisW, 0) : 0;
        double right = isSet(style.right) ? Size.resolveLength(style.right, basisW, 0) : 0;
        double top = isSet(style.top) ? Size.resolveLength(style.top, basisH, 0) : 0;
        double bottom = isSet(style.bottom) ? Size.resolveLength(style.bottom, basisH, 0) : 0;
        return new Position(left - right, top - bottom);
    }

    private static Position resolveOutOfFlowOffset(Element element, String positionType) {
        Style style = element.getComputedStyle();
        Size selfSize = Size.box(element);

        double containerW;
        double containerH;
        if ("fixed".equals(positionType)) {
            Size window = Size.getWindowSize();
            containerW = window.width();
            containerH = window.height();
        } else {
            Element parent = element.parentElement;
            if (parent != null) {
                Size parentContent = Box.of(parent).innerSize();
                containerW = parentContent.width();
                containerH = parentContent.height();
            } else {
                Size window = Size.getWindowSize();
                containerW = window.width();
                containerH = window.height();
            }
        }

        boolean hasLeft = isSet(style.left);
        boolean hasRight = isSet(style.right);
        boolean hasTop = isSet(style.top);
        boolean hasBottom = isSet(style.bottom);

        double x = 0;
        double y = 0;

        if (hasLeft) {
            x = Size.resolveLength(style.left, containerW, 0);
        } else if (hasRight) {
            double right = Size.resolveLength(style.right, containerW, 0);
            x = containerW - selfSize.width() - right;
        }

        if (hasTop) {
            y = Size.resolveLength(style.top, containerH, 0);
        } else if (hasBottom) {
            double bottom = Size.resolveLength(style.bottom, containerH, 0);
            y = containerH - selfSize.height() - bottom;
        }

        return new Position(x, y);
    }

    @Override
    public String toString() {
        return "[" + x + "," + y + "]";
    }
}

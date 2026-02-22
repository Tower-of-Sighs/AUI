package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;


import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Position {
    public static final Position ZERO = new Position(0, 0);

    public double x;
    public double y;

    public Position(double x, double y) {
        this.x = (int) x;
        this.y = (int) y;
    }

    public Position add(Position position) {
        return new Position(x + position.x, y + position.y);
    }

    public static Position of(Element element) {
        if (element == null) return ZERO;

        Position cache = element.getRenderer().position.get();
        if (cache != null) return cache;

        Style style = element.getComputedStyle();
        Position resultPosition = ZERO;

        String positionType = style.position;
        boolean isAbsolute = "absolute".equals(positionType);
        boolean isFixed = "fixed".equals(positionType);

        if (isAbsolute || isFixed) {
            Element parent = isFixed ? null : element.getParentStackContext();

            Position parentPos = (parent == null) ? ZERO : Position.of(parent);
            Size parentSize = (parent == null) ? Size.getWindowSize() : Size.of(parent);
            Box parentBox = (parent == null) ? null : Box.of(parent);

            double borderLeft = (parentBox == null) ? 0 : parentBox.getBorderLeft();
            double borderTop = (parentBox == null) ? 0 : parentBox.getBorderTop();
            double borderRight = (parentBox == null) ? 0 : parentBox.getBorderRight();
            double borderBottom = (parentBox == null) ? 0 : parentBox.getBorderBottom();

            double x = parentPos.x + borderLeft;
            double y = parentPos.y + borderTop;

            Size selfSize = Size.of(element);

            if (!"unset".equals(style.left)) {
                x += parseSignedInt(style.left);
            } else if (!"unset".equals(style.right)) {
                x = parentPos.x + parentSize.width() - borderRight - selfSize.width() - parseSignedInt(style.right);
            } else {
                if (element.parentElement != null) {
                    CopyOnWriteArrayList<Element> siblings = new CopyOnWriteArrayList<>(List.of(element));
                    Position staticPos = computeNormalFlowChildPosition(element, element.parentElement, siblings);
                    x = staticPos.x;
                }
            }

            if (!"unset".equals(style.top)) {
                y += parseSignedInt(style.top);
            } else if (!"unset".equals(style.bottom)) {
                y = parentPos.y + parentSize.height() - borderBottom - selfSize.height() - parseSignedInt(style.bottom);
            } else {
                if (element.parentElement != null) {
                    CopyOnWriteArrayList<Element> siblings = new CopyOnWriteArrayList<>(List.of(element));
                    Position staticPos = computeNormalFlowChildPosition(element, element.parentElement, siblings);
                    y = staticPos.y;
                }
            }

            if (parent != null) {
                x -= parent.scrollLeft;
                y -= parent.getScrollTop();
            }

            resultPosition = new Position(x, y);
        } else {
            Element parent = element.parentElement;
            if (parent != null) {
                resultPosition = computeNormalFlowChildPosition(element, parent, parent.children);
            }

            if ("relative".equals(positionType)) {
                int top = "unset".equals(style.top) ? 0 : parseSignedInt(style.top);
                int bottom = "unset".equals(style.bottom) ? 0 : parseSignedInt(style.bottom);
                int left = "unset".equals(style.left) ? 0 : parseSignedInt(style.left);
                int right = "unset".equals(style.right) ? 0 : parseSignedInt(style.right);

                resultPosition = resultPosition.add(new Position(left - right, top - bottom));
            }

            if (parent != null) {
                resultPosition = resultPosition.add(new Position(-parent.scrollLeft, -parent.getScrollTop()));
            }
        }

        element.getRenderer().position.set(resultPosition);
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
            }
            else if (Character.isDigit(c)) {
                numberBuilder.append(c);
                foundNumber = true;
            }
            else if (foundNumber) {
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

    @Override
    public String toString() {
        return "[" + x + "," + y + "]";
    }
}

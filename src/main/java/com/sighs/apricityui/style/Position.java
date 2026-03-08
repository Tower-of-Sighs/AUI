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
        this.x = x;
        this.y = y;
    }

    public Position add(Position position) {
        return new Position(x + position.x, y + position.y);
    }

    public static Position getOffset(Element element) {
        if (element == null) return ZERO;

        // 缓存依然有效，但现在缓存的是本地 offset
        Position cache = element.getRenderer().position.get();
        if (cache != null) return cache;

        Style style = element.getComputedStyle();
        Position resultPosition = ZERO;

        String positionType = style.position;
        // 如果是绝对定位，计算相对于最近层叠上下文（或父级）的偏移
        if ("absolute".equals(positionType) || "fixed".equals(positionType)) {
            int top = parseSignedInt(style.top);
            int left = parseSignedInt(style.left);
            int bottom = parseSignedInt(style.bottom);
            int right = parseSignedInt(style.right);
            resultPosition = new Position(left - right, top - bottom);
        } else {
            // 普通流布局（Flex/Grid）
            Element parent = element.parentElement;
            if (parent != null) {
                // 这里调用布局引擎计算相对于父容器 content-box 的位置
                resultPosition = computeNormalFlowChildPosition(element, parent, parent.children);
            }
        }

        element.getRenderer().position.set(resultPosition);
        return resultPosition;
    }

    public static Position of(Element element) {
        if (element == null) return ZERO;
        Position resultPosition = new Position(0, 0);
        for (Element e : element.getRoute()) {
            resultPosition = resultPosition.add(Position.getOffset(e));
            if (!e.uuid.equals(element.uuid)) resultPosition = resultPosition.add(new Position(-e.getScrollLeft(), -e.getScrollTop()));
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

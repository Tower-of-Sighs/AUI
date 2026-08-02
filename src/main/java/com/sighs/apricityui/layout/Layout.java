package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.*;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

import java.util.ArrayList;
import java.util.List;
import com.sighs.apricityui.parser.CSS;

public final class Layout {
    private Layout() {
    }

    /**
     * 按顶层空白切分 CSS 值，函数体（calc()/min() 等）内的空白不切分。
     * 包内各布局器的 paren-aware 分词统一走这里（Box/Grid 原各自实现了一份）。
     */
    static List<String> splitTopLevelWhitespace(String value) {
        List<String> parts = new ArrayList<>();
        if (value == null || value.isBlank()) return parts;
        int depth = 0;
        int start = -1;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && depth > 0) depth--;
            if (Character.isWhitespace(c) && depth == 0) {
                if (start >= 0) {
                    parts.add(value.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) parts.add(value.substring(start));
        return parts;
    }

    public static Position computeChildPosition(Element element, Element parent, List<Element> siblings) {
        if (parent == null) return Position.ZERO;
        String display = parent.getComputedStyle().display;
        if (isGridDisplay(display)) {
            return Grid.computeChildPosition(element, parent, siblings);
        }
        if (isFlexDisplay(display)) {
            return Flex.computeChildPosition(element, parent, siblings);
        }
        return NormalFlow.computeChildPosition(element, parent, siblings);
    }

    public static Size computeContentSize(Element element) {
        if (element == null) return Size.ZERO;
        String display = element.getComputedStyle().display;
        if (isGridDisplay(display)) {
            return Grid.computeContentSize(element);
        }
        if (isFlexDisplay(display)) {
            return Flex.computeContentSize(element);
        }
        return NormalFlow.computeContentSize(element);
    }

    public static boolean isFlexDisplay(String display) {
        if (display == null) return false;
        String value = display.trim().toLowerCase();
        return "flex".equals(value) || "inline-flex".equals(value);
    }

    public static boolean isGridDisplay(String display) {
        if (display == null) return false;
        String value = display.trim().toLowerCase();
        return "grid".equals(value) || "inline-grid".equals(value);
    }

    public static boolean isInFlow(Style style) {
        if (style == null) return false;
        if ("none".equals(style.display)) return false;
        return !"absolute".equals(style.position) && !"fixed".equals(style.position);
    }
}

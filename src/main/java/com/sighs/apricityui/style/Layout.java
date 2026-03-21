package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.List;

public final class Layout {
    private Layout() {
    }

    public static Position computeChildPosition(Element element, Element parent, List<Element> siblings) {
        if (parent == null) return Position.ZERO;
        String display = parent.getComputedStyle().display;
        if ("grid".equals(display)) {
            return Grid.computeChildPosition(element, parent, siblings);
        }
        if ("flex".equals(display)) {
            return Flex.computeChildPosition(element, parent, siblings);
        }
        return NormalFlow.computeChildPosition(element, parent, siblings);
    }

    public static Size computeContentSize(Element element) {
        if (element == null) return Size.ZERO;
        String display = element.getComputedStyle().display;
        if ("grid".equals(display)) {
            return Grid.computeContentSize(element);
        }
        if ("flex".equals(display)) {
            return Flex.computeContentSize(element);
        }
        return NormalFlow.computeContentSize(element);
    }

    public static boolean isInFlow(Style style) {
        if (style == null) return false;
        if ("none".equals(style.display)) return false;
        return !"absolute".equals(style.position) && !"fixed".equals(style.position);
    }
}

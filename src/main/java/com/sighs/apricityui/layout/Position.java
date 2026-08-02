package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.*;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

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
            resultPosition = computeNormalFlowChildPosition(element, parent, parent.getRenderChildren());
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
        double x = 0.0;
        double y = 0.0;
        for (Element e : element.getRouteArray()) {
            Position offset = Position.getOffset(e);
            x += offset.x;
            y += offset.y;
            if (!e.uuid.equals(element.uuid)) {
                x -= e.getScrollLeft();
                y -= e.getScrollTop();
            }
            if ("fixed".equals(e.getComputedStyle().position)) break;
        }
        return x == 0.0 && y == 0.0 ? ZERO : new Position(x, y);
    }

    private static Position computeNormalFlowChildPosition(Element element, Element parent, List<Element> siblings) {
        return Layout.computeChildPosition(element, parent, siblings);
    }

    /**
     * 解析字符串中出现的第一个数字（可带符号）。与 Size.parseNumber 的差异：
     * parseNumber 只解析前导数字，这里允许任意前缀字符（如 "translate(7px)"），
     * 因此先定位到第一个候选起点，再委托 parseNumber 完成扫描。
     */
    public static double parseSignedNumber(String str) {
        if (str == null || str.isEmpty() || "unset".equals(str)) return 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            boolean signStart = (c == '-' || c == '+') && i + 1 < str.length()
                    && Character.isDigit(str.charAt(i + 1));
            if (Character.isDigit(c) || signStart) {
                Double number = Size.parseNumber(str.substring(i));
                return number == null ? 0 : number;
            }
        }
        return 0;
    }

    private static boolean isSet(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.trim().toLowerCase();
        return !"unset".equals(normalized) && !"auto".equals(normalized);
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

    /**
     * CSS2 §10.1：absolute 的包含块是最近的 position ≠ static 的祖先
     * （relative/absolute/fixed）的 padding box；没有这样的祖先时返回 null，
     * 表示使用初始包含块（文档视口，随内容滚动，区别于 fixed）。
     */
    public static Element findContainingBlock(Element element) {
        for (Element e = element == null ? null : element.parentElement; e != null; e = e.parentElement) {
            String position = e.getComputedStyle().position;
            if (position == null) continue;
            String value = position.trim().toLowerCase(java.util.Locale.ROOT);
            if (!value.isEmpty() && !"static".equals(value) && !"unset".equals(value)) return e;
        }
        return null;
    }

    /**
     * 包含块原点在父元素坐标系中的偏移：沿父链向上，逐个减去各级祖先相对
     * 其父元素 border-box 原点的偏移，直到（不含）包含块祖先。cb 为 null
     * 时一直减到顶层元素，得到相对文档原点的偏移。最终文档坐标中这些中间
     * 偏移会在 Position.of 累加时重新加回并相互抵消。
     */
    private static Position containingBlockShift(Element element, Element cb) {
        double x = 0;
        double y = 0;
        for (Element e = element.parentElement; e != null && e != cb; e = e.parentElement) {
            Position offset = Position.getOffset(e);
            x -= offset.x;
            y -= offset.y;
        }
        return new Position(x, y);
    }

    private static Position resolveOutOfFlowOffset(Element element, String positionType) {
        Style style = element.getComputedStyle();
        Size selfSize = Size.box(element);

        double containerW;
        double containerH;
        double originX = 0;
        double originY = 0;
        if ("fixed".equals(positionType)) {
            Size window = viewportContainingBlockSize(element);
            containerW = window.width();
            containerH = window.height();
        } else {
            Element containingBlock = findContainingBlock(element);
            if (containingBlock != null) {
                Box cbBox = Box.of(containingBlock);
                Size cbSize = Size.of(containingBlock);
                containerW = Math.max(0, cbSize.width() - cbBox.getBorderHorizontal());
                containerH = Math.max(0, cbSize.height() - cbBox.getBorderVertical());
                Position shift = containingBlockShift(element, containingBlock);
                originX = cbBox.getBorderLeft() + shift.x;
                originY = cbBox.getBorderTop() + shift.y;
            } else {
                // 初始包含块：文档视口（随内容滚动，区别于 fixed）
                Size window = viewportContainingBlockSize(element);
                containerW = window.width();
                containerH = window.height();
                Position shift = containingBlockShift(element, null);
                originX = shift.x;
                originY = shift.y;
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

        return new Position(originX + x, originY + y);
    }

    /**
     * Fixed positioning is relative to the owning document's viewport. Using
     * the thread-local global window size here is incorrect when layout is
     * queried from a keyboard/default-action callback without a document
     * context (for example, a browser-sized DevTools document).
     *
     * 初始包含块（无 positioned 祖先的 absolute）使用同一视口尺寸，
     * 区别仅在于初始包含块随内容滚动。
     */
    static Size viewportContainingBlockSize(Element element) {
        if (element != null && element.document != null) {
            Size viewport = new Size(
                    element.document.getViewport().layoutWidth(),
                    element.document.getViewport().layoutHeight()
            );
            // Test/headless documents may still carry the constructor's 1x1
            // placeholder viewport. Keep the existing global fallback until
            // the owning document has a usable viewport.
            if (viewport.width() > 1 && viewport.height() > 1) return viewport;
        }
        return Size.getWindowSize();
    }

    @Override
    public String toString() {
        return "[" + x + "," + y + "]";
    }
}

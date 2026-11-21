package com.sighs.apricityui.layout;

import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.ScrollbarStyle;
import com.sighs.apricityui.style.Style;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;

/** Solves scrollbar occupancy from used boxes, before publishing visual geometry. */
public final class ScrollLayout {
    private static final ThreadLocal<Pass> ACTIVE = new ThreadLocal<>();

    private ScrollLayout() {}

    public record Metrics(Size port, Size content, boolean horizontal, boolean vertical,
                          double horizontalGutter, double verticalGutter) {
        static final Metrics EMPTY = new Metrics(Size.ZERO, Size.ZERO, false, false, 0, 0);
    }

    static List<Element> candidates(List<Element> targets, Predicate<Element> included) {
        List<Element> out = new ArrayList<>();
        for (Element element : targets) {
            if (!included.test(element) || !Interaction.isDisplayed(element)) continue;
            var x = ScrollbarStyle.overflow(element, true);
            var y = ScrollbarStyle.overflow(element, false);
            if (element instanceof AbstractText || ScrollbarStyle.isViewport(element)
                    || x == Interaction.Overflow.AUTO || y == Interaction.Overflow.AUTO
                    || x == Interaction.Overflow.SCROLL || y == Interaction.Overflow.SCROLL
                    || x == Interaction.Overflow.HIDDEN || y == Interaction.Overflow.HIDDEN
                    || ScrollbarStyle.stable(element) || element.getLayout().scrollMetrics != Metrics.EMPTY) {
                out.add(element);
            }
        }
        return out;
    }

    public static Metrics metrics(Element element) { return element.getLayout().scrollMetrics; }

    public static double gutter(Element element, boolean horizontalBar) {
        Metrics metrics = constraints(element);
        if (metrics != Metrics.EMPTY) {
            return horizontalBar ? metrics.horizontalGutter() : metrics.verticalGutter();
        }
        if (!ScrollbarStyle.stable(element)
                && ScrollbarStyle.overflow(element, horizontalBar) != Interaction.Overflow.SCROLL) return 0;
        return ScrollbarStyle.hidden(element) ? 0 : ScrollbarStyle.gutter(element);
    }

    static boolean begin(Element owner) {
        boolean x = ScrollbarStyle.overflow(owner, true) == Interaction.Overflow.SCROLL;
        boolean y = ScrollbarStyle.overflow(owner, false) == Interaction.Overflow.SCROLL;
        return update(owner, Size.ZERO, Size.ZERO, x, y);
    }

    /** Each pass can only add bars. A new layout starts from the mandatory gutters again. */
    static boolean measure(Element owner) {
        LayoutMeasureCache.recordMeasurement();
        Metrics previous = constraints(owner);
        Size rawPort = ScrollbarStyle.isViewport(owner)
                ? owner.document.getViewportSize() : Box.of(owner).rawInnerSize();
        Size port = new Size(Math.max(0, rawPort.width() - previous.verticalGutter()),
                Math.max(0, rawPort.height() - previous.horizontalGutter()));
        Size content = owner instanceof AbstractText control
                ? control.measureControlScrollContent() : measureLayoutScrollArea(owner);
        if (ScrollbarStyle.isViewport(owner) && owner.document.body != null) {
            Size body = measureLayoutScrollArea(owner.document.body);
            content = new Size(Math.max(content.width(), body.width()), Math.max(content.height(), body.height()));
        }
        boolean x = previous.horizontal() || ScrollbarStyle.auto(owner, ScrollbarStyle.overflow(owner, true))
                && content.width() > port.width() + 0.5;
        boolean y = previous.vertical() || ScrollbarStyle.auto(owner, ScrollbarStyle.overflow(owner, false))
                && content.height() > port.height() + 0.5;
        return update(owner, port, content, x, y);
    }

    static Metrics constraints(Element owner) {
        Pass pass = ACTIVE.get();
        return pass == null ? metrics(owner) : pass.trial.getOrDefault(owner, metrics(owner));
    }

    private static boolean update(Element owner, Size port, Size content, boolean x, boolean y) {
        boolean stable = ScrollbarStyle.stable(owner);
        double size = (stable || x || y) && !ScrollbarStyle.hidden(owner) ? ScrollbarStyle.gutter(owner) : 0;
        double horizontal = x || stable ? size : 0;
        double vertical = y || stable ? size : 0;
        var state = owner.getLayout();
        Metrics previous = constraints(owner);
        ACTIVE.get().trial.put(owner, new Metrics(port, content, x, y, horizontal, vertical));
        if (horizontal == previous.horizontalGutter() && vertical == previous.verticalGutter()) return false;
        state.invalidateLayoutSubtree();
        state.invalidateLayoutAncestors();
        return true;
    }

    /** Trial gutters are visible only to the layout pass; readers keep the last published metrics. */
    static final class Pass {
        private final IdentityHashMap<Element, Metrics> trial = new IdentityHashMap<>();

        Scope enter() {
            Pass previous = ACTIVE.get();
            ACTIVE.set(this);
            return () -> {
                if (previous == null) ACTIVE.remove();
                else ACTIVE.set(previous);
            };
        }

        void publish() {
            trial.forEach((owner, metrics) -> {
                owner.getLayout().scrollMetrics = metrics;
                owner.scrollWidth = metrics.content().width();
                owner.scrollHeight = metrics.content().height();
                if (owner.document != null) owner.document.onScrollMetricsCommitted(owner);
            });
            trial.clear();
        }
    }

    interface Scope extends AutoCloseable {
        @Override void close();
    }

    private static Size measureLayoutScrollArea(Element scrollport) {
        if (scrollport == null) return Size.ZERO;
        Box box = Box.of(scrollport);
        double contentOriginX = box.offset("left");
        double contentOriginY = box.offset("top");
        double width = 0;
        double height = 0;
        for (Element child : scrollport.getRenderChildren()) {
            Style style = child.getComputedStyle();
            if ("none".equals(style.display) || "fixed".equals(style.position)) continue;

            Position offset = Position.getOffset(child);
            Size outerSize = Box.of(child).size();
            width = Math.max(width, offset.x - contentOriginX + outerSize.width());
            height = Math.max(height, offset.y - contentOriginY + outerSize.height());
        }
        return new Size(Math.max(0, width), Math.max(0, height));
    }

}

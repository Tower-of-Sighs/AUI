package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Shared columns and row geometry for the Markdown table subset. */
public final class Table {
    private Table() {}

    private record Entry(Position position, Size size) {}
    private record Row(Element element, Element group, List<Element> cells) {}
    private record Result(IdentityHashMap<Element, Entry> entries, Size content) {
        static final Result EMPTY = new Result(new IdentityHashMap<>(), Size.ZERO);
    }

    private static final ThreadLocal<Set<Element>> RESOLVING = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    public static boolean isStructure(String display) {
        return "table".equals(display) || isGroup(display) || "table-row".equals(display);
    }

    private static boolean isGroup(String display) {
        return "table-row-group".equals(display) || "table-header-group".equals(display);
    }

    private static Element owner(Element element) {
        if (element == null) return null;
        String display = element.getComputedStyle().display;
        if ("table".equals(display)) return element;
        if (!isStructure(display) && !"table-cell".equals(display)) return null;
        for (Element current = element.parentElement; current != null; current = current.parentElement) {
            display = current.getComputedStyle().display;
            if ("table".equals(display)) return current;
            if (!isGroup(display) && !"table-row".equals(display)) return null;
        }
        return null;
    }

    public static Size resolveAssignedSize(Element element) {
        Element table = owner(element);
        if (table == null || table == element || RESOLVING.get().contains(table)) return null;
        Entry entry = layout(table, null).entries.get(element);
        return entry == null ? null : entry.size;
    }

    public static Position computeChildPosition(Element element, Element parent) {
        Element table = owner(parent);
        if (table == null) return NormalFlow.computeChildPosition(element, parent, parent.getRenderChildren());
        Entry entry = layout(table, null).entries.get(element);
        return entry == null ? Position.ZERO : entry.position;
    }

    public static Size computeContentSize(Element element) {
        Element table = owner(element);
        if (table == null) return NormalFlow.computeContentSize(element);
        Result result = layout(table, null);
        if (table == element) return result.content;
        Entry entry = result.entries.get(element);
        return entry == null ? Size.ZERO : entry.size;
    }

    static Size contentAtWidth(Element table, double width) {
        return layout(table, width).content;
    }

    private static Result layout(Element table, Double forcedWidth) {
        Set<Element> resolving = RESOLVING.get();
        if (!resolving.add(table)) return Result.EMPTY;
        try {
            Style style = table.getComputedStyle();
            Box box = Box.of(table);
            double horizontal = box.getBorderHorizontal() + box.getPaddingHorizontal();
            double basis = Size.getScaleWidth(table);
            Double constraint = Size.getNaturalContentWidthConstraint(table);
            boolean natural = Size.isNaturalMeasurementContext();
            boolean minimum = Size.isMinContentMeasurementContext();
            Double explicit = natural && constraint == null && style.widthLength().dependsOnPercentage()
                    ? null : style.widthLength().resolve(basis);
            Size assigned = natural ? null : table.getRenderer().size.get();
            double available = Math.max(0, basis - horizontal - box.getMarginHorizontal());
            boolean definite = forcedWidth != null || constraint != null || assigned != null || explicit != null;
            double width = forcedWidth != null ? forcedWidth : constraint != null ? constraint
                    : assigned != null ? Math.max(0, assigned.width() - horizontal)
                    : explicit != null ? Math.max(0, explicit - (style.isBorderBox() ? horizontal : 0))
                    : natural ? Double.NaN : available;
            if (minimum) width = 0;
            double mode = definite ? 1 : 0;
            Result cached = (Result) LayoutMeasureCache.getObject(LayoutMeasureCache.LAYOUT_TABLE,
                    table, width, mode, natural);
            if (cached != null) return cached;
            Result result = compute(table, width, definite);
            LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_TABLE, table, width, mode, natural, result);
            return result;
        } finally {
            resolving.remove(table);
            if (resolving.isEmpty()) RESOLVING.remove();
        }
    }

    private static Result compute(Element table, double available, boolean definite) {
        List<Row> rows = new ArrayList<>();
        List<Element> groups = new ArrayList<>();
        // shortcut: well-formed Markdown rows only; add anonymous boxes with full CSS table support.
        for (Element child : table.getRenderChildren()) {
            if (!child.getComputedStyle().isInFlow()) continue;
            if (isGroup(child.getComputedStyle().display)) {
                groups.add(child);
                collectRows(child, child, rows);
            } else if ("table-row".equals(child.getComputedStyle().display)) {
                rows.add(row(child, null));
            }
        }
        int columns = 0;
        for (Row row : rows) columns = Math.max(columns, row.cells.size());
        double[] minimum = new double[columns], preferred = new double[columns];
        for (Row row : rows) {
            for (int i = 0; i < row.cells.size(); i++) {
                Element cell = row.cells.get(i);
                minimum[i] = Math.max(minimum[i], Size.unconstrainedNatural(cell, true).width());
                preferred[i] = Math.max(preferred[i], Size.unconstrainedNatural(cell, false).width());
            }
        }
        double maximum = 0;
        for (int i = 0; i < columns; i++) maximum += Math.max(minimum[i], preferred[i]);
        double target = !definite && Double.isFinite(available) ? Math.min(maximum, available) : available;
        double[] widths = TableColumns.distribute(minimum, preferred, target);
        double width = 0;
        for (double column : widths) width += column;
        if (columns == 0 && definite && Double.isFinite(available)) width = Math.max(0, available);

        IdentityHashMap<Element, Entry> entries = new IdentityHashMap<>();
        IdentityHashMap<Element, Double> groupStarts = new IdentityHashMap<>();
        IdentityHashMap<Element, Double> groupHeights = new IdentityHashMap<>();
        Box tableBox = Box.of(table);
        double y = 0;
        for (Row row : rows) {
            double height = 0;
            for (int i = 0; i < row.cells.size(); i++) {
                Element cell = row.cells.get(i);
                Box box = Box.of(cell);
                height = Math.max(height, Size.naturalAtContentWidth(cell,
                        Math.max(0, widths[i] - box.getPaddingHorizontal() - box.getBorderHorizontal())).height());
            }
            Double rowHeight = row.element.getComputedStyle().heightLength().resolve(0);
            if (rowHeight != null) height = Math.max(height, rowHeight);
            double x = 0;
            for (int i = 0; i < row.cells.size(); i++) {
                entries.put(row.cells.get(i), new Entry(new Position(x, 0), new Size(widths[i], height)));
                x += widths[i];
            }
            double rowX = tableBox.offset("left"), rowY = tableBox.offset("top") + y;
            if (row.group != null) {
                groupStarts.putIfAbsent(row.group, y);
                rowX = 0;
                rowY = y - groupStarts.get(row.group);
                groupHeights.put(row.group, rowY + height);
            }
            entries.put(row.element, new Entry(new Position(rowX, rowY), new Size(width, height)));
            y += height;
        }
        for (Element group : groups) {
            entries.put(group, new Entry(new Position(tableBox.offset("left"),
                    tableBox.offset("top") + groupStarts.getOrDefault(group, y)),
                    new Size(width, groupHeights.getOrDefault(group, 0d))));
        }
        return new Result(entries, new Size(width, y));
    }

    private static void collectRows(Element parent, Element group, List<Row> rows) {
        for (Element child : parent.getRenderChildren()) {
            if (child.getComputedStyle().isInFlow() && "table-row".equals(child.getComputedStyle().display)) {
                rows.add(row(child, group));
            }
        }
    }

    private static Row row(Element element, Element group) {
        List<Element> cells = new ArrayList<>();
        for (Element child : element.getRenderChildren()) {
            if (child.getComputedStyle().isInFlow() && "table-cell".equals(child.getComputedStyle().display)) cells.add(child);
        }
        return new Row(element, group, cells);
    }
}

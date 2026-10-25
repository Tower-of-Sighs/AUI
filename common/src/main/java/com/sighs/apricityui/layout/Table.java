package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Shared columns, spans and row-group geometry for automatic tables.
 */
public final class Table {
    private static final ThreadLocal<Set<Element>> RESOLVING = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private Table() {
    }

    public static boolean isStructure(String display) {
        return "table".equals(display) || isGroup(display) || "table-row".equals(display);
    }

    private static boolean isGroup(String display) {
        return "table-row-group".equals(display) || "table-header-group".equals(display)
                || "table-footer-group".equals(display);
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
            Size assigned = natural ? null : table.getLayout().size.get();
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
        List<Section> sections = sections(table);
        List<Element> rows = new ArrayList<>();
        List<Cell> cells = new ArrayList<>();
        int columnCount = 0;
        for (Section section : sections) {
            int first = rows.size();
            rows.addAll(section.rows);
            List<Integer> occupied = new ArrayList<>();
            for (int r = first; r < rows.size(); r++) {
                int column = 0;
                for (Element element : rows.get(r).getRenderChildren()) {
                    Style style = element.getComputedStyle();
                    if (!style.isInFlow() || !"table-cell".equals(style.display)) continue;
                    while (column < occupied.size() && occupied.get(column) > r) column++;
                    int colspan = span(element, "colspan", 1000);
                    int rowspan = span(element, "rowspan", 65534);
                    rowspan = rowspan == 0 ? rows.size() - r : Math.min(rowspan, rows.size() - r);
                    while (occupied.size() < column + colspan) occupied.add(0);
                    for (int c = column; c < column + colspan; c++)
                        occupied.set(c, Math.max(occupied.get(c), r + rowspan));
                    double minimum = Size.unconstrainedNatural(element, true).width();
                    double preferred = Size.unconstrainedNatural(element, false).width();
                    CssLength length = style.widthLength();
                    double percent = length.isPercent() ? Math.max(0, length.resolveOr(0, 1)) : -1;
                    Double specified = length.dependsOnPercentage() ? null : length.resolve(0);
                    if (specified != null) {
                        Box box = Box.of(element);
                        preferred = Math.max(minimum, specified + (style.isBorderBox() ? 0
                                : box.getPaddingHorizontal() + box.getBorderHorizontal()));
                    }
                    cells.add(new Cell(element, r, column, colspan, rowspan, minimum, preferred, percent, specified != null));
                    column += colspan;
                    columnCount = Math.max(columnCount, column);
                }
            }
        }
        TableColumns.Column[] columns = new TableColumns.Column[columnCount];
        for (int i = 0; i < columnCount; i++) columns[i] = new TableColumns.Column();
        for (Cell cell : cells) {
            columns[cell.column].active = true;
            if (cell.colspan == 1) TableColumns.encompass(columns[cell.column], cell.minimum,
                    cell.preferred, cell.percent, cell.constrained);
        }
        cells.stream().filter(c -> c.colspan > 1)
                .sorted(Comparator.comparingInt(Cell::colspan).thenComparingInt(Cell::column))
                .forEach(c -> TableColumns.span(columns, c.column, c.colspan, c.minimum, c.preferred, c.percent, c.constrained));
        TableColumns.clampPercentages(columns);
        double maximum = TableColumns.preferredWidth(columns);
        double target = !definite && Double.isFinite(available) ? Math.min(maximum, available) : available;
        double[] widths = TableColumns.distribute(columns, target, true);
        double[] xs = offsets(widths);
        double width = xs[columnCount];
        if (columnCount == 0 && definite && Double.isFinite(available)) width = Math.max(0, available);

        double[] heights = new double[rows.size()];
        boolean[] constrained = new boolean[rows.size()], starts = new boolean[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            CssLength height = rows.get(r).getComputedStyle().heightLength();
            Double specified = height.dependsOnPercentage() ? null : height.resolve(0);
            constrained[r] = specified != null;
            if (specified != null) heights[r] = Math.max(0, specified);
        }
        List<TallCell> tall = new ArrayList<>();
        for (Cell cell : cells) {
            Box box = Box.of(cell.element);
            double height = Size.naturalAtContentWidth(cell.element, Math.max(0,
                    xs[cell.column + cell.colspan] - xs[cell.column] - box.getPaddingHorizontal() - box.getBorderHorizontal())).height();
            if (cell.rowspan == 1) {
                heights[cell.row] = Math.max(heights[cell.row], height);
                CssLength length = cell.element.getComputedStyle().heightLength();
                constrained[cell.row] |= !length.dependsOnPercentage() && length.resolve(0) != null;
            } else {
                starts[cell.row] = true;
                tall.add(new TallCell(cell, height));
            }
        }
        tall.sort(Comparator.comparingInt((TallCell t) -> t.cell.row + t.cell.rowspan)
                .thenComparingInt(t -> t.cell.rowspan));
        for (TallCell t : tall)
            TableRows.distribute(heights, constrained, starts, t.cell.row, t.cell.rowspan, t.height);
        double[] ys = offsets(heights);
        IdentityHashMap<Element, Entry> entries = new IdentityHashMap<>();
        for (Cell cell : cells)
            entries.put(cell.element, new Entry(new Position(xs[cell.column], 0),
                    new Size(xs[cell.column + cell.colspan] - xs[cell.column], ys[cell.row + cell.rowspan] - ys[cell.row])));
        Box tableBox = Box.of(table);
        int first = 0;
        for (Section section : sections) {
            int end = first + section.rows.size();
            if (section.element != null) entries.put(section.element, new Entry(
                    new Position(tableBox.offset("left"), tableBox.offset("top") + ys[first]),
                    new Size(width, ys[end] - ys[first])));
            for (int r = first; r < end; r++)
                entries.put(rows.get(r), new Entry(
                        section.element == null ? new Position(tableBox.offset("left"), tableBox.offset("top") + ys[r])
                                : new Position(0, ys[r] - ys[first]), new Size(width, heights[r])));
            first = end;
        }
        return new Result(entries, new Size(width, ys[rows.size()]));
    }

    private static double[] offsets(double[] sizes) {
        double[] result = new double[sizes.length + 1];
        for (int i = 0; i < sizes.length; i++) result[i + 1] = result[i] + sizes[i];
        return result;
    }

    private static List<Section> sections(Element table) {
        List<Section> result = new ArrayList<>();
        Section header = null, footer = null, direct = null;
        // shortcut: well-formed table structure only; add anonymous box repair with full CSS tables.
        for (Element child : table.getRenderChildren()) {
            Style style = child.getComputedStyle();
            if (!style.isInFlow()) continue;
            if (isGroup(style.display)) {
                direct = null;
                List<Element> rows = new ArrayList<>();
                for (Element row : child.getRenderChildren())
                    if (row.getComputedStyle().isInFlow()
                            && "table-row".equals(row.getComputedStyle().display)) rows.add(row);
                Section section = new Section(child, rows);
                if (header == null && "table-header-group".equals(style.display)) header = section;
                else if (footer == null && "table-footer-group".equals(style.display)) footer = section;
                else result.add(section);
            } else if ("table-row".equals(style.display)) {
                if (direct == null) {
                    direct = new Section(null, new ArrayList<>());
                    result.add(direct);
                }
                direct.rows.add(child);
            } else direct = null;
        }
        if (header != null) result.add(0, header);
        if (footer != null) result.add(footer);
        return result;
    }

    static int span(Element cell, String name, int limit) {
        if (!"TD".equalsIgnoreCase(cell.tagName) && !"TH".equalsIgnoreCase(cell.tagName)) return 1;
        String raw = cell.getAttribute(name);
        if (raw == null) return 1;
        int i = 0;
        while (i < raw.length() && " \t\n\r\f".indexOf(raw.charAt(i)) >= 0) i++;
        if (i < raw.length() && raw.charAt(i) == '+') i++;
        int start = i, value = 0;
        while (i < raw.length() && raw.charAt(i) >= '0' && raw.charAt(i) <= '9') {
            value = Math.min(limit, value * 10 + raw.charAt(i++) - '0');
        }
        if (i == start || (value == 0 && "colspan".equals(name))) return 1;
        return value;
    }

    private record Entry(Position position, Size size) {
    }

    private record Section(Element element, List<Element> rows) {
    }

    private record Cell(Element element, int row, int column, int colspan, int rowspan,
                        double minimum, double preferred, double percent, boolean constrained) {
    }

    private record TallCell(Cell cell, double height) {
    }

    private record Result(IdentityHashMap<Element, Entry> entries, Size content) {
        static final Result EMPTY = new Result(new IdentityHashMap<>(), Size.ZERO);
    }
}

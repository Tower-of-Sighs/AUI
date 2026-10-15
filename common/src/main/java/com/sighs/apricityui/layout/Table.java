package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CSS 表格布局：{@code table} / {@code table-row} / {@code table-cell} 的行列分配。
 *
 * <p>浏览器里 {@code <table>} 走的是自动表格布局：各列先取本列单元格的 max-content 宽度，
 * 再把表格的已用宽度按 max-content 比例分给各列，行高取该行最高单元格在该列宽下的高度。
 * 引擎原先把 {@code table-row}/{@code table-cell} 一律降级成 {@code block}，三列会塌成一列。</p>
 */
public final class Table {
    private static final ThreadLocal<Set<Element>> RESOLVING = ThreadLocal.withInitial(HashSet::new);

    private Table() {
    }

    private record Layout(List<Element> rows, double[] colW, double[] rowH, double width, double height) {
    }

    public static boolean isTableContainer(String display) {
        return "table".equals(display) || "inline-table".equals(display);
    }

    public static boolean isTableRowGroup(String display) {
        return "table-row-group".equals(display) || "table-header-group".equals(display)
                || "table-footer-group".equals(display);
    }

    public static boolean isTableRow(String display) {
        return "table-row".equals(display);
    }

    public static boolean isTableCell(String display) {
        return "table-cell".equals(display);
    }

    private static String displayOf(Element element) {
        if (element == null) return "";
        String display = element.getComputedStyle().display;
        return display == null ? "" : display.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 该 parent→child 这一层是否由表格布局接管。
     *
     * <p>只在父子都是真正的表格部件时才接管：主题可以用 {@code tr{display:flex}} 自己模拟表格
     * （t01 就是这么写的），那种单元格由 flex 分配，不能当成表格列。</p>
     */
    public static boolean handlesChildPosition(Element child, Element parent) {
        String childDisplay = displayOf(child);
        if (isTableCell(childDisplay)) return isTableRow(displayOf(parent));
        if (isTableRow(childDisplay)) {
            String parentDisplay = displayOf(parent);
            return isTableContainer(parentDisplay) || isTableRowGroup(parentDisplay);
        }
        return false;
    }

    /** 表格内容盒尺寸；没有任何表格行/单元格时返回 {@code null}，由调用方回退常规流。 */
    public static Size computeContentSize(Element table) {
        Layout layout = getOrComputeLayout(table);
        if (layout.colW().length == 0) return null;
        return new Size(layout.width(), layout.height());
    }

    /** 行组 / 行 / 单元格被分配到的尺寸；不属于表格时返回 {@code null}。 */
    public static Size resolveAssignedSize(Element element) {
        if (element == null || element.parentElement == null) return null;
        Element table = tableOf(element.parentElement);
        if (table == null || RESOLVING.get().contains(table)) return null;
        Layout layout = getOrComputeLayout(table);
        String display = displayOf(element);
        Element row = isTableCell(display) ? element.parentElement : element;
        int rowIndex = layout.rows().indexOf(row);
        if (rowIndex < 0) return null;
        if (isTableCell(display)) {
            int column = columnOf(row, element);
            if (column < 0 || column >= layout.colW().length) return null;
            return new Size(layout.colW()[column], layout.rowH()[rowIndex]);
        }
        if (isTableRow(display)) return new Size(layout.width(), layout.rowH()[rowIndex]);
        if (isTableRowGroup(display)) return new Size(layout.width(), groupHeight(layout, element));
        return null;
    }

    public static Position computeChildPosition(Element element, Element parent) {
        Box parentBox = Box.of(parent);
        Element table = tableOf(parent);
        if (table == null) return new Position(parentBox.offset("left"), parentBox.offset("top"));
        Layout layout = getOrComputeLayout(table);
        String display = displayOf(element);
        if (isTableCell(display)) {
            int column = columnOf(parent, element);
            double x = column >= 0 && column < layout.colW().length ? prefixSum(layout.colW(), column) : 0;
            // 单元格相对行盒定位（行盒自身的 y 由行所在的流给出）。
            return new Position(parentBox.offset("left") + x, parentBox.offset("top"));
        }
        int rowIndex = layout.rows().indexOf(element);
        double y = rowIndex >= 0 ? prefixSum(layout.rowH(), rowIndex) : 0;
        if (isTableRow(display) && isTableRowGroup(displayOf(parent))) {
            y -= groupOffset(layout, parent);
        }
        return new Position(parentBox.offset("left"), parentBox.offset("top") + y);
    }

    private static double groupOffset(Layout layout, Element group) {
        List<Element> rows = rowsOf(group);
        if (rows.isEmpty()) return 0;
        int firstIndex = layout.rows().indexOf(rows.get(0));
        return firstIndex > 0 ? prefixSum(layout.rowH(), firstIndex) : 0;
    }

    private static double groupHeight(Layout layout, Element group) {
        double height = 0;
        for (Element row : rowsOf(group)) {
            int index = layout.rows().indexOf(row);
            if (index >= 0) height += layout.rowH()[index];
        }
        return height;
    }

    private static double prefixSum(double[] values, int end) {
        double sum = 0;
        for (int i = 0; i < end && i < values.length; i++) sum += values[i];
        return sum;
    }

    private static int columnOf(Element row, Element cell) {
        List<Element> cells = cellsOf(row);
        return cells.indexOf(cell);
    }

    private static Element tableOf(Element from) {
        Element current = from;
        while (current != null) {
            String display = displayOf(current);
            if (isTableContainer(display)) return current;
            if (!isTableRow(display) && !isTableRowGroup(display) && !isTableCell(display)) return null;
            current = current.parentElement;
        }
        return null;
    }

    private static List<Element> rowsOf(Element group) {
        List<Element> rows = new ArrayList<>();
        for (Element child : group.getRenderChildren()) {
            if (!child.getComputedStyle().isInFlow()) continue;
            String display = displayOf(child);
            if (isTableRow(display) || isTableRowGroup(display)) rows.add(child);
        }
        return rows;
    }

    private static void collectRows(List<Element> children, List<Element> out) {
        for (Element child : children) {
            String display = displayOf(child);
            if (isTableRow(display)) {
                out.add(child);
            } else if (isTableRowGroup(display)) {
                for (Element row : rowsOf(child)) {
                    if (isTableRow(displayOf(row))) out.add(row);
                }
            }
        }
    }

    private static List<Element> cellsOf(Element row) {
        List<Element> cells = new ArrayList<>();
        for (Element child : row.getRenderChildren()) {
            if (isTableCell(displayOf(child)) && child.getComputedStyle().isInFlow()) cells.add(child);
        }
        return cells;
    }

    private static Layout getOrComputeLayout(Element table) {
        double availableWidth = resolveAvailableWidth(table);
        boolean natural = Size.isNaturalMeasurementContext();
        Layout cached = (Layout) LayoutMeasureCache.getObject(LayoutMeasureCache.LAYOUT_TABLE, table,
                availableWidth, Double.NaN, natural);
        if (cached != null) return cached;

        Set<Element> resolving = RESOLVING.get();
        if (!resolving.add(table)) return new Layout(List.of(), new double[0], new double[0], 0, 0);
        try {
            Layout result = computeLayout(table, availableWidth);
            LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_TABLE, table,
                    availableWidth, Double.NaN, natural, result);
            return result;
        } finally {
            resolving.remove(table);
            if (resolving.isEmpty()) RESOLVING.remove();
        }
    }

    private static double resolveAvailableWidth(Element table) {
        Style style = table.getComputedStyle();
        Box box = Box.of(table);
        CssLength widthLength = style.widthLength();
        double basis = Size.getScaleWidth(table);
        if (widthLength.resolve(basis) == null) {
            Size own = table.getRenderer().size.get();
            if (own != null && own.width() > 0) return Math.max(0, box.innerSize().width());
            return Double.NaN;
        }
        if (widthLength.isPercent() && basis <= 0) return Double.NaN;
        double resolved = widthLength.resolveOr(0, basis);
        double horizontalBox = box.getBorderHorizontal() + box.getPaddingHorizontal();
        return Math.max(0, style.isBorderBox() ? resolved - horizontalBox : resolved);
    }

    private static Layout computeLayout(Element table, double availableWidth) {
        List<Element> rows = new ArrayList<>();
        collectRows(table.getRenderChildren(), rows);
        int columns = 0;
        for (Element row : rows) columns = Math.max(columns, cellsOf(row).size());
        if (rows.isEmpty() || columns == 0) {
            return new Layout(List.of(), new double[0], new double[0], 0, 0);
        }

        double[] maxContent = new double[columns];
        for (Element row : rows) {
            List<Element> cells = cellsOf(row);
            for (int i = 0; i < cells.size(); i++) {
                maxContent[i] = Math.max(maxContent[i], Size.natural(cells.get(i)).width());
            }
        }
        double sum = 0;
        for (double width : maxContent) sum += width;

        double tableWidth = Double.isNaN(availableWidth) || availableWidth <= 0 ? sum : availableWidth;
        double[] colW = new double[columns];
        if (sum <= 0) {
            double each = tableWidth / columns;
            java.util.Arrays.fill(colW, each);
        } else if (tableWidth >= sum) {
            for (int i = 0; i < columns; i++) colW[i] = tableWidth * maxContent[i] / sum;
        } else {
            // 可用宽度不足：不再压缩（max-content 是各列的固有下限），让内容溢出表格宽度。
            System.arraycopy(maxContent, 0, colW, 0, columns);
        }

        double[] rowH = new double[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            List<Element> cells = cellsOf(rows.get(r));
            double height = 0;
            for (int i = 0; i < cells.size(); i++) {
                Element cell = cells.get(i);
                Box box = Box.of(cell);
                double contentWidth = Math.max(0, colW[i] - box.getMarginHorizontal()
                        - box.getBorderHorizontal() - box.getPaddingHorizontal());
                height = Math.max(height, Size.naturalAtContentWidth(cell, contentWidth).height()
                        + box.getMarginVertical());
            }
            rowH[r] = height;
        }

        double totalCol = 0;
        for (double width : colW) totalCol += width;
        double totalRow = 0;
        for (double height : rowH) totalRow += height;
        return new Layout(rows, colW, rowH, totalCol, totalRow);
    }
}

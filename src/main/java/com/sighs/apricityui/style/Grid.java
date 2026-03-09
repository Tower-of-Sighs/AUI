package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Global Grid layout (MVP + alignment + placement/span)
 * <p>
 * Supported:
 * - display: grid
 * - grid-template-columns / grid-template-rows: number | (px|auto)+
 * - gap / row-gap / column-gap
 * - justify-items / align-items (align-items reuses Style.alignItems)
 * - justify-self / align-self (per-item override)
 * - grid-row / grid-column with span (basic)
 * <p>
 * Placement notes:
 * - Auto flow is row-major.
 * - If grid-row/grid-column specifies an explicit start (e.g. "2" or "2 / span 3"),
 * the algorithm will try to place at/after that coordinate.
 * - Conflicts (overlaps) are allowed; this implementation is fail-soft.
 * <p>
 * Indexing:
 * - grid-row/grid-column use 1-based grid lines (CSS-like); internally converted to 0-based track index.
 */
public final class Grid {
    private Grid() {
    }

    private enum TrackType {FIXED, AUTO}

    private record Track(TrackType type, int px) {
    }

    private record Gaps(int rowGap, int colGap) {
    }

    private enum Align {START, CENTER, END, STRETCH}

    private record SpanSpec(int start, int span) {
        // start: 0-based track index, -1 means auto
        static SpanSpec auto() {
            return new SpanSpec(-1, 1);
        }
    }

    private record ItemSpec(SpanSpec col, SpanSpec row, Element el) {
    }

    private record Placement(int col, int row, int colSpan, int rowSpan) {
    }

    private record Layout(List<Element> flow,
                          List<Placement> placements,
                          List<Track> cols,
                          List<Track> rows,
                          int[] colW,
                          int[] rowH,
                          Gaps gaps) {
    }

    public static Position computeChildPosition(Element element, Element parent, List<Element> siblings) {
        Box parentBox = Box.of(parent);

        Layout layout = computeLayout(parent, siblings);

        int idx = layout.flow.indexOf(element);
        if (idx < 0) {
            // Not in normal flow (absolute/fixed/none), fallback to parent's content origin.
            return new Position(parentBox.offset("left"), parentBox.offset("top"));
        }

        Placement p = layout.placements.get(idx);

        double baseX = parentBox.offset("left") + prefixSum(layout.colW, p.col) + (double) p.col * layout.gaps.colGap;
        double baseY = parentBox.offset("top") + prefixSum(layout.rowH, p.row) + (double) p.row * layout.gaps.rowGap;

        double cellW = spanSum(layout.colW, p.col, p.colSpan) + (double) (p.colSpan - 1) * layout.gaps.colGap;
        double cellH = spanSum(layout.rowH, p.row, p.rowSpan) + (double) (p.rowSpan - 1) * layout.gaps.rowGap;

        Size itemSize = Size.box(element);
        double dx = computeJustifyOffset(element, parent, cellW, itemSize.width());
        double dy = computeAlignOffset(element, parent, cellH, itemSize.height());

        return new Position(baseX + dx, baseY + dy);
    }

    public static Size computeContentSize(Element gridContainer) {
        Layout layout = computeLayout(gridContainer, gridContainer.children);
        Box box = Box.of(gridContainer);

        if (layout.flow.isEmpty()) {
            double w = box.getBorderHorizontal() + box.getPaddingHorizontal();
            double h = box.getBorderVertical() + box.getPaddingVertical();
            return new Size(w, h);
        }

        double gridW = sum(layout.colW) + (double) layout.gaps.colGap * Math.max(0, layout.colW.length - 1);
        double gridH = sum(layout.rowH) + (double) layout.gaps.rowGap * Math.max(0, layout.rowH.length - 1);

        double totalW = gridW + box.getBorderHorizontal() + box.getPaddingHorizontal();
        double totalH = gridH + box.getBorderVertical() + box.getPaddingVertical();
        return new Size(totalW, totalH);
    }

    // ---------------- layout core ----------------

    private static Layout computeLayout(Element gridContainer, List<Element> siblings) {
        Style ps = gridContainer.getComputedStyle();

        Gaps gaps = parseGaps(ps);

        List<Element> flow = collectFlowChildren(siblings);
        if (flow.isEmpty()) {
            List<Track> cols0 = parseTracks(ps.gridTemplateColumns, 1);
            List<Track> rows0 = parseTracks(ps.gridTemplateRows, 1);
            return new Layout(flow, List.of(), cols0, rows0, new int[]{0}, new int[]{0}, gaps);
        }

        // 1) parse base tracks
        List<Track> cols = parseTracks(ps.gridTemplateColumns, 1);
        List<Track> rows = parseTracks(ps.gridTemplateRows, 0); // may be empty -> auto grow

        // 2) parse item specs
        List<ItemSpec> items = new ArrayList<>();
        int requiredCols = Math.max(1, cols.size());
        for (Element e : flow) {
            Style es = e.getComputedStyle();

            SpanSpec col = parseSpanSpec(es.gridColumn);
            SpanSpec row = parseSpanSpec(es.gridRow);

            // Ensure at least span columns exist even if start is auto
            requiredCols = Math.max(requiredCols, spanRequirement(col));

            // If explicit start, ensure enough columns
            if (col.start >= 0) requiredCols = Math.max(requiredCols, col.start + col.span);

            items.add(new ItemSpec(col, row, e));
        }

        // expand columns as needed
        while (cols.size() < requiredCols) cols.add(new Track(TrackType.AUTO, 0));

        int colCount = cols.size();

        // 3) auto placement with occupancy
        Occupancy occ = new Occupancy(colCount);

        List<Placement> placements = new ArrayList<>(items.size());
        int cursorRow = 0;
        int cursorCol = 0;

        for (ItemSpec it : items) {
            SpanSpec c = it.col;
            SpanSpec r = it.row;

            int colSpan = Math.max(1, c.span);
            int rowSpan = Math.max(1, r.span);

            // if span exceeds columns, extend columns (fail-soft)
            if (colSpan > colCount) {
                int add = colSpan - colCount;
                for (int i = 0; i < add; i++) cols.add(new Track(TrackType.AUTO, 0));
                colCount = cols.size();
                occ = occ.resize(colCount); // rebuild occupancy to new width
            }

            int placedCol;
            int placedRow;

            boolean hasCol = c.start >= 0;
            boolean hasRow = r.start >= 0;

            if (hasCol && hasRow) {
                placedCol = c.start;
                placedRow = r.start;
                occ.ensureRows(placedRow + rowSpan);
                // allow overlap (fail-soft)
                occ.mark(placedRow, placedCol, rowSpan, colSpan);
            } else if (hasRow) {
                // fixed row, find first fit in that row (or later rows)
                int[] rc = findFirstFit(occ, r.start, 0, rowSpan, colSpan);
                placedRow = rc[0];
                placedCol = rc[1];
                occ.mark(placedRow, placedCol, rowSpan, colSpan);
            } else if (hasCol) {
                // fixed col, find first fit in that column scanning rows
                int[] rc = findFirstFitAtCol(occ, 0, c.start, rowSpan, colSpan);
                placedRow = rc[0];
                placedCol = rc[1];
                occ.mark(placedRow, placedCol, rowSpan, colSpan);
            } else {
                // both auto: row-major from cursor
                int[] rc = findFirstFit(occ, cursorRow, cursorCol, rowSpan, colSpan);
                placedRow = rc[0];
                placedCol = rc[1];
                occ.mark(placedRow, placedCol, rowSpan, colSpan);

                // advance cursor
                cursorRow = placedRow;
                cursorCol = placedCol + colSpan;
                if (cursorCol >= colCount) {
                    cursorRow += 1;
                    cursorCol = 0;
                }
            }

            placements.add(new Placement(placedCol, placedRow, colSpan, rowSpan));
        }

        // 4) resolve required row count
        int requiredRows = 1;
        for (Placement p : placements) {
            requiredRows = Math.max(requiredRows, p.row + p.rowSpan);
        }

        if (rows.isEmpty()) {
            rows = makeAutoTracks(requiredRows);
        } else {
            while (rows.size() < requiredRows) rows.add(new Track(TrackType.AUTO, 0));
        }

        // 5) compute track sizes (fixed + auto sizing)
        int[] colW = computeColWidths(cols, placements, flow, gaps.colGap);
        int[] rowH = computeRowHeights(rows, placements, flow, gaps.rowGap);

        return new Layout(flow, placements, cols, rows, colW, rowH, gaps);
    }

    // ---------------- placement parsing ----------------

    private static int spanRequirement(SpanSpec spec) {
        // If start is auto, ensure we have at least span columns; otherwise start+span handled elsewhere.
        return spec.start < 0 ? spec.span : 0;
    }

    /**
     * Parse a CSS-like grid-row/grid-column value into (start, span).
     * <p>
     * Supported patterns:
     * - "auto" / "unset" / null -> auto, span 1
     * - "N" -> start line N, span 1
     * - "span S" -> auto start, span S
     * - "N / M" -> start line N, end line M => span = max(1, M - N)
     * - "N / span S" -> start line N, span S
     * - "auto / span S" -> auto start, span S
     */
    private static SpanSpec parseSpanSpec(String raw) {
        if (raw == null) return SpanSpec.auto();
        raw = raw.trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank() || "unset".equals(raw) || "auto".equals(raw)) return SpanSpec.auto();

        String[] parts = raw.split("/");
        String a = parts[0].trim();

        Integer start = null;
        Integer span = null;

        if (a.startsWith("span")) {
            span = parsePositiveInt(a.substring(4).trim(), 1);
        } else if ("auto".equals(a)) {
            start = -1;
        } else if (a.matches("^\\d+$")) {
            start = Math.max(1, Integer.parseInt(a)) - 1; // 1-based line -> 0-based track index
        } else {
            // unknown token -> auto
            start = -1;
        }

        if (parts.length >= 2) {
            String b = parts[1].trim();
            if (b.startsWith("span")) {
                span = parsePositiveInt(b.substring(4).trim(), 1);
            } else if (b.matches("^\\d+$") && start != null && start >= 0) {
                int endLine = Integer.parseInt(b);
                int startLine = start + 1; // back to 1-based line
                span = Math.max(1, endLine - startLine);
            } else {
                // unsupported / auto
            }
        }

        int s = (start == null) ? -1 : start;
        int sp = (span == null) ? 1 : Math.max(1, span);
        return new SpanSpec(s, sp);
    }

    private static int parsePositiveInt(String s, int fallback) {
        if (s == null) return fallback;
        s = s.trim();
        if (s.isEmpty()) return fallback;
        StringBuilder num = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) num.append(c);
            else break;
        }
        if (num.isEmpty()) return fallback;
        try {
            int v = Integer.parseInt(num.toString());
            return v > 0 ? v : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    // ---------------- track sizing ----------------

    private static int[] computeColWidths(List<Track> cols, List<Placement> placements, List<Element> flow, int colGap) {
        int colCount = cols.size();
        int[] w = new int[colCount];

        // fixed tracks first
        for (int i = 0; i < colCount; i++) {
            Track t = cols.get(i);
            if (t.type == TrackType.FIXED) w[i] = t.px;
        }

        // auto sizing based on items (including spanning)
        for (int idx = 0; idx < flow.size(); idx++) {
            Element el = flow.get(idx);
            Placement p = placements.get(idx);

            int span = Math.max(1, p.colSpan);
            int internalGaps = Math.max(0, span - 1) * colGap;

            int desired = (int) Math.ceil(Math.max(0, Size.box(el).width() - internalGaps));

            int fixedSum = 0;
            int autoCount = 0;
            for (int c = p.col; c < p.col + span && c < colCount; c++) {
                Track t = cols.get(c);
                if (t.type == TrackType.FIXED) fixedSum += t.px;
                else autoCount++;
            }

            if (autoCount <= 0) continue;

            int remain = Math.max(0, desired - fixedSum);
            int per = (int) Math.ceil(remain / (double) autoCount);

            for (int c = p.col; c < p.col + span && c < colCount; c++) {
                Track t = cols.get(c);
                if (t.type == TrackType.AUTO) {
                    w[c] = Math.max(w[c], per);
                }
            }
        }

        return w;
    }

    private static int[] computeRowHeights(List<Track> rows, List<Placement> placements, List<Element> flow, int rowGap) {
        int rowCount = rows.size();
        int[] h = new int[rowCount];

        // fixed tracks first
        for (int i = 0; i < rowCount; i++) {
            Track t = rows.get(i);
            if (t.type == TrackType.FIXED) h[i] = t.px;
        }

        // auto sizing based on items (including spanning)
        for (int idx = 0; idx < flow.size(); idx++) {
            Element el = flow.get(idx);
            Placement p = placements.get(idx);

            int span = Math.max(1, p.rowSpan);
            int internalGaps = Math.max(0, span - 1) * rowGap;

            int desired = (int) Math.ceil(Math.max(0, Size.box(el).height() - internalGaps));

            int fixedSum = 0;
            int autoCount = 0;
            for (int r = p.row; r < p.row + span && r < rowCount; r++) {
                Track t = rows.get(r);
                if (t.type == TrackType.FIXED) fixedSum += t.px;
                else autoCount++;
            }

            if (autoCount <= 0) continue;

            int remain = Math.max(0, desired - fixedSum);
            int per = (int) Math.ceil(remain / (double) autoCount);

            for (int r = p.row; r < p.row + span && r < rowCount; r++) {
                Track t = rows.get(r);
                if (t.type == TrackType.AUTO) {
                    h[r] = Math.max(h[r], per);
                }
            }
        }

        return h;
    }

    // ---------------- alignment ----------------

    private static double computeJustifyOffset(Element element, Element parent, double cellW, double itemW) {
        Style ps = parent.getComputedStyle();
        Style es = element.getComputedStyle();

        Align container = normalizeAlign(ps.justifyItems, Align.START);
        Align self = normalizeAlign(es.justifySelf, container);

        return switch (self) {
            case CENTER -> (cellW - itemW) / 2.0;
            case END -> (cellW - itemW);
            case STRETCH, START -> 0.0;
        };
    }

    private static double computeAlignOffset(Element element, Element parent, double cellH, double itemH) {
        Style ps = parent.getComputedStyle();
        Style es = element.getComputedStyle();

        Align container = normalizeAlign(ps.alignItems, Align.START);
        Align self = normalizeAlign(es.alignSelf, container);

        return switch (self) {
            case CENTER -> (cellH - itemH) / 2.0;
            case END -> (cellH - itemH);
            case STRETCH, START -> 0.0;
        };
    }

    private static Align normalizeAlign(String raw, Align fallback) {
        if (raw == null) return fallback;
        raw = raw.trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank() || "unset".equals(raw) || "auto".equals(raw)) return fallback;

        return switch (raw) {
            case "start", "flex-start", "left", "top" -> Align.START;
            case "center" -> Align.CENTER;
            case "end", "flex-end", "right", "bottom" -> Align.END;
            case "stretch" -> Align.STRETCH;
            default -> fallback;
        };
    }

    // ---------------- gaps / tracks parsing ----------------

    private static List<Element> collectFlowChildren(List<Element> siblings) {
        List<Element> flow = new ArrayList<>();
        for (Element c : siblings) {
            Style cs = c.getComputedStyle();
            if ("none".equals(cs.display)) continue;
            if ("absolute".equals(cs.position) || "fixed".equals(cs.position)) continue;
            flow.add(c);
        }
        return flow;
    }

    private static Gaps parseGaps(Style s) {
        int row = (s.rowGap != null && !"unset".equals(s.rowGap)) ? Size.parse(s.rowGap) : -1;
        int col = (s.columnGap != null && !"unset".equals(s.columnGap)) ? Size.parse(s.columnGap) : -1;

        String gap = (s.gap == null) ? "0px" : s.gap.trim();
        String[] parts = gap.split("\\s+");
        int a = parts.length > 0 ? Size.parse(parts[0]) : 0;
        int b = parts.length > 1 ? Size.parse(parts[1]) : a;

        if (row < 0) row = Math.max(0, a);
        if (col < 0) col = Math.max(0, b);
        return new Gaps(row, col);
    }

    /**
     * Parse track list.
     * Supported: number | (px|auto)+
     * - pure number: creates N auto tracks
     * - tokens: "18px auto 18px"
     */
    private static List<Track> parseTracks(String raw, int fallbackCount) {
        raw = raw == null ? "unset" : raw.trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank() || "unset".equals(raw)) {
            return makeAutoTracks(Math.max(1, fallbackCount));
        }

        // Pure number => track count
        if (raw.matches("^\\d+$")) {
            int n = Integer.parseInt(raw);
            return makeAutoTracks(Math.max(1, n));
        }

        String[] tokens = raw.split("\\s+");
        List<Track> out = new ArrayList<>();
        for (String t : tokens) {
            if (t.isBlank()) continue;
            if ("auto".equals(t)) {
                out.add(new Track(TrackType.AUTO, 0));
                continue;
            }

            int px = Size.parse(t);
            if (px >= 0) {
                out.add(new Track(TrackType.FIXED, px));
            } else {
                // Unknown token => treat as auto (fail-soft)
                out.add(new Track(TrackType.AUTO, 0));
            }
        }
        if (out.isEmpty()) return makeAutoTracks(Math.max(1, fallbackCount));
        return out;
    }

    private static List<Track> makeAutoTracks(int n) {
        List<Track> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Track(TrackType.AUTO, 0));
        return out;
    }

    // ---------------- occupancy + search ----------------

    private static final class Occupancy {
        private final int cols;
        private final List<boolean[]> rows = new ArrayList<>();

        Occupancy(int cols) {
            this.cols = Math.max(1, cols);
        }

        Occupancy resize(int newCols) {
            // Rebuild occupancy with new width, preserving existing marks where possible.
            Occupancy n = new Occupancy(newCols);
            for (boolean[] r : rows) {
                boolean[] nr = new boolean[newCols];
                int copy = Math.min(r.length, nr.length);
                System.arraycopy(r, 0, nr, 0, copy);
                n.rows.add(nr);
            }
            return n;
        }

        void ensureRows(int count) {
            while (rows.size() < count) {
                rows.add(new boolean[cols]);
            }
        }

        boolean fits(int row, int col, int rowSpan, int colSpan) {
            if (col < 0 || row < 0) return false;
            if (col + colSpan > cols) return false;
            ensureRows(row + rowSpan);
            for (int r = row; r < row + rowSpan; r++) {
                boolean[] rr = rows.get(r);
                for (int c = col; c < col + colSpan; c++) {
                    if (rr[c]) return false;
                }
            }
            return true;
        }

        void mark(int row, int col, int rowSpan, int colSpan) {
            ensureRows(row + rowSpan);
            int c0 = Math.max(0, col);
            int c1 = Math.min(cols, col + colSpan);
            for (int r = row; r < row + rowSpan; r++) {
                boolean[] rr = rows.get(r);
                for (int c = c0; c < c1; c++) rr[c] = true;
            }
        }
    }

    // find fit scanning row-major from (startRow, startCol)
    private static int[] findFirstFit(Occupancy occ, int startRow, int startCol, int rowSpan, int colSpan) {
        int row = Math.max(0, startRow);
        int col0 = Math.max(0, startCol);

        while (true) {
            occ.ensureRows(row + rowSpan);

            for (int col = col0; col <= occ.cols - colSpan; col++) {
                if (occ.fits(row, col, rowSpan, colSpan)) {
                    return new int[]{row, col};
                }
            }

            // next row
            row += 1;
            col0 = 0;
        }
    }

    // find fit at a fixed column scanning rows
    private static int[] findFirstFitAtCol(Occupancy occ, int startRow, int fixedCol, int rowSpan, int colSpan) {
        int row = Math.max(0, startRow);
        int col = Math.max(0, fixedCol);

        while (true) {
            if (occ.fits(row, col, rowSpan, colSpan)) {
                return new int[]{row, col};
            }
            row += 1;
        }
    }

    // ---------------- math helpers ----------------

    private static double sum(int[] arr) {
        double s = 0;
        for (int v : arr) s += v;
        return s;
    }

    private static double prefixSum(int[] arr, int count) {
        double s = 0;
        for (int i = 0; i < count && i < arr.length; i++) s += arr[i];
        return s;
    }

    private static double spanSum(int[] arr, int start, int span) {
        double s = 0;
        int end = Math.min(arr.length, start + span);
        for (int i = Math.max(0, start); i < end; i++) s += arr[i];
        return s;
    }
}

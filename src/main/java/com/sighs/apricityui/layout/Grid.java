package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.*;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Interaction;

/**
 * Global Grid layout (MVP + alignment + placement/span)
 * <p>
 * Supported:
 * - display: grid
 * - grid-template-columns / grid-template-rows: number | px | auto | fr | minmax() | repeat()
 * - repeat(auto-fill/auto-fit, Npx) for a basic auto-repeat variant
 * - gap / row-gap / column-gap
 * - justify-items / align-items (align-items reuses Style.alignItems)
 * - justify-self / align-self (per-item override)
 * - grid-row / grid-column with span (basic)
 */
public final class Grid {
    private static final ThreadLocal<Set<Element>> RESOLVING = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>())
    );

    private Grid() {
    }

    private enum TrackType {FIXED, AUTO, FR, MINMAX}

    private record Track(TrackType type, int px, double fr, Track minTrack, Track maxTrack) {
        static Track fixed(int px) {
            return new Track(TrackType.FIXED, Math.max(0, px), 0, null, null);
        }

        static Track auto() {
            return new Track(TrackType.AUTO, 0, 0, null, null);
        }

        static Track fr(double fr) {
            return new Track(TrackType.FR, 0, Math.max(0, fr), null, null);
        }

        static Track minmax(Track minTrack, Track maxTrack) {
            return new Track(TrackType.MINMAX, 0, 0, minTrack, maxTrack);
        }
    }

    private record ParsedTracks(List<Track> tracks) {
    }

    private record Gaps(int rowGap, int colGap) {
    }

    private record SpanSpec(int start, int span) {
        static SpanSpec auto() {
            return new SpanSpec(-1, 1);
        }
    }

    private record ItemSpec(SpanSpec col, SpanSpec row, Element el) {
    }

    private record Placement(int col, int row, int colSpan, int rowSpan) {
    }

    private record GridLayout(List<Element> flow,
                          List<Placement> placements,
                          List<Track> cols,
                          List<Track> rows,
                          double[] colW,
                          double[] rowH,
                          Gaps gaps) {
    }

    public static Position computeChildPosition(Element element, Element parent, List<Element> siblings) {
        Box parentBox = Box.of(parent);
        GridLayout layout = getOrComputeLayout(parent, siblings);

        int idx = layout.flow.indexOf(element);
        if (idx < 0) {
            return new Position(parentBox.offset("left"), parentBox.offset("top"));
        }

        Placement p = layout.placements.get(idx);
        double baseX = parentBox.offset("left") + prefixSum(layout.colW, p.col) + (double) p.col * layout.gaps.colGap;
        double baseY = parentBox.offset("top") + prefixSum(layout.rowH, p.row) + (double) p.row * layout.gaps.rowGap;

        double cellW = spanSum(layout.colW, p.col, p.colSpan) + (double) (p.colSpan - 1) * layout.gaps.colGap;
        double cellH = spanSum(layout.rowH, p.row, p.rowSpan) + (double) (p.rowSpan - 1) * layout.gaps.rowGap;

        Size assignedSize = resolveAssignedSize(element, parent, layout, p, cellW, cellH);
        Size itemSize = assignedSize != null ? assignedSize : Size.box(element);
        Style ps = parent.getComputedStyle();
        Style es = element.getComputedStyle();
        double dx = computeAlignmentOffset(ps.justifyItems, es.justifySelf, cellW, itemSize.width());
        double dy = computeAlignmentOffset(ps.alignItems, es.alignSelf, cellH, itemSize.height());
        return new Position(baseX + dx, baseY + dy);
    }

    /**
     * 如果网格项在对应轴上为 stretch（grid 默认），把它的大小设为网格区域大小。
     * 这样网格项不会溢出单元格，也符合浏览器默认行为。
     */
    public static Size resolveAssignedSize(Element element) {
        if (element == null || element.parentElement == null) return null;
        Element parent = element.parentElement;
        if (!Layout.isGridDisplay(parent.getComputedStyle().display)
                || RESOLVING.get().contains(parent)) return null;
        GridLayout layout = getOrComputeLayout(parent, parent.getRenderChildren());
        int index = layout.flow.indexOf(element);
        if (index < 0) return null;
        Placement placement = layout.placements.get(index);
        double cellW = spanSum(layout.colW, placement.col, placement.colSpan)
                + (double) Math.max(0, placement.colSpan - 1) * layout.gaps.colGap;
        double cellH = spanSum(layout.rowH, placement.row, placement.rowSpan)
                + (double) Math.max(0, placement.rowSpan - 1) * layout.gaps.rowGap;
        return resolveAssignedSize(element, parent, layout, placement, cellW, cellH);
    }

    private static Size resolveAssignedSize(Element element, Element parent, GridLayout layout,
                                            Placement placement, double cellW, double cellH) {
        Style parentStyle = parent.getComputedStyle();
        Style selfStyle = element.getComputedStyle();
        boolean stretchW = isGridStretch(parentStyle.justifyItems, selfStyle.justifySelf);
        boolean stretchH = isGridStretch(parentStyle.alignItems, selfStyle.alignSelf);
        if (!stretchW && !stretchH) return null;

        // 如果元素在对应轴上有明确尺寸，保持其显式大小，不做拉伸。
        boolean hasExplicitWidth = Size.parseNumber(selfStyle.width) != null;
        boolean hasExplicitHeight = Size.parseNumber(selfStyle.height) != null;
        if (stretchW && hasExplicitWidth) stretchW = false;
        if (stretchH && hasExplicitHeight) stretchH = false;
        if (!stretchW && !stretchH) return null;

        Size current = Size.natural(element);
        Box box = Box.of(element);
        double targetW = stretchW ? Math.max(0, cellW - box.getMarginHorizontal()) : current.width();
        double targetH = stretchH ? Math.max(0, cellH - box.getMarginVertical()) : current.height();

        // Stretch fills the grid area's margin box. Size stores the item's
        // used border-box size, independent of box-sizing.
        if (stretchW && hasContentBasedAutomaticMinimum(selfStyle, layout.cols,
                placement.col, placement.colSpan, true)) {
            targetW = Math.max(targetW, current.width());
        }
        if (stretchH && hasContentBasedAutomaticMinimum(selfStyle, layout.rows,
                placement.row, placement.rowSpan, false)) {
            targetH = Math.max(targetH, current.height());
        }

        double finalW = stretchW ? Math.max(0, targetW) : current.width();
        double finalH = stretchH ? Math.max(0, targetH) : current.height();
        return new Size(finalW, finalH);
    }

    private static boolean hasContentBasedAutomaticMinimum(Style style, List<Track> tracks,
                                                            int start, int span, boolean horizontal) {
        String minimum = horizontal ? style.minWidth : style.minHeight;
        if (Size.tryResolveLength(minimum, 0) != null) return false;
        String overflow = horizontal
                ? Interaction.resolveOverflowX(style)
                : Interaction.resolveOverflowY(style);
        if (!"visible".equals(Interaction.normalizeOverflow(overflow))) return false;

        boolean spansAutoMinimum = false;
        boolean spansFlexible = false;
        int resolvedSpan = Math.max(1, span);
        int end = Math.min(tracks.size(), start + resolvedSpan);
        for (int i = Math.max(0, start); i < end; i++) {
            Track track = tracks.get(i);
            spansAutoMinimum |= hasAutoMinimum(track);
            spansFlexible |= frWeight(track) > 0;
        }
        return spansAutoMinimum && (resolvedSpan <= 1 || !spansFlexible);
    }

    private static boolean hasAutoMinimum(Track track) {
        if (track == null) return false;
        return switch (track.type) {
            case AUTO, FR -> true;
            case FIXED -> false;
            case MINMAX -> track.minTrack != null && track.minTrack.type == TrackType.AUTO;
        };
    }

    private static boolean isGridStretch(String containerValue, String selfValue) {
        Align container = Align.normalize(containerValue, Align.STRETCH);
        Align self = Align.normalize(selfValue, container);
        return self == Align.STRETCH;
    }

    public static Size computeContentSize(Element gridContainer) {
        GridLayout layout = getOrComputeLayout(gridContainer, gridContainer.getRenderChildren());
        if (layout.flow.isEmpty()) return Size.ZERO;

        double gridW = sum(layout.colW) + (double) layout.gaps.colGap * Math.max(0, layout.colW.length - 1);
        double gridH = sum(layout.rowH) + (double) layout.gaps.rowGap * Math.max(0, layout.rowH.length - 1);
        return new Size(gridW, gridH);
    }

    private static GridLayout getOrComputeLayout(Element gridContainer, List<Element> siblings) {
        Size available = resolveAvailableTrackSpace(gridContainer);
        boolean natural = Size.isNaturalMeasurementContext();
        GridLayout cached = (GridLayout) LayoutMeasureCache.getObject(LayoutMeasureCache.LAYOUT_GRID, gridContainer,
                available.width(), available.height(), natural);
        if (cached != null) return cached;

        Set<Element> resolving = RESOLVING.get();
        if (!resolving.add(gridContainer)) {
            return new GridLayout(List.of(), List.of(), List.of(), List.of(), new double[]{0}, new double[]{0}, new Gaps(0, 0));
        }
        try {
            GridLayout result = computeLayout(gridContainer, siblings, available);
            LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_GRID, gridContainer,
                    available.width(), available.height(), natural, result);
            return result;
        } finally {
            resolving.remove(gridContainer);
            if (resolving.isEmpty()) RESOLVING.remove();
        }
    }

    private static GridLayout computeLayout(Element gridContainer, List<Element> siblings, Size availableSize) {
        Style ps = gridContainer.getComputedStyle();
        Gaps gaps = parseGaps(ps);
        List<Element> flow = collectFlowChildren(siblings);

        ParsedTracks parsedCols = parseTracks(ps.gridTemplateColumns, 1, availableSize.width(), gaps.colGap);
        ParsedTracks parsedRows = parseTracks(ps.gridTemplateRows, 0, availableSize.height(), gaps.rowGap);

        if (flow.isEmpty()) {
            List<Track> cols0 = parsedCols.tracks().isEmpty() ? makeAutoTracks(1) : parsedCols.tracks();
            List<Track> rows0 = parsedRows.tracks().isEmpty() ? makeAutoTracks(1) : parsedRows.tracks();
            return new GridLayout(flow, List.of(), cols0, rows0, new double[]{0}, new double[]{0}, gaps);
        }

        List<Track> cols = new ArrayList<>(parsedCols.tracks());
        List<Track> rows = new ArrayList<>(parsedRows.tracks());

        List<ItemSpec> items = new ArrayList<>();
        int requiredCols = Math.max(1, cols.size());
        for (Element e : flow) {
            Style es = e.getComputedStyle();
            SpanSpec col = parseSpanSpec(es.gridColumn);
            SpanSpec row = parseSpanSpec(es.gridRow);
            requiredCols = Math.max(requiredCols, spanRequirement(col));
            if (col.start >= 0) requiredCols = Math.max(requiredCols, col.start + col.span);
            items.add(new ItemSpec(col, row, e));
        }

        while (cols.size() < requiredCols) cols.add(Track.auto());
        int colCount = cols.size();
        Occupancy occ = new Occupancy(colCount);
        List<Placement> placements = new ArrayList<>(items.size());
        int cursorRow = 0;
        int cursorCol = 0;

        for (ItemSpec it : items) {
            SpanSpec c = it.col;
            SpanSpec r = it.row;
            int colSpan = Math.max(1, c.span);
            int rowSpan = Math.max(1, r.span);

            if (colSpan > colCount) {
                int add = colSpan - colCount;
                for (int i = 0; i < add; i++) cols.add(Track.auto());
                colCount = cols.size();
                occ = occ.resize(colCount);
            }

            int placedCol;
            int placedRow;
            boolean hasCol = c.start >= 0;
            boolean hasRow = r.start >= 0;

            if (hasCol && hasRow) {
                placedCol = c.start;
                placedRow = r.start;
                occ.ensureRows(placedRow + rowSpan);
                occ.mark(placedRow, placedCol, rowSpan, colSpan);
            } else if (hasRow) {
                int[] rc = findFirstFit(occ, r.start, 0, rowSpan, colSpan);
                placedRow = rc[0];
                placedCol = rc[1];
                occ.mark(placedRow, placedCol, rowSpan, colSpan);
            } else if (hasCol) {
                int[] rc = findFirstFitAtCol(occ, 0, c.start, rowSpan, colSpan);
                placedRow = rc[0];
                placedCol = rc[1];
                occ.mark(placedRow, placedCol, rowSpan, colSpan);
            } else {
                int[] rc = findFirstFit(occ, cursorRow, cursorCol, rowSpan, colSpan);
                placedRow = rc[0];
                placedCol = rc[1];
                occ.mark(placedRow, placedCol, rowSpan, colSpan);
                cursorRow = placedRow;
                cursorCol = placedCol + colSpan;
                if (cursorCol >= colCount) {
                    cursorRow += 1;
                    cursorCol = 0;
                }
            }

            placements.add(new Placement(placedCol, placedRow, colSpan, rowSpan));
        }

        int requiredRows = 1;
        for (Placement p : placements) {
            requiredRows = Math.max(requiredRows, p.row + p.rowSpan);
        }
        if (rows.isEmpty()) {
            rows = makeAutoTracks(requiredRows);
        } else {
            while (rows.size() < requiredRows) rows.add(Track.auto());
        }

        double[] colW = computeTrackSizes(cols, placements, flow, gaps.colGap, availableSize.width(), true, null, 0);
        double[] rowH = computeTrackSizes(rows, placements, flow, gaps.rowGap, availableSize.height(), false,
                colW, gaps.colGap);
        return new GridLayout(flow, placements, cols, rows, colW, rowH, gaps);
    }

    private static int spanRequirement(SpanSpec spec) {
        return spec.start < 0 ? spec.span : 0;
    }

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
            start = Math.max(1, Integer.parseInt(a)) - 1;
        } else {
            start = -1;
        }

        if (parts.length >= 2) {
            String b = parts[1].trim();
            if (b.startsWith("span")) {
                span = parsePositiveInt(b.substring(4).trim(), 1);
            } else if (b.matches("^\\d+$") && start != null && start >= 0) {
                int endLine = Integer.parseInt(b);
                int startLine = start + 1;
                span = Math.max(1, endLine - startLine);
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

    private static double[] computeTrackSizes(List<Track> tracks, List<Placement> placements, List<Element> flow,
                                           int gap, double availableSpace, boolean columnAxis,
                                           double[] resolvedColumns, int columnGap) {
        int count = tracks.size();
        double[] resolved = new double[count];
        boolean[] growable = new boolean[count];
        double totalFr = 0;

        for (int i = 0; i < count; i++) {
            Track track = tracks.get(i);
            resolved[i] = minimumTrackSize(track);
            if (canGrowForItemContribution(track)) growable[i] = true;
            totalFr += frWeight(track);
        }

        for (int idx = 0; idx < flow.size(); idx++) {
            Element el = flow.get(idx);
            Placement p = placements.get(idx);
            int start = columnAxis ? p.col : p.row;
            int span = Math.max(1, columnAxis ? p.colSpan : p.rowSpan);
            int internalGaps = Math.max(0, span - 1) * gap;
            // Track sizing must use the item's intrinsic contribution, not the
            // size assigned by this grid's resolved track layout. Reusing it
            // creates a feedback loop for auto-sized grids: a collapsed 0fr
            // row assigns 0px to its item, then a later 1fr layout measures that
            // stale 0px and can never grow even when the item now has children.
            Size naturalSize = columnAxis || resolvedColumns == null
                    ? Size.natural(el)
                    : measureAtGridAreaWidth(el, p, resolvedColumns, columnGap);
            Box itemBox = Box.of(el);
            double outerContribution = columnAxis
                    ? naturalSize.width() + itemBox.getMarginHorizontal()
                    : naturalSize.height() + itemBox.getMarginVertical();
            double desired = Math.max(0, outerContribution - internalGaps);

            double current = 0;
            int growableCount = 0;
            for (int i = start; i < start + span && i < count; i++) {
                current += resolved[i];
                if (growable[i]) growableCount++;
            }
            if (desired <= current || growableCount <= 0) continue;

            double extra = desired - current;
            double spanFr = 0;
            for (int i = start; i < start + span && i < count; i++) {
                spanFr += frWeight(tracks.get(i));
            }

            for (int i = start; i < start + span && i < count; i++) {
                if (!growable[i]) continue;
                double add;
                double weight = frWeight(tracks.get(i));
                if (spanFr > 0 && weight > 0) {
                    add = extra * (weight / spanFr);
                } else {
                    add = extra / growableCount;
                }
                resolved[i] = applyGrowthCap(tracks.get(i), resolved[i] + Math.max(0, add));
            }
        }

        double base = sum(resolved);
        double availableTracks = Math.max(0, availableSpace - (double) gap * Math.max(0, count - 1));
        if (availableTracks > base && totalFr > 0) {
            double remaining = availableTracks - base;
            distributeWeightedGrowth(tracks, resolved, remaining, totalFr);
        }

        return resolved;
    }

    private static Size measureAtGridAreaWidth(Element element, Placement placement,
                                               double[] resolvedColumns, int columnGap) {
        double areaWidth = spanSum(resolvedColumns, placement.col, placement.colSpan)
                + (double) Math.max(0, placement.colSpan - 1) * columnGap;
        Box box = Box.of(element);
        double contentWidth = areaWidth - box.getBorderHorizontal() - box.getPaddingHorizontal();
        return Size.naturalAtContentWidth(element, Math.max(0, contentWidth));
    }

    private static void distributeWeightedGrowth(List<Track> tracks, double[] resolved, double remaining, double totalFr) {
        if (remaining <= 0 || totalFr <= 0) return;
        double assigned = 0;
        int lastFlexible = -1;
        for (int i = 0; i < tracks.size(); i++) {
            double weight = frWeight(tracks.get(i));
            if (weight <= 0) continue;
            lastFlexible = i;
            double add = remaining * (weight / totalFr);
            resolved[i] = applyGrowthCap(tracks.get(i), resolved[i] + Math.max(0, add));
            assigned += Math.max(0, add);
        }
        double leftover = remaining - assigned;
        if (leftover > 0.000001 && lastFlexible >= 0) {
            resolved[lastFlexible] = applyGrowthCap(tracks.get(lastFlexible), resolved[lastFlexible] + leftover);
        }
    }

    private static int minimumTrackSize(Track track) {
        return switch (track.type) {
            case FIXED -> track.px;
            case AUTO, FR -> 0;
            case MINMAX -> minimumTrackSize(track.minTrack);
        };
    }

    private static boolean canGrow(Track track) {
        return switch (track.type) {
            case AUTO -> true;
            case FR -> track.fr > 0;
            case FIXED -> false;
            case MINMAX -> canGrowBeyondMinimum(track.maxTrack);
        };
    }

    private static boolean canGrowForItemContribution(Track track) {
        return switch (track.type) {
            case AUTO -> true;
            case FR -> track.fr > 0;
            case FIXED -> false;
            case MINMAX -> track.minTrack != null
                    && !(track.minTrack.type == TrackType.FIXED && track.minTrack.px == 0)
                    && canGrow(track.minTrack);
        };
    }

    private static boolean canGrowBeyondMinimum(Track track) {
        return switch (track.type) {
            case AUTO, FR -> true;
            case FIXED -> false;
            case MINMAX -> canGrowBeyondMinimum(track.maxTrack);
        };
    }

    private static double frWeight(Track track) {
        return switch (track.type) {
            case FR -> Math.max(0, track.fr);
            case MINMAX -> frWeight(track.maxTrack);
            default -> 0;
        };
    }

    private static double applyGrowthCap(Track track, double candidate) {
        return switch (track.type) {
            case FIXED -> track.px;
            case AUTO, FR -> Math.max(0, candidate);
            case MINMAX -> {
                double min = minimumTrackSize(track.minTrack);
                double capped = Math.max(min, candidate);
                if (track.maxTrack != null && track.maxTrack.type == TrackType.FIXED) {
                    capped = Math.min(capped, track.maxTrack.px);
                }
                yield capped;
            }
        };
    }

    /** 网格项在其单元格内的对齐偏移：container 提供默认，self 可覆盖。 */
    private static double computeAlignmentOffset(String containerRaw, String selfRaw,
                                                 double cellExtent, double itemExtent) {
        Align container = Align.normalize(containerRaw, Align.START);
        Align self = Align.normalize(selfRaw, container);
        return switch (self) {
            case CENTER -> (cellExtent - itemExtent) / 2.0;
            case END -> (cellExtent - itemExtent);
            case STRETCH, START -> 0.0;
        };
    }

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

    private static ParsedTracks parseTracks(String raw, int fallbackCount, double availableSpace, int gap) {
        raw = raw == null ? "unset" : raw.trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank() || "unset".equals(raw)) {
            return new ParsedTracks(makeAutoTracks(Math.max(1, fallbackCount)));
        }
        if (raw.matches("^\\d+$")) {
            int n = Integer.parseInt(raw);
            return new ParsedTracks(makeAutoTracks(Math.max(1, n)));
        }

        List<String> tokens = splitTopLevelWhitespace(raw);
        List<Track> out = new ArrayList<>();
        for (String token : tokens) {
            expandTrackToken(token, out, availableSpace, gap);
        }
        if (out.isEmpty()) return new ParsedTracks(makeAutoTracks(Math.max(1, fallbackCount)));
        return new ParsedTracks(out);
    }

    private static void expandTrackToken(String token, List<Track> out, double availableSpace, int gap) {
        if (token == null) return;
        String value = token.trim();
        if (value.isEmpty()) return;

        if (value.startsWith("repeat(") && value.endsWith(")")) {
            String inner = value.substring(7, value.length() - 1).trim();
            List<String> args = Background.splitTopLevelComma(inner);
            if (args.size() == 2) {
                String repeatCount = args.get(0).trim();
                List<String> repeated = splitTopLevelWhitespace(args.get(1));
                if ("auto-fill".equals(repeatCount) || "auto-fit".equals(repeatCount)) {
                    int resolved = resolveAutoRepeatCount(repeated, availableSpace, gap);
                    for (int i = 0; i < resolved; i++) {
                        for (String repeatedToken : repeated) {
                            expandTrackToken(repeatedToken, out, availableSpace, gap);
                        }
                    }
                    return;
                }

                Integer count = parsePositiveIntObject(repeatCount);
                if (count != null) {
                    for (int i = 0; i < count; i++) {
                        for (String repeatedToken : repeated) {
                            expandTrackToken(repeatedToken, out, availableSpace, gap);
                        }
                    }
                    return;
                }
            }
        }

        out.add(parseSingleTrack(value));
    }

    private static Track parseSingleTrack(String token) {
        if ("auto".equals(token)) return Track.auto();

        if (token.startsWith("minmax(") && token.endsWith(")")) {
            String inner = token.substring(7, token.length() - 1).trim();
            List<String> args = Background.splitTopLevelComma(inner);
            if (args.size() == 2) {
                Track minTrack = parseSingleTrack(args.get(0).trim());
                Track maxTrack = parseSingleTrack(args.get(1).trim());
                return Track.minmax(minTrack, maxTrack);
            }
            return Track.auto();
        }

        if (token.endsWith("fr")) {
            Double number = Size.parseNumber(token);
            return Track.fr(number == null ? 1d : number);
        }

        int px = Size.parse(token);
        if (px >= 0) return Track.fixed(px);
        return Track.auto();
    }

    private static int resolveAutoRepeatCount(List<String> repeated, double availableSpace, int gap) {
        if (repeated == null || repeated.isEmpty()) return 1;
        int baseSize = 0;
        for (String token : repeated) {
            Track track = parseSingleTrack(token);
            baseSize += switch (track.type) {
                case FIXED -> track.px;
                case MINMAX -> minimumTrackSize(track);
                default -> 0;
            };
        }
        if (baseSize <= 0 || availableSpace <= 0) return 1;
        return Math.max(1, (int) Math.floor((availableSpace + gap) / (baseSize + gap)));
    }

    private static Integer parsePositiveIntObject(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (!raw.matches("^\\d+$")) return null;
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<Track> makeAutoTracks(int n) {
        List<Track> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(Track.auto());
        return out;
    }

    private static Size resolveAvailableTrackSpace(Element gridContainer) {
        Style style = gridContainer.getComputedStyle();
        Box box = Box.of(gridContainer);
        boolean borderBox = box.isBorderBox();
        double widthBasis = Size.getScaleWidth(gridContainer);
        double width = resolveAvailableAxisSize(style.width, widthBasis, box.getBorderHorizontal() + box.getPaddingHorizontal(), borderBox);
        Double explicitParentHeight = Size.getExplicitContainingBlockHeight(gridContainer);
        double heightBasis = explicitParentHeight != null ? explicitParentHeight : 0;
        double height = resolveAvailableAxisSize(style.height, heightBasis, box.getBorderVertical() + box.getPaddingVertical(), borderBox);
        return new Size(width, height);
    }

    private static double resolveAvailableAxisSize(String raw, double percentBasis, double boxExtent, boolean borderBox) {
        Double parsed = Size.parseNumber(raw);
        if (parsed == null) {
            return Math.max(0, percentBasis);
        }
        if (Size.isPercent(raw) && percentBasis <= 0) {
            return 0;
        }
        double resolved = Size.resolveLength(raw, percentBasis, parsed);
        return Math.max(0, borderBox ? resolved - boxExtent : resolved);
    }

    private static List<String> splitTopLevelWhitespace(String value) {
        return Layout.splitTopLevelWhitespace(value);
    }

    private static final class Occupancy {
        private final int cols;
        private final List<boolean[]> rows = new ArrayList<>();

        Occupancy(int cols) {
            this.cols = Math.max(1, cols);
        }

        Occupancy resize(int newCols) {
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
            while (rows.size() < count) rows.add(new boolean[cols]);
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

    private static int[] findFirstFit(Occupancy occ, int startRow, int startCol, int rowSpan, int colSpan) {
        int row = Math.max(0, startRow);
        int col0 = Math.max(0, startCol);
        while (true) {
            occ.ensureRows(row + rowSpan);
            for (int col = col0; col <= occ.cols - colSpan; col++) {
                if (occ.fits(row, col, rowSpan, colSpan)) return new int[]{row, col};
            }
            row += 1;
            col0 = 0;
        }
    }

    private static int[] findFirstFitAtCol(Occupancy occ, int startRow, int fixedCol, int rowSpan, int colSpan) {
        int row = Math.max(0, startRow);
        int col = Math.max(0, fixedCol);
        while (true) {
            if (occ.fits(row, col, rowSpan, colSpan)) return new int[]{row, col};
            row += 1;
        }
    }

    private static double sum(double[] arr) {
        double s = 0;
        for (double v : arr) s += v;
        return s;
    }

    private static double prefixSum(double[] arr, int count) {
        double s = 0;
        for (int i = 0; i < count && i < arr.length; i++) s += arr[i];
        return s;
    }

    private static double spanSum(double[] arr, int start, int span) {
        double s = 0;
        int end = Math.min(arr.length, start + span);
        for (int i = Math.max(0, start); i < end; i++) s += arr[i];
        return s;
    }
}

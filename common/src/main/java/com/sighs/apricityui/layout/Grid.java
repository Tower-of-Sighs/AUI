package com.sighs.apricityui.layout;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.ShorthandParser;
import com.sighs.apricityui.style.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 位置和尺寸共享同一次布局结果，避免两条计算路径使用不同的轨道。 */
public final class Grid {
    /** Grid 自动放置和盒对齐关键字，与 Flex 使用相同的取值接口。 */
    public static final class KeywordValue {
        public final String value;

        public KeywordValue(String value) {
            this.value = value;
        }

        public boolean is(String keyword) {
            return keyword.equals(value);
        }

        public boolean contains(String part) {
            return value != null && value.contains(part);
        }

        public String value() {
            return value;
        }
    }

    public KeywordValue gridAutoFlow;
    public KeywordValue alignContent;
    public KeywordValue justifyContent;
    public KeywordValue alignItems;
    public KeywordValue justifyItems;

    public Grid(Style style) {
        gridAutoFlow = new KeywordValue(style.gridAutoFlow);
        alignContent = new KeywordValue(style.alignContent);
        justifyContent = new KeywordValue(style.justifyContent);
        alignItems = new KeywordValue(style.alignItems);
        justifyItems = new KeywordValue(style.justifyItems);
    }

    public static Grid of(Element element) {
        Style style = element.getComputedStyle();
        Grid cached = element.getRenderer().gridCache;
        if (cached != null
                && keywordEquals(cached.gridAutoFlow, style.gridAutoFlow)
                && keywordEquals(cached.alignContent, style.alignContent)
                && keywordEquals(cached.justifyContent, style.justifyContent)
                && keywordEquals(cached.alignItems, style.alignItems)
                && keywordEquals(cached.justifyItems, style.justifyItems)) {
            return cached;
        }
        Grid grid = new Grid(style);
        element.getRenderer().gridCache = grid;
        return grid;
    }

    private static boolean keywordEquals(KeywordValue keyword, String value) {
        return java.util.Objects.equals(keyword.value, value);
    }

    public static final int MAX_TRACKS = 10_000;
    private static final double EPSILON = 0.000001;
    private static final KeywordValue ROW = new KeywordValue("row");
    private static final KeywordValue STRETCH = new KeywordValue("stretch");
    private static final ThreadLocal<Set<Element>> LAYOUT_IN_PROGRESS = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private enum TrackSizingKind { LENGTH, AUTO, MIN, MAX, FR }
    private record TrackBreadth(TrackSizingKind kind, CssLength length, double weight) {
        double fixed(double basis) {
            if (kind != TrackSizingKind.LENGTH || (!Double.isFinite(basis) && length.dependsOnPercentage())) return Double.NaN;
            Double n = length.resolve(Double.isFinite(basis) ? basis : 0);
            return n == null || !Double.isFinite(n) ? Double.NaN : Math.max(0, n);
        }
    }

    private record GridTrack(TrackBreadth min, TrackBreadth max, CssLength fit) {
        double weight() {
            return max.kind == TrackSizingKind.FR ? max.weight : 0;
        }
        boolean autoMinimum() {
            return min.kind == TrackSizingKind.AUTO;
        }
    }

    private static final TrackBreadth AUTO = new TrackBreadth(TrackSizingKind.AUTO, null, 0);
    private static final GridTrack AUTO_TRACK = new GridTrack(AUTO, AUTO, null);
    private static final class GridAxis {
        final List<GridTrack> tracks = new ArrayList<>();
        final Map<String, List<Integer>> names = new LinkedHashMap<>();
        final BitSet autoFit = new BitSet();
        int explicit, origin;
        double[] sizes, starts, ends;
        boolean[] collapsed;
        double extent;
        void name(String name, int line) {
            names.computeIfAbsent(name, ignored -> new ArrayList<>()).add(line);
        }
        void extend(int before, int after, List<GridTrack> implicit) {
            origin = Math.min(MAX_TRACKS - 1, Math.max(0, before));
            int explicitSize = tracks.size();
            ArrayList<GridTrack> all = new ArrayList<>();
            for (int i = -origin; i < 0; i++) {
                all.add(implicit.get(Math.floorMod(i, implicit.size())));
            }
            all.addAll(tracks);
            int count = Math.min(MAX_TRACKS, Math.max(all.size(), origin + after));
            while (all.size() < count) {
                all.add(implicit.get(Math.floorMod(all.size() - origin - explicitSize, implicit.size())));
            }
            if (all.size() > MAX_TRACKS) all.subList(MAX_TRACKS, all.size()).clear();
            tracks.clear();
            tracks.addAll(all);
            collapsed = new boolean[tracks.size()];
        }
        double areaStart(int line) {
            return line < starts.length ? starts[Math.max(0, line)] : extent;
        }
        double areaEnd(int line) {
            return line > 0 ? ends[Math.min(ends.length, line) - 1] : 0;
        }
        double span(int start, int span) {
            return Math.max(0, areaEnd(start + span) - areaStart(start));
        }
    }

    private record GridLine(boolean span, int number, String name, boolean auto) {}
    private record GridRange(Integer start, int span) {}
    private static final class GridParticipant {
        final Element element;
        final Text text;
        final GridRange columnPlacement, rowPlacement;
        int column, row, columnSpan, rowSpan;
        Size used;
        Text.WrappedText wrapped;
        GridParticipant(Element element, Text text, GridRange column, GridRange row) {
            this.element = element;
            this.text = text;
            this.columnPlacement = column;
            this.rowPlacement = row;
            columnSpan = column.span;
            rowSpan = row.span;
        }
        int order() {
            return element == null ? 0 : Flex.resolveOrder(element);
        }
    }

    public record Area(double x, double y, double width, double height) {}
    private record GridItemLayout(Position position, Size size) {}
    private record GridLayoutResult(IdentityHashMap<Element, GridItemLayout> items, List<Layout.DirectTextLayout> text,
                          GridAxis columns, GridAxis rows, Size content) {
        static final GridLayoutResult EMPTY = new GridLayoutResult(new IdentityHashMap<>(), List.of(), null, null, Size.ZERO);
    }

    public static Position computeChildPosition(Element element, Element parent, List<Element> siblings) {
        GridItemLayout entry = getOrComputeLayout(parent).items.get(element);
        Box box = Box.of(parent);
        return entry == null ? new Position(box.offset("left"), box.offset("top")) : entry.position;
    }

    public static Size resolveAssignedSize(Element element) {
        Element parent = element == null ? null : element.parentElement;
        if (parent == null || !parent.getComputedStyle().isGridDisplay() || LAYOUT_IN_PROGRESS.get().contains(parent)) return null;
        GridItemLayout entry = getOrComputeLayout(parent).items.get(element);
        return entry == null ? null : entry.size;
    }

    public static Size computeContentSize(Element element) {
        return getOrComputeLayout(element).content;
    }

    public static List<Layout.DirectTextLayout> computeDirectTextLayouts(Element element) {
        if (element == null || !element.getComputedStyle().isGridDisplay()) return List.of();
        return getOrComputeLayout(element).text;
    }

    private static GridLayoutResult getOrComputeLayout(Element parent) {
        if (parent == null) return GridLayoutResult.EMPTY;
        Set<Element> resolving = LAYOUT_IN_PROGRESS.get();
        if (!resolving.add(parent)) return GridLayoutResult.EMPTY;
        try {
            double width = resolveAvailableSize(parent, true), height = resolveAvailableSize(parent, false);
            boolean natural = Size.isNaturalMeasurementContext();
            GridLayoutResult cached = (GridLayoutResult) LayoutMeasureCache.getObject(LayoutMeasureCache.LAYOUT_GRID, parent, width, height, natural);
            if (cached != null) return cached;
            GridLayoutResult result = computeGridLayout(parent, width, height);
            LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_GRID, parent, width, height, natural, result);
            return result;
        }
        finally {
            resolving.remove(parent);
            if (resolving.isEmpty()) LAYOUT_IN_PROGRESS.remove();
        }
    }

    private static double resolveAvailableSize(Element parent, boolean horizontal) {
        Style style = parent.getComputedStyle();
        Box box = Box.of(parent);
        CssLength length = horizontal ? style.widthLength() : style.heightLength();
        double extent = horizontal ? box.getBorderHorizontal() + box.getPaddingHorizontal()
                : box.getBorderVertical() + box.getPaddingVertical();
        Double definiteHeight = Size.getExplicitContainingBlockHeight(parent);
        double basis = horizontal ? Size.getScaleWidth(parent) : definiteHeight == null ? Double.NaN : definiteHeight;
        Double declared = !horizontal && length.isPercent() && !Double.isFinite(basis) ? null : length.resolve(Double.isFinite(basis) ? basis : 0);
        if (declared != null) return Math.max(0, declared - (style.isBorderBox() ? extent : 0));
        if (horizontal) {
            Double constraint = Size.getNaturalContentWidthConstraint(parent);
            if (constraint != null) return constraint;
            if (Size.isNaturalMeasurementContext() || "inline-grid".equals(style.display)) return Double.NaN;
            Size assigned = parent.getRenderer().size.get();
            if (assigned != null) return Math.max(0, Box.of(parent).innerSize().width());
            assigned = resolveAssignedSize(parent);
            if (assigned != null) return Math.max(0, assigned.width() - extent);
            return Math.max(0, basis - extent - box.getMarginHorizontal());
        }
        Size assigned = Size.isNaturalMeasurementContext() ? null : parent.getRenderer().size.get();
        return assigned == null ? Double.NaN : Math.max(0, assigned.height() - extent);
    }

    private static TrackBreadth parseTrackBreadth(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "auto" -> AUTO;
            case "min-content" -> new TrackBreadth(TrackSizingKind.MIN, null, 0);
            case "max-content" -> new TrackBreadth(TrackSizingKind.MAX, null, 0);
            default -> value.endsWith("fr") ? new TrackBreadth(TrackSizingKind.FR, null, Double.parseDouble(value.substring(0, value.length() - 2)))
                    : new TrackBreadth(TrackSizingKind.LENGTH, CssLength.parse(value), 0);
        };
    }

    private static GridTrack parseTrack(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("minmax(")) {
            List<String> args = ShorthandParser.parseGridFunctionArguments(raw);
            return new GridTrack(parseTrackBreadth(args.get(0)), parseTrackBreadth(args.get(1)), null);
        }
        if (lower.startsWith("fit-content(")) return new GridTrack(AUTO, new TrackBreadth(TrackSizingKind.MAX, null, 0), CssLength.parse(ShorthandParser.parseGridFunctionArguments(raw).get(0)));
        TrackBreadth b = parseTrackBreadth(raw);
        return new GridTrack(b.kind == TrackSizingKind.FR ? AUTO : b, b, null);
    }

    private static List<GridTrack> parseImplicitTracks(String raw) {
        ArrayList<GridTrack> tracks = new ArrayList<>();
        if (ShorthandParser.isValidGridTrackList(raw, false)) for (String token : ShorthandParser.splitGridTokens(raw)) tracks.add(parseTrack(token));
        return tracks.isEmpty() ? List.of(AUTO_TRACK) : tracks;
    }

    private static GridAxis parseTemplateTracks(String raw, double available, double gap) {
        GridAxis axis = new GridAxis();
        if (raw == null || !ShorthandParser.isValidGridTrackList(raw, true) || "none".equalsIgnoreCase(raw)) return axis;
        List<String> tokens = ShorthandParser.splitGridTokens(raw);
        String autoRepeat = null;
        for (String token : tokens) {
            if (token.toLowerCase(Locale.ROOT).startsWith("repeat(")) {
                List<String> args = ShorthandParser.parseGridFunctionArguments(token);
                if (args.get(0).toLowerCase(Locale.ROOT).startsWith("auto-")) autoRepeat = token;
            }
        }
        int repetitions = 1;
        if (autoRepeat != null && Double.isFinite(available)) {
            GridAxis rest = new GridAxis(), repeated = new GridAxis();
            for (String token : tokens) {
                if (!token.equals(autoRepeat)) appendTrackToken(rest, token, 1, false);
            }
            appendTrackList(repeated, ShorthandParser.parseGridFunctionArguments(autoRepeat).get(1));
            double restSize = computeRepeatSize(rest, available);
            double repeatedSize = computeRepeatSize(repeated, available);
            double room = available - restSize - gap * Math.max(0, rest.tracks.size() - 1);
            double unit = Math.max(1, repeatedSize) + gap * repeated.tracks.size();
            repetitions = Math.max(1, Math.min(MAX_TRACKS,
                    (int) Math.floor((room + (rest.tracks.isEmpty() ? gap : 0)) / unit)));
        }
        for (String token : tokens) {
            appendTrackToken(axis, token, repetitions, true);
        }
        axis.explicit = axis.tracks.size();
        return axis;
    }

    private static double computeRepeatSize(GridAxis axis, double basis) {
        double sum = 0;
        for (GridTrack t : axis.tracks) {
            double min = t.min.fixed(basis), max = t.max.fixed(basis);
            sum += Double.isFinite(max) ? Math.max(Double.isFinite(min) ? min : 0, max) : Double.isFinite(min) ? Math.max(1, min) : 1;
        }
        return sum;
    }

    private static void appendTrackList(GridAxis axis, String raw) {
        for (String token : ShorthandParser.splitGridTokens(raw)) {
            appendTrackToken(axis, token, 1, false);
        }
    }

    private static void appendTrackToken(GridAxis axis, String token, int autoCount, boolean preserveFit) {
        if (token.startsWith("[")) {
            for (String name : ShorthandParser.splitGridTokens(token.substring(1, token.length() - 1))) {
                axis.name(name, axis.tracks.size());
            }
        } else if (token.toLowerCase(Locale.ROOT).startsWith("repeat(")) {
            List<String> a = ShorthandParser.parseGridFunctionArguments(token);
            Integer n = ShorthandParser.parseGridInteger(a.get(0));
            int count = n == null ? autoCount : n;
            boolean fit = preserveFit && "auto-fit".equalsIgnoreCase(a.get(0));
            for (int i = 0; i < count && axis.tracks.size() < MAX_TRACKS; i++) {
                int start = axis.tracks.size();
                appendTrackList(axis, a.get(1));
                if (fit) axis.autoFit.set(start, axis.tracks.size());
            }
        } else if (axis.tracks.size() < MAX_TRACKS) axis.tracks.add(parseTrack(token));
    }

    private static GridLine parseLine(String raw) {
        boolean span = false;
        int number = 0;
        String name = null;
        for (String token : ShorthandParser.splitGridTokens(raw)) {
            if ("auto".equalsIgnoreCase(token)) return new GridLine(false, 0, null, true);
            if ("span".equalsIgnoreCase(token)) span = true;
            else if (ShorthandParser.parseGridInteger(token) != null) number = ShorthandParser.parseGridInteger(token);
            else name = token;
        }
        return new GridLine(span, number == 0 && (name != null || span) ? 1 : number, name, number == 0 && name == null && !span);
    }

    private static Integer resolveLine(GridAxis axis, GridLine line, boolean end) {
        if (line.auto || line.span) return null;
        int n = line.number;
        if (line.name == null) return n > 0 ? n - 1 : axis.explicit + n + 1;
        List<Integer> named = axis.names.get(line.name + (end ? "-end" : "-start"));
        if (n == 1 && named != null && !named.isEmpty()) return named.get(0);
        named = axis.names.getOrDefault(line.name, List.of());
        List<Integer> unique = named.stream().distinct().sorted().toList();
        if (n > 0) return n <= unique.size() ? unique.get(n - 1) : axis.explicit + n - unique.size();
        return -n <= unique.size() ? unique.get(unique.size() + n) : n + unique.size();
    }

    private static int namedSpan(GridAxis axis, int anchor, GridLine line, boolean backwards) {
        int count = Math.max(1, line.number), cursor = anchor;
        Set<Integer> names = Set.copyOf(axis.names.getOrDefault(line.name, List.of()));
        int steps = 0;
        while (count > 0 && steps++ < MAX_TRACKS) {
            cursor += backwards ? -1 : 1;
            if (line.name == null || names.contains(cursor) || cursor < 0 || cursor > axis.explicit) count--;
        }
        return Math.max(1, Math.abs(cursor - anchor));
    }

    private static GridRange resolveItemRange(GridAxis axis, String startRaw, String endRaw) {
        GridLine start = parseLine(startRaw), end = parseLine(endRaw);
        if (start.span && end.span) end = new GridLine(false, 0, null, true);
        Integer a = resolveLine(axis, start, false), b = resolveLine(axis, end, true);
        if (a != null && b != null) return new GridRange(Math.min(a, b), Math.min(MAX_TRACKS, Math.max(1, Math.abs(b - a))));
        if (a != null) return new GridRange(a, end.span ? namedSpan(axis, a, end, false) : 1);
        if (b != null) {
            int span = start.span ? namedSpan(axis, b, start, true) : 1;
            return new GridRange(b - span, span);
        }
        return new GridRange(null, Math.max(1, start.span ? start.number : end.span ? end.number : 1));
    }

    private static List<GridParticipant> buildParticipants(Element parent, GridAxis columns, GridAxis rows) {
        List<GridParticipant> items = new ArrayList<>();
        boolean hasText = false;
        for (Node node : parent.getRenderChildNodes()) {
            if (node instanceof Element element) {
                Style style = element.getComputedStyle();
                if (!style.isInFlow() || "none".equals(style.display)) continue;
                items.add(new GridParticipant(element, null, resolveItemRange(columns, style.gridColumnStart, style.gridColumnEnd), resolveItemRange(rows, style.gridRowStart, style.gridRowEnd)));
            } else if (node instanceof TextNode textNode) {
                Text text = Layout.directText(parent, textNode.getTextContent());
                if (text != null) {
                    items.add(new GridParticipant(null, text, new GridRange(null, 1), new GridRange(null, 1)));
                    hasText = true;
                }
            }
        }
        if (!hasText && parent.innerText != null && !parent.innerText.isBlank()) {
            Text text = Layout.directText(parent, parent.innerText);
            if (text != null) items.add(new GridParticipant(null, text, new GridRange(null, 1), new GridRange(null, 1)));
        }
        items.sort(Comparator.comparingInt(GridParticipant::order));
        return items;
    }

    private static void applyTemplateAreas(Style style, GridAxis columns, GridAxis rows) {
        Map<String, int[]> areas = ShorthandParser.parseGridTemplateAreas(style.gridTemplateAreas);
        if (areas == null) return;
        List<String> strings = ShorthandParser.splitGridTokens(style.gridTemplateAreas);
        if (!strings.isEmpty() && !"none".equalsIgnoreCase(style.gridTemplateAreas)) {
            int cols = strings.get(0).substring(1, strings.get(0).length() - 1).trim().split("\\s+").length;
            while (columns.tracks.size() < Math.min(MAX_TRACKS, cols)) {
                columns.tracks.add(AUTO_TRACK);
            }
            while (rows.tracks.size() < Math.min(MAX_TRACKS, strings.size())) {
                rows.tracks.add(AUTO_TRACK);
            }
        }
        columns.explicit = columns.tracks.size();
        rows.explicit = rows.tracks.size();
        areas.forEach((name, a) -> {
            rows.name(name + "-start", a[0]);
            rows.name(name + "-end", a[2]);
            columns.name(name + "-start", a[1]);
            columns.name(name + "-end", a[3]);
        });
    }

    private static final class Occupancy {
        final Map<Integer, BitSet> lines = new LinkedHashMap<>();
        boolean fits(int major, int minor, int majorSpan, int minorSpan, int minorCount) {
            if (minor < 0 || minor + minorSpan > minorCount || major < 0 || major + majorSpan > MAX_TRACKS) return false;
            for (int i = major; i < major + majorSpan; i++) {
                BitSet occupied = lines.get(i);
                if (occupied == null) continue;
                int next = occupied.nextSetBit(minor);
                if (next >= 0 && next < minor + minorSpan) return false;
            }
            return true;
        }
        void mark(int major, int minor, int majorSpan, int minorSpan) {
            for (int i = major; i < Math.min(MAX_TRACKS, major + majorSpan); i++)
                lines.computeIfAbsent(i, ignored -> new BitSet()).set(minor, Math.min(MAX_TRACKS, minor + minorSpan));
        }
    }

    private static void placeItems(List<GridParticipant> items, GridAxis columns, GridAxis rows, Style style, Grid grid) {
        KeywordValue autoFlow = resolveKeyword(grid.gridAutoFlow, ROW);
        boolean columnFlow = autoFlow.contains("column"), dense = autoFlow.contains("dense");
        GridAxis minorAxis = columnFlow ? rows : columns, majorAxis = columnFlow ? columns : rows;
        int beforeMinor = 0, beforeMajor = 0, minorCount = minorAxis.explicit, majorCount = majorAxis.explicit;
        for (GridParticipant item : items) {
            GridRange minor = columnFlow ? item.rowPlacement : item.columnPlacement, major = columnFlow ? item.columnPlacement : item.rowPlacement;
            beforeMinor = Math.max(beforeMinor, minor.start == null ? 0 : -minor.start);
            beforeMajor = Math.max(beforeMajor, major.start == null ? 0 : -major.start);
            minorCount = Math.max(minorCount, minor.start == null ? minor.span : minor.start + minor.span);
            majorCount = Math.max(majorCount, major.start == null ? 0 : major.start + major.span);
        }
        minorAxis.extend(beforeMinor, items.isEmpty() ? minorCount : Math.max(1, minorCount), parseImplicitTracks(columnFlow ? style.gridAutoRows : style.gridAutoColumns));
        majorAxis.extend(beforeMajor, majorCount, parseImplicitTracks(columnFlow ? style.gridAutoColumns : style.gridAutoRows));
        minorCount = Math.max(1, minorAxis.tracks.size());
        Occupancy occupied = new Occupancy();
        Set<GridParticipant> placed = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Integer, Integer> lockedCursor = new LinkedHashMap<>();
        // 显式项先占格，其次放置固定行/列的项，最后推进自动放置游标。
        for (int phase = 0; phase < 3; phase++) {
            int cursorMajor = 0, cursorMinor = 0;
            for (GridParticipant item : items) {
                if (placed.contains(item)) continue;
                GridRange minor = columnFlow ? item.rowPlacement : item.columnPlacement, major = columnFlow ? item.columnPlacement : item.rowPlacement;
                boolean fixedMinor = minor.start != null, fixedMajor = major.start != null;
                if (phase == 0 && !(fixedMinor && fixedMajor) || phase == 1 && !fixedMajor) continue;
                int minorSpan = Math.min(minorCount, minor.span), majorSpan = Math.min(MAX_TRACKS, major.span);
                int majorPosition = fixedMajor ? Math.max(0, Math.min(MAX_TRACKS - majorSpan, major.start + majorAxis.origin)) : dense ? 0 : cursorMajor;
                int minorPosition = fixedMinor ? Math.max(0, Math.min(minorCount - minorSpan, minor.start + minorAxis.origin)) : dense || fixedMajor ? 0 : cursorMinor;
                if (phase == 1 && !dense && !fixedMinor) minorPosition = lockedCursor.getOrDefault(majorPosition, 0);
                if (fixedMajor) minorCount = Math.min(MAX_TRACKS, Math.max(minorCount, minorPosition + minorSpan));
                if (phase == 2 && fixedMinor && !dense && minorPosition < cursorMinor) majorPosition++;
                if (!(fixedMinor && fixedMajor)) {
                    while (!occupied.fits(majorPosition, minorPosition, majorSpan, minorSpan, minorCount)) {
                        if (!fixedMinor) minorPosition++;
                        if (fixedMinor || minorPosition + minorSpan > minorCount) {
                            if (fixedMajor) {
                                minorCount = Math.min(MAX_TRACKS, minorPosition + minorSpan);
                            } else {
                                minorPosition = fixedMinor ? minorPosition : 0;
                                majorPosition++;
                            }
                        }
                        if (majorPosition + majorSpan > MAX_TRACKS || fixedMajor && minorCount == MAX_TRACKS) {
                            majorPosition = Math.min(majorPosition, MAX_TRACKS - majorSpan);
                            minorPosition = Math.min(minorPosition, minorCount - minorSpan);
                            break;
                        }
                    }
                }
                occupied.mark(majorPosition, minorPosition, majorSpan, minorSpan);
                item.column = columnFlow ? majorPosition : minorPosition;
                item.row = columnFlow ? minorPosition : majorPosition;
                item.columnSpan = columnFlow ? majorSpan : minorSpan;
                item.rowSpan = columnFlow ? minorSpan : majorSpan;
                placed.add(item);
                if (phase == 1) lockedCursor.put(majorPosition, minorPosition + minorSpan);
                if (phase == 2 && !dense) {
                    cursorMajor = majorPosition;
                    cursorMinor = minorPosition + minorSpan;
                    if (cursorMinor >= minorCount) {
                        cursorMajor++;
                        cursorMinor = 0;
                    }
                }
                majorCount = Math.max(majorCount, majorPosition + majorSpan - majorAxis.origin);
            }
        }
        appendImplicitTracks(minorAxis, minorCount, parseImplicitTracks(columnFlow ? style.gridAutoRows : style.gridAutoColumns));
        appendImplicitTracks(majorAxis, majorCount + majorAxis.origin, parseImplicitTracks(columnFlow ? style.gridAutoColumns : style.gridAutoRows));
        collapseEmptyAutoFitTracks(columns, items, true);
        collapseEmptyAutoFitTracks(rows, items, false);
    }

    private static void appendImplicitTracks(GridAxis axis, int count, List<GridTrack> implicit) {
        while (axis.tracks.size() < Math.min(MAX_TRACKS, count))
            axis.tracks.add(implicit.get(Math.floorMod(axis.tracks.size() - axis.origin - axis.explicit, implicit.size())));
        axis.collapsed = new boolean[axis.tracks.size()];
    }

    private static void collapseEmptyAutoFitTracks(GridAxis axis, List<GridParticipant> items, boolean horizontal) {
        for (int i = axis.autoFit.nextSetBit(0); i >= 0; i = axis.autoFit.nextSetBit(i + 1)) {
            int index = i + axis.origin;
            if (index >= axis.collapsed.length) continue;
            axis.collapsed[index] = true;
            for (GridParticipant item : items) {
                int start = horizontal ? item.column : item.row, span = horizontal ? item.columnSpan : item.rowSpan;
                if (index >= start && index < start + span) {
                    axis.collapsed[index] = false;
                    break;
                }
            }
        }
    }

    private static double resolveGap(String raw, double basis) {
        Double value = CssLength.parse(raw).resolve(Double.isFinite(basis) ? basis : 0);
        return value == null || !Double.isFinite(value) ? 0 : Math.max(0, value);
    }

    private static double sumGaps(GridAxis axis, int start, int span, double gap) {
        double total = 0;
        for (int i = start + 1; i < Math.min(axis.tracks.size(), start + span); i++)
            if (!axis.collapsed[i - 1] && !axis.collapsed[i]) total += gap;
        return total;
    }

    private static double measureContribution(GridParticipant item, boolean horizontal, boolean minimum, GridAxis columns) {
        if (item.element == null) {
            if (horizontal) return minimum ? Text.minContentWidth(item.text) : Text.wrap(item.text, 0).width();
            return Text.wrap(item.text, Math.max(EPSILON, columns.span(item.column, item.columnSpan))).height(item.text.lineHeight);
        }
        Element element = item.element;
        Box box = Box.of(element);
        Size size;
        if (horizontal) size = minimum ? Size.minContent(element) : Size.natural(element);
        else {
            double area = columns.span(item.column, item.columnSpan);
            size = Size.naturalAtContentWidth(element, Math.max(0, area - box.getMarginHorizontal() - box.getBorderHorizontal() - box.getPaddingHorizontal()));
        }
        return Math.max(0, horizontal ? size.width() + box.getMarginHorizontal() : size.height() + box.getMarginVertical());
    }

    private static double resolveAutomaticMinimum(GridParticipant item, GridAxis axis, boolean horizontal, double minimum) {
        if (item.element == null) return minimum;
        Style style = item.element.getComputedStyle();
        Interaction.Overflow overflow = horizontal ? style.overflowX() : style.overflowY();
        // clip is non-scrollable and retains the content-based minimum.
        if (overflow != Interaction.Overflow.VISIBLE && overflow != Interaction.Overflow.CLIP) return 0;
        int start = horizontal ? item.column : item.row, span = horizontal ? item.columnSpan : item.rowSpan;
        boolean auto = false, flexible = false;
        for (int i = start; i < Math.min(axis.tracks.size(), start + span); i++) {
            auto |= axis.tracks.get(i).autoMinimum();
            flexible |= axis.tracks.get(i).weight() > 0;
        }
        CssLength min = horizontal ? style.minWidthLength() : style.minHeightLength();
        CssLength preferred = horizontal ? style.widthLength() : style.heightLength();
        if (!preferred.isPercent() && preferred.resolve(0) != null) return span > 1 && flexible ? 0 : minimum;
        Double explicit = min.resolve(0);
        if (explicit != null) return Math.max(0, explicit);
        return auto && (span == 1 || !flexible) ? minimum : 0;
    }

    private static void distributeExtraSpace(double[] values, double[] caps, List<Integer> candidates, double extra) {
        List<Integer> active = new ArrayList<>(candidates);
        while (extra > EPSILON && !active.isEmpty()) {
            double share = extra / active.size(), spent = 0;
            for (int i : active) {
                double add = Math.max(0, Math.min(share, caps[i] - values[i]));
                values[i] += add;
                spent += add;
            }
            extra -= spent;
            active.removeIf(i -> caps[i] - values[i] <= EPSILON);
            if (spent <= EPSILON) break;
        }
    }

    private static double sumTrackSizes(double[] values, int start, int span) {
        double total = 0;
        for (int i = start; i < Math.min(values.length, start + span); i++) {
            total += values[i];
        }
        return total;
    }

    private static double findFractionSize(GridAxis axis, double[] sizes, double space, int start, int span) {
        List<Integer> active = new ArrayList<>();
        double remaining = space;
        for (int i = start; i < Math.min(sizes.length, start + span); i++) {
            if (!axis.collapsed[i] && axis.tracks.get(i).weight() > 0) active.add(i);
            else remaining -= sizes[i];
        }
        double fraction = 0;
        while (!active.isEmpty()) {
            double weight = 0;
            for (int i : active) weight += axis.tracks.get(i).weight();
            fraction = Math.max(0, remaining / Math.max(1, weight));
            final double candidate = fraction;
            List<Integer> frozen = active.stream()
                    .filter(i -> sizes[i] > candidate * axis.tracks.get(i).weight() + EPSILON).toList();
            if (frozen.isEmpty()) break;
            for (int i : frozen) {
                remaining -= sizes[i];
                active.remove(Integer.valueOf(i));
            }
        }
        return fraction;
    }

    private static void computeTrackSizes(GridAxis axis, List<GridParticipant> items, double available, double gap,
                                   boolean horizontal, GridAxis columns, KeywordValue alignment) {
        int count = axis.tracks.size();
        double[] base = new double[count], limit = new double[count], intrinsic = new double[count];
        double[] infinite = new double[count];
        Arrays.fill(infinite, Double.POSITIVE_INFINITY);
        for (int i = 0; i < count; i++) {
            GridTrack track = axis.tracks.get(i);
            double min = track.min.fixed(available), max = track.max.fixed(available);
            base[i] = Double.isFinite(min) ? min : 0;
            limit[i] = Double.isFinite(max) ? Math.max(base[i], max) : Double.POSITIVE_INFINITY;
            intrinsic[i] = base[i];
            if (axis.collapsed[i]) base[i] = limit[i] = intrinsic[i] = 0;
        }
        List<GridParticipant> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(i -> horizontal ? i.columnSpan : i.rowSpan));
        for (GridParticipant item : sorted) {
            int start = horizontal ? item.column : item.row, span = horizontal ? item.columnSpan : item.rowSpan;
            double min = measureContribution(item, horizontal, true, columns), max = measureContribution(item, horizontal, false, columns);
            double auto = resolveAutomaticMinimum(item, axis, horizontal, min), internal = sumGaps(axis, start, span, gap);
            for (int phase = 0; phase < 2; phase++) {
                List<Integer> candidates = new ArrayList<>();
                double desired = 0;
                for (int i = start; i < Math.min(count, start + span); i++) {
                    if (axis.collapsed[i]) continue;
                    GridTrack t = axis.tracks.get(i);
                    if (phase == 0 && !Double.isFinite(t.min.fixed(available))) {
                        candidates.add(i);
                        desired = Math.max(desired, t.min.kind == TrackSizingKind.MAX ? max : t.min.kind == TrackSizingKind.MIN ? min : auto);
                    } else if (phase == 1 && !Double.isFinite(t.max.fixed(available))) {
                        candidates.add(i);
                        desired = Math.max(desired, t.max.kind == TrackSizingKind.MIN ? min : max);
                    }
                }
                double[] target = phase == 0 ? base : intrinsic;
                boolean spansFlexibleTracks = span > 1 && candidates.stream().anyMatch(i -> axis.tracks.get(i).weight() > 0);
                if (spansFlexibleTracks) {
                    double fraction = findFractionSize(axis, target, desired - internal, start, span);
                    for (int i : candidates) {
                        target[i] = Math.max(target[i], fraction * axis.tracks.get(i).weight());
                    }
                } else {
                    distributeExtraSpace(target, phase == 0 ? limit : infinite, candidates,
                            Math.max(0, desired - internal - sumTrackSizes(target, start, span)));
                    if (phase == 0) distributeExtraSpace(target, infinite, candidates,
                            Math.max(0, desired - internal - sumTrackSizes(target, start, span)));
                }
            }
        }
        for (int i = 0; i < count; i++) {
            GridTrack t = axis.tracks.get(i);
            if (!Double.isFinite(limit[i])) {
                limit[i] = t.max.kind == TrackSizingKind.FR ? Double.POSITIVE_INFINITY : Math.max(base[i], intrinsic[i]);
                if (t.fit != null) {
                    Double cap = t.fit.resolve(Double.isFinite(available) ? available : 0);
                    if (cap != null) limit[i] = Math.max(base[i], Math.min(limit[i], cap));
                }
            }
        }
        double space = Double.isFinite(available) ? Math.max(0, available - sumGaps(axis, 0, count, gap)) : Double.NaN;
        List<Integer> grow = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (axis.tracks.get(i).max.kind != TrackSizingKind.FR && !axis.collapsed[i]) grow.add(i);
        }
        if (Double.isFinite(space)) distributeExtraSpace(base, limit, grow, Math.max(0, space - sumTrackSizes(base, 0, count)));
        else for (int i : grow) base[i] = Math.max(base[i], limit[i]);
        List<Integer> flexible = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (axis.tracks.get(i).weight() > 0 && !axis.collapsed[i]) flexible.add(i);
        }
        double factor = 0;
        if (Double.isFinite(space)) {
            factor = findFractionSize(axis, base, space, 0, count);
        } else {
            for (int i : flexible) factor = Math.max(factor, Math.max(base[i], intrinsic[i]) / axis.tracks.get(i).weight());
        }
        for (int i : flexible) {
            base[i] = Math.max(base[i], factor * axis.tracks.get(i).weight());
        }
        KeywordValue align = resolveKeyword(alignment, STRETCH);
        if (Double.isFinite(space) && (align.is("stretch") || align.is("normal"))) {
            List<Integer> autos = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                if (axis.tracks.get(i).max.kind == TrackSizingKind.AUTO && !axis.collapsed[i]) autos.add(i);
            }
            distributeExtraSpace(base, infinite, autos, Math.max(0, space - sumTrackSizes(base, 0, count)));
        }
        axis.sizes = base;
        positionTracks(axis, available, gap, alignment);
    }

    private static KeywordValue resolveKeyword(KeywordValue keyword, KeywordValue fallback) {
        String raw = keyword.value();
        if (raw == null || raw.isBlank()) return fallback;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "auto", "unset", "initial" -> fallback;
            default -> normalized.equals(raw) ? keyword : new KeywordValue(normalized);
        };
    }

    private static double resolveAlignmentOffset(KeywordValue alignment, double free) {
        String raw = alignment.value();
        if (raw.startsWith("safe ") && free < 0) return 0;
        KeywordValue value = alignment.contains("safe ")
                ? new KeywordValue(raw.replace("unsafe ", "").replace("safe ", "")) : alignment;
        if (value.is("center")) return free / 2;
        if (value.is("end") || value.is("flex-end") || value.is("self-end") || value.is("right")) return free;
        return 0;
    }

    private static void positionTracks(GridAxis axis, double available, double gap, KeywordValue alignment) {
        int count = axis.sizes.length, visible = 0;
        for (boolean collapsed : axis.collapsed) {
            if (!collapsed) visible++;
        }
        double total = sumTrackSizes(axis.sizes, 0, count) + sumGaps(axis, 0, count, gap);
        double free = Double.isFinite(available) ? available - total : 0;
        KeywordValue align = resolveKeyword(alignment, STRETCH);
        double offset = resolveAlignmentOffset(align, free), extra = 0;
        if (free > 0 && visible > 0) {
            switch (align.value()) {
                case "space-between" -> {
                    if (visible > 1) extra = free / (visible - 1);
                }
                case "space-around" -> {
                    extra = free / visible;
                    offset = extra / 2;
                }
                case "space-evenly" -> {
                    extra = free / (visible + 1);
                    offset = extra;
                }
                default -> {}
            }
        } else if (align.is("space-around") || align.is("space-evenly")) offset = Math.max(0, free) / 2;
        axis.starts = new double[count];
        axis.ends = new double[count];
        double cursor = offset;
        boolean seen = false;
        for (int i = 0; i < count; i++) {
            if (i > 0 && !axis.collapsed[i - 1] && !axis.collapsed[i]) cursor += gap;
            if (!axis.collapsed[i] && seen) cursor += extra;
            axis.starts[i] = cursor;
            cursor += axis.sizes[i];
            axis.ends[i] = cursor;
            if (!axis.collapsed[i]) seen = true;
        }
        axis.extent = cursor;
    }

    private static double clampItemSize(Element element, double borderSize, double area, boolean horizontal) {
        Style style = element.getComputedStyle();
        Box elementBox = Box.of(element);
        double boxExtent = horizontal ? elementBox.getBorderHorizontal() + elementBox.getPaddingHorizontal() : elementBox.getBorderVertical() + elementBox.getPaddingVertical();
        Double min = (horizontal ? style.minWidthLength() : style.minHeightLength()).resolve(area);
        Double max = (horizontal ? style.maxWidthLength() : style.maxHeightLength()).resolve(area);
        if (max != null) borderSize = Math.min(borderSize, Math.max(boxExtent, max + (style.isBorderBox() ? 0 : boxExtent)));
        if (min != null) borderSize = Math.max(borderSize, min + (style.isBorderBox() ? 0 : boxExtent));
        return Math.max(boxExtent, borderSize);
    }

    private static boolean hasAutoMargin(Style style, boolean horizontal, boolean end) {
        return "auto".equals(horizontal ? end ? style.marginRight : style.marginLeft : end ? style.marginBottom : style.marginTop);
    }

    private static KeywordValue resolveItemAlignment(Grid grid, Style item, boolean horizontal) {
        KeywordValue fallback = resolveKeyword(horizontal ? grid.justifyItems : grid.alignItems, STRETCH);
        return resolveKeyword(new KeywordValue(horizontal ? item.justifySelf : item.alignSelf), fallback);
    }

    private static Size resolveItemUsedSize(GridParticipant item, Grid grid, GridAxis columns, GridAxis rows) {
        double areaWidth = columns.span(item.column, item.columnSpan);
        double areaHeight = rows.span(item.row, item.rowSpan);
        if (item.element == null) {
            item.wrapped = Text.wrap(item.text, Math.max(EPSILON, areaWidth));
            item.text.size = new Size(item.wrapped.width(), item.wrapped.height(item.text.lineHeight));
            return item.text.size;
        }
        Element element = item.element;
        Style style = element.getComputedStyle();
        Box box = Box.of(element);
        double boxHorizontal = box.getBorderHorizontal() + box.getPaddingHorizontal();
        double boxVertical = box.getBorderVertical() + box.getPaddingVertical();
        KeywordValue horizontalAlignment = resolveItemAlignment(grid, style, true);
        KeywordValue verticalAlignment = resolveItemAlignment(grid, style, false);
        Double explicitWidth = style.widthLength().resolve(areaWidth);
        Double explicitHeight = style.heightLength().resolve(areaHeight);
        boolean stretchWidth = (horizontalAlignment.is("normal") || horizontalAlignment.is("stretch"))
                && !hasAutoMargin(style, true, false) && !hasAutoMargin(style, true, true);
        boolean stretchHeight = (verticalAlignment.is("normal") || verticalAlignment.is("stretch"))
                && !hasAutoMargin(style, false, false) && !hasAutoMargin(style, false, true);
        Double aspectRatio = Size.parseAspectRatio(style.aspectRatio);
        if (aspectRatio != null && verticalAlignment.is("normal")) stretchHeight = false;
        Size naturalSize = Size.natural(element);
        double width;
        if (explicitWidth != null) {
            width = explicitWidth + (style.isBorderBox() ? 0 : boxHorizontal);
        } else {
            double availableWidth = Math.max(boxHorizontal, areaWidth - box.getMarginHorizontal());
            width = stretchWidth ? availableWidth : Math.min(naturalSize.width(), availableWidth);
            width = Math.max(width, resolveAutomaticMinimum(item, columns, true, Size.minContent(element).width()));
        }
        width = clampItemSize(element, width, areaWidth, true);
        Size measuredSize = Size.naturalAtContentWidth(element, Math.max(0, width - boxHorizontal));
        double height;
        if (explicitHeight != null) {
            height = explicitHeight + (style.isBorderBox() ? 0 : boxVertical);
        } else {
            height = stretchHeight ? Math.max(boxVertical, areaHeight - box.getMarginVertical()) : measuredSize.height();
        }
        if (aspectRatio != null && aspectRatio > 0) {
            if (explicitHeight == null && !(stretchWidth && stretchHeight)) {
                height = style.isBorderBox() ? width / aspectRatio : (width - boxHorizontal) / aspectRatio + boxVertical;
            } else if (explicitWidth == null && !stretchWidth && explicitHeight != null) {
                width = style.isBorderBox() ? height * aspectRatio : (height - boxVertical) * aspectRatio + boxHorizontal;
            }
        }
        if (explicitHeight == null) height = Math.max(height, resolveAutomaticMinimum(item, rows, false, measuredSize.height()));
        return new Size(clampItemSize(element, width, areaWidth, true), clampItemSize(element, height, areaHeight, false));
    }

    private static double resolveItemAlignmentOffset(GridParticipant item, Grid grid, double extent, double size, boolean horizontal) {
        if (item.element == null) return resolveAlignmentOffset(resolveKeyword(horizontal ? grid.justifyItems : grid.alignItems, STRETCH), extent - size);
        Style style = item.element.getComputedStyle();
        Box box = Box.of(item.element);
        double margins = horizontal ? box.getMarginHorizontal() : box.getMarginVertical();
        double free = extent - size - margins;
        boolean start = hasAutoMargin(style, horizontal, false), end = hasAutoMargin(style, horizontal, true);
        if (free > 0 && (start || end)) return start ? free / (end ? 2 : 1) : 0;
        return resolveAlignmentOffset(resolveItemAlignment(grid, style, horizontal), free);
    }

    private static double resolveItemBaseline(GridParticipant item, boolean last) {
        Text text = item.text == null ? Text.of(item.element) : item.text;
        if (text != null && text.content != null && !text.content.isBlank()) {
            Box box = item.element == null ? null : Box.of(item.element);
            double inset = box == null ? 0 : box.getMarginTop() + box.getBorderTop() + box.getPaddingTop();
            double contentWidth = Math.max(EPSILON, item.used.width() - (box == null ? 0 : box.getBorderHorizontal() + box.getPaddingHorizontal()));
            int lines = item.wrapped == null ? Text.wrap(text, contentWidth).lines().size() : item.wrapped.lines().size();
            return inset + Text.baselineOffset(text) + (last ? Math.max(0, lines - 1) * text.lineHeight : 0);
        }
        return item.used.height();
    }

    private static GridLayoutResult computeGridLayout(Element parent, double width, double height) {
        Style style = parent.getComputedStyle();
        Grid grid = Grid.of(parent);
        double columnGap = resolveGap(style.columnGap, width), rowGap = resolveGap(style.rowGap, height);
        GridAxis columns = parseTemplateTracks(style.gridTemplateColumns, width, columnGap), rows = parseTemplateTracks(style.gridTemplateRows, height, rowGap);
        applyTemplateAreas(style, columns, rows);
        List<GridParticipant> items = buildParticipants(parent, columns, rows);
        placeItems(items, columns, rows, style, grid);
        computeTrackSizes(columns, items, width, columnGap, true, columns, grid.justifyContent);
        computeTrackSizes(rows, items, height, rowGap, false, columns, grid.alignContent);
        growBaselineRows(items, grid, columns, rows, height, rowGap);
        Box box = Box.of(parent);
        IdentityHashMap<Element, GridItemLayout> result = new IdentityHashMap<>();
        List<Layout.DirectTextLayout> text = new ArrayList<>();
        Map<Integer, Double> first = new LinkedHashMap<>(), last = new LinkedHashMap<>();
        for (GridParticipant item : items) {
            item.used = resolveItemUsedSize(item, grid, columns, rows);
            KeywordValue align = item.element == null ? resolveKeyword(grid.alignItems, STRETCH) : resolveItemAlignment(grid, item.element.getComputedStyle(), false);
            if (align.contains("baseline")) {
                boolean isLast = align.value().startsWith("last");
                (isLast ? last : first).merge(isLast ? item.row + item.rowSpan - 1 : item.row,
                        isLast ? item.used.height() - resolveItemBaseline(item, true) : resolveItemBaseline(item, false), Math::max);
            }
        }
        for (GridParticipant item : items) {
            double w = columns.span(item.column, item.columnSpan), h = rows.span(item.row, item.rowSpan);
            double dx = resolveItemAlignmentOffset(item, grid, w, item.used.width(), true), dy = resolveItemAlignmentOffset(item, grid, h, item.used.height(), false);
            KeywordValue align = item.element == null ? resolveKeyword(grid.alignItems, STRETCH) : resolveItemAlignment(grid, item.element.getComputedStyle(), false);
            if (align.contains("baseline")) {
                boolean isLast = align.value().startsWith("last");
                dy = isLast ? h - item.used.height() - last.getOrDefault(item.row + item.rowSpan - 1, 0d) + item.used.height() - resolveItemBaseline(item, true)
                        : first.getOrDefault(item.row, 0d) - resolveItemBaseline(item, false);
            }
            Position position = new Position(box.offset("left") + columns.areaStart(item.column) + dx, box.offset("top") + rows.areaStart(item.row) + dy);
            if (item.element != null) result.put(item.element, new GridItemLayout(position, item.used));
            else text.add(new Layout.DirectTextLayout(item.text, position, item.wrapped));
        }
        return new GridLayoutResult(result, List.copyOf(text), columns, rows, new Size(Math.max(0, columns.extent), Math.max(0, rows.extent)));
    }

    private static void growBaselineRows(List<GridParticipant> items, Grid grid, GridAxis columns, GridAxis rows, double height, double gap) {
        Map<Integer, double[]> groups = new LinkedHashMap<>();
        for (GridParticipant item : items) {
            KeywordValue align = item.element == null ? resolveKeyword(grid.alignItems, STRETCH) : resolveItemAlignment(grid, item.element.getComputedStyle(), false);
            if (!align.contains("baseline")) continue;
            item.used = resolveItemUsedSize(item, grid, columns, rows);
            boolean last = align.value().startsWith("last");
            int row = last ? item.row + item.rowSpan - 1 : item.row;
            double baseline = resolveItemBaseline(item, last);
            double[] group = groups.computeIfAbsent(row * 2 + (last ? 1 : 0), ignored -> new double[2]);
            group[0] = Math.max(group[0], baseline);
            group[1] = Math.max(group[1], item.used.height() - baseline);
        }
        groups.forEach((key, group) -> {
            int row = key / 2;
            GridTrack track = rows.tracks.get(row);
            if (!Double.isFinite(track.max.fixed(height))) rows.sizes[row] = Math.max(rows.sizes[row], group[0] + group[1]);
        });
        positionTracks(rows, height, gap, grid.alignContent);
    }
    /** Absolute grid children use existing tracks and never create or occupy tracks. */
    public static Area absoluteArea(Element element, Element containingBlock) {
        if (containingBlock == null || element.parentElement != containingBlock || !containingBlock.getComputedStyle().isGridDisplay()
                || LAYOUT_IN_PROGRESS.get().contains(containingBlock)) return null;
        GridLayoutResult result = getOrComputeLayout(containingBlock);
        if (result.columns == null) return null;
        Style style = element.getComputedStyle();
        Box box = Box.of(containingBlock);
        Size size = Size.of(containingBlock);
        double[] x = resolveAbsoluteAxis(result.columns, style.gridColumnStart, style.gridColumnEnd,
                box.getPaddingLeft(), size.width() - box.getBorderHorizontal() - box.getPaddingRight());
        double[] y = resolveAbsoluteAxis(result.rows, style.gridRowStart, style.gridRowEnd,
                box.getPaddingTop(), size.height() - box.getBorderVertical() - box.getPaddingBottom());
        return new Area(box.getBorderLeft() + x[0], box.getBorderTop() + y[0], Math.max(0, x[1] - x[0]), Math.max(0, y[1] - y[0]));
    }

    private static double[] resolveAbsoluteAxis(GridAxis axis, String start, String end, double paddingStart, double paddingEnd) {
        GridRange range = resolveItemRange(axis, start, end);
        Integer a = resolveLine(axis, parseLine(start), false), b = resolveLine(axis, parseLine(end), true);
        if (parseLine(start).span && b != null) a = range.start;
        if (parseLine(end).span && a != null) b = a + range.span;
        int count = axis.tracks.size();
        double lo = a == null || a + axis.origin < 0 || a + axis.origin > count ? 0 : paddingStart + axis.areaStart(a + axis.origin);
        double hi = b == null || b + axis.origin < 0 || b + axis.origin > count ? paddingEnd : paddingStart + axis.areaEnd(b + axis.origin);
        return new double[]{lo, hi};
    }
}

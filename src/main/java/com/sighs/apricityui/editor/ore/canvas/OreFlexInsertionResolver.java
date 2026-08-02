package com.sighs.apricityui.editor.ore.canvas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.sighs.apricityui.parser.CSS;

/**
 * Resolves an insertion point from rendered Flex item rectangles. The caller
 * supplies the final geometry, so wrapping, reverse axes and CSS order are
 * reflected without inferring layout from model order.
 */
public final class OreFlexInsertionResolver {
    private static final double LINE_TOLERANCE = 2.0;

    public record Bounds(double left, double top, double width, double height) {
        public double right() { return left + width; }
        public double bottom() { return top + height; }
        public double centerX() { return left + width / 2.0; }
        public double centerY() { return top + height / 2.0; }
    }

    public record Item(UUID id, Bounds bounds, boolean absolute) { }

    /** beforeId is null when the point is after every item on the selected visual line. */
    public record Insertion(UUID beforeId, boolean row, double coordinate, double crossStart, double crossSize) { }

    public Insertion resolve(String direction, String wrap, List<Item> candidates, double x, double y) {
        boolean row = !"column".equals(direction) && !"column-reverse".equals(direction);
        boolean reverse = "row-reverse".equals(direction) || "column-reverse".equals(direction);
        List<Item> items = candidates == null ? List.of() : candidates.stream()
                .filter(item -> item != null && item.id() != null && item.bounds() != null && !item.absolute())
                .toList();
        if (items.isEmpty()) return new Insertion(null, row, row ? x : y, row ? y : x, 0);

        List<List<Item>> lines = lines(items, row);
        double crossPointer = row ? y : x;
        List<Item> line = lines.stream().min(Comparator.comparingDouble(value -> Math.abs(crossPointer - lineCrossCenter(value, row))))
                .orElse(items);
        line = new ArrayList<>(line);
        line.sort(Comparator.comparingDouble(item -> mainCenter(item, row)));
        if (reverse) line.sort(Comparator.comparingDouble((Item item) -> mainCenter(item, row)).reversed());

        double pointer = row ? x : y;
        for (Item item : line) {
            if (pointer <= mainCenter(item, row)) return insertionBefore(item, row, reverse);
        }
        return insertionAfter(line.get(line.size() - 1), row, reverse);
    }

    private List<List<Item>> lines(List<Item> items, boolean row) {
        List<Item> ordered = new ArrayList<>(items);
        ordered.sort(Comparator.comparingDouble(item -> crossCenter(item, row)));
        List<List<Item>> lines = new ArrayList<>();
        for (Item item : ordered) {
            if (lines.isEmpty() || Math.abs(lineCrossCenter(lines.get(lines.size() - 1), row) - crossCenter(item, row)) > LINE_TOLERANCE) {
                lines.add(new ArrayList<>());
            }
            lines.get(lines.size() - 1).add(item);
        }
        return lines;
    }

    private Insertion insertionBefore(Item item, boolean row, boolean reverse) {
        Bounds bounds = item.bounds();
        double coordinate = row ? (reverse ? bounds.right() : bounds.left()) : (reverse ? bounds.bottom() : bounds.top());
        return new Insertion(item.id(), row, coordinate, row ? bounds.top() : bounds.left(), row ? bounds.height() : bounds.width());
    }

    private Insertion insertionAfter(Item item, boolean row, boolean reverse) {
        Bounds bounds = item.bounds();
        double coordinate = row ? (reverse ? bounds.left() : bounds.right()) : (reverse ? bounds.top() : bounds.bottom());
        return new Insertion(null, row, coordinate, row ? bounds.top() : bounds.left(), row ? bounds.height() : bounds.width());
    }

    private double mainCenter(Item item, boolean row) { return row ? item.bounds().centerX() : item.bounds().centerY(); }
    private double crossCenter(Item item, boolean row) { return row ? item.bounds().centerY() : item.bounds().centerX(); }
    private double lineCrossCenter(List<Item> line, boolean row) {
        return line.stream().mapToDouble(item -> crossCenter(item, row)).average().orElse(0);
    }
}

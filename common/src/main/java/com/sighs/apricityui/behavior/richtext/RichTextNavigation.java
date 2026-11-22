package com.sighs.apricityui.behavior.richtext;

import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.layout.TextFlowGeometry;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 富文本光标几何与行移动：把单元扁平文本的归一化偏移映射到**视觉行**（同一视觉行的
 * 多个 run 合并为一行，与 {@code TextSelection.locateRuns} / {@code Element.drawChildTextRuns}
 * 同一套 run 几何公式），支撑光标绘制与 Home/End/↑/↓ 移动。
 */
public final class RichTextNavigation {
    private RichTextNavigation() {
    }

    /**
     * 单元的全部视觉行（y0 优先排序；原子对象段并入其所属文本行）。
     */
    public static List<VisualLine> linesOf(Element unit) {
        List<VisualLine> result = new ArrayList<>();
        if (unit == null) return result;
        var flow = TextFlowGeometry.of(unit);
        var lines = flow.lines();
        List<RunSegment> objectRows = new ArrayList<>();
        collectObjectSegments(unit, unit, objectRows);
        if (lines.isEmpty() && flow.leaf().isEmpty() && objectRows.isEmpty()) return result;

        Position origin = Rect.of(unit).getContentPosition();
        List<RunSegment> rows = new ArrayList<>();
        for (var line : lines) {
            int start = SelectionUnits.baseOffsetOfDescendant(unit, line.source()) + line.start();
            rows.add(new RunSegment(line.run().text(), line.content(), start,
                    origin.x + line.x() - unit.scrollLeft, origin.y + line.y(), line.width(), line.advances()));
        }
        for (var line : flow.leaf()) {
            rows.add(new RunSegment(line.text(), line.content(), line.start(),
                    origin.x + line.x() - unit.scrollLeft, origin.y + line.y(), line.width(), line.advances()));
        }
        rows.sort(Comparator.comparingDouble(RunSegment::y0).thenComparingDouble(RunSegment::x0));
        for (int i = 0; i < rows.size(); i++) {
            RunSegment first = rows.get(i);
            List<RunSegment> group = new ArrayList<>();
            group.add(first);
            int startNorm = first.startNorm();
            int endNorm = first.startNorm() + first.content().length();
            double y0 = first.y0();
            double lineHeight = first.text().lineHeight;
            int j = i + 1;
            while (j < rows.size()) {
                RunSegment next = rows.get(j);
                if (Math.abs(y0 - next.y0()) > lineHeight * 0.5) break;
                group.add(next);
                startNorm = Math.min(startNorm, next.startNorm());
                endNorm = Math.max(endNorm, next.startNorm() + next.content().length());
                j++;
            }
            group.sort(Comparator.comparingDouble(RunSegment::x0));
            result.add(new VisualLine(startNorm, endNorm, y0, lineHeight, group));
            i = j - 1;
        }
        // 原子对象段并入其 startNorm 所在的文本行（对象在文本段之间）
        for (RunSegment object : objectRows) {
            int targetIndex = -1;
            for (int i = 0; i < result.size(); i++) {
                VisualLine line = result.get(i);
                if (object.startNorm() >= line.startNorm() && object.startNorm() <= line.endNorm()) {
                    targetIndex = i;
                    break;
                }
            }
            if (targetIndex < 0) {
                // 无匹配文本行（如仅含对象）：独立成行
                result.add(new VisualLine(object.startNorm(), object.startNorm() + 1,
                        object.y0(), object.text().lineHeight, List.of(object)));
                continue;
            }
            VisualLine line = result.get(targetIndex);
            List<RunSegment> merged = new ArrayList<>(line.segments());
            merged.add(object);
            merged.sort(Comparator.comparingDouble(RunSegment::x0));
            int startNorm = Math.min(line.startNorm(), object.startNorm());
            int endNorm = Math.max(line.endNorm(), object.startNorm() + 1);
            result.set(targetIndex, new VisualLine(startNorm, endNorm, line.y0(), line.lineHeight(), merged));
        }
        return result;
    }

    /**
     * 包含给定归一化偏移的视觉行；偏移落在行间隙时取最近行。
     */
    public static VisualLine locateLine(Element unit, int normOffset) {
        List<VisualLine> lines = linesOf(unit);
        if (lines.isEmpty()) return null;
        for (VisualLine line : lines) {
            if (normOffset >= line.startNorm() && normOffset <= line.endNorm()) {
                return line;
            }
        }
        VisualLine best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (VisualLine line : lines) {
            int distance;
            if (normOffset < line.startNorm()) {
                distance = line.startNorm() - normOffset;
            } else {
                distance = normOffset - line.endNorm();
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = line;
            }
        }
        return best;
    }

    /**
     * 当前视觉行首（Home）。
     */
    public static int lineStartOffset(Element unit, int normOffset) {
        VisualLine line = locateLine(unit, normOffset);
        return line == null ? 0 : line.startNorm();
    }

    /**
     * 当前视觉行尾（End）。
     */
    public static int lineEndOffset(Element unit, int normOffset) {
        VisualLine line = locateLine(unit, normOffset);
        return line == null ? normOffset : line.endNorm();
    }

    /**
     * 上/下移动：目标行取视觉顺序上一条/下一条，保持视觉列。
     */
    public static int lineMoveOffset(Element unit, int normOffset, int delta) {
        if (delta == 0) return normOffset;
        List<VisualLine> lines = linesOf(unit);
        if (lines.isEmpty()) return normOffset;
        int current = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (normOffset >= lines.get(i).startNorm() && normOffset <= lines.get(i).endNorm()) {
                current = i;
                break;
            }
        }
        if (current < 0) {
            VisualLine nearest = locateLine(unit, normOffset);
            current = nearest == null ? 0 : lines.indexOf(nearest);
        }
        int target = Math.max(0, Math.min(current + delta, lines.size() - 1));
        if (target == current) return normOffset;
        return lines.get(target).offsetForX(lines.get(current).xForOffset(normOffset));
    }

    /**
     * 归一化偏移 → 光标坐标（左上角 + 行高），供画光标。
     */
    public static Caret caretPosition(Element unit, int normOffset) {
        VisualLine line = locateLine(unit, normOffset);
        if (line == null) {
            return new Caret(0, 0, Size.DEFAULT_LINE_HEIGHT);
        }
        return new Caret(line.xForOffset(normOffset), line.y0(), line.lineHeight());
    }

    /**
     * 收集单元内的原子对象段（宽度 = 对象盒子宽）。
     */
    private static void collectObjectSegments(Element unit, Element current, List<RunSegment> out) {
        for (Node child : current.getRenderChildNodes()) {
            if (!(child instanceof Element childElement)) continue;
            if (SelectionUnits.isAtomicObject(childElement)) {
                Element.DOMRect rect = childElement.getBoundingClientRect();
                double x0 = rect != null ? rect.x : 0;
                double y0 = rect != null ? rect.y : 0;
                double width = rect != null && rect.width > 0 ? rect.width : 0;
                int startNorm = SelectionUnits.baseOffsetOfDescendant(unit, childElement);
                out.add(new RunSegment(Text.of(unit), "", startNorm, x0, y0, width));
                continue;
            }
            if (childElement instanceof com.sighs.apricityui.element.AbstractText) continue;
            if (SelectionUnits.isLineBreak(childElement)) continue;
            collectObjectSegments(unit, childElement, out);
        }
    }

    /**
     * 视觉行内的一段（run 或原子对象）：归一化区间 [startNorm, startNorm+占位长度) 与几何。
     */
    public record RunSegment(Text text, String content, int startNorm, double x0, double y0, double width,
                             TextFlowGeometry.Advances advances) {
        public RunSegment(Text text, String content, int startNorm, double x0, double y0, double width) {
            this(text, content, startNorm, x0, y0, width, null);
        }
    }

    /**
     * 一个视觉行：多个 run 段的合并（段按 x0 顺序，行内偏移经各段度量）。
     */
    public record VisualLine(int startNorm, int endNorm, double y0, double lineHeight,
                             List<RunSegment> segments) {

        /**
         * 行内归一化偏移 → 绝对 X 坐标。
         */
        public double xForOffset(int normOffset) {
            double x = 0;
            for (RunSegment segment : segments) {
                int segmentEnd = segment.startNorm() + segment.content().length();
                if (segment.content().isEmpty()) {
                    // 原子对象段：占 1 个归一化字符
                    segmentEnd = segment.startNorm() + 1;
                }
                if (normOffset <= segment.startNorm()) {
                    break;
                }
                if (normOffset >= segmentEnd) {
                    x += segment.width();
                    continue;
                }
                if (segment.content().isEmpty()) {
                    break;
                }
                x += segment.advances().xAt(normOffset - segment.startNorm());
                break;
            }
            return segments.isEmpty() ? 0 : segments.get(0).x0() + x;
        }

        /**
         * 绝对 X 坐标 → 行内最近的字符偏移。
         */
        public int offsetForX(double x) {
            if (segments.isEmpty()) return startNorm;
            double x0 = segments.get(0).x0();
            double relative = x - x0;
            double acc = 0;
            for (RunSegment segment : segments) {
                if (segment.content().isEmpty()) {
                    // 原子对象段：宽度内任意点 → 对象起点
                    if (relative <= acc + segment.width()) {
                        return segment.startNorm();
                    }
                    acc += segment.width();
                    continue;
                }
                double segmentWidth = segment.width();
                if (relative <= acc + segmentWidth) {
                    return segment.startNorm() + segment.advances().offsetAt(relative - acc);
                }
                acc += segmentWidth;
            }
            return endNorm;
        }
    }

    /**
     * 光标位置：左上角坐标 + 行高。
     */
    public record Caret(double x, double y, double lineHeight) {
    }
}

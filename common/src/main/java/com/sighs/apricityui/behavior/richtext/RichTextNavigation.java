package com.sighs.apricityui.behavior.richtext;

import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.dom.CommentNode;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.layout.NormalFlow;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.util.TextMetrics;

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

    /** 视觉行内的一段 run：归一化区间 [startNorm, startNorm+content.length()) 与几何。 */
    public record RunSegment(Text text, String content, int startNorm, double x0, double y0) {
    }

    /** 一个视觉行：多个 run 段的合并（段按 x0 顺序，行内偏移经各段度量）。 */
    public record VisualLine(int startNorm, int endNorm, double y0, double lineHeight,
                             List<RunSegment> segments) {

        /** 行内归一化偏移 → 绝对 X 坐标。 */
        public double xForOffset(int normOffset) {
            double x = 0;
            for (RunSegment segment : segments) {
                int segmentEnd = segment.startNorm() + segment.content().length();
                if (normOffset <= segment.startNorm()) {
                    break;
                }
                if (normOffset >= segmentEnd) {
                    x += Text.measureLine(segment.text(), segment.content());
                    continue;
                }
                x += Text.measureLine(segment.text(), segment.content().substring(0, normOffset - segment.startNorm()));
                break;
            }
            return segments.isEmpty() ? 0 : segments.get(0).x0() + x;
        }

        /** 绝对 X 坐标 → 行内最近的字符偏移。 */
        public int offsetForX(double x) {
            if (segments.isEmpty()) return startNorm;
            double x0 = segments.get(0).x0();
            double relative = x - x0;
            double acc = 0;
            for (RunSegment segment : segments) {
                double segmentWidth = Text.measureLine(segment.text(), segment.content());
                if (relative <= acc + segmentWidth) {
                    double local = relative - acc;
                    int best = 0;
                    double bestDistance = Double.MAX_VALUE;
                    double current = 0;
                    for (int i = 0; i <= segment.content().length(); i++) {
                        double distance = Math.abs(current - local);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = i;
                        }
                        if (i < segment.content().length()) {
                            current += Text.measureLine(segment.text(), segment.content().substring(i, i + 1));
                        }
                    }
                    return segment.startNorm() + best;
                }
                acc += segmentWidth;
            }
            return endNorm;
        }
    }

    /** 光标位置：左上角坐标 + 行高。 */
    public record Caret(double x, double y, double lineHeight) {
    }

    /** 单元的全部视觉行（y0 优先排序）。 */
    public static List<VisualLine> linesOf(Element unit) {
        List<VisualLine> result = new ArrayList<>();
        if (unit == null) return result;
        List<NormalFlow.TextRunLayout> runs = NormalFlow.computeTextRuns(unit);
        if (runs.isEmpty()) return result;
        Rect rect = Rect.of(unit);
        Position contentPos = rect.getContentPosition();
        boolean alignDirect = shouldAlignDirect(unit);
        double contentWidth = alignDirect ? Box.of(unit).innerSize().width() : 0;

        List<RunSegment> rows = new ArrayList<>();
        for (NormalFlow.TextRunLayout run : runs) {
            if (run == null || run.text() == null || run.lines() == null) continue;
            Text runText = run.text();
            double lineHeight = runText.lineHeight;
            Node node = run.node() != null ? run.node() : run.owner();
            int runBase = SelectionUnits.baseOffsetOfDescendant(unit, node);
            for (int i = 0; i < run.lines().size(); i++) {
                String line = run.lines().get(i);
                double lineWidth = Text.measureLine(runText, line == null ? "" : line);
                double alignOffset = (alignDirect && run.owner() == unit)
                        ? TextMetrics.computeAlignedX(runText, contentWidth, lineWidth, i == 0)
                        : 0;
                double y0 = contentPos.y + run.y() + i * lineHeight;
                double x0 = contentPos.x + (i == 0 ? run.x() : 0) + alignOffset - unit.scrollLeft;
                int lineStartNorm = runBase + SelectionUnits.runLineStart(run, i);
                rows.add(new RunSegment(runText, line == null ? "" : line, lineStartNorm, x0, y0));
            }
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
        return result;
    }

    /** 包含给定归一化偏移的视觉行；偏移落在行间隙时取最近行。 */
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

    /** 当前视觉行首（Home）。 */
    public static int lineStartOffset(Element unit, int normOffset) {
        VisualLine line = locateLine(unit, normOffset);
        return line == null ? 0 : line.startNorm();
    }

    /** 当前视觉行尾（End）。 */
    public static int lineEndOffset(Element unit, int normOffset) {
        VisualLine line = locateLine(unit, normOffset);
        return line == null ? normOffset : line.endNorm();
    }

    /** 上/下移动：目标行取视觉顺序上一条/下一条，保持视觉列。 */
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

    /** 归一化偏移 → 光标坐标（左上角 + 行高），供画光标。 */
    public static Caret caretPosition(Element unit, int normOffset) {
        VisualLine line = locateLine(unit, normOffset);
        if (line == null) return new Caret(0, 0, 16);
        return new Caret(line.xForOffset(normOffset), line.y0(), line.lineHeight());
    }

    private static boolean shouldAlignDirect(Element unit) {
        if (unit == null) return false;
        boolean hasText = false;
        for (Node child : unit.getRenderChildNodes()) {
            if (child instanceof CommentNode) continue;
            if (child instanceof TextNode textNode) {
                hasText |= textNode.getTextContent() != null && !textNode.getTextContent().isEmpty();
                continue;
            }
            if (child instanceof Element element && !Layout.isInFlow(element.getComputedStyle())) continue;
            return false;
        }
        return hasText;
    }
}

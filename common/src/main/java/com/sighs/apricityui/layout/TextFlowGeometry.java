package com.sighs.apricityui.layout;

import com.sighs.apricityui.dom.CommentNode;
import com.sighs.apricityui.dom.TextTransform;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.util.TextMetrics;

import java.util.ArrayList;
import java.util.List;

/** Local text geometry. Document transforms and scrolling do not invalidate its lines. */
public final class TextFlowGeometry {
    private static final TextFlowGeometry EMPTY = new TextFlowGeometry(List.of(), List.of(), List.of(), List.of(), List.of());
    private final List<NormalFlow.TextRunLayout> runs;
    private final List<Line> lines;
    private final List<Layout.DirectTextLayout> direct;
    private final List<DirectLine> directLines;
    private final List<LeafLine> leaf;

    private TextFlowGeometry(List<NormalFlow.TextRunLayout> runs, List<Line> lines,
                             List<Layout.DirectTextLayout> direct, List<DirectLine> directLines,
                             List<LeafLine> leaf) {
        this.runs = runs;
        this.lines = lines;
        this.direct = direct;
        this.directLines = directLines;
        this.leaf = leaf;
    }

    public List<NormalFlow.TextRunLayout> runs() { return runs; }
    public List<Line> lines() { return lines; }
    public List<Layout.DirectTextLayout> direct() { return direct; }
    public List<DirectLine> directLines() { return directLines; }
    public List<LeafLine> leaf() { return leaf; }

    /** Also supports explicit drawing and synthetic events before the first document frame. */
    public static TextFlowGeometry of(Element element) {
        if (element == null) return EMPTY;
        return element.getLayout().textFlow();
    }

    static TextFlowGeometry measure(Element element) {
        if (element instanceof AbstractText) return EMPTY;
        var style = element.getComputedStyle();
        if (style.isFlexDisplay() || style.isGridDisplay()) {
            List<Layout.DirectTextLayout> direct = Layout.computeDirectTextLayouts(element);
            if (direct.isEmpty()) return EMPTY;
            List<DirectLine> directLines = new ArrayList<>();
            for (Layout.DirectTextLayout layout : direct) {
                Text text = layout.text();
                if (text == null || text.content == null) continue;
                List<String> contentLines = layout.lines();
                int[] starts = layout.lineStarts();
                for (int i = 0; i < contentLines.size(); i++) {
                    String content = contentLines.get(i);
                    double width = Text.measureLine(text, content);
                    directLines.add(new DirectLine(layout, i, starts[i], content, width,
                            Advances.measure(text, content, width)));
                }
            }
            return new TextFlowGeometry(List.of(), List.of(), List.copyOf(direct),
                    List.copyOf(directLines), List.of());
        }
        boolean aroundFloats = NormalFlow.usesFloatLines(element);
        if (!aroundFloats && (element.getRenderChildNodes().isEmpty() || element.getRenderChildren().isEmpty()
                && element.getRenderChildNodes().stream().anyMatch(n -> n instanceof TextNode t && !t.getTextContent().isEmpty()))) {
            return measureLeaf(element);
        }
        var runs = NormalFlow.computeTextRuns(element);
        if (runs.isEmpty()) return measureLeaf(element);
        boolean alignDirect = !aroundFloats && alignsDirectText(element);
        double width = alignDirect ? Box.of(element).innerSize().width() : 0;
        List<Line> lines = new ArrayList<>();
        for (int r = 0; r < runs.size(); r++) {
            var run = runs.get(r);
            int start = run.sourceStart();
            for (int i = 0; i < run.lines().size(); i++) {
                String content = run.lines().get(i);
                if (i > 0) {
                    start += run.lines().get(i - 1).length();
                    int found = run.text().content.indexOf(content, start);
                    if (found >= 0) start = found;
                }
                if (content.isEmpty()) continue;
                double lineWidth = Text.measureLine(run.text(), content);
                double align = alignDirect && run.owner() == element
                        ? TextMetrics.computeAlignedX(run.text(), width, lineWidth, i == 0) : 0;
                lines.add(new Line(run, r, content, start, (i == 0 ? run.x() : 0) + align,
                        run.y() + i * run.text().lineHeight, lineWidth, Advances.measure(run.text(), content, lineWidth)));
            }
        }
        return new TextFlowGeometry(List.copyOf(runs), List.copyOf(lines), List.of(), List.of(), List.of());
    }

    private static TextFlowGeometry measureLeaf(Element element) {
        String content = TextTransform.apply(Text.directNormalizedText(element), element);
        if (content == null || content.codePoints().noneMatch(c -> c != 0xfffc
                && !Character.isWhitespace(c) && !Character.isSpaceChar(c))) return EMPTY;
        Text text = TextMetrics.cloneTextForSegment(Text.of(element), content, com.sighs.apricityui.parser.Color.BLACK);
        Size inner = Box.of(element).innerSize();
        var wrapped = Text.wrapCached(element, text);
        List<String> rendered = element.resolveRenderedLines(text, inner.width(), inner.height());
        double height = Math.max(text.lineHeight, rendered.size() * text.lineHeight);
        double top = TextMetrics.computeVerticalOffset(text, inner.height(), height);
        List<LeafLine> lines = new ArrayList<>(rendered.size());
        for (int i = 0; i < rendered.size(); i++) {
            String line = rendered.get(i);
            String source = wrapped.lines().get(i);
            int length = 0;
            while (length < line.length() && length < source.length() && line.charAt(length) == source.charAt(length)) length++;
            double width = Text.measureLine(text, line);
            lines.add(new LeafLine(text, line, wrapped.starts()[i], length,
                    TextMetrics.computeAlignedX(text, inner.width(), width, i == 0), top + i * text.lineHeight,
                    width, Advances.measure(text, line, width)));
        }
        return new TextFlowGeometry(List.of(), List.of(), List.of(), List.of(), List.copyOf(lines));
    }

    private static boolean alignsDirectText(Element element) {
        boolean hasText = false;
        for (Node child : element.getRenderChildNodes()) {
            if (child instanceof CommentNode) continue;
            if (child instanceof TextNode textNode) {
                hasText |= !textNode.getTextContent().isEmpty();
            } else if (!(child instanceof Element e) || Layout.isInFlow(e.getComputedStyle())) {
                return false;
            }
        }
        return hasText;
    }

    public record Line(NormalFlow.TextRunLayout run, int runIndex, String content,
                       int start, double x, double y, double width, Advances advances) {
        public double height() { return run.text().lineHeight; }
        public Node source() { return run.node() == null ? run.owner() : run.node(); }
    }

    public record DirectLine(Layout.DirectTextLayout layout, int lineIndex, int start,
                             String content, double width, Advances advances) {
    }

    public record LeafLine(Text text, String content, int start, int selectableLength,
                           double x, double y, double width, Advances advances) {
        public double height() { return text.lineHeight; }
    }

    /** Pixel stops indexed by UTF-16 offsets; never returns the middle of a surrogate pair. */
    public static final class Advances {
        private final int[] offsets;
        private final double[] positions;

        private Advances(int[] offsets, double[] positions) {
            this.offsets = offsets;
            this.positions = positions;
        }

        public static Advances measure(Text text, String line, double width) {
            LayoutMeasureCache.recordMeasurement();
            int count = line.codePointCount(0, line.length());
            int[] offsets = new int[count + 1];
            double[] positions = new double[count + 1];
            for (int i = 1, start = 0; i <= count; i++) {
                int end = start + Character.charCount(line.codePointAt(start));
                offsets[i] = end;
                double advance = Text.measureLine(text, line.substring(start, end));
                double stroke = i == 1 ? 0 : text.strokeWidth * 2;
                positions[i] = positions[i - 1] + Math.max(0, advance - stroke);
                start = end;
            }
            if (positions[count] > 0) {
                double scale = Math.max(0, width) / positions[count];
                for (int i = 1; i <= count; i++) positions[i] *= scale;
            }
            return new Advances(offsets, positions);
        }

        public int offsetAt(double x) {
            int hi = positions.length - 1;
            if (x <= 0) return 0;
            if (x >= positions[hi]) return offsets[hi];
            int lo = 0;
            while (lo + 1 < hi) {
                int mid = (lo + hi) >>> 1;
                if (positions[mid] < x) lo = mid;
                else hi = mid;
            }
            return x - positions[lo] <= positions[hi] - x ? offsets[lo] : offsets[hi];
        }

        public double xAt(int offset) {
            int index = java.util.Arrays.binarySearch(offsets, offset);
            if (index < 0) index = Math.max(0, -index - 2);
            return positions[index];
        }
    }
}

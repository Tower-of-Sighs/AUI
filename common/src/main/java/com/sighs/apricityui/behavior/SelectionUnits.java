package com.sighs.apricityui.behavior;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.layout.Flex;
import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.layout.NormalFlow;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档级文字选择的“单元”模型。
 * <p>
 * 单元（unit）是自行绘制可选文本的元素：不是输入控件（AbstractText）、不是由祖先
 * 内联绘制、自身含有可选文本且 user-select 允许。普通流块单元的扁平文本 = 直接文本
 * + 内联后代文本（按绘制顺序拼接）；flex/grid 容器只取直接文本（flex/grid 子项是独立单元）。
 */
public final class SelectionUnits {
    private SelectionUnits() {
    }

    /** <br> 在扁平化文本中的占位符：普通流里被当作硬换行，归一化后再还原为 \n（避免被空白折叠吞掉）。 */
    public static final char BR_SENTINEL = '\u0001';

    /** 原子对象（img/hr）在扁平化文本中的占位符：占据一个原子单位，还原为 U+FFFC 对象替换符。 */
    public static final char OBJECT_SENTINEL = '\u0002';

    /** 是否为富文本中的原子对象节点（替换/绘制元素，编辑模型中占一个原子单位）。 */
    public static boolean isAtomicObject(Element element) {
        if (element == null) return false;
        boolean supportedTag = switch (element.tagName) {
            case "IMG", "HR", "SVG", "CANVAS", "TEXTURE", "SPRITE" -> true;
            default -> false;
        };
        if (!supportedTag) return false;
        for (Element current = element.parentElement; current != null; current = current.parentElement) {
            if (current instanceof com.sighs.apricityui.element.RichText) return true;
        }
        return false;
    }

    /** 命中元素解析出的文档选择位置：单元 + 单元扁平文本内的偏移。 */
    public record UnitOffset(Element unit, int offset) {
    }

    /** 元素视图上下文：所属单元、元素文本在单元扁平文本中的基偏移、元素自身的可选文本。 */
    public record UnitContext(Element unit, int baseOffset, String text) {
    }

    /** 行内文本标记标签：strong/em/u/s/b/i/span/a 等。它们不是独立选择单元，
     *  文本永远归最近的块单元。判定不依赖 computed style(布局未就绪时 display 判错,
     *  会把 u/strong 误当成单元,导致光标锚在行内元素上)。 */
    private static final java.util.Set<String> INLINE_MARKUP_TAGS = java.util.Set.of(
            "U", "STRONG", "EM", "S", "B", "I", "SPAN", "A", "CODE", "MARK", "SMALL",
            "SUB", "SUP", "INS", "DEL", "Q", "ABBR", "CITE", "DFN", "KBD", "SAMP",
            "TIME", "VAR", "FONT");

    /**
     * 判断元素是否为选择单元。
     * <p>
     * 富文本单元（含内联文本后代）也满足条件 —— 这是相对旧实现的预期行为变化。
     */
    public static boolean isSelectionUnit(Element element) {
        if (element == null || element instanceof AbstractText) return false;
        if (INLINE_MARKUP_TAGS.contains(element.tagName.toUpperCase(java.util.Locale.ROOT))) return false;
        if (NormalFlow.isInlineTextPaintedByAncestor(element)) return false;
        if (!Interaction.isUserSelectable(element)) return false;
        return !flattenedSelectableText(element).isEmpty();
    }

    /**
     * 从命中元素向上解析最近的单元（含自身）。命中点位于输入控件内时返回 null：
     * 输入控件的文本不参与文档级选择。
     */
    public static Element resolveUnit(Element element) {
        for (Element current = element; current != null; current = current.parentElement) {
            if (current instanceof AbstractText) return null;
            if (isSelectionUnit(current)) return current;
        }
        return null;
    }

    /**
     * 元素在文档级选择中的视图上下文：所属单元、元素文本在单元扁平文本中的基偏移、
     * 元素自身的可选文本。元素不属于任何单元（或无文本）时返回 null。
     */
    public static UnitContext resolveUnitContext(Element element) {
        if (element == null) return null;
        Element unit = resolveUnit(element);
        if (unit == null) return null;
        if (element == unit) {
            String text = flattenedSelectableText(unit);
            return text.isEmpty() ? null : new UnitContext(unit, 0, text);
        }
        String own = ownSelectableText(element);
        if (own.isEmpty()) return null;
        return new UnitContext(unit, baseOffsetOfDescendant(unit, element), own);
    }

    /**
     * 单元自身绘制文本的扁平化字符串（按绘制顺序拼接后做 innerText 式空白归一化）。
     * 递归停止于子单元：子单元的文本归子单元所有。
     * <p>
     * 结果按元素实例缓存在所属文档的 {@link Document#getCachedFlattened} 中，
     * DOM/样式/文本内容变更时由文档统一失效。
     */
    public static String flattenedSelectableText(Element element) {
        if (element == null || element instanceof AbstractText) return "";
        Document document = element.document;
        if (document == null) return computeFlattenedSelectableText(element);
        return document.getCachedFlattened(element);
    }

    /** 扁平化文本的实际计算（绕过文档缓存），供缓存 miss 时调用。 */
    public static String computeFlattenedSelectableText(Element element) {
        if (element == null || element instanceof AbstractText) return "";
        String display = element.getComputedStyle().display;
        if (Layout.isFlexDisplay(display) || Layout.isGridDisplay(display)) {
            StringBuilder flexText = new StringBuilder();
            for (String fragment : flexTextFragments(element)) {
                flexText.append(fragment);
            }
            return flexText.toString();
        }
        StringBuilder raw = new StringBuilder();
        flattenRaw(element, element, raw);
        if (raw.length() == 0) return "";
        String normalized = Text.normalizeWhiteSpaceContent(raw.toString(), Text.getWhiteSpace(element));
        if (normalized == null || normalized.isEmpty()) return "";
        if (normalized.indexOf(BR_SENTINEL) >= 0) {
            normalized = normalized.replace(BR_SENTINEL, '\n');
        }
        if (normalized.indexOf(OBJECT_SENTINEL) >= 0) {
            normalized = normalized.replace(OBJECT_SENTINEL, '\uFFFC');
        }
        return normalized;
    }

    /**
     * 元素自身的可选文本（直接文本节点 + innerText 回退，独立归一化）。
     * 用于单元内子元素的视图（例如富文本单元里的 b/span）。
     */
    public static String ownSelectableText(Element element) {
        if (element == null) return "";
        String raw;
        if (!element.childNodes.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (Node child : element.childNodes) {
                if (child instanceof TextNode textNode) {
                    builder.append(textNode.getTextContent());
                }
            }
            raw = builder.toString();
            if (raw.isEmpty()) {
                raw = element.innerText == null ? "" : element.innerText;
            }
        } else {
            raw = element.innerText == null ? "" : element.innerText;
        }
        if (raw.isEmpty()) return "";
        String normalized = Text.normalizeWhiteSpaceContent(raw, Text.getWhiteSpace(element));
        return normalized == null ? "" : normalized;
    }

    /**
     * 子节点（TextNode 或元素）在单元扁平文本中的起始偏移（DOM 序累计长度）。
     * 无法归位（例如目标不在单元子树内）时返回 0。
     */
    public static int baseOffsetOfDescendant(Element unit, Node descendant) {
        if (unit == null || descendant == null) return 0;
        if (descendant == unit) return 0;
        String display = unit.getComputedStyle().display;
        if (Layout.isFlexDisplay(display) || Layout.isGridDisplay(display)) {
            return baseOffsetInFlexDirectText(unit, descendant);
        }
        StringBuilder raw = new StringBuilder();
        if (findRawPrefix(unit, unit, descendant, raw)) {
            return normalizedPrefixLength(raw.toString(), Text.getWhiteSpace(unit));
        }
        return 0;
    }

    /**
     * 前缀的归一化长度：前缀在整串归一化中是“内部文本”，其后还有内容，因此前缀末尾
     * 的空白不能像独立归一化那样被裁掉，应保留为一个空格。实现：末尾附加哨兵字符使
     * 尾部空白变为内部空白参与折叠，再减去哨兵长度。
     */
    private static int normalizedPrefixLength(String raw, String whiteSpace) {
        if (raw == null || raw.isEmpty()) return 0;
        String normalized = Text.normalizeWhiteSpaceContent(raw + '\u0000', whiteSpace);
        if (normalized == null) return 0;
        return Math.max(0, normalized.length() - 1);
    }

    /** 文档内全部单元，按 DOM 前序排列。结果按文档缓存在 {@link Document#getCachedUnits()} 中。 */
    public static List<Element> enumerateUnits(Document document) {
        if (document == null) return new ArrayList<>();
        return document.getCachedUnits();
    }

    /** 单元枚举的实际计算（绕过文档缓存），供缓存 miss 时调用。 */
    public static List<Element> computeUnits(Document document) {
        List<Element> result = new ArrayList<>();
        if (document == null) return result;
        for (Element element : document.getElements()) {
            if (isSelectionUnit(element)) {
                result.add(element);
            }
        }
        return result;
    }

    /**
     * 单元的文本是否由 drawChildTextRuns 绘制（普通流 run 或 flex 直接文本布局），
     * 而不是由 drawInnerText 绘制。决定选择高亮在哪条绘制路径里分段。
     * <p>
     * 与 Element.drawChildTextRuns 的提前返回条件保持一致：只有直接文本（无元素子节点）
     * 时文本由 drawInnerText 绘制；grid 的直接文本当前不绘制，故不参与分段。
     * 普通流以实际计算出的 run 是否为空为准（例如 innerText 回退 + 空内联子元素时
     * 没有 run，文本仍由 drawInnerText 绘制）。
     */
    public static boolean paintsTextViaRuns(Element element) {
        if (element == null || element instanceof AbstractText) return false;
        Document document = element.document;
        if (document == null) return computePaintsTextViaRuns(element);
        return document.getCachedPaintsRuns(element);
    }

    /** run 绘制判定的实际计算（绕过文档缓存），供缓存 miss 时调用。 */
    public static boolean computePaintsTextViaRuns(Element element) {
        if (element == null || element instanceof AbstractText) return false;
        if (element.getRenderChildNodes().isEmpty()) return false;
        String display = element.getComputedStyle().display;
        if (Layout.isGridDisplay(display)) return false;
        if (Layout.isFlexDisplay(display)) {
            return !Flex.computeDirectTextLayouts(element).isEmpty();
        }
        if (element.getRenderChildren().isEmpty()) {
            for (Node child : element.getRenderChildNodes()) {
                if (child instanceof TextNode textNode && !textNode.getTextContent().isEmpty()) {
                    return false;
                }
            }
        }
        return !NormalFlow.computeTextRuns(element).isEmpty();
    }

    /**
     * flex/grid 容器的直接文本片段（与 Flex.buildParticipants 的归一化一致：
     * 逐片段归一化、跳过空白片段；无文本节点时回退到 innerText）。片段顺序即 DOM 序。
     */
    public static List<String> flexTextFragments(Element element) {
        List<String> fragments = new ArrayList<>();
        if (element == null) return fragments;
        for (Node child : element.getRenderChildNodes()) {
            if (!(child instanceof TextNode textNode)) continue;
            String normalized = Text.normalizeWhiteSpaceContent(textNode.getTextContent(), Text.getWhiteSpace(element));
            if (normalized == null || normalized.isBlank()) continue;
            fragments.add(normalized);
        }
        if (fragments.isEmpty() && element.innerText != null && !element.innerText.isBlank()) {
            String normalized = Text.normalizeWhiteSpaceContent(element.innerText, Text.getWhiteSpace(element));
            if (normalized != null && !normalized.isBlank()) {
                fragments.add(normalized);
            }
        }
        return fragments;
    }

    /** 单元原始文本视图：raw 保留逐字内容，normalized 为扁平化归一化串；rawStart/rawEnd 给出每个归一化字符对应的原始区间。 */
    public record RawText(String raw, String normalized, int[] rawStart, int[] rawEnd, int[][] skippedRuns) {
        /** 归一化区间 [nStart, nEnd) 对应的原始子串；越界钳制，空区间返回空串，BR 占位符还原为 \n。 */
        public String rawRangeForNormalizedRange(int nStart, int nEnd) {
            if (raw == null || normalized == null || rawStart == null || rawEnd == null) return "";
            if (normalized.isEmpty() || raw.isEmpty()) return "";
            int length = normalized.length();
            int start = Math.max(0, Math.min(nStart, length));
            int end = Math.max(start, Math.min(nEnd, length));
            if (start >= end) return "";
            int rawStartIndex = rawStart[start];
            int rawEndIndex = rawEnd[end - 1];
            if (start == 0) rawStartIndex = extendLeft(rawStartIndex);
            if (end == length) rawEndIndex = extendRight(rawEndIndex);
            String substring = raw.substring(rawStartIndex, rawEndIndex);
            if (substring.indexOf(BR_SENTINEL) >= 0) {
                substring = substring.replace(BR_SENTINEL, '\n');
            }
            if (substring.indexOf(OBJECT_SENTINEL) >= 0) {
                substring = substring.replace(OBJECT_SENTINEL, '\uFFFC');
            }
            return substring;
        }

        /** 选区起点在归一化串首时，把被折叠丢弃的行首空白一并纳入（与浏览器复制行为一致）。 */
        private int extendLeft(int index) {
            if (skippedRuns == null) return index;
            boolean changed;
            do {
                changed = false;
                for (int[] run : skippedRuns) {
                    if (run[1] == index && run[0] != run[1]) {
                        index = run[0];
                        changed = true;
                    }
                }
            } while (changed);
            return index;
        }

        /** 选区终点在归一化串尾时，把被折叠丢弃的行尾空白一并纳入。 */
        private int extendRight(int index) {
            if (skippedRuns == null) return index;
            boolean changed;
            do {
                changed = false;
                for (int[] run : skippedRuns) {
                    if (run[0] == index && run[0] != run[1]) {
                        index = run[1];
                        changed = true;
                    }
                }
            } while (changed);
            return index;
        }
    }

    /** 单元的原始文本视图；非选择单元或无可选文本时返回 null。结果按元素缓存在文档中。 */
    public static RawText rawTextOf(Element unit) {
        if (unit == null || unit instanceof AbstractText) return null;
        Document document = unit.document;
        if (document == null) return computeRawTextOf(unit);
        return document.getCachedRaw(unit);
    }

    /** 原始文本视图的实际计算（绕过文档缓存），供缓存 miss 时调用。 */
    public static RawText computeRawTextOf(Element unit) {
        if (unit == null || unit instanceof AbstractText) return null;
        String display = unit.getComputedStyle().display;
        if (Layout.isFlexDisplay(display) || Layout.isGridDisplay(display)) {
            return buildFlexRawText(unit);
        }
        StringBuilder raw = new StringBuilder();
        flattenRaw(unit, unit, raw);
        if (raw.length() == 0) return null;
        return normalizeWithSpans(unit, raw.toString());
    }

    /** 归一化区间 [nStart, nEnd) 对应的单元原始文本子串；越界钳制，空选区返回空串。 */
    public static String rawRangeForNormalizedRange(Element unit, int nStart, int nEnd) {
        RawText rawText = rawTextOf(unit);
        if (rawText == null) return "";
        return rawText.rawRangeForNormalizedRange(nStart, nEnd);
    }

    /** <br> 是否作为硬换行元素处理（普通流布局与选择文本扁平化共用同一判定）。 */
    public static boolean isLineBreak(Element element) {
        return element != null && "BR".equals(element.tagName);
    }

    /**
     * TextRunLayout 某行在其内容（run.text().content）中的起始偏移。
     * 行是内容的连续分解（软换行仅吞掉断行字符），用下一行内容的 indexOf 向前定位。
     */
    public static int runLineStart(NormalFlow.TextRunLayout run, int lineIndex) {
        if (run == null || lineIndex <= 0) return 0;
        String content = run.text() == null ? null : run.text().content;
        List<String> lines = run.lines();
        if (lines == null || content == null) return 0;
        int cursor = 0;
        for (int i = 0; i < lineIndex; i++) {
            String current = lines.get(i);
            String next = lines.get(i + 1);
            int searchFrom = cursor + current.length();
            int found = next == null ? -1 : content.indexOf(next, searchFrom);
            cursor = found >= 0 ? found : searchFrom;
        }
        return cursor;
    }

    private static void flattenRaw(Element unit, Element current, StringBuilder raw) {
        boolean contributed = false;
        for (Node child : current.getRenderChildNodes()) {
            if (child instanceof TextNode textNode) {
                String content = textNode.getTextContent();
                if (content != null && !content.isEmpty()) contributed = true;
                raw.append(content);
                continue;
            }
            if (!(child instanceof Element childElement)) continue;
            if (childElement instanceof AbstractText) continue;
            if (isLineBreak(childElement)) {
                // <br> 作为硬换行：占位符不被空白折叠吞掉，归一化后还原为 \n
                raw.append(BR_SENTINEL);
                contributed = true;
                continue;
            }
            if (isAtomicObject(childElement)) {
                // img/hr 作为原子对象：占据一个原子单位（还原为 U+FFFC）
                raw.append(OBJECT_SENTINEL);
                contributed = true;
                continue;
            }
            if (isSelectionUnit(childElement)) continue;
            int before = raw.length();
            flattenRaw(unit, childElement, raw);
            if (raw.length() != before) contributed = true;
        }
        // 子节点没有文本时回退到 innerText（与 Text.of 的解析一致）
        if (!contributed && current.innerText != null && !current.innerText.isEmpty()) {
            raw.append(current.innerText);
        }
    }

    private static boolean findRawPrefix(Element unit, Element current, Node target, StringBuilder raw) {
        for (Node child : current.getRenderChildNodes()) {
            if (child == target) return true;
            if (child instanceof TextNode textNode) {
                raw.append(textNode.getTextContent());
                continue;
            }
            if (!(child instanceof Element childElement)) continue;
            if (childElement instanceof AbstractText) continue;
            if (isLineBreak(childElement)) {
                if (child == target) return true;
                raw.append(BR_SENTINEL);
                continue;
            }
            if (isAtomicObject(childElement)) {
                if (child == target) return true;
                raw.append(OBJECT_SENTINEL);
                continue;
            }
            if (isSelectionUnit(childElement)) continue;
            if (findRawPrefix(unit, childElement, target, raw)) return true;
        }
        return false;
    }

    private static int baseOffsetInFlexDirectText(Element unit, Node target) {
        int base = 0;
        boolean anyNonBlankTextNode = false;
        for (Node child : unit.getRenderChildNodes()) {
            if (!(child instanceof TextNode textNode)) continue;
            if (child == target) return base;
            String normalized = Text.normalizeWhiteSpaceContent(textNode.getTextContent(), Text.getWhiteSpace(unit));
            if (normalized == null || normalized.isBlank()) continue;
            anyNonBlankTextNode = true;
            base += normalized.length();
        }
        // 目标不是直接文本节点：innerText 回退片段位于全部文本节点之后
        if (!anyNonBlankTextNode && unit.innerText != null && !unit.innerText.isBlank()) {
            String normalized = Text.normalizeWhiteSpaceContent(unit.innerText, Text.getWhiteSpace(unit));
            if (normalized != null && !normalized.isBlank()) {
                base += normalized.length();
            }
        }
        return base;
    }

    // ------------------------------------------------------------------
    // 原始文本 ↔ 归一化文本的逐字符映射（与 Text.normalizeWhiteSpaceContent 语义一致）
    // ------------------------------------------------------------------

    private static RawText normalizeWithSpans(Element unit, String raw) {
        String whiteSpace = Text.getWhiteSpace(unit);
        StringBuilder normalized = new StringBuilder(raw.length());
        ArrayList<int[]> spans = new ArrayList<>();
        ArrayList<int[]> skipped = new ArrayList<>();
        String value = whiteSpace == null ? "normal" : whiteSpace;
        switch (value) {
            case "pre", "pre-wrap", "break-spaces" -> normalizePre(raw, normalized, spans);
            case "pre-line" -> normalizePreLine(raw, normalized, spans, skipped);
            default -> normalizeSingleLine(raw, normalized, spans, skipped);
        }
        return new RawText(raw, normalized.toString(), startsOf(spans), endsOf(spans), skipped.toArray(new int[0][]));
    }

    /** flex/grid：逐文本节点归一化（与 flexTextFragments 一致，空白节点被跳过），raw 仍保留全部逐字内容。 */
    private static RawText buildFlexRawText(Element unit) {
        String whiteSpace = Text.getWhiteSpace(unit);
        StringBuilder raw = new StringBuilder();
        StringBuilder normalized = new StringBuilder();
        ArrayList<int[]> spans = new ArrayList<>();
        ArrayList<int[]> skipped = new ArrayList<>();
        boolean anyNonBlank = false;
        for (Node child : unit.getRenderChildNodes()) {
            if (!(child instanceof TextNode textNode)) continue;
            String content = textNode.getTextContent();
            if (content == null || content.isEmpty()) continue;
            String fragment = Text.normalizeWhiteSpaceContent(content, whiteSpace);
            if (fragment == null || fragment.isBlank()) {
                int base = raw.length();
                raw.append(content);
                skipped.add(new int[]{base, base + content.length()});
                continue;
            }
            anyNonBlank = true;
            appendFragment(raw, normalized, spans, content, whiteSpace);
        }
        if (!anyNonBlank && unit.innerText != null && !unit.innerText.isBlank()) {
            String content = unit.innerText;
            String fragment = Text.normalizeWhiteSpaceContent(content, whiteSpace);
            if (fragment != null && !fragment.isBlank()) {
                appendFragment(raw, normalized, spans, content, whiteSpace);
            }
        }
        if (normalized.length() == 0) return null;
        return new RawText(raw.toString(), normalized.toString(), startsOf(spans), endsOf(spans), skipped.toArray(new int[0][]));
    }

    private static void appendFragment(StringBuilder raw, StringBuilder normalized, ArrayList<int[]> spans,
                                       String content, String whiteSpace) {
        int base = raw.length();
        raw.append(content);
        StringBuilder fragmentNormalized = new StringBuilder();
        ArrayList<int[]> fragmentSpans = new ArrayList<>();
        String value = whiteSpace == null ? "normal" : whiteSpace;
        switch (value) {
            case "pre", "pre-wrap", "break-spaces" -> normalizePre(content, fragmentNormalized, fragmentSpans);
            case "pre-line" -> {
                ArrayList<int[]> fragmentSkipped = new ArrayList<>();
                normalizePreLine(content, fragmentNormalized, fragmentSpans, fragmentSkipped);
            }
            default -> {
                ArrayList<int[]> fragmentSkipped = new ArrayList<>();
                normalizeSingleLine(content, fragmentNormalized, fragmentSpans, fragmentSkipped);
            }
        }
        for (int i = 0; i < fragmentSpans.size(); i++) {
            int[] span = fragmentSpans.get(i);
            normalized.append(fragmentNormalized.charAt(i));
            spans.add(new int[]{base + span[0], base + span[1]});
        }
    }

    private static void normalizePre(String raw, StringBuilder normalized, ArrayList<int[]> spans) {
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\r') {
                int spanEnd = i + 1;
                if (i + 1 < raw.length() && raw.charAt(i + 1) == '\n') spanEnd = i + 2;
                emit(normalized, spans, '\n', i, spanEnd);
                if (spanEnd == i + 2) i++;
            } else {
                emit(normalized, spans, c, i, i + 1);
            }
        }
    }

    private static void normalizeSingleLine(String raw, StringBuilder normalized, ArrayList<int[]> spans, ArrayList<int[]> skipped) {
        boolean pendingSpace = false;
        boolean emitted = false;
        int pendingStart = -1;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\r') {
                if (!pendingSpace) pendingStart = i;
                if (i + 1 < raw.length() && raw.charAt(i + 1) == '\n') i++;
                pendingSpace = true;
                continue;
            }
            if (c == '\n' || isCollapsibleSpace(c)) {
                if (!pendingSpace) pendingStart = i;
                pendingSpace = true;
                continue;
            }
            if (pendingSpace && emitted) {
                emit(normalized, spans, ' ', pendingStart, i);
            } else if (pendingSpace) {
                // 行首空白被折叠丢弃：记录区间供选区边界扩展
                skipped.add(new int[]{pendingStart, i});
            }
            emit(normalized, spans, c, i, i + 1);
            pendingSpace = false;
            emitted = true;
        }
        if (pendingSpace) {
            // 行尾空白被折叠丢弃
            skipped.add(new int[]{pendingStart, raw.length()});
        }
    }

    private static void normalizePreLine(String raw, StringBuilder normalized, ArrayList<int[]> spans, ArrayList<int[]> skipped) {
        int lineStart = 0;
        for (int i = 0; i <= raw.length(); i++) {
            boolean end = i >= raw.length();
            char c = end ? '\n' : raw.charAt(i);
            if (c == '\r') {
                int spanEnd = i + 1;
                if (i + 1 < raw.length() && raw.charAt(i + 1) == '\n') spanEnd = i + 2;
                appendCollapsedLine(raw, lineStart, i, normalized, spans, skipped);
                if (!end) emit(normalized, spans, '\n', i, spanEnd);
                lineStart = spanEnd;
                i = spanEnd - 1;
                continue;
            }
            if (c == '\n') {
                appendCollapsedLine(raw, lineStart, i, normalized, spans, skipped);
                if (!end) emit(normalized, spans, '\n', i, i + 1);
                lineStart = i + 1;
                continue;
            }
        }
    }

    private static void appendCollapsedLine(String raw, int lineStart, int lineEnd, StringBuilder normalized,
                                            ArrayList<int[]> spans, ArrayList<int[]> skipped) {
        boolean pendingSpace = false;
        boolean emitted = false;
        int pendingStart = -1;
        for (int i = lineStart; i < lineEnd; i++) {
            char c = raw.charAt(i);
            if (isCollapsibleSpace(c)) {
                if (!pendingSpace) pendingStart = i;
                pendingSpace = true;
                continue;
            }
            if (pendingSpace && emitted) {
                emit(normalized, spans, ' ', pendingStart, i);
            } else if (pendingSpace) {
                skipped.add(new int[]{pendingStart, i});
            }
            emit(normalized, spans, c, i, i + 1);
            pendingSpace = false;
            emitted = true;
        }
        if (pendingSpace) {
            skipped.add(new int[]{pendingStart, lineEnd});
        }
    }

    private static void emit(StringBuilder normalized, ArrayList<int[]> spans, char c, int rawStart, int rawEnd) {
        normalized.append(c);
        spans.add(new int[]{rawStart, rawEnd});
    }

    private static int[] startsOf(ArrayList<int[]> spans) {
        int[] result = new int[spans.size()];
        for (int i = 0; i < spans.size(); i++) result[i] = spans.get(i)[0];
        return result;
    }

    private static int[] endsOf(ArrayList<int[]> spans) {
        int[] result = new int[spans.size()];
        for (int i = 0; i < spans.size(); i++) result[i] = spans.get(i)[1];
        return result;
    }

    private static boolean isCollapsibleSpace(char c) {
        return c == ' ' || c == '\t' || c == '\u000B' || c == '\f';
    }
}

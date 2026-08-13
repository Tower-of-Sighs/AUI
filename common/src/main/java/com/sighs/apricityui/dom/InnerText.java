package com.sighs.apricityui.dom;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Layout-aware implementation of the HTMLElement innerText getter and setter. */
public final class InnerText {
    private static final java.util.Set<String> REPLACED_CONTENT_TAGS = java.util.Set.of(
            "INPUT", "TEXTAREA", "IFRAME", "AUDIO", "VIDEO", "CANVAS", "OBJECT", "IMG", "IMAGE"
    );

    private InnerText() {
    }

    public static String get(Element element) {
        if (element == null) return "";
        if (!hasRenderedBox(element)) return element.getTextContent();

        Builder builder = new Builder();
        appendElement(element, builder, true, false);
        return builder.finish();
    }

    public static void set(Element element, String value) {
        if (element == null) return;
        String normalized = normalizeLineEndings(value == null ? "" : value);

        // Reuse textContent's invalidation and replacement path, then build the
        // DOM representation required by innerText: every newline becomes BR.
        element.setTextContent("");
        if (normalized.isEmpty() || element.document == null) return;

        int start = 0;
        for (int i = 0; i <= normalized.length(); i++) {
            if (i < normalized.length() && normalized.charAt(i) != '\n') continue;
            if (i > start) {
                element.appendChild(element.document.createTextNode(normalized.substring(start, i)));
            }
            if (i < normalized.length()) {
                element.appendChild(element.document.createElement("br"));
            }
            start = i + 1;
        }
    }

    private static void appendElement(Element element, Builder builder, boolean root, boolean blockified) {
        if (element == null || element.isPseudoElement()) return;
        if (!root && !isRendered(element)) return;

        String tag = normalizedTag(element);
        if ("BR".equals(tag)) {
            if (!root) builder.hardBreak();
            return;
        }
        if (root && ("TR".equals(tag) || "table-row".equals(effectiveDisplay(element)))) {
            appendTableRow(element, builder);
            return;
        }
        if (root && ("TD".equals(tag) || "TH".equals(tag)
                || "table-cell".equals(effectiveDisplay(element)))) {
            appendChildren(element, builder, Interaction.isVisible(element), false);
            return;
        }
        boolean visible = Interaction.isVisible(element);
        String display = effectiveDisplay(element);
        boolean paragraph = "P".equals(tag);
        boolean independentInline = isIndependentInline(display) || "BUTTON".equals(tag);
        boolean block = paragraph || blockified || isBlockLevel(display) || isOutOfFlow(element);
        int boundaryLines = paragraph ? 2 : 1;

        if (root && REPLACED_CONTENT_TAGS.contains(tag)) return;
        if (!root && "HR".equals(tag)) {
            builder.requiredBreak(1);
            appendChildren(element, builder, visible, false);
            builder.requiredBreak(1);
            return;
        }
        if (!root && REPLACED_CONTENT_TAGS.contains(tag)) {
            if (block) {
                builder.requiredBreak(1);
            } else {
                builder.atomicBoundary();
            }
            return;
        }

        if (!root && visible && block) builder.requiredBreak(boundaryLines);

        if ("SELECT".equals(tag)) {
            if (visible) appendSelect(element, builder);
        } else if (isTableContainer(tag, display)) {
            if ("inline-table".equals(display) && !root) {
                appendIndependentTable(element, builder, visible);
            } else if (visible) {
                appendTable(element, builder);
            }
        } else if (independentInline && !root) {
            appendIndependentInline(element, builder, visible);
        } else if ("DETAILS".equals(tag) && !element.hasAttribute("open")) {
            appendClosedDetails(element, builder);
        } else {
            appendChildren(element, builder, visible, isFlexOrGrid(display));
        }

        if (!root && visible && block) builder.requiredBreak(boundaryLines);
    }

    private static void appendChildren(Element element, Builder builder, boolean visible, boolean blockifyChildren) {
        if (element.childNodes.isEmpty()) {
            if (visible && element.innerText != null && !element.innerText.isEmpty()) {
                builder.text(element.innerText, whiteSpaceOf(element), textTransformOf(element), languageOf(element));
            }
            return;
        }

        for (Node child : element.childNodes) {
            if (child instanceof TextNode textNode) {
                if (visible) {
                    builder.text(textNode.getTextContent(), whiteSpaceOf(element),
                            textTransformOf(element), languageOf(element));
                }
            } else if (child instanceof Element childElement) {
                appendElement(childElement, builder, false, blockifyChildren);
            }
        }
    }

    private static void appendIndependentInline(Element element, Builder parent, boolean visible) {
        parent.atomicBoundary();
        if (visible) {
            Builder inner = new Builder();
            appendChildren(element, inner, true, isFlexOrGrid(effectiveDisplay(element)));
            parent.literal(inner.finish());
        } else {
            // visibility:hidden does not hide descendants that explicitly restore visibility.
            Builder inner = new Builder();
            appendChildren(element, inner, false, isFlexOrGrid(effectiveDisplay(element)));
            parent.literal(inner.finish());
        }
        parent.atomicBoundary();
    }

    private static void appendIndependentTable(Element element, Builder parent, boolean visible) {
        parent.atomicBoundary();
        if (visible) {
            Builder inner = new Builder();
            appendTable(element, inner);
            parent.literal(inner.finish());
        }
        parent.atomicBoundary();
    }

    private static void appendClosedDetails(Element details, Builder builder) {
        for (Node child : details.childNodes) {
            if (child instanceof Element element && "SUMMARY".equals(normalizedTag(element))) {
                appendElement(element, builder, false, false);
                return;
            }
        }
    }

    private static void appendSelect(Element select, Builder builder) {
        List<Element> options = new ArrayList<>();
        collectOptions(select, options);
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) builder.hardBreak();
            Element option = options.get(i);
            builder.text(option.getTextContent(), whiteSpaceOf(option),
                    textTransformOf(option), languageOf(option));
        }
    }

    private static void collectOptions(Element current, List<Element> options) {
        for (Node child : current.childNodes) {
            if (!(child instanceof Element element)) continue;
            if ("OPTION".equals(normalizedTag(element))) options.add(element);
            else collectOptions(element, options);
        }
    }

    private static void appendTable(Element table, Builder builder) {
        List<Element> rows = new ArrayList<>();
        collectRows(table, rows);
        if (!rows.isEmpty()) {
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) builder.hardBreak();
                appendTableRow(rows.get(i), builder);
            }
            return;
        }

        // Tolerate the simplified parser's malformed-table tree shape.
        List<Element> cells = directCells(table);
        if (!cells.isEmpty()) {
            appendCells(cells, builder);
            return;
        }
        appendChildren(table, builder, Interaction.isVisible(table), false);
    }

    private static void collectRows(Element current, List<Element> rows) {
        for (Node child : current.childNodes) {
            if (!(child instanceof Element element) || !isRendered(element)) continue;
            if ("TR".equals(normalizedTag(element)) || "table-row".equals(effectiveDisplay(element))) {
                rows.add(element);
            } else {
                collectRows(element, rows);
            }
        }
    }

    private static void appendTableRow(Element row, Builder builder) {
        List<Element> cells = directCells(row);
        if (cells.isEmpty()) {
            appendChildren(row, builder, Interaction.isVisible(row), false);
        } else {
            appendCells(cells, builder);
        }
    }

    private static List<Element> directCells(Element parent) {
        List<Element> cells = new ArrayList<>();
        for (Node child : parent.childNodes) {
            if (!(child instanceof Element element) || !isRendered(element)) continue;
            String tag = normalizedTag(element);
            String display = effectiveDisplay(element);
            if ("TD".equals(tag) || "TH".equals(tag) || "table-cell".equals(display)) cells.add(element);
        }
        return cells;
    }

    private static void appendCells(List<Element> cells, Builder builder) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) builder.tab();
            Element cell = cells.get(i);
            Builder inner = new Builder();
            appendChildren(cell, inner, Interaction.isVisible(cell), false);
            builder.literal(inner.finish());
        }
    }

    private static boolean hasRenderedBox(Element element) {
        if (!element.isConnected()) return false;
        for (Element current = element; current != null; current = current.parentElement) {
            if (current.hasAttribute("hidden")) return false;
            if ("none".equals(effectiveDisplay(current))) return false;
            if (current != element && REPLACED_CONTENT_TAGS.contains(normalizedTag(current))) return false;
        }
        return true;
    }

    private static boolean isRendered(Element element) {
        return element != null && !element.hasAttribute("hidden") && !"none".equals(effectiveDisplay(element));
    }

    private static boolean isBlockLevel(String display) {
        if (display == null) return true;
        return switch (display) {
            case "inline", "inline-block", "inline-flex", "inline-grid", "inline-table", "contents", "none" -> false;
            default -> true;
        };
    }

    private static boolean isIndependentInline(String display) {
        return "inline-block".equals(display) || "inline-flex".equals(display)
                || "inline-grid".equals(display) || "inline-table".equals(display);
    }

    private static boolean isFlexOrGrid(String display) {
        return Layout.isFlexDisplay(display) || Layout.isGridDisplay(display);
    }

    private static boolean isOutOfFlow(Element element) {
        String position = element.getComputedStyle().position;
        return "absolute".equals(position) || "fixed".equals(position);
    }

    private static boolean isTableContainer(String tag, String display) {
        return "TABLE".equals(tag) || "table".equals(display) || "inline-table".equals(display);
    }

    private static String effectiveDisplay(Element element) {
        String declared = declaredDisplay(element);
        if (declared != null) {
            String normalized = declared.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("table") || normalized.equals("inline-table")
                    || normalized.equals("table-row") || normalized.equals("table-cell")
                    || normalized.equals("table-caption") || normalized.equals("contents")) {
                return normalized;
            }
            String computed = element.getComputedStyle().display;
            return computed == null ? "block" : computed.trim().toLowerCase(Locale.ROOT);
        }
        String tag = normalizedTag(element);
        return switch (tag) {
            case "TABLE" -> "table";
            case "TR" -> "table-row";
            case "TD", "TH" -> "table-cell";
            case "CAPTION" -> "table-caption";
            default -> computedDisplay(element);
        };
    }

    private static String declaredDisplay(Element element) {
        String declared = element.cssCache.get("display");
        String styleAttribute = element.getAttribute("style");
        if (styleAttribute != null && styleAttribute.toLowerCase(Locale.ROOT).contains("display")) {
            declared = element.getInlineStylePropertyValue("display");
        }
        return declared;
    }

    private static String computedDisplay(Element element) {
        Style style = element.getComputedStyle();
        return style.display == null ? "block" : style.display.trim().toLowerCase(Locale.ROOT);
    }

    private static String whiteSpaceOf(Element element) {
        String value = element.getComputedStyle().whiteSpace;
        if ((value == null || value.equals("normal")) && "PRE".equals(normalizedTag(element))) return "pre";
        return value == null ? "normal" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String textTransformOf(Element element) {
        String value = element.getComputedStyle().textTransform;
        return value == null ? "none" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String languageOf(Element element) {
        for (Element current = element; current != null; current = current.parentElement) {
            String language = current.getAttribute("lang");
            if (language != null && !language.isBlank()) return language;
        }
        return "";
    }

    private static String normalizedTag(Element element) {
        return element.tagName == null ? "" : element.tagName.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static final class Builder {
        private final StringBuilder output = new StringBuilder();
        private boolean pendingSpace;
        private boolean preserveNextLeadingSpace;
        private boolean lineBoundary = true;
        private int requiredBreaks;

        void text(String raw, String whiteSpace, String transform, String language) {
            if (raw == null || raw.isEmpty()) return;
            String value = TextTransform.apply(raw, transform, language);
            switch (whiteSpace) {
                case "pre", "pre-wrap", "break-spaces" -> appendPreserved(value);
                case "pre-line" -> appendCollapsed(value, true);
                default -> appendCollapsed(value, false);
            }
        }

        void literal(String value) {
            if (value == null || value.isEmpty()) return;
            flushRequiredBreaks();
            flushPendingSpace(false);
            output.append(value);
            lineBoundary = value.charAt(value.length() - 1) == '\n' || value.charAt(value.length() - 1) == '\t';
            preserveNextLeadingSpace = false;
        }

        void hardBreak() {
            pendingSpace = false;
            flushRequiredBreaks();
            output.append('\n');
            lineBoundary = true;
            preserveNextLeadingSpace = false;
        }

        void requiredBreak(int count) {
            pendingSpace = false;
            requiredBreaks = Math.max(requiredBreaks, Math.max(0, count));
            preserveNextLeadingSpace = false;
        }

        void tab() {
            pendingSpace = false;
            flushRequiredBreaks();
            output.append('\t');
            lineBoundary = true;
            preserveNextLeadingSpace = false;
        }

        void atomicBoundary() {
            flushRequiredBreaks();
            flushPendingSpace(true);
            preserveNextLeadingSpace = true;
        }

        String finish() {
            pendingSpace = false;
            requiredBreaks = 0;
            return output.toString();
        }

        private void appendCollapsed(String value, boolean preserveNewlines) {
            String normalized = normalizeLineEndings(value);
            for (int i = 0; i < normalized.length(); i++) {
                char current = normalized.charAt(i);
                if (current == '\n' && preserveNewlines) {
                    hardBreak();
                } else if (isCollapsibleSpace(current) || current == '\n') {
                    pendingSpace = true;
                } else {
                    appendCharacter(current);
                }
            }
        }

        private void appendPreserved(String value) {
            String normalized = normalizeLineEndings(value);
            for (int i = 0; i < normalized.length(); i++) {
                char current = normalized.charAt(i);
                if (current == '\n') {
                    flushRequiredBreaks();
                    flushPendingSpace(false);
                    output.append('\n');
                    lineBoundary = true;
                    preserveNextLeadingSpace = false;
                } else {
                    flushRequiredBreaks();
                    flushPendingSpace(false);
                    output.append(current);
                    lineBoundary = false;
                    preserveNextLeadingSpace = false;
                }
            }
        }

        private void appendCharacter(char value) {
            flushRequiredBreaks();
            flushPendingSpace(false);
            output.append(value);
            lineBoundary = false;
            preserveNextLeadingSpace = false;
        }

        private void flushPendingSpace(boolean force) {
            if (!pendingSpace) return;
            if (force || preserveNextLeadingSpace || !lineBoundary) {
                output.append(' ');
                lineBoundary = false;
            }
            pendingSpace = false;
        }

        private void flushRequiredBreaks() {
            if (requiredBreaks <= 0) return;
            if (output.length() > 0) {
                output.append("\n".repeat(requiredBreaks));
            }
            requiredBreaks = 0;
            lineBoundary = true;
            preserveNextLeadingSpace = false;
        }

        private static boolean isCollapsibleSpace(char value) {
            return value == ' ' || value == '\t' || value == '\f' || value == '\u000B';
        }

    }
}

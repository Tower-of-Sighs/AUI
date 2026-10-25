package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.task.FrameTaskScheduler;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class DevToolsDomTree {
    private static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
            "param", "source", "track", "wbr"
    );
    private static final int ROW_HEIGHT = 20;
    private static final int OVERSCAN = 2;
    private static final int MAX_NEW_ROWS_PER_FRAME = 2;
    private final DevToolsController controller;
    private final List<Row> rows = new ArrayList<>();
    private final Map<Row, RowView> renderedRows = new LinkedHashMap<>();
    private UUID countedDocumentUuid;
    private long countedMutationVersion = -1L;
    private int countedNodes;
    private Element container;
    private Element surface;
    private Element selected;
    private Document target;
    private long generation;
    private int firstRendered = -1;
    private int endRendered = -1;
    private double renderedScrollTop;
    DevToolsDomTree(DevToolsController controller) {
        this.controller = controller;
    }

    private static void updateToken(Element content, int index, String className, String text) {
        Node node = index < content.getChildNodes().size() ? content.getChildNodes().get(index) : null;
        if (node == null || (className == null) != (node instanceof TextNode)) {
            Node replacement = className == null ? content.document.createTextNode(text)
                    : DevToolsDom.text(content.document, "SPAN", className, text);
            if (node == null) content.appendChild(replacement);
            else content.replaceChild(replacement, node);
            return;
        }
        if (node instanceof Element span && !className.equals(span.getAttribute("class"))) {
            DevToolsDom.setClass(span, className);
        }
        if (!text.equals(node.getTextContent())) node.setTextContent(text);
    }

    private static void setAttributeIfChanged(Element element, String name, String value) {
        if (!value.equals(element.getAttribute(name))) element.setAttribute(name, value);
    }

    private static int countNodesRecursive(Element root) {
        int count = 1;
        boolean countedDomChild = false;
        for (Node child : root.getChildNodes()) {
            if (child instanceof TextNode textNode) {
                if (textNode.getTextContent() != null && !textNode.getTextContent().isBlank()) {
                    count++;
                    countedDomChild = true;
                }
                continue;
            }
            if (child instanceof Element childElement) {
                count += countNodesRecursive(childElement);
                countedDomChild = true;
            }
        }
        if (!countedDomChild && root.innerText != null && !root.innerText.isBlank()) count++;
        return count;
    }

    static boolean hasInspectableChildren(Element element) {
        if (element == null) return false;
        for (Node child : element.getChildNodes()) {
            if (child instanceof Element) return true;
            if (child instanceof TextNode textNode
                    && textNode.getTextContent() != null
                    && !textNode.getTextContent().isBlank()) return true;
        }
        return element.innerText != null && !element.innerText.isBlank();
    }

    private static String tagName(Element element) {
        return element.tagName.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    void render(Element container, Element countLabel, Document targetDocument, Element selected) {
        boolean sameContainer = this.container == container;
        clear();
        this.container = container;
        this.target = targetDocument;
        this.selected = selected;
        DevToolsDom.clear(container);
        if (targetDocument == null || targetDocument.documentElement == null) {
            countLabel.setTextContent(DevToolsTranslations.translate("devtools.apricityui.node_count", 0));
            container.append(DevToolsDom.text(container.document, "DIV", "empty-state-text",
                    DevToolsTranslations.translate("devtools.apricityui.no_debuggable_document")));
            return;
        }
        countLabel.setTextContent(DevToolsTranslations.translate("devtools.apricityui.node_count", countNodes(targetDocument)));
        surface = DevToolsDom.element(container.document, "DIV", "dom-tree-surface");
        container.append(surface);
        rebuildRows();
        if (!sameContainer) container.setScrollTop(0);
        updateVisibleRows();
        renderedScrollTop = container.getScrollTop();
        watchViewport(generation);
    }

    void clear() {
        generation++;
        rows.clear();
        renderedRows.clear();
        container = null;
        surface = null;
        selected = null;
        target = null;
    }

    private void watchViewport(long ticket) {
        FrameTaskScheduler.scheduleAfterFrames(1, deadline -> {
            if (ticket != generation || container == null || !container.isConnected()
                    || !container.document.isActive()) return true;
            if (Double.compare(renderedScrollTop, container.getScrollTop()) == 0) {
                Document.runWithContext(container.document, () -> updateVisibleRows(deadline, MAX_NEW_ROWS_PER_FRAME));
            }
            watchViewport(ticket);
            return true;
        });
    }

    boolean prepareRender() {
        if (surface == null || Double.compare(renderedScrollTop, container.getScrollTop()) == 0) return false;
        renderedScrollTop = container.getScrollTop();
        // Scrolling must refill the recycled viewport before paint; expansion remains budgeted.
        updateVisibleRows();
        return true;
    }

    void toggle(Element container, Element node, Element selected) {
        if (this.container != container || surface == null) return;
        this.selected = selected;
        rebuildRows();
        for (Map.Entry<Row, RowView> entry : renderedRows.entrySet()) {
            Row row = entry.getKey();
            if (row.element == node && row.text == null && !row.closing) {
                DevToolsDom.setClass(entry.getValue().element.children.get(0),
                        controller.isCollapsed(node) ? "dom-toggle collapsed" : "dom-toggle");
                break;
            }
        }
    }

    private void rebuildRows() {
        firstRendered = -1;
        rows.clear();
        collectNode(target.documentElement, 0);
        surface.setAttribute("style", "height:" + (rows.size() * ROW_HEIGHT + 12) + "px;");
    }

    private void collectNode(Element node, int depth) {
        rows.add(new Row(node, node, null, depth, false));
        if (controller.isCollapsed(node)) return;
        boolean hasChild = false;
        for (Node child : node.getChildNodes()) {
            if (child instanceof Element element) {
                collectNode(element, depth + 1);
                hasChild = true;
            } else if (child instanceof TextNode text && addText(node, child, text.getTextContent(), depth + 1)) {
                hasChild = true;
            }
        }
        if (!hasChild) addText(node, node, node.innerText, depth + 1);
        if (!VOID_ELEMENTS.contains(tagName(node))) rows.add(new Row(node, node, null, depth, true));
    }

    private boolean addText(Element owner, Node source, String text, int depth) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) return false;
        rows.add(new Row(owner, source,
                normalized.length() > 28 ? normalized.substring(0, 28) + "\u2026" : normalized, depth, false));
        return true;
    }

    void reveal(UUID uuid) {
        if (container == null || surface == null) return;
        container.document.commitStyleRecalc();
        container.document.commitRenderState();
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (row.text == null && !row.closing && row.element.uuid.equals(uuid)) {
                container.setScrollTop(Math.max(0, 6 + (index + 0.5) * ROW_HEIGHT - container.getClientHeight() / 2));
                updateVisibleRows();
                return;
            }
        }
    }

    private void updateVisibleRows() {
        updateVisibleRows(Long.MAX_VALUE, Integer.MAX_VALUE);
    }

    private void updateVisibleRows(long deadline, int newRowLimit) {
        if (surface == null) return;
        double height = Math.max(ROW_HEIGHT, container.getClientHeight());
        int first = Math.max(0, (int) Math.floor((container.getScrollTop() - 6) / ROW_HEIGHT) - OVERSCAN);
        first = Math.min(first, Math.max(0, rows.size() - (int) Math.ceil(height / ROW_HEIGHT)));
        int end = Math.min(rows.size(), first + (int) Math.ceil(height / ROW_HEIGHT) + OVERSCAN * 2 + 1);
        if (first == firstRendered && end == endRendered) return;
        firstRendered = -1;
        Map<Row, Integer> wanted = new LinkedHashMap<>();
        for (int index = first; index < end; index++) wanted.put(rows.get(index), index);
        // Keep the selected opening row available while smooth scrolling reveals it.
        if (selected != null) {
            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                if (row.text == null && !row.closing && row.element == selected) {
                    wanted.putIfAbsent(row, index);
                    break;
                }
            }
        }
        ArrayDeque<RowView> reusable = new ArrayDeque<>();
        renderedRows.entrySet().removeIf(entry -> {
            if (wanted.containsKey(entry.getKey())) return false;
            reusable.add(entry.getValue());
            return true;
        });
        int created = 0;
        boolean complete = true;
        for (Map.Entry<Row, Integer> entry : wanted.entrySet()) {
            Row row = entry.getKey();
            RowView binding = renderedRows.get(row);
            if (binding == null) {
                // Each row also incurs style/layout work after the scheduler yields.
                if (created >= newRowLimit || System.nanoTime() >= deadline) {
                    complete = false;
                    continue;
                }
                binding = reusable.pollFirst();
                if (binding == null) binding = createRow();
                updateRow(binding, row);
                renderedRows.put(row, binding);
                created++;
            }
            Element view = binding.element;
            String position = "top:" + (6 + entry.getValue() * ROW_HEIGHT) + "px;";
            if (!position.equals(view.getAttribute("style"))) view.setAttribute("style", position);
            if (row.text == null && !row.closing && hasInspectableChildren(row.element)) {
                String toggleClass = controller.isCollapsed(row.element) ? "dom-toggle collapsed" : "dom-toggle";
                if (!toggleClass.equals(view.children.get(0).getAttribute("class"))) {
                    DevToolsDom.setClass(view.children.get(0), toggleClass);
                }
            }
            if (view.parentElement != surface) surface.append(view);
        }
        for (RowView binding : reusable) {
            if (complete) {
                binding.element.remove();
            } else {
                // Retain the pool across budget-limited frames without displaying stale rows.
                String position = binding.element.getAttribute("style");
                if (!position.contains("visibility:hidden")) {
                    binding.element.setAttribute("style", position + "visibility:hidden;");
                }
                renderedRows.put(binding.row, binding);
            }
        }
        if (complete) {
            firstRendered = first;
            endRendered = end;
        }
    }

    private RowView createRow() {
        Document document = container.document;
        Element view = DevToolsDom.element(document, "DIV", "dom-node");
        RowView binding = new RowView(view);
        Element toggle = DevToolsDom.text(document, "SPAN", "dom-toggle", "\u25be");
        toggle.addEventListener("click", event -> {
            Row row = binding.row;
            if (row.text != null || row.closing || !hasInspectableChildren(row.element)) return;
            event.stopPropagation();
            controller.toggleCollapsed(row.element);
        });
        view.append(toggle);
        view.append(DevToolsDom.element(document, "SPAN", "dom-content"));
        bindNodeRow(binding);
        return binding;
    }

    private void updateRow(RowView binding, Row row) {
        if (binding.row != null) controller.clearHoverFromView(binding.row.element);
        binding.row = row;
        Element view = binding.element;
        boolean selectedNode = row.text == null && row.element == selected;
        setAttributeIfChanged(view, "class", "dom-node" + (row.closing ? " dom-node-close" : "")
                + (selectedNode ? " selected" : ""));
        view.removeAttribute("data-node-id");
        view.removeAttribute("data-closing-node-id");
        if (row.text == null)
            view.setAttribute(row.closing ? "data-closing-node-id" : "data-node-id", row.element.uuid.toString());
        boolean expandable = row.text == null && !row.closing && hasInspectableChildren(row.element);
        Element toggle = view.children.get(0);
        setAttributeIfChanged(toggle, "class", expandable
                ? (controller.isCollapsed(row.element) ? "dom-toggle collapsed" : "dom-toggle") : "dom-toggle leaf");
        setAttributeIfChanged(toggle, "style", "left:" + (14 + row.depth * 12) + "px;top:1px;");
        Element content = view.children.get(1);
        setAttributeIfChanged(content, "style", "left:" + (25 + row.depth * 12) + "px;");
        int token = 0;
        if (row.text != null) {
            updateToken(content, token++, "dom-text", "\"" + row.text + "\"");
        } else if (row.closing) {
            updateToken(content, token++, "dom-tag", "</" + tagName(row.element) + ">");
        } else {
            updateToken(content, token++, "dom-tag", "<" + tagName(row.element));
            for (Map.Entry<String, String> attribute : row.element.getAttributes().entrySet()) {
                updateToken(content, token++, null, " ");
                updateToken(content, token++, "dom-attr-name", attribute.getKey());
                updateToken(content, token++, null, "=");
                updateToken(content, token++, "dom-attr-val", "\"" + safe(attribute.getValue()) + "\"");
            }
            updateToken(content, token++, "dom-tag", ">");
        }
        while (content.getChildNodes().size() > token) content.getLastChild().remove();
    }

    private void bindNodeRow(RowView binding) {
        Element row = binding.element;
        row.addEventListener("mouseenter", event -> {
            if (binding.row.text == null) controller.hoverFromView(binding.row.element);
        });
        row.addEventListener("mouseleave", event -> {
            if (binding.row.text == null) controller.clearHoverFromView(binding.row.element);
        });
        row.addEventListener("click", event -> {
            if (binding.row.text != null) return;
            event.stopPropagation();
            controller.selectFromView(binding.row.element);
        });
        row.addEventListener("contextmenu", event -> {
            if (binding.row.text != null) return;
            event.preventDefault();
            event.stopPropagation();
            if (event instanceof MouseEvent mouseEvent) {
                controller.showElementContextMenu(binding.row.element, mouseEvent);
            }
        });
    }

    private int countNodes(Document document) {
        Element root = document == null ? null : document.documentElement;
        if (root == null) return 0;
        long mutationVersion = root.getSubtreeMutationVersion();
        if (document.getUuid().equals(countedDocumentUuid) && mutationVersion == countedMutationVersion) {
            return countedNodes;
        }
        countedDocumentUuid = document.getUuid();
        countedMutationVersion = mutationVersion;
        countedNodes = countNodesRecursive(root);
        return countedNodes;
    }

    private record Row(Element element, Node source, String text, int depth, boolean closing) {
    }

    private static final class RowView {
        private final Element element;
        private Row row;

        private RowView(Element element) {
            this.element = element;
        }
    }
}

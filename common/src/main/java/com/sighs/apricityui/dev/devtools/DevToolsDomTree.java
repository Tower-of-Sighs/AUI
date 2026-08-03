package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.event.MouseEvent;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class DevToolsDomTree {
    private static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
            "param", "source", "track", "wbr"
    );
    private final DevToolsController controller;
    private UUID countedDocumentUuid;
    private long countedMutationVersion = -1L;
    private int countedNodes;

    DevToolsDomTree(DevToolsController controller) {
        this.controller = controller;
    }

    void render(Element container, Element countLabel, Document targetDocument, Element selected) {
        DevToolsDom.clear(container);
        if (targetDocument == null || targetDocument.documentElement == null) {
            countLabel.setTextContent(DevToolsTranslations.translate("devtools.apricityui.node_count", 0));
            container.append(DevToolsDom.text(container.document, "DIV", "empty-state-text",
                    DevToolsTranslations.translate("devtools.apricityui.no_debuggable_document")));
            return;
        }
        int count = countNodes(targetDocument);
        countLabel.setTextContent(DevToolsTranslations.translate("devtools.apricityui.node_count", count));
        appendNode(container, targetDocument.documentElement, selected, 0);
    }

    private void appendNode(Element container, Element node, Element selected, int depth) {
        boolean selectedNode = selected != null && selected.uuid.equals(node.uuid);
        boolean hasChildren = hasInspectableChildren(node);
        boolean collapsed = controller.isCollapsed(node);

        Element row = DevToolsDom.element(container.document, "DIV", selectedNode ? "dom-node selected" : "dom-node");
        row.setAttribute("style", "padding-left:" + (14 + depth * 12) + "px;");
        row.setAttribute("data-node-id", node.uuid.toString());

        Element toggle = DevToolsDom.text(container.document, "SPAN",
                hasChildren ? (collapsed ? "dom-toggle collapsed" : "dom-toggle") : "dom-toggle leaf", "\u25be");
        if (hasChildren) {
            toggle.addEventListener("click", event -> {
                event.stopPropagation();
                controller.toggleCollapsed(node);
            });
        }
        row.append(toggle);

        Element content = DevToolsDom.element(container.document, "SPAN", "dom-content");
        content.append(DevToolsDom.text(container.document, "SPAN", "dom-tag", "<" + tagName(node)));
        for (Map.Entry<String, String> attribute : node.getAttributes().entrySet()) {
            content.append(DevToolsDom.text(container.document, "SPAN", "dom-text", " "));
            content.append(DevToolsDom.text(container.document, "SPAN", "dom-attr-name", attribute.getKey()));
            content.append(DevToolsDom.text(container.document, "SPAN", "dom-text", "="));
            content.append(DevToolsDom.text(container.document, "SPAN", "dom-attr-val",
                    "\"" + safe(attribute.getValue()) + "\""));
        }
        content.append(DevToolsDom.text(container.document, "SPAN", "dom-tag", ">"));
        row.append(content);
        bindNodeRow(row, node);
        container.append(row);

        if (collapsed) return;
        if (!hasChildren) {
            appendClosingRow(container, node, selectedNode, depth);
            return;
        }

        Element children = DevToolsDom.element(container.document, "DIV", "dom-children");
        children.setAttribute("data-parent", node.uuid.toString());
        appendChildren(children, node, selected, depth + 1);
        appendClosingRow(children, node, selectedNode, depth);
        container.append(children);
    }

    private void appendClosingRow(Element container, Element node, boolean selectedNode, int depth) {
        if (VOID_ELEMENTS.contains(tagName(node))) return;
        Element closeRow = DevToolsDom.element(container.document, "DIV",
                selectedNode ? "dom-node dom-node-close selected" : "dom-node dom-node-close");
        closeRow.setAttribute("style", "padding-left:" + (14 + depth * 12) + "px;");
        closeRow.setAttribute("data-closing-node-id", node.uuid.toString());
        closeRow.append(DevToolsDom.text(container.document, "SPAN", "dom-toggle leaf", "\u25be"));
        Element closeContent = DevToolsDom.element(container.document, "SPAN", "dom-content");
        closeContent.append(DevToolsDom.text(container.document, "SPAN", "dom-tag", "</" + tagName(node) + ">"));
        closeRow.append(closeContent);
        bindNodeRow(closeRow, node);
        container.append(closeRow);
    }

    private void appendChildren(Element container, Element parent, Element selected, int depth) {
        boolean appendedDomChild = false;
        for (Node child : parent.getChildNodes()) {
            if (child instanceof TextNode textNode) {
                if (appendTextRow(container, textNode.getTextContent(), depth)) appendedDomChild = true;
                continue;
            }
            if (child instanceof Element childElement) {
                appendNode(container, childElement, selected, depth);
                appendedDomChild = true;
            }
        }
        if (!appendedDomChild && parent.innerText != null && !parent.innerText.isBlank()) {
            appendTextRow(container, parent.innerText, depth);
        }
    }

    private boolean appendTextRow(Element container, String value, int depth) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return false;
        String display = normalized.length() > 28 ? normalized.substring(0, 28) + "\u2026" : normalized;
        Element row = DevToolsDom.element(container.document, "DIV", "dom-node");
        row.setAttribute("style", "padding-left:" + (14 + depth * 12) + "px;");
        row.append(DevToolsDom.text(container.document, "SPAN", "dom-toggle leaf", "\u25be"));
        Element content = DevToolsDom.element(container.document, "SPAN", "dom-content");
        content.append(DevToolsDom.text(container.document, "SPAN", "dom-text", "\"" + display + "\""));
        row.append(content);
        container.append(row);
        return true;
    }

    private void bindNodeRow(Element row, Element target) {
        row.addEventListener("mouseenter", event -> controller.hoverFromView(target));
        row.addEventListener("mouseleave", event -> controller.clearHoverFromView(target));
        row.addEventListener("click", event -> {
            event.stopPropagation();
            controller.selectFromView(target);
        });
        row.addEventListener("contextmenu", event -> {
            event.preventDefault();
            event.stopPropagation();
            if (event instanceof MouseEvent mouseEvent) {
                controller.showElementContextMenu(target, mouseEvent);
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
}

package com.sighs.apricityui.dev.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.layout.Box;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;

final class DebugDom {
    static final int DEFAULT_MAX_DEPTH = 32;
    static final int DEFAULT_MAX_NODES = 5000;
    static final int MAX_DEPTH = 128;
    static final int MAX_NODES = 20000;

    private DebugDom() {
    }

    static JsonObject query(Document document, String selector) {
        Element element;
        try {
            element = document.querySelector(selector);
        } catch (RuntimeException invalidSelector) {
            throw invalidSelector(selector);
        }
        JsonObject result = new JsonObject();
        if (element == null) result.add("nodeId", JsonNull.INSTANCE);
        else result.addProperty("nodeId", element.uuid.toString());
        return result;
    }

    static JsonObject queryAll(Document document, String selector) {
        JsonArray ids = new JsonArray();
        try {
            for (Element element : document.querySelectorAll(selector)) {
                ids.add(element.uuid.toString());
            }
        } catch (RuntimeException invalidSelector) {
            throw invalidSelector(selector);
        }
        JsonObject result = new JsonObject();
        result.add("nodeIds", ids);
        return result;
    }

    static JsonObject snapshot(Document document, int maxDepth, int maxNodes) {
        Node root = document.documentElement != null ? document.documentElement : document.body;
        JsonObject result = new JsonObject();
        if (root == null) {
            result.add("root", JsonNull.INSTANCE);
            result.addProperty("nodeCount", 0);
            return result;
        }
        Counter counter = new Counter(maxNodes);
        result.add("root", snapshotNode(root, 0, maxDepth, counter));
        result.addProperty("nodeCount", counter.count);
        return result;
    }

    static JsonObject attributes(Element element) {
        JsonObject attributes = new JsonObject();
        for (Map.Entry<String, String> entry : new TreeMap<>(element.getAttributes()).entrySet()) {
            attributes.addProperty(entry.getKey(), entry.getValue());
        }
        JsonObject result = new JsonObject();
        result.add("attributes", attributes);
        return result;
    }

    static JsonObject text(Node node) {
        JsonObject result = new JsonObject();
        result.addProperty("text", node.getTextContent());
        return result;
    }

    static JsonObject computedStyle(Element element) {
        JsonObject result = new JsonObject();
        result.addProperty("cssText", element.getComputedStyle().toCss());
        return result;
    }

    static JsonObject boxModel(Document document, Element element) {
        Element.DOMRect border = element.getBoundingClientRect();
        Box box = Box.of(element);
        JsonObject result = new JsonObject();
        result.add("margin", screenRect(document,
                border.x - box.getMarginLeft(),
                border.y - box.getMarginTop(),
                border.width + box.getMarginHorizontal(),
                border.height + box.getMarginVertical()));
        result.add("border", screenRect(document, border.x, border.y, border.width, border.height));
        double paddingX = border.x + box.getBorderLeft();
        double paddingY = border.y + box.getBorderTop();
        double paddingWidth = Math.max(0, border.width - box.getBorderHorizontal());
        double paddingHeight = Math.max(0, border.height - box.getBorderVertical());
        result.add("padding", screenRect(document, paddingX, paddingY, paddingWidth, paddingHeight));
        result.add("content", screenRect(document,
                paddingX + box.getPaddingLeft(),
                paddingY + box.getPaddingTop(),
                Math.max(0, paddingWidth - box.getPaddingHorizontal()),
                Math.max(0, paddingHeight - box.getPaddingVertical())));
        return result;
    }

    static Element requireElement(Document document, String nodeId) {
        Node node = requireNode(document, nodeId);
        if (!(node instanceof Element element)) {
            throw new DebugProtocolException(DebugProtocolException.NODE_DETACHED, "Node is detached");
        }
        return element;
    }

    static Node requireNode(Document document, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new DebugProtocolException(DebugProtocolException.INVALID_PARAMS, "nodeId is required");
        }
        Node node = findNode(document, nodeId);
        if (node == null || node.document != document || !node.isConnected()) {
            throw new DebugProtocolException(DebugProtocolException.NODE_DETACHED, "Node is detached");
        }
        return node;
    }

    private static Node findNode(Document document, String nodeId) {
        Node root = document.documentElement != null ? document.documentElement : document.body;
        if (root == null) return null;
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (node.uuid.toString().equals(nodeId)) return node;
            for (int index = node.childNodes.size() - 1; index >= 0; index--) {
                Node child = node.childNodes.get(index);
                if (child != null) pending.push(child);
            }
        }
        return null;
    }

    private static DebugProtocolException invalidSelector(String selector) {
        return new DebugProtocolException(DebugProtocolException.INVALID_PARAMS,
                "Invalid selector: " + selector);
    }

    private static JsonObject snapshotNode(Node node, int depth, int maxDepth, Counter counter) {
        counter.increment();
        JsonObject result = new JsonObject();
        result.addProperty("nodeId", node.uuid.toString());
        result.addProperty("nodeType", node.getNodeType());
        result.addProperty("nodeName", node.getNodeName());
        if (node instanceof Element element) {
            JsonObject attributes = new JsonObject();
            for (Map.Entry<String, String> entry : new TreeMap<>(element.getAttributes()).entrySet()) {
                attributes.addProperty(entry.getKey(), entry.getValue());
            }
            result.add("attributes", attributes);
        } else {
            result.addProperty("text", node.getTextContent());
        }

        JsonArray children = new JsonArray();
        if (depth < maxDepth) {
            for (Node child : node.childNodes) {
                if (child != null) children.add(snapshotNode(child, depth + 1, maxDepth, counter));
            }
        }
        result.add("children", children);
        return result;
    }

    private static JsonObject screenRect(Document document, double x, double y, double width, double height) {
        var position = document.documentToScreenPosition(new com.sighs.apricityui.layout.Position(x, y));
        JsonObject rect = new JsonObject();
        rect.addProperty("x", position.x);
        rect.addProperty("y", position.y);
        rect.addProperty("width", width * document.getViewportScaleX());
        rect.addProperty("height", height * document.getViewportScaleY());
        rect.addProperty("left", position.x);
        rect.addProperty("top", position.y);
        rect.addProperty("right", position.x + width * document.getViewportScaleX());
        rect.addProperty("bottom", position.y + height * document.getViewportScaleY());
        return rect;
    }

    private static final class Counter {
        private final int limit;
        private int count;

        private Counter(int limit) {
            this.limit = limit;
        }

        private void increment() {
            count++;
            if (count > limit) {
                throw new DebugProtocolException(DebugProtocolException.LIMIT_EXCEEDED,
                        "DOM snapshot exceeds maxNodes=" + limit);
            }
        }
    }
}

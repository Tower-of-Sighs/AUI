package com.sighs.apricityui.util;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;

public final class AuiLog {
    private static final int DEFAULT_TEXT_LIMIT = 180;

    private AuiLog() {
    }

    public static String source(String path) {
        return path == null || path.isBlank() ? "<inline>" : path;
    }

    public static String compact(String value) {
        return compact(value, DEFAULT_TEXT_LIMIT);
    }

    public static String compact(String value, int limit) {
        if (value == null) return "<null>";
        String normalized = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        int safeLimit = Math.max(16, limit);
        if (normalized.length() <= safeLimit) return normalized;
        return normalized.substring(0, safeLimit - 3) + "...";
    }

    public static String node(Node node) {
        if (node == null) return "<null>";
        if (node instanceof Element element) return element(element);
        return node.getClass().getSimpleName() + "@" + shortUuid(node.uuid);
    }

    public static String element(Element element) {
        if (element == null) return "<null>";
        String tag = element.tagName == null || element.tagName.isBlank()
                ? element.getClass().getSimpleName()
                : element.tagName;
        String id = element.id;
        String suffix = id == null || id.isBlank() ? "" : "#" + compact(id, 64);
        return tag + suffix + "@" + shortUuid(element.uuid);
    }

    private static String shortUuid(java.util.UUID uuid) {
        if (uuid == null) return "?";
        String text = uuid.toString();
        return text.length() <= 8 ? text : text.substring(0, 8);
    }
}

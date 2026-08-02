package com.sighs.apricityui.util;

import java.util.Locale;
import java.util.Map;
import com.sighs.apricityui.dom.CommentNode;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.parser.HTML;

/**
 * 把 DOM 节点序列化为 HTML 字符串的纯函数工具（从 Element 拆出）。
 * 不持有任何状态，仅依赖节点的公开成员。
 */
public final class HtmlSerializer {
    private HtmlSerializer() {
    }

    public static String serializeNode(Node node) {
        if (node == null) return "";
        if (node instanceof TextNode textNode) {
            return escapeHtml(textNode.getTextContent());
        }
        if (node instanceof CommentNode commentNode) {
            return "<!--" + escapeHtml(commentNode.getTextContent()) + "-->";
        }
        if (!(node instanceof Element element)) {
            return "";
        }
        return serializeHtml(element);
    }

    public static String serializeHtml(Element element) {
        if (element == null) return "";
        StringBuilder builder = new StringBuilder();
        builder.append('<').append(element.tagName.toLowerCase(Locale.ROOT));
        for (Map.Entry<String, String> entry : element.getAttributes().entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) continue;
            builder.append(' ').append(key);
            String value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                builder.append("=\"").append(escapeHtml(value)).append('"');
            }
        }
        builder.append('>');
        if (!element.childNodes.isEmpty()) {
            for (Node child : element.childNodes) {
                builder.append(serializeNode(child));
            }
        } else if (!element.innerText.isEmpty()) {
            builder.append(escapeHtml(element.innerText));
        }
        builder.append("</").append(element.tagName.toLowerCase(Locale.ROOT)).append('>');
        return builder.toString();
    }

    public static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

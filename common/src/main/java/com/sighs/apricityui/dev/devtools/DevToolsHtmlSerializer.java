package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.dom.CommentNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.dom.TextNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Serializes the live DOM while restoring source blocks extracted by the runtime loader. */
final class DevToolsHtmlSerializer {
    private static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "basefont", "bgsound", "br", "col", "embed", "frame", "hr", "img",
            "input", "keygen", "link", "meta", "param", "source", "texture", "track", "wbr"
    );
    private static final Set<String> RAW_TEXT_ELEMENTS = Set.of(
            "iframe", "noembed", "noframes", "plaintext", "script", "style", "xmp"
    );
    private static final Pattern DOCTYPE = Pattern.compile("(?is)<!doctype\\s+[^>]+>");
    private static final Pattern STYLE_OR_SCRIPT = Pattern.compile(
            "(?is)<style\\b[^>]*>.*?</style\\s*>|<script\\b[^>]*>.*?</script\\s*>"
    );
    private static final Pattern LINK = Pattern.compile("(?is)<link\\b[^>]*>");
    private static final Pattern REL_ATTRIBUTE = Pattern.compile(
            "(?is)\\brel\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'<>`]+))"
    );

    private DevToolsHtmlSerializer() {
    }

    static String serialize(Document document, String originalHtml) {
        if (document == null || document.documentElement == null) return "";
        String original = originalHtml == null ? "" : originalHtml;
        String doctype = findDoctype(original);
        List<SourceBlock> blocks = extractSourceBlocks(original);
        StringBuilder output = new StringBuilder(doctype.length() + 1024);
        output.append(doctype).append('\n');
        serializeElement(document.documentElement, output, blocks);
        output.append('\n');
        return output.toString();
    }

    static String serializeElement(Element element) {
        if (element == null) return "";
        StringBuilder output = new StringBuilder(256);
        serializeElement(element, output, List.of());
        return output.toString();
    }

    private static void serializeElement(Element element, StringBuilder output, List<SourceBlock> blocks) {
        String tag = element.tagName.toLowerCase(Locale.ROOT);
        output.append('<').append(tag);
        for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
            String name = attribute.getKey();
            if (name == null || name.isBlank()) continue;
            output.append(' ').append(name).append("=\"")
                    .append(escapeAttribute(attribute.getValue())).append('"');
        }
        output.append('>');
        if (VOID_ELEMENTS.contains(tag)) return;

        for (Node child : element.childNodes) serializeNode(child, output, tag, blocks);
        if (element.childNodes.isEmpty() && element.getTextContent() != null) {
            output.append(escapeText(element.getTextContent(), RAW_TEXT_ELEMENTS.contains(tag)));
        }
        if ("head".equals(tag)) appendBlocks(output, blocks, true);
        if ("body".equals(tag)) appendBlocks(output, blocks, false);
        output.append("</").append(tag).append('>');
    }

    private static void serializeNode(Node node, StringBuilder output, String parentTag, List<SourceBlock> blocks) {
        if (node instanceof Element element) {
            serializeElement(element, output, blocks);
        } else if (node instanceof TextNode text) {
            output.append(escapeText(text.getData(), RAW_TEXT_ELEMENTS.contains(parentTag)));
        } else if (node instanceof CommentNode comment) {
            output.append("<!--").append(comment.getTextContent()).append("-->");
        }
    }

    private static void appendBlocks(StringBuilder output, List<SourceBlock> blocks, boolean head) {
        for (SourceBlock block : blocks) {
            if (block.head() == head) output.append('\n').append(block.html()).append('\n');
        }
    }

    private static String findDoctype(String source) {
        Matcher matcher = DOCTYPE.matcher(source);
        return matcher.find() ? matcher.group() : "<!DOCTYPE html>";
    }

    private static List<SourceBlock> extractSourceBlocks(String source) {
        ArrayList<SourceBlock> result = new ArrayList<>();
        if (source.isBlank()) return result;
        int headStart = indexOfIgnoreCase(source, "<head");
        int headEnd = indexOfIgnoreCase(source, "</head");
        collectBlocks(result, STYLE_OR_SCRIPT.matcher(source), headStart, headEnd, false);
        collectBlocks(result, LINK.matcher(source), headStart, headEnd, true);
        result.sort(Comparator.comparingInt(SourceBlock::offset));
        return result;
    }

    private static void collectBlocks(List<SourceBlock> target, Matcher matcher,
                                      int headStart, int headEnd, boolean stylesheetOnly) {
        while (matcher.find()) {
            if (stylesheetOnly && !isStylesheetLink(matcher.group())) continue;
            boolean inHead = headStart >= 0 && matcher.start() > headStart
                    && (headEnd < 0 || matcher.start() < headEnd);
            target.add(new SourceBlock(matcher.start(), matcher.group(), inHead));
        }
    }

    private static boolean isStylesheetLink(String link) {
        Matcher matcher = REL_ATTRIBUTE.matcher(link == null ? "" : link);
        if (!matcher.find()) return false;
        String value = matcher.group(1) != null ? matcher.group(1)
                : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        if (value == null) return false;
        for (String token : value.trim().split("\\s+")) {
            if ("stylesheet".equalsIgnoreCase(token)) return true;
        }
        return false;
    }

    private static int indexOfIgnoreCase(String source, String needle) {
        return source.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static String escapeAttribute(String value) {
        return escapeText(value, false).replace("\"", "&quot;");
    }

    private static String escapeText(String value, boolean raw) {
        if (value == null || value.isEmpty()) return "";
        if (raw) return value;
        return value.replace("&", "&amp;").replace("\u00a0", "&nbsp;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private record SourceBlock(int offset, String html, boolean head) {
    }
}

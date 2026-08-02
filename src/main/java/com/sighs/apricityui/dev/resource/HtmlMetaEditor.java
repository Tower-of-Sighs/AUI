package com.sighs.apricityui.dev.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.sighs.apricityui.parser.HTML;

/** Reads and updates only the meta elements owned by an HTML document's head. */
public final class HtmlMetaEditor {
    private static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
            "param", "source", "track", "wbr"
    );

    private HtmlMetaEditor() {
    }

    public static LoadResult load(Path path) {
        String error = validateTarget(path);
        if (!error.isBlank()) return LoadResult.failure(error);
        try {
            String html = Files.readString(path, StandardCharsets.UTF_8);
            return LoadResult.success(extractMetaMarkup(html));
        } catch (IOException ignored) {
            return LoadResult.failure("Could not read the HTML file");
        }
    }

    public static EditResult save(Path path, String metaMarkup) {
        String error = validateTarget(path);
        if (!error.isBlank()) return EditResult.failure(error);
        if (!isValidMetaMarkup(metaMarkup)) {
            return EditResult.failure("META editor accepts only <meta> tags");
        }
        try {
            String html = Files.readString(path, StandardCharsets.UTF_8);
            String updated = replaceMetaMarkup(html, metaMarkup);
            Files.writeString(path, updated, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return EditResult.success(path);
        } catch (IOException ignored) {
            return EditResult.failure("Could not update the HTML file");
        }
    }

    public static boolean isValidMetaMarkup(String markup) {
        String source = normalizeNewlines(markup).trim();
        if (source.isEmpty()) return true;
        List<Tag> tags = scanTags(source, 0, source.length());
        if (tags.isEmpty()) return false;
        int cursor = 0;
        for (Tag tag : tags) {
            if (!source.substring(cursor, tag.start()).isBlank()) return false;
            if (tag.closing() || !"meta".equals(tag.name())) return false;
            cursor = tag.end();
        }
        return source.substring(cursor).isBlank();
    }

    public static MetaSettings parseSettings(String markup) {
        String source = markup == null ? "" : markup;
        String charset = "";
        String fontMode = "";
        String viewport = "";
        String mouseEvents = "";
        List<String> preserved = new ArrayList<>();
        for (Tag tag : scanTags(source, 0, source.length())) {
            if (tag.closing() || !"meta".equals(tag.name())) continue;
            String raw = source.substring(tag.start(), tag.end()).trim();
            String tagCharset = attributeValue(source, tag, "charset");
            if (tagCharset != null) {
                if (charset.isEmpty()) charset = tagCharset;
                continue;
            }
            String name = attributeValue(source, tag, "name");
            String content = attributeValue(source, tag, "content");
            if (name == null) {
                preserved.add(raw);
                continue;
            }
            switch (name.trim().toLowerCase(Locale.ROOT)) {
                case "aui-font-mode" -> {
                    if (fontMode.isEmpty()) fontMode = safe(content);
                }
                case "aui-viewport" -> {
                    if (viewport.isEmpty()) viewport = safe(content);
                }
                case "aui-mouse-events" -> {
                    if (mouseEvents.isEmpty()) mouseEvents = safe(content);
                }
                default -> preserved.add(raw);
            }
        }
        return new MetaSettings(charset, fontMode, viewport, mouseEvents, preserved);
    }

    public static String toMetaMarkup(MetaSettings settings) {
        if (settings == null) return "";
        List<String> tags = new ArrayList<>();
        appendAttributeMeta(tags, "charset", settings.charset());
        appendNamedMeta(tags, "aui-font-mode", settings.fontMode());
        appendNamedMeta(tags, "aui-viewport", settings.viewport());
        appendNamedMeta(tags, "aui-mouse-events", settings.mouseEvents());
        for (String preserved : settings.preservedMeta()) {
            if (preserved != null && !preserved.isBlank()) tags.add(preserved.trim());
        }
        return String.join("\n", tags);
    }

    static String extractMetaMarkup(String html) {
        HeadRange head = findHead(html);
        List<Tag> metas = head == null
                ? findImplicitHeadMetas(html)
                : scanTags(html, head.contentStart(), head.contentEnd()).stream()
                .filter(tag -> !tag.closing() && "meta".equals(tag.name()))
                .toList();
        List<String> tags = new ArrayList<>();
        for (Tag tag : metas) {
            tags.add(html.substring(tag.start(), tag.end()).trim());
        }
        return String.join("\n", tags);
    }

    static String replaceMetaMarkup(String html, String metaMarkup) {
        String source = html == null ? "" : html;
        String replacement = normalizeNewlines(metaMarkup).trim();
        if (!isValidMetaMarkup(replacement)) {
            throw new IllegalArgumentException("Only meta tags are allowed");
        }

        HeadRange head = findHead(source);
        if (head == null) {
            List<Tag> implicitMetas = findImplicitHeadMetas(source);
            String withoutImplicitMetas = removeTags(source, implicitMetas);
            return addHead(withoutImplicitMetas, replacement);
        }

        List<Tag> metas = scanTags(source, head.contentStart(), head.contentEnd()).stream()
                .filter(tag -> !tag.closing() && "meta".equals(tag.name()))
                .toList();
        if (metas.isEmpty()) {
            if (replacement.isEmpty()) return source;
            String newline = newlineOf(source);
            String indent = childIndent(source, head.openStart());
            String block = newline + indent + indentLines(replacement, indent, newline) + newline;
            return source.substring(0, head.contentStart()) + block + source.substring(head.contentStart());
        }

        Tag first = metas.get(0);
        String indent = lineIndent(source, first.start());
        String newline = newlineOf(source);
        StringBuilder updated = new StringBuilder(source.length() + replacement.length());
        updated.append(source, 0, first.start());
        if (!replacement.isEmpty()) updated.append(indentLines(replacement, indent, newline));
        int cursor = first.end();
        for (int i = 1; i < metas.size(); i++) {
            Tag tag = metas.get(i);
            updated.append(source, cursor, tag.start());
            cursor = tag.end();
        }
        updated.append(source, cursor, source.length());
        return updated.toString();
    }

    private static List<Tag> findImplicitHeadMetas(String html) {
        List<Tag> metas = new ArrayList<>();
        if (html == null || html.isEmpty()) return metas;
        Deque<String> ancestors = new ArrayDeque<>();
        for (Tag tag : scanTags(html, 0, html.length())) {
            if (tag.closing()) {
                popThrough(ancestors, tag.name());
                continue;
            }
            if ("body".equals(tag.name())) break;
            boolean documentLevel = ancestors.isEmpty()
                    || (ancestors.size() == 1 && "html".equals(ancestors.peek()));
            if (documentLevel && "meta".equals(tag.name())) metas.add(tag);
            if (!tag.selfClosing() && !VOID_ELEMENTS.contains(tag.name())) ancestors.push(tag.name());
        }
        return metas;
    }

    private static void popThrough(Deque<String> ancestors, String name) {
        if (!ancestors.contains(name)) return;
        while (!ancestors.isEmpty()) {
            if (name.equals(ancestors.pop())) return;
        }
    }

    private static String removeTags(String source, List<Tag> tags) {
        if (tags.isEmpty()) return source;
        StringBuilder result = new StringBuilder(source.length());
        int cursor = 0;
        for (Tag tag : tags) {
            result.append(source, cursor, tag.start());
            cursor = tag.end();
        }
        result.append(source, cursor, source.length());
        return result.toString();
    }

    private static String addHead(String html, String replacement) {
        if (replacement.isEmpty()) return html;
        String newline = newlineOf(html);
        String block = "<head>" + newline + "    " + indentLines(replacement, "    ", newline)
                + newline + "</head>";
        List<Tag> tags = scanTags(html, 0, html.length());
        Tag body = firstTag(tags, "body", false);
        if (body != null) return html.substring(0, body.start()) + block + newline + html.substring(body.start());
        Tag root = firstTag(tags, "html", false);
        if (root != null) return html.substring(0, root.end()) + newline + block + html.substring(root.end());
        return block + newline + html;
    }

    private static HeadRange findHead(String html) {
        if (html == null || html.isEmpty()) return null;
        List<Tag> tags = scanTags(html, 0, html.length());
        Tag open = firstTag(tags, "head", false);
        if (open == null) return null;
        for (Tag tag : tags) {
            if (tag.start() >= open.end() && tag.closing() && "head".equals(tag.name())) {
                return new HeadRange(open.start(), open.end(), tag.start());
            }
        }
        return null;
    }

    private static Tag firstTag(List<Tag> tags, String name, boolean closing) {
        for (Tag tag : tags) {
            if (tag.closing() == closing && name.equals(tag.name())) return tag;
        }
        return null;
    }

    private static List<Tag> scanTags(String source, int from, int to) {
        List<Tag> result = new ArrayList<>();
        if (source == null || source.isEmpty()) return result;
        int limit = Math.min(source.length(), Math.max(from, to));
        int cursor = Math.max(0, from);
        while (cursor < limit) {
            int start = source.indexOf('<', cursor);
            if (start < 0 || start >= limit) break;
            if (source.startsWith("<!--", start)) {
                int commentEnd = source.indexOf("-->", start + 4);
                cursor = commentEnd < 0 ? limit : commentEnd + 3;
                continue;
            }
            int end = findTagEnd(source, start + 1, limit);
            if (end < 0) break;
            Tag tag = parseTag(source, start, end + 1);
            if (tag != null) {
                result.add(tag);
                if (!tag.closing() && ("script".equals(tag.name()) || "style".equals(tag.name()))) {
                    int rawClose = indexOfIgnoreCase(source, "</" + tag.name(), tag.end(), limit);
                    if (rawClose >= 0) {
                        cursor = rawClose;
                        continue;
                    }
                }
            }
            cursor = end + 1;
        }
        return result;
    }

    private static int findTagEnd(String source, int from, int limit) {
        char quote = 0;
        for (int i = from; i < limit; i++) {
            char ch = source.charAt(i);
            if (quote != 0) {
                if (ch == quote) quote = 0;
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (ch == '>') {
                return i;
            }
        }
        return -1;
    }

    private static String attributeValue(String source, Tag tag, String attributeName) {
        int cursor = tag.start() + 1;
        int limit = tag.end() - 1;
        while (cursor < limit && Character.isWhitespace(source.charAt(cursor))) cursor++;
        while (cursor < limit && isNameCharacter(source.charAt(cursor))) cursor++;
        while (cursor < limit) {
            while (cursor < limit && Character.isWhitespace(source.charAt(cursor))) cursor++;
            if (cursor >= limit || source.charAt(cursor) == '/') break;
            int nameStart = cursor;
            while (cursor < limit && isNameCharacter(source.charAt(cursor))) cursor++;
            if (cursor == nameStart) {
                cursor++;
                continue;
            }
            String name = source.substring(nameStart, cursor);
            while (cursor < limit && Character.isWhitespace(source.charAt(cursor))) cursor++;
            String value = "";
            if (cursor < limit && source.charAt(cursor) == '=') {
                cursor++;
                while (cursor < limit && Character.isWhitespace(source.charAt(cursor))) cursor++;
                if (cursor < limit && (source.charAt(cursor) == '\'' || source.charAt(cursor) == '"')) {
                    char quote = source.charAt(cursor++);
                    int valueStart = cursor;
                    while (cursor < limit && source.charAt(cursor) != quote) cursor++;
                    value = source.substring(valueStart, cursor);
                    if (cursor < limit) cursor++;
                } else {
                    int valueStart = cursor;
                    while (cursor < limit && !Character.isWhitespace(source.charAt(cursor))
                            && source.charAt(cursor) != '/') cursor++;
                    value = source.substring(valueStart, cursor);
                }
            }
            if (attributeName.equalsIgnoreCase(name)) return decodeAttribute(value);
        }
        return null;
    }

    private static Tag parseTag(String source, int start, int end) {
        int cursor = start + 1;
        while (cursor < end && Character.isWhitespace(source.charAt(cursor))) cursor++;
        boolean closing = cursor < end && source.charAt(cursor) == '/';
        if (closing) cursor++;
        while (cursor < end && Character.isWhitespace(source.charAt(cursor))) cursor++;
        int nameStart = cursor;
        while (cursor < end) {
            char ch = source.charAt(cursor);
            if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != ':') break;
            cursor++;
        }
        if (cursor == nameStart) return null;
        int slash = end - 2;
        while (slash > cursor && Character.isWhitespace(source.charAt(slash))) slash--;
        boolean selfClosing = !closing && slash >= cursor && source.charAt(slash) == '/';
        return new Tag(start, end, source.substring(nameStart, cursor).toLowerCase(Locale.ROOT), closing,
                selfClosing);
    }

    private static boolean isNameCharacter(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '-' || ch == ':' || ch == '_';
    }

    private static void appendAttributeMeta(List<String> tags, String name, String value) {
        if (value == null || value.isBlank()) return;
        tags.add("<meta " + name + "=\"" + encodeAttribute(value.trim()) + "\">");
    }

    private static void appendNamedMeta(List<String> tags, String name, String content) {
        if (content == null || content.isBlank()) return;
        tags.add("<meta name=\"" + name + "\" content=\"" + encodeAttribute(content.trim()) + "\">");
    }

    private static String encodeAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String decodeAttribute(String value) {
        return safe(value).replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int indexOfIgnoreCase(String source, String target, int from, int limit) {
        int max = Math.min(source.length(), limit) - target.length();
        for (int i = Math.max(0, from); i <= max; i++) {
            if (source.regionMatches(true, i, target, 0, target.length())) return i;
        }
        return -1;
    }

    private static String childIndent(String source, int parentStart) {
        return lineIndent(source, parentStart) + "    ";
    }

    private static String lineIndent(String source, int position) {
        int lineStart = Math.max(source.lastIndexOf('\n', Math.max(0, position - 1)) + 1, 0);
        int cursor = lineStart;
        while (cursor < position) {
            char ch = source.charAt(cursor);
            if (ch != ' ' && ch != '\t') break;
            cursor++;
        }
        return source.substring(lineStart, cursor);
    }

    private static String indentLines(String value, String indent, String newline) {
        return normalizeNewlines(value).replace("\n", newline + indent);
    }

    private static String normalizeNewlines(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String newlineOf(String html) {
        return html != null && html.contains("\r\n") ? "\r\n" : "\n";
    }

    private static String validateTarget(Path path) {
        if (path == null || !Files.isRegularFile(path)) return "HTML file is unavailable";
        Path name = path.getFileName();
        if (name == null || !name.toString().toLowerCase(Locale.ROOT).endsWith(".html")) {
            return "META editor supports HTML files only";
        }
        return "";
    }

    private record Tag(int start, int end, String name, boolean closing, boolean selfClosing) {
    }

    private record HeadRange(int openStart, int contentStart, int contentEnd) {
    }

    public record MetaSettings(String charset, String fontMode, String viewport, String mouseEvents,
                               List<String> preservedMeta) {
        public MetaSettings {
            charset = safe(charset);
            fontMode = safe(fontMode);
            viewport = safe(viewport);
            mouseEvents = safe(mouseEvents);
            preservedMeta = preservedMeta == null ? List.of() : List.copyOf(preservedMeta);
        }
    }

    public record LoadResult(boolean success, String metaMarkup, String message) {
        private static LoadResult success(String markup) {
            return new LoadResult(true, markup, "");
        }

        private static LoadResult failure(String message) {
            return new LoadResult(false, "", message);
        }
    }

    public record EditResult(boolean success, Path target, String message) {
        private static EditResult success(Path target) {
            return new EditResult(true, target, "");
        }

        private static EditResult failure(String message) {
            return new EditResult(false, null, message);
        }
    }
}

package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.parser.CSS;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Writes stylesheet edits without serializing the inspected DOM unless requested. */
final class DevToolsCssSerializer {
    private static final Pattern STYLE_TAG = Pattern.compile(
            "(?is)(<style\\b[^>]*>)(.*?)(</style\\s*>)"
    );
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern URL = Pattern.compile(
            "(?i)url\\s*\\(\\s*['\"]?(.*?)['\"]?\\s*\\)"
    );
    private static final Pattern MEDIA_LENGTH = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private DevToolsCssSerializer() {
    }

    static Result prepare(Document document, String originalHtml,
                          DevToolsDocumentStore.SaveTarget htmlTarget,
                          List<Loader.StaticResourceEntry> entries,
                          boolean production, boolean saveDomTree) {
        if (document == null || htmlTarget == null || originalHtml == null) {
            return Result.failure("Nothing to save");
        }

        Map<String, List<CSS.DebugRule>> rulesBySource = rulesBySource(document.CSSDebugRules);
        MediaViewport viewport = mediaViewport(document);
        String html = originalHtml;
        List<CSS.DebugRule> inlineRules = rulesBySource.get(htmlTarget.relativePath());
        if (inlineRules != null && !inlineRules.isEmpty()) {
            html = rewriteInlineStyles(html, htmlTarget.relativePath(), inlineRules, viewport);
        }
        if (saveDomTree) html = DevToolsHtmlSerializer.serialize(document, html);

        ArrayList<Edit> edits = new ArrayList<>();
        if (saveDomTree || !html.equals(originalHtml)) {
            edits.add(new Edit(htmlTarget, html));
        }

        for (Map.Entry<String, List<CSS.DebugRule>> source : rulesBySource.entrySet()) {
            String sourcePath = source.getKey();
            if (sourcePath.equals(htmlTarget.relativePath())) continue;
            if (Loader.isRemotePath(sourcePath)) {
                return Result.failure("Remote stylesheets cannot be saved");
            }

            DevToolsDocumentStore.Resolution resolution =
                    DevToolsDocumentStore.resolveResource(sourcePath, entries, production);
            String originalCss = resolution.writable()
                    ? DevToolsDocumentStore.read(resolution.target())
                    : DevToolsDocumentStore.readResource(sourcePath);
            if (originalCss == null) {
                return Result.failure(resolution.writable()
                        ? "Could not read the source CSS file" : resolution.message());
            }
            String updatedCss = rewriteStylesheet(originalCss, sourcePath, source.getValue(), viewport);
            if (!resolution.writable()) {
                if (updatedCss.equals(originalCss)) continue;
                return Result.failure(resolution.message());
            }
            if (!updatedCss.equals(originalCss)) edits.add(new Edit(resolution.target(), updatedCss));
        }
        return Result.success(edits);
    }

    private static Map<String, List<CSS.DebugRule>> rulesBySource(List<CSS.DebugRule> rules) {
        LinkedHashMap<String, List<CSS.DebugRule>> result = new LinkedHashMap<>();
        if (rules == null) return result;
        for (CSS.DebugRule rule : rules) {
            if (rule == null) continue;
            String sourcePath = normalizeSourcePath(rule.sourcePath());
            if (sourcePath.isBlank()) continue;
            result.computeIfAbsent(sourcePath, ignored -> new ArrayList<>()).add(rule);
        }
        for (List<CSS.DebugRule> sourceRules : result.values()) {
            sourceRules.sort(Comparator.comparingInt(CSS.DebugRule::order));
        }
        return result;
    }

    private static String rewriteInlineStyles(String html, String sourcePath,
                                              List<CSS.DebugRule> rules, MediaViewport viewport) {
        Matcher matcher = STYLE_TAG.matcher(html);
        StringBuffer output = new StringBuffer();
        RuleCursor cursor = new RuleCursor(rules);
        while (matcher.find()) {
            String css = matcher.group(2);
            String updated = rewriteStylesheet(css, sourcePath, cursor, viewport);
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                    matcher.group(1) + updated + matcher.group(3)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String rewriteStylesheet(String css, String sourcePath,
                                             List<CSS.DebugRule> rules, MediaViewport viewport) {
        return rewriteStylesheet(css, sourcePath, new RuleCursor(rules), viewport);
    }

    private static String rewriteStylesheet(String css, String sourcePath, RuleCursor cursor,
                                            MediaViewport viewport) {
        if (css == null || css.isEmpty()) return css == null ? "" : css;
        List<RuleBlock> blocks = collectRuleBlocks(css, viewport);
        if (blocks.isEmpty()) return css;

        ArrayList<Replacement> replacements = new ArrayList<>();
        for (RuleBlock block : blocks) {
            CSS.DebugRule rule = cursor.take(block.selector());
            if (rule == null) continue;
            Map<String, CSS.Declaration> original = parseDeclarations(
                    css.substring(block.bodyStart(), block.bodyEnd()), sourcePath);
            if (sameDeclarations(original, rule.properties())) continue;
            String replacement = serializeDeclarations(
                    rule.properties(), css.substring(block.bodyStart(), block.bodyEnd()), block.indent());
            replacements.add(new Replacement(block.bodyStart(), block.bodyEnd(), replacement));
        }
        StringBuilder output = new StringBuilder(css);
        for (int index = replacements.size() - 1; index >= 0; index--) {
            Replacement replacement = replacements.get(index);
            output.replace(replacement.start(), replacement.end(), replacement.content());
        }
        return output.toString();
    }

    private static List<RuleBlock> collectRuleBlocks(String css, MediaViewport viewport) {
        ArrayList<RuleBlock> result = new ArrayList<>();
        collectRuleBlocks(css, 0, css.length(), result, true, viewport);
        return result;
    }

    private static void collectRuleBlocks(String css, int start, int end, List<RuleBlock> result,
                                          boolean active, MediaViewport viewport) {
        int segmentStart = start;
        int index = start;
        while (index < end) {
            char current = css.charAt(index);
            if (current == '/' && index + 1 < end && css.charAt(index + 1) == '*') {
                index = skipComment(css, index, end);
                continue;
            }
            if (current == '\'' || current == '"') {
                index = skipString(css, index, end);
                continue;
            }
            if (current == ';') {
                segmentStart = index + 1;
                index++;
                continue;
            }
            if (current == '{') {
                int close = findClosingBrace(css, index, end);
                if (close < 0) return;
                String selector = normalizeSelector(css.substring(segmentStart, index));
                if (!selector.isBlank()) {
                    String indent = lineIndent(css, segmentStart, index);
                    if (selector.startsWith("@")) {
                        boolean childActive = active && isActiveAtRule(selector, viewport);
                        collectRuleBlocks(css, index + 1, close, result, childActive, viewport);
                    } else if (active) {
                        result.add(new RuleBlock(index + 1, close, selector, indent));
                    }
                }
                index = close + 1;
                segmentStart = index;
                continue;
            }
            if (current == '}') segmentStart = index + 1;
            index++;
        }
    }

    private static boolean isActiveAtRule(String selector, MediaViewport viewport) {
        String normalized = selector.trim();
        if (!normalized.regionMatches(true, 0, "@media", 0, "@media".length())) return false;
        String query = normalized.substring("@media".length()).trim().toLowerCase(Locale.ROOT);
        if (query.isBlank() || "all".equals(query) || "screen".equals(query)
                || "only screen".equals(query)) return true;
        for (String rawPart : query.split("\\band\\b")) {
            String part = rawPart.trim();
            if (part.isEmpty() || "screen".equals(part) || "only screen".equals(part)
                    || "all".equals(part)) continue;
            if (part.startsWith("(") && part.endsWith(")")) {
                part = part.substring(1, part.length() - 1).trim();
            }
            int colon = part.indexOf(':');
            if (colon < 0) return false;
            String feature = part.substring(0, colon).trim();
            int value = parseMediaLength(part.substring(colon + 1).trim());
            switch (feature) {
                case "min-width" -> { if (viewport.width() < value) return false; }
                case "max-width" -> { if (viewport.width() > value) return false; }
                case "min-height" -> { if (viewport.height() < value) return false; }
                case "max-height" -> { if (viewport.height() > value) return false; }
                case "width" -> { if (viewport.width() != value) return false; }
                case "height" -> { if (viewport.height() != value) return false; }
                case "orientation" -> {
                    String orientation = part.substring(colon + 1).trim();
                    boolean landscape = viewport.width() >= viewport.height();
                    if ("landscape".equals(orientation) && !landscape) return false;
                    if ("portrait".equals(orientation) && landscape) return false;
                }
                default -> { return false; }
            }
        }
        return true;
    }

    private static int parseMediaLength(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        Matcher matcher = MEDIA_LENGTH.matcher(raw.trim());
        try {
            return matcher.find() ? (int) Math.round(Double.parseDouble(matcher.group())) : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static MediaViewport mediaViewport(Document document) {
        try {
            return new MediaViewport(
                    (int) Math.round(document.getViewport().layoutWidth()),
                    (int) Math.round(document.getViewport().layoutHeight()));
        } catch (RuntimeException ignored) {
            return new MediaViewport(1024, 768);
        }
    }

    private static int findClosingBrace(String css, int open, int end) {
        int depth = 1;
        int index = open + 1;
        while (index < end) {
            char current = css.charAt(index);
            if (current == '/' && index + 1 < end && css.charAt(index + 1) == '*') {
                index = skipComment(css, index, end);
                continue;
            }
            if (current == '\'' || current == '"') {
                index = skipString(css, index, end);
                continue;
            }
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return index;
            index++;
        }
        return -1;
    }

    private static int skipComment(String css, int start, int end) {
        int close = css.indexOf("*/", start + 2);
        return close < 0 || close + 2 > end ? end : close + 2;
    }

    private static int skipString(String css, int start, int end) {
        char quote = css.charAt(start++);
        while (start < end) {
            char current = css.charAt(start++);
            if (current == '\\') {
                if (start < end) start++;
            } else if (current == quote) {
                break;
            }
        }
        return start;
    }

    private static String lineIndent(String css, int segmentStart, int braceStart) {
        int lineStart = css.lastIndexOf('\n', Math.max(segmentStart, braceStart - 1)) + 1;
        int first = lineStart;
        while (first < braceStart && (css.charAt(first) == ' ' || css.charAt(first) == '\t')) first++;
        return css.substring(lineStart, first);
    }

    private static Map<String, CSS.Declaration> parseDeclarations(String body, String sourcePath) {
        LinkedHashMap<String, CSS.Declaration> result = new LinkedHashMap<>();
        String clean = COMMENT.matcher(body == null ? "" : body).replaceAll("");
        for (String pair : clean.split(";")) {
            if (pair == null || pair.isBlank()) continue;
            String[] parts = pair.split(":", 2);
            if (parts.length != 2) continue;
            String property = normalizeProperty(parts[0]);
            if (property.isBlank()) continue;
            CSS.Declaration declaration = stripImportant(parts[1].trim());
            String value = declaration.value();
            if (value == null || value.isBlank()) continue;
            if (value.contains("url(")) value = normalizeUrlValue(value, sourcePath);
            CSS.Declaration normalized = new CSS.Declaration(value, declaration.important());
            CSS.Declaration old = result.get(property);
            if (old == null || !old.important() || normalized.important()) result.put(property, normalized);
        }
        return result;
    }

    private static CSS.Declaration stripImportant(String value) {
        if (value == null || value.isBlank()) return new CSS.Declaration(value, false);
        String normalized = value.trim();
        if (normalized.toLowerCase(Locale.ROOT).endsWith("!important")) {
            return new CSS.Declaration(
                    normalized.substring(0, normalized.length() - "!important".length()).trim(), true);
        }
        return new CSS.Declaration(normalized, false);
    }

    private static String normalizeProperty(String raw) {
        String property = raw == null ? "" : raw.trim();
        if (property.startsWith("--")) return property;
        String normalized = property.toLowerCase(Locale.ROOT);
        return "-webkit-appearance".equals(normalized) ? "appearance" : normalized;
    }

    private static String normalizeUrlValue(String value, String sourcePath) {
        Matcher matcher = URL.matcher(value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String rawPath = matcher.group(1).trim();
            String resolved = Loader.resolve(sourcePath, rawPath);
            String replacement = Loader.isRemotePath(resolved)
                    ? "url(\"" + resolved + "\")"
                    : "url(\"/" + resolved + "\")";
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static boolean sameDeclarations(Map<String, CSS.Declaration> left,
                                             Map<String, CSS.Declaration> right) {
        if (left == null || right == null || left.size() != right.size()) return false;
        for (Map.Entry<String, CSS.Declaration> entry : left.entrySet()) {
            CSS.Declaration other = right.get(entry.getKey());
            if (other == null || !entry.getValue().equals(other)) return false;
        }
        return true;
    }

    private static String serializeDeclarations(Map<String, CSS.Declaration> declarations,
                                                String originalBody, String ruleIndent) {
        if (declarations == null || declarations.isEmpty()) {
            return originalBody != null && originalBody.matches("(?s).*\\R.*")
                    ? newlineOf(originalBody) + ruleIndent : "";
        }
        boolean multiline = originalBody != null && originalBody.matches("(?s).*\\R.*");
        StringBuilder output = new StringBuilder();
        if (!multiline) {
            output.append(' ');
            appendDeclarations(output, declarations, "", " ");
            output.append(' ');
            return output.toString();
        }

        String newline = newlineOf(originalBody);
        String propertyIndent = propertyIndent(originalBody, ruleIndent);
        output.append(newline);
        appendDeclarations(output, declarations, propertyIndent, newline);
        output.append(newline).append(ruleIndent);
        return output.toString();
    }

    private static void appendDeclarations(StringBuilder output,
                                           Map<String, CSS.Declaration> declarations,
                                           String indent, String separator) {
        boolean first = true;
        for (Map.Entry<String, CSS.Declaration> entry : declarations.entrySet()) {
            CSS.Declaration declaration = entry.getValue();
            if (declaration == null || declaration.value() == null || declaration.value().isBlank()) continue;
            if (!first) output.append(separator);
            first = false;
            output.append(indent).append(entry.getKey()).append(": ").append(declaration.value());
            if (declaration.important()) output.append(" !important");
            output.append(';');
        }
    }

    private static String propertyIndent(String body, String ruleIndent) {
        String[] lines = body.split("\\R");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                int first = 0;
                while (first < line.length() && (line.charAt(first) == ' ' || line.charAt(first) == '\t')) first++;
                return line.substring(0, first);
            }
        }
        return ruleIndent + (ruleIndent.contains("\t") ? "\t" : "  ");
    }

    private static String newlineOf(String source) {
        return source != null && source.contains("\r\n") ? "\r\n" : "\n";
    }

    private static String normalizeSelector(String selector) {
        return COMMENT.matcher(selector == null ? "" : selector).replaceAll("")
                .trim().replaceAll("\\s+", " ");
    }

    private static String normalizeSourcePath(String path) {
        if (path == null) return "";
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    record Edit(DevToolsDocumentStore.SaveTarget target, String content) {
    }

    record Result(boolean success, List<Edit> edits, String message) {
        static Result success(List<Edit> edits) {
            return new Result(true, List.copyOf(edits), "");
        }

        static Result failure(String message) {
            return new Result(false, List.of(), message);
        }
    }

    private record MediaViewport(int width, int height) {
    }

    private record RuleBlock(int bodyStart, int bodyEnd, String selector, String indent) {
    }

    private record Replacement(int start, int end, String content) {
    }

    private static final class RuleCursor {
        private final List<CSS.DebugRule> rules;
        private int next;

        private RuleCursor(List<CSS.DebugRule> rules) {
            this.rules = rules == null ? List.of() : rules;
        }

        private CSS.DebugRule take(String selector) {
            for (int index = next; index < rules.size(); index++) {
                CSS.DebugRule candidate = rules.get(index);
                if (normalizeSelector(candidate.selector()).equals(selector)) {
                    next = index + 1;
                    return candidate;
                }
            }
            return null;
        }
    }
}

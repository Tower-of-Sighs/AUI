package com.sighs.apricityui.resource;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Selector;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.layout.Size;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CSS {
    /**
     * 带 !important 标志的 CSS 声明。value 中不再包含 "!important" 后缀。
     */
    public record Declaration(String value, boolean important) {
    }

    public record DebugRule(String selector, Map<String, Declaration> properties, String sourcePath, int order) {
        public DebugRule {
            properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
        }
    }

    public static void readCSS(String css, Map<String, Map<String, Declaration>> targetCache, String contextPath) {
        Parser.parse(css, targetCache, null, contextPath, 0, null);
    }

    public static void readCSS(String css, Map<String, Map<String, Declaration>> targetCache,
                               String contextPath, Size viewport) {
        Parser.parse(css, targetCache, null, contextPath, 0, viewport);
    }

    public static int readCSS(String css, Map<String, Map<String, Declaration>> targetCache,
                              List<DebugRule> debugRules, String contextPath, int orderStart) {
        return Parser.parse(css, targetCache, debugRules, contextPath, orderStart, null);
    }

    public static int readCSS(String css, Map<String, Map<String, Declaration>> targetCache,
                              List<DebugRule> debugRules, String contextPath, int orderStart,
                              Size viewport) {
        return Parser.parse(css, targetCache, debugRules, contextPath, orderStart, viewport);
    }

    /** Rebuilds the selector cache from the author declarations exposed to DevTools. */
    public static void rebuildCacheFromDebugRules(List<DebugRule> debugRules,
                                                  Map<String, Map<String, Declaration>> targetCache) {
        if (targetCache == null) return;
        targetCache.clear();
        if (debugRules == null || debugRules.isEmpty()) return;
        ArrayList<DebugRule> ordered = new ArrayList<>(debugRules);
        ordered.sort(Comparator.comparingInt(DebugRule::order));
        for (DebugRule rule : ordered) {
            if (rule == null || rule.selector() == null || rule.selector().isBlank()) continue;
            Map<String, Declaration> expanded = Parser.expandAuthorProperties(rule.properties());
            for (String selector : rule.selector().split("\\s*,\\s*")) {
                String normalized = selector.trim();
                if (normalized.isEmpty()) continue;
                targetCache.merge(normalized, new LinkedHashMap<>(expanded), (oldMap, newMap) -> {
                    newMap.forEach((property, declaration) -> Parser.putDeclaration(oldMap, property, declaration));
                    return oldMap;
                });
            }
        }
    }

    public static class Extractor {
        private static final Pattern STYLE_TAG_PATTERN =
                Pattern.compile("(?i)<style\\b([^>]*)>(.*?)</style\\s*>", Pattern.DOTALL);
        private static final Pattern LINK_TAG_PATTERN =
            Pattern.compile("(?i)<link\\b([^>]*?)>", Pattern.DOTALL);

        private final List<String> cachedStyleSrcs = new ArrayList<>();
        private final List<String> cachedStyleContents = new ArrayList<>();
        private final String contextPath;

        public Extractor(String contextPath) {
            this.contextPath = contextPath;
        }

        public String handle(String html) {
            if (html == null || html.isEmpty()) return html;

            Matcher matcher = STYLE_TAG_PATTERN.matcher(html);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String attrText = matcher.group(1);
                String innerCss = matcher.group(2);

                String srcValue = findAttrValue(attrText, "src");
                if (srcValue != null && !srcValue.isEmpty()) cachedStyleSrcs.add(srcValue);

                if (innerCss != null && !innerCss.isBlank()) {
                    cachedStyleContents.add(innerCss.trim());
                }
                matcher.appendReplacement(sb, "");
            }
            matcher.appendTail(sb);

            Matcher linkMatcher = LINK_TAG_PATTERN.matcher(sb.toString());
            StringBuffer linkFree = new StringBuffer();
            while (linkMatcher.find()) {
                String attrText = linkMatcher.group(1);
                if (!isStylesheetLink(attrText)) {
                    linkMatcher.appendReplacement(linkFree, Matcher.quoteReplacement(linkMatcher.group()));
                    continue;
                }

                String hrefValue = findAttrValue(attrText, "href");
                if (hrefValue != null && !hrefValue.isEmpty()) {
                    cachedStyleSrcs.add(hrefValue);
                }
                linkMatcher.appendReplacement(linkFree, "");
            }
            linkMatcher.appendTail(linkFree);
            return linkFree.toString();
        }

        private static boolean isStylesheetLink(String attrText) {
            String relValue = findAttrValue(attrText, "rel");
            if (relValue == null || relValue.isBlank()) return false;
            for (String token : relValue.trim().split("\\s+")) {
                if ("stylesheet".equalsIgnoreCase(token)) return true;
            }
            return false;
        }

        private static String findAttrValue(String attrText, String attrName) {
            if (attrText == null || attrText.isBlank() || attrName == null || attrName.isBlank()) return null;
            Pattern attrPattern = Pattern.compile(
                    "(?i)\\b" + Pattern.quote(attrName) + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))"
            );
            Matcher matcher = attrPattern.matcher(attrText);
            if (!matcher.find()) return null;
            for (int i = 2; i <= 4; i++) {
                String value = matcher.group(i);
                if (value != null) return value.trim();
            }
            return null;
        }

        public void pushToDocument(Document document) {
            StyleAsyncHandler.INSTANCE.attach(document, contextPath, cachedStyleSrcs, cachedStyleContents);
        }
    }

    static class Parser {
        private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
        private static final Pattern RULE_PATTERN = Pattern.compile("(.*?)\\s*\\{([^}]*)}", Pattern.DOTALL);
        private static final Pattern URL_EXTRACTOR = Pattern.compile("url\\s*\\(\\s*['\"]?(.*?)['\"]?\\s*\\)");
        private static final Pattern KEYFRAMES_HEAD_PATTERN = Pattern.compile(
                "(?i)@(?:-webkit-)?keyframes\\s+((?:\"[^\"]+\"|'[^']+'|[\\w-]+))\\s*\\{"
        );
        private static final Pattern FRAME_PATTERN = Pattern.compile("(?is)([^{}]+?)\\{([^{}]*)}");

        public static String parseAndRegisterAnimations(String css, String contextPath) {
            if (css == null) return "";
            StringBuilder cleanCss = new StringBuilder(COMMENT_PATTERN.matcher(css).replaceAll(""));
            Matcher matcher = KEYFRAMES_HEAD_PATTERN.matcher(cleanCss);

            int offset = 0;
            while (matcher.find(offset)) {
                String animName = normalizeKeyframeName(matcher.group(1));
                int blockStart = matcher.end();
                int braceCount = 1;
                int blockEnd = -1;
                for (int i = blockStart; i < cleanCss.length(); i++) {
                    char c = cleanCss.charAt(i);
                    if (c == '{') braceCount++;
                    else if (c == '}') braceCount--;

                    if (braceCount == 0) {
                        blockEnd = i;
                        break;
                    }
                }

                if (blockEnd != -1) {
                    String fullContent = cleanCss.substring(blockStart, blockEnd);
                    // 解析内部帧
                    Matcher frameMatcher = FRAME_PATTERN.matcher(fullContent);
                    while (frameMatcher.find()) {
                        String percentStr = frameMatcher.group(1);
                        String rules = frameMatcher.group(2);
                        Map<String, Declaration> properties = parseProperties(rules, contextPath);
                        Map<String, String> valueMap = new HashMap<>();
                        for (Map.Entry<String, Declaration> entry : properties.entrySet()) {
                            valueMap.put(entry.getKey(), entry.getValue().value());
                        }
                        for (String token : percentStr.split(",")) {
                            Double percent = parseKeyframePercent(token.trim());
                            if (percent == null) continue;
                            Animation.registerKeyframe(animName, percent, valueMap);
                        }
                    }

                    // 移除已处理的@keyframes
                    cleanCss.delete(matcher.start(), blockEnd + 1);
                    offset = matcher.start();
                } else {
                    offset = matcher.end();
                }
            }
            return cleanCss.toString();
        }

        public static int parse(String css, Map<String, Map<String, Declaration>> targetCache,
                                List<DebugRule> debugRules, String contextPath, int orderStart,
                                Size viewport) {
            if (css == null || css.isBlank()) return orderStart;
            String normalizedCss = evaluateMediaRules(parseAndRegisterAnimations(css, contextPath), viewport);

            Matcher matcher = RULE_PATTERN.matcher(normalizedCss);
            int order = orderStart;

            while (matcher.find()) {
                String selector = matcher.group(1).trim();
                // 忽略空的或可能是残留的 @ 规则
                if (selector.isEmpty() || selector.startsWith("@")) continue;

                String rules = matcher.group(2).trim();
                List<String> selectors = Selector.splitSelectorList(selector);
                Map<String, Declaration> authoredProperties = parseProperties(rules, contextPath, false);
                Map<String, Declaration> properties = expandAuthorProperties(authoredProperties);

                for (String sel : selectors) {
                    String normalizedSelector = sel.trim();
                    targetCache.merge(normalizedSelector, new LinkedHashMap<>(properties), (oldMap, newMap) -> {
                        newMap.forEach((property, declaration) -> putDeclaration(oldMap, property, declaration));
                        return oldMap;
                    });
                }
                int ruleOrder = order++;
                if (debugRules != null) {
                    debugRules.add(new DebugRule(selector, authoredProperties, contextPath, ruleOrder));
                }
            }
            return order;
        }

        private static String evaluateMediaRules(String css, Size viewport) {
            if (css == null || css.isBlank()) return "";
            StringBuilder output = new StringBuilder();
            int index = 0;
            while (index < css.length()) {
                int mediaIndex = indexOfIgnoreCase(css, "@media", index);
                if (mediaIndex < 0) {
                    output.append(css, index, css.length());
                    break;
                }
                output.append(css, index, mediaIndex);
                int headerStart = mediaIndex + 6;
                int openBrace = css.indexOf('{', headerStart);
                if (openBrace < 0) {
                    output.append(css.substring(mediaIndex));
                    break;
                }
                int closeBrace = findMatchingBrace(css, openBrace);
                if (closeBrace < 0) {
                    output.append(css.substring(mediaIndex));
                    break;
                }
                String query = css.substring(headerStart, openBrace).trim();
                String body = css.substring(openBrace + 1, closeBrace);
                if (matchesMediaQuery(query, viewport)) {
                    output.append(body);
                }
                index = closeBrace + 1;
            }
            return output.toString();
        }

        private static int indexOfIgnoreCase(String source, String target, int fromIndex) {
            return source.toLowerCase(Locale.ROOT).indexOf(target.toLowerCase(Locale.ROOT), fromIndex);
        }

        private static int findMatchingBrace(String text, int openBrace) {
            int depth = 0;
            for (int i = openBrace; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
            return -1;
        }

        private static boolean matchesMediaQuery(String query, Size viewport) {
            if (query == null || query.isBlank()) return true;
            String normalized = query.trim().toLowerCase(Locale.ROOT);
            if ("all".equals(normalized)) return true;
            if ("screen".equals(normalized) || "only screen".equals(normalized)) return true;

            int width = viewport == null
                    ? resolveViewportLength("aui.test.viewport.width", 1024, true)
                    : (int) Math.round(viewport.width());
            int height = viewport == null
                    ? resolveViewportLength("aui.test.viewport.height", 768, false)
                    : (int) Math.round(viewport.height());

            String[] andParts = normalized.split("\\band\\b");
            for (String rawPart : andParts) {
                String part = rawPart.trim();
                if (part.isEmpty() || "screen".equals(part) || "only screen".equals(part) || "all".equals(part)) {
                    continue;
                }
                if (part.startsWith("(") && part.endsWith(")")) {
                    part = part.substring(1, part.length() - 1).trim();
                }
                if (part.startsWith("min-width")) {
                    if (width < parseMediaLength(part.substring(part.indexOf(':') + 1))) return false;
                    continue;
                }
                if (part.startsWith("max-width")) {
                    if (width > parseMediaLength(part.substring(part.indexOf(':') + 1))) return false;
                    continue;
                }
                if (part.startsWith("min-height")) {
                    if (height < parseMediaLength(part.substring(part.indexOf(':') + 1))) return false;
                    continue;
                }
                if (part.startsWith("max-height")) {
                    if (height > parseMediaLength(part.substring(part.indexOf(':') + 1))) return false;
                    continue;
                }
                if (part.startsWith("width")) {
                    if (width != parseMediaLength(part.substring(part.indexOf(':') + 1))) return false;
                    continue;
                }
                if (part.startsWith("height")) {
                    if (height != parseMediaLength(part.substring(part.indexOf(':') + 1))) return false;
                    continue;
                }
                if (part.startsWith("orientation")) {
                    String orientation = part.substring(part.indexOf(':') + 1).trim();
                    boolean landscape = width >= height;
                    if ("landscape".equals(orientation) && !landscape) return false;
                    if ("portrait".equals(orientation) && landscape) return false;
                    continue;
                }
                return false;
            }
            return true;
        }

        private static int parseMediaLength(String raw) {
            if (raw == null || raw.isBlank()) return 0;
            Double parsed = Size.parseNumber(raw);
            return parsed == null ? 0 : (int) Math.round(parsed);
        }

        private static int resolveViewportLength(String systemProperty, int fallback, boolean width) {
            // 优先使用 ApricityScreen 设置的逻辑视口覆盖，而不是 Minecraft 物理窗口尺寸，
            // 这样 @media 能根据当前 AUI 文档的实际渲染视口进行适配。
            String override = System.getProperty(systemProperty);
            if (override != null && !override.isBlank()) {
                Double parsed = Size.parseNumber(override);
                if (parsed != null) return (int) Math.round(parsed);
            }
            try {
                return (int) Math.round(width ? Size.getWindowSize().width() : Size.getWindowSize().height());
            } catch (Throwable ignored) {
                return fallback;
            }
        }

        private static String normalizeKeyframeName(String keyframeName) {
            if (keyframeName == null) return null;
            String name = keyframeName.trim();
            if ((name.startsWith("\"") && name.endsWith("\"")) || (name.startsWith("'") && name.endsWith("'"))) {
                return name.substring(1, name.length() - 1).trim();
            }
            return name;
        }

        private static Double parseKeyframePercent(String token) {
            if (token == null || token.isBlank()) return null;
            if ("from".equalsIgnoreCase(token)) return 0d;
            if ("to".equalsIgnoreCase(token)) return 100d;
            if (!token.endsWith("%")) return null;
            String number = token.substring(0, token.length() - 1).trim();
            try {
                return Double.parseDouble(number);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static Map<String, Declaration> parseProperties(String rules, String contextPath) {
            return parseProperties(rules, contextPath, true);
        }

        private static Map<String, Declaration> parseProperties(String rules, String contextPath,
                                                                 boolean expand) {
            Map<String, Declaration> properties = new LinkedHashMap<>();
            String[] pairs = rules.split(";");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = normalizePropertyName(kv[0]);
                    String rawValue = kv[1].trim();
                    Declaration declaration = stripImportant(rawValue);
                    String value = declaration.value();
                    if (value.contains("url(")) {
                        value = normalizeUrlValue(value, contextPath);
                    }
                    if (!key.isEmpty() && !value.isEmpty()) {
                        Declaration normalized = new Declaration(value, declaration.important());
                        putDeclaration(properties, key, normalized);
                        if (expand) expandShorthand(properties, key, normalized);
                    }
                }
            }
            return properties;
        }

        private static String normalizePropertyName(String raw) {
            String property = raw == null ? "" : raw.trim();
            return property.startsWith("--") ? property : property.toLowerCase(Locale.ROOT);
        }

        private static Map<String, Declaration> expandAuthorProperties(Map<String, Declaration> authored) {
            LinkedHashMap<String, Declaration> expanded = new LinkedHashMap<>();
            if (authored == null) return expanded;
            for (Map.Entry<String, Declaration> entry : authored.entrySet()) {
                String property = entry.getKey();
                Declaration declaration = entry.getValue();
                if (property == null || property.isBlank() || declaration == null) continue;
                putDeclaration(expanded, property, declaration);
                expandShorthand(expanded, property, declaration);
            }
            return expanded;
        }

        /**
         * CSS shorthands participate in the cascade as their constituent longhands. Expanding them
         * while declarations still retain source order prevents a less-specific longhand from
         * overriding a more-specific shorthand (for example padding-left versus padding).
         */
        private static void expandShorthand(Map<String, Declaration> properties, String property,
                                            Declaration declaration) {
            String normalized = property.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "margin", "padding" -> putFourSides(properties, normalized, declaration);
                case "inset" -> putFourProperties(
                        properties, new String[]{"top", "right", "bottom", "left"}, declaration);
                case "border-width" -> putFourProperties(properties, new String[]{
                        "border-top-width", "border-right-width", "border-bottom-width", "border-left-width"
                }, declaration);
                case "border-color" -> putFourProperties(properties, new String[]{
                        "border-top-color", "border-right-color", "border-bottom-color", "border-left-color"
                }, declaration);
                case "border" -> {
                    for (String side : List.of("top", "right", "bottom", "left")) {
                        putDeclaration(properties, "border-" + side, declaration);
                    }
                }
                case "gap" -> {
                    List<String> values = splitCssValueTokens(declaration.value());
                    String row = values.isEmpty() ? declaration.value() : values.get(0);
                    String column = values.size() > 1 ? values.get(1) : row;
                    putDeclaration(properties, "row-gap", new Declaration(row, declaration.important()));
                    putDeclaration(properties, "column-gap", new Declaration(column, declaration.important()));
                }
                default -> {
                }
            }
        }

        private static void putFourSides(Map<String, Declaration> properties, String base,
                                         Declaration declaration) {
            putFourProperties(properties, new String[]{
                    base + "-top", base + "-right", base + "-bottom", base + "-left"
            }, declaration);
        }

        private static void putFourProperties(Map<String, Declaration> properties, String[] names,
                                              Declaration declaration) {
            String[] values = expandFourSideValues(declaration.value());
            for (int i = 0; i < names.length; i++) {
                putDeclaration(properties, names[i], new Declaration(values[i], declaration.important()));
            }
        }

        private static String[] expandFourSideValues(String value) {
            List<String> tokens = splitCssValueTokens(value);
            if (tokens.isEmpty()) return new String[]{value, value, value, value};
            return switch (tokens.size()) {
                case 1 -> new String[]{tokens.get(0), tokens.get(0), tokens.get(0), tokens.get(0)};
                case 2 -> new String[]{tokens.get(0), tokens.get(1), tokens.get(0), tokens.get(1)};
                case 3 -> new String[]{tokens.get(0), tokens.get(1), tokens.get(2), tokens.get(1)};
                default -> new String[]{tokens.get(0), tokens.get(1), tokens.get(2), tokens.get(3)};
            };
        }

        private static List<String> splitCssValueTokens(String value) {
            List<String> tokens = new ArrayList<>();
            if (value == null || value.isBlank()) return tokens;
            StringBuilder token = new StringBuilder();
            int parentheses = 0;
            for (int i = 0; i < value.length(); i++) {
                char character = value.charAt(i);
                if (Character.isWhitespace(character) && parentheses == 0) {
                    if (!token.isEmpty()) {
                        tokens.add(token.toString());
                        token.setLength(0);
                    }
                    continue;
                }
                if (character == '(') parentheses++;
                else if (character == ')' && parentheses > 0) parentheses--;
                token.append(character);
            }
            if (!token.isEmpty()) tokens.add(token.toString());
            return tokens;
        }

        private static void putDeclaration(Map<String, Declaration> properties, String property,
                                           Declaration declaration) {
            Declaration existing = properties.get(property);
            if (existing != null && existing.important() && !declaration.important()) return;
            properties.put(property, declaration);
        }

        private static Declaration stripImportant(String value) {
            if (value == null || value.isBlank()) return new Declaration(value, false);
            String normalized = value.trim();
            if (normalized.toLowerCase(Locale.ROOT).endsWith("!important")) {
                return new Declaration(
                        normalized.substring(0, normalized.length() - "!important".length()).trim(),
                        true
                );
            }
            return new Declaration(normalized, false);
        }

        private static String normalizeUrlValue(String value, String contextPath) {
            Matcher matcher = URL_EXTRACTOR.matcher(value);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String rawPath = matcher.group(1).trim();
                if (rawPath.isEmpty()) continue;
                String resolvedPath = Loader.resolve(contextPath, rawPath);
                String replacement = Loader.isRemotePath(resolvedPath)
                        ? "url(\"" + resolvedPath + "\")"
                        : "url(\"/" + resolvedPath + "\")";
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(buffer);
            return buffer.toString();
        }
    }
}

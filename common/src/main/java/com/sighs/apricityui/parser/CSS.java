package com.sighs.apricityui.parser;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.Selector;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.util.AuiLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

public class CSS {
    /** 提取 CSS url(...) 引用的公共正则，resource 包共用。 */
    public static final Pattern URL_EXTRACTOR = Pattern.compile("url\\s*\\(\\s*['\"]?(.*?)['\"]?\\s*\\)");
    private static final Map<StylesheetCacheKey, CompiledStylesheet> COMPILED_STYLESHEETS = new ConcurrentHashMap<>();
    private static final Set<String> WARMED_FONT_FAMILIES = ConcurrentHashMap.newKeySet();

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
        compiledStylesheet(css, contextPath, viewport).apply(targetCache, null, 0);
    }

    public static int readCSS(String css, Map<String, Map<String, Declaration>> targetCache,
                              List<DebugRule> debugRules, String contextPath, int orderStart) {
        return Parser.parse(css, targetCache, debugRules, contextPath, orderStart, null);
    }

    public static int readCSS(String css, Map<String, Map<String, Declaration>> targetCache,
                              List<DebugRule> debugRules, String contextPath, int orderStart,
                              Size viewport) {
        return compiledStylesheet(css, contextPath, viewport).apply(targetCache, debugRules, orderStart);
    }

    public static void clearCompiledStylesheets() {
        COMPILED_STYLESHEETS.clear();
        WARMED_FONT_FAMILIES.clear();
    }

    public static void warmUp(String css, String contextPath, Size viewport) {
        CompiledStylesheet stylesheet = compiledStylesheet(css, contextPath, viewport);
        stylesheet.fontFamilies.stream()
                .filter(WARMED_FONT_FAMILIES::add)
                .forEach(Text::warmUpFontFamily);
    }

    static int compiledStylesheetCount() {
        return COMPILED_STYLESHEETS.size();
    }

    private static CompiledStylesheet compiledStylesheet(String css, String contextPath, Size viewport) {
        int width = viewport == null
                ? Parser.resolveViewportLength("aui.test.viewport.width", 1024, true)
                : (int) Math.round(viewport.width());
        int height = viewport == null
                ? Parser.resolveViewportLength("aui.test.viewport.height", 768, false)
                : (int) Math.round(viewport.height());
        StylesheetCacheKey key = new StylesheetCacheKey(
                css == null ? "" : css,
                contextPath == null ? "" : contextPath,
                width,
                height
        );
        return COMPILED_STYLESHEETS.computeIfAbsent(key, ignored -> compileStylesheet(
                key.css,
                key.contextPath,
                new Size(key.viewportWidth, key.viewportHeight)
        ));
    }

    private static CompiledStylesheet compileStylesheet(String css, String contextPath, Size viewport) {
        LinkedHashMap<String, Map<String, Declaration>> rules = new LinkedHashMap<>();
        ArrayList<DebugRule> debugRules = new ArrayList<>();
        int ruleCount = Parser.parse(css, rules, debugRules, contextPath, 0, viewport);
        Selector.warmUp(rules.keySet());
        LinkedHashMap<String, Map<String, Declaration>> immutableRules = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> fontFamilies = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Declaration>> entry : rules.entrySet()) {
            LinkedHashMap<String, Declaration> properties = new LinkedHashMap<>(entry.getValue());
            immutableRules.put(entry.getKey(), Collections.unmodifiableMap(properties));
            Declaration family = properties.get("font-family");
            if (family != null && family.value() != null && !family.value().isBlank()) {
                fontFamilies.put(family.value(), Boolean.TRUE);
            }
        }
        return new CompiledStylesheet(
                Collections.unmodifiableMap(immutableRules),
                List.copyOf(debugRules),
                ruleCount,
                Set.copyOf(fontFamilies.keySet())
        );
    }

    private record StylesheetCacheKey(String css, String contextPath, int viewportWidth, int viewportHeight) {
    }

    private record CompiledStylesheet(
            Map<String, Map<String, Declaration>> rules,
            List<DebugRule> debugRules,
            int ruleCount,
            Set<String> fontFamilies
    ) {
        private int apply(Map<String, Map<String, Declaration>> targetCache,
                          List<DebugRule> targetDebugRules,
                          int orderStart) {
            if (targetCache == null) return orderStart;
            for (Map.Entry<String, Map<String, Declaration>> entry : rules.entrySet()) {
                LinkedHashMap<String, Declaration> properties = new LinkedHashMap<>(entry.getValue());
                targetCache.merge(entry.getKey(), properties, (oldMap, newMap) -> {
                    newMap.forEach((property, declaration) -> Parser.putDeclaration(oldMap, property, declaration));
                    return oldMap;
                });
            }
            if (targetDebugRules != null) {
                for (DebugRule rule : debugRules) {
                    targetDebugRules.add(new DebugRule(
                            rule.selector(),
                            rule.properties(),
                            rule.sourcePath(),
                            orderStart + rule.order()
                    ));
                }
            }
            return orderStart + ruleCount;
        }
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

    public static class Extractor extends TagExtractor {
        private static final Pattern STYLE_TAG_PATTERN =
                Pattern.compile("(?i)<style\\b([^>]*)>(.*?)</style\\s*>", Pattern.DOTALL);
        private static final Pattern LINK_TAG_PATTERN =
            Pattern.compile("(?i)<link\\b([^>]*?)>", Pattern.DOTALL);
        private static final Pattern STYLE_OPEN_MARKER = Pattern.compile("(?i)<style\\b");
        private static final Pattern STYLE_CLOSE_MARKER = Pattern.compile("(?i)</style\\s*>");

        public Extractor(String contextPath) {
            super(contextPath, STYLE_OPEN_MARKER, STYLE_CLOSE_MARKER, "CSS", "style");
        }

        @Override
        protected String extract(String html) {
            return extractLinks(removeTags(html, STYLE_TAG_PATTERN));
        }

        @Override
        protected void onTag(String attrText, String inner) {
            String srcValue = HTML.findAttrValue(attrText, "src");
            if (srcValue != null && !srcValue.isEmpty()) {
                ApricityUI.LOGGER.warn(
                        "[AUI CSS] style tag uses unsupported src attribute path={} src={}",
                        AuiLog.source(contextPath),
                        srcValue
                );
            }

            if (inner != null && !inner.isBlank()) {
                cachedContents.add(inner.trim());
            }
        }

        /** 处理 <link rel="stylesheet">：缓存 href，移除或保留非样式表链接。 */
        private String extractLinks(String html) {
            Matcher linkMatcher = LINK_TAG_PATTERN.matcher(html);
            StringBuffer linkFree = new StringBuffer();
            while (linkMatcher.find()) {
                String attrText = linkMatcher.group(1);
                if (!isStylesheetLink(attrText)) {
                    linkMatcher.appendReplacement(linkFree, Matcher.quoteReplacement(linkMatcher.group()));
                    continue;
                }

                String hrefValue = HTML.findAttrValue(attrText, "href");
                if (hrefValue != null && !hrefValue.isEmpty()) {
                    if (isBinaryStylesheetResource(hrefValue)) {
                        linkMatcher.appendReplacement(linkFree, "");
                        continue;
                    }
                    cachedSrcs.add(hrefValue);
                } else {
                    ApricityUI.LOGGER.warn(
                            "[AUI CSS] stylesheet link has no href path={} attributes={}",
                            AuiLog.source(contextPath),
                            AuiLog.compact(attrText)
                    );
                }
                linkMatcher.appendReplacement(linkFree, "");
            }
            linkMatcher.appendTail(linkFree);
            return linkFree.toString();
        }

        private static boolean isStylesheetLink(String attrText) {
            String relValue = HTML.findAttrValue(attrText, "rel");
            if (relValue == null || relValue.isBlank()) return false;
            for (String token : relValue.trim().split("\\s+")) {
                if ("stylesheet".equalsIgnoreCase(token)) return true;
            }
            return false;
        }

        private static boolean isBinaryStylesheetResource(String href) {
            if (href == null || href.isBlank()) return false;
            String path = href.trim();
            int query = path.indexOf('?');
            if (query >= 0) path = path.substring(0, query);
            int fragment = path.indexOf('#');
            if (fragment >= 0) path = path.substring(0, fragment);
            String lower = path.toLowerCase(Locale.ROOT);
            return lower.endsWith(".woff")
                    || lower.endsWith(".woff2")
                    || lower.endsWith(".ttf")
                    || lower.endsWith(".otf")
                    || lower.endsWith(".eot");
        }

        @Override
        public void pushToDocument(Document document) {
            if (document == null) {
                ApricityUI.LOGGER.error("[AUI CSS] cannot attach styles without a document path={}", AuiLog.source(contextPath));
                return;
            }
            ResourceUsageIndex.recordCss(contextPath, cachedSrcs);
            StyleAsyncHandler.INSTANCE.attach(document, contextPath, cachedSrcs, cachedContents);
        }
    }

    static class Parser {
        private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
        private static final Pattern RULE_PATTERN = Pattern.compile("(.*?)\\s*\\{([^}]*)}", Pattern.DOTALL);
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
                if (animName == null || animName.isBlank()) {
                    ApricityUI.LOGGER.error("[AUI CSS] keyframes rule has no name path={}", AuiLog.source(contextPath));
                }
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
                            if (percent == null) {
                                ApricityUI.LOGGER.warn(
                                        "[AUI CSS] invalid keyframe selector path={} animation={} selector={}",
                                        AuiLog.source(contextPath),
                                        animName,
                                        AuiLog.compact(token)
                                );
                                continue;
                            }
                            Animation.registerKeyframe(animName, percent, valueMap);
                        }
                    }

                    // 移除已处理的@keyframes
                    cleanCss.delete(matcher.start(), blockEnd + 1);
                    offset = matcher.start();
                } else {
                    ApricityUI.LOGGER.error(
                            "[AUI CSS] unterminated keyframes block path={} animation={}",
                            AuiLog.source(contextPath),
                            animName
                    );
                    offset = matcher.end();
                }
            }
            return cleanCss.toString();
        }

        public static int parse(String css, Map<String, Map<String, Declaration>> targetCache,
                                List<DebugRule> debugRules, String contextPath, int orderStart,
                                Size viewport) {
            if (targetCache == null) {
                ApricityUI.LOGGER.error("[AUI CSS] cannot parse into a null rule cache path={}", AuiLog.source(contextPath));
                return orderStart;
            }
            try {
                if (css == null || css.isBlank()) return orderStart;
            String normalizedCss = evaluateMediaRules(parseAndRegisterAnimations(css, contextPath), viewport, contextPath);
            int openBraces = countCharacter(normalizedCss, '{');
            int closeBraces = countCharacter(normalizedCss, '}');
            if (openBraces != closeBraces) {
                ApricityUI.LOGGER.error(
                        "[AUI CSS] unmatched braces path={} open={} close={} snippet={}",
                        AuiLog.source(contextPath),
                        openBraces,
                        closeBraces,
                        AuiLog.compact(normalizedCss)
                );
            }

            Matcher matcher = RULE_PATTERN.matcher(normalizedCss);
            int order = orderStart;

            while (matcher.find()) {
                String selector = matcher.group(1).trim();
                // 忽略空的或可能是残留的 @ 规则
                if (selector.isEmpty()) continue;
                if (selector.startsWith("@")) {
                    ApricityUI.LOGGER.warn(
                            "[AUI CSS] unsupported or leftover at-rule was ignored path={} rule={}",
                            AuiLog.source(contextPath),
                            AuiLog.compact(selector)
                    );
                    continue;
                }

                String rules = matcher.group(2).trim();
                List<String> selectors = Selector.splitSelectorList(selector);
                if (selectors.isEmpty()) {
                    ApricityUI.LOGGER.warn(
                            "[AUI CSS] rule has no selector path={} rule={}",
                            AuiLog.source(contextPath),
                            AuiLog.compact(selector)
                    );
                    continue;
                }
                Map<String, Declaration> authoredProperties = parseProperties(rules, contextPath, false);
                Map<String, Declaration> properties = expandAuthorProperties(authoredProperties);

                for (String sel : selectors) {
                    String normalizedSelector = sel.trim();
                    if (normalizedSelector.isEmpty()) continue;
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
            } catch (RuntimeException exception) {
                ApricityUI.LOGGER.error(
                        "[AUI CSS] stylesheet parse failed path={} length={} snippet={}",
                        AuiLog.source(contextPath),
                        css == null ? 0 : css.length(),
                        AuiLog.compact(css),
                        exception
                );
                throw exception;
            }
        }

        private static int countCharacter(String text, char target) {
            if (text == null || text.isEmpty()) return 0;
            int count = 0;
            for (int index = 0; index < text.length(); index++) {
                if (text.charAt(index) == target) count++;
            }
            return count;
        }

        private static String evaluateMediaRules(String css, Size viewport, String contextPath) {
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
                    ApricityUI.LOGGER.error(
                            "[AUI CSS] @media rule has no opening brace path={} query={}",
                            AuiLog.source(contextPath),
                            AuiLog.compact(css.substring(headerStart))
                    );
                    output.append(css.substring(mediaIndex));
                    break;
                }
                int closeBrace = findMatchingBrace(css, openBrace);
                if (closeBrace < 0) {
                    ApricityUI.LOGGER.error(
                            "[AUI CSS] @media rule is not closed path={} query={}",
                            AuiLog.source(contextPath),
                            AuiLog.compact(css.substring(headerStart, openBrace))
                    );
                    output.append(css.substring(mediaIndex));
                    break;
                }
                String query = css.substring(headerStart, openBrace).trim();
                String body = css.substring(openBrace + 1, closeBrace);
                if (matchesMediaQuery(query, viewport, contextPath)) {
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

        private static boolean matchesMediaQuery(String query, Size viewport, String contextPath) {
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
                ApricityUI.LOGGER.warn(
                        "[AUI CSS] unsupported media feature ignored path={} feature={}",
                        AuiLog.source(contextPath),
                        AuiLog.compact(part)
                );
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
            if (rules == null || rules.isBlank()) return properties;
            int malformedPairs = 0;
            int emptyDeclarations = 0;
            String[] pairs = rules.split(";");
            for (String pair : pairs) {
                if (pair == null || pair.isBlank()) continue;
                String[] kv = pair.split(":", 2);
                if (kv.length != 2) {
                    malformedPairs++;
                    continue;
                }
                {
                    String key = normalizePropertyName(kv[0]);
                    String rawValue = kv[1].trim();
                    Declaration declaration = stripImportant(rawValue);
                    String value = declaration.value();
                    if (value.contains("url(")) {
                        value = normalizeUrlValue(value, contextPath);
                    }
                    if (!key.isEmpty() && value != null && !value.isEmpty()) {
                        Declaration normalized = new Declaration(value, declaration.important());
                        putDeclaration(properties, key, normalized);
                        if (expand) expandShorthand(properties, key, normalized);
                    } else {
                        emptyDeclarations++;
                    }
                }
            }
            if (malformedPairs > 0 || emptyDeclarations > 0) {
                ApricityUI.LOGGER.warn(
                        "[AUI CSS] invalid declarations skipped path={} malformedPairs={} emptyDeclarations={} rules={}",
                        AuiLog.source(contextPath),
                        malformedPairs,
                        emptyDeclarations,
                        AuiLog.compact(rules)
                );
            }
            return properties;
        }

        private static String normalizePropertyName(String raw) {
            String property = raw == null ? "" : raw.trim();
            if (property.startsWith("--")) return property;
            String normalized = property.toLowerCase(Locale.ROOT);
            return "-webkit-appearance".equals(normalized) ? "appearance" : normalized;
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
            boolean found = false;
            while (matcher.find()) {
                found = true;
                String rawPath = matcher.group(1).trim();
                if (rawPath.isEmpty()) {
                    ApricityUI.LOGGER.warn(
                            "[AUI CSS] empty url() value path={} declaration={}",
                            AuiLog.source(contextPath),
                            AuiLog.compact(value)
                    );
                    continue;
                }
                String resolvedPath = Loader.resolve(contextPath, rawPath);
                String replacement = Loader.isRemotePath(resolvedPath)
                        ? "url(\"" + resolvedPath + "\")"
                        : "url(\"/" + resolvedPath + "\")";
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(buffer);
            if (!found) {
                ApricityUI.LOGGER.warn(
                        "[AUI CSS] malformed url() value path={} declaration={}",
                        AuiLog.source(contextPath),
                        AuiLog.compact(value)
                );
            }
            return buffer.toString();
        }
    }
}

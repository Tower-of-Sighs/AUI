package com.sighs.apricityui.resource;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.style.Animation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CSS {
    public record DebugRule(String selector, Map<String, String> properties, String sourcePath, int order) {
    }

    public static void readCSS(String css, Map<String, Map<String, String>> targetCache, String contextPath) {
        Parser.parse(css, targetCache, null, contextPath, 0);
    }

    public static int readCSS(String css, Map<String, Map<String, String>> targetCache,
                              List<DebugRule> debugRules, String contextPath, int orderStart) {
        return Parser.parse(css, targetCache, debugRules, contextPath, orderStart);
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
        // 支持跨行 selector；否则像
        // `a,\n b { ... }`
        // 这种写法会只解析到最后一行，导致前面的 selector 丢失。
        private static final Pattern RULE_PATTERN = Pattern.compile("(?s)([^{}]+?)\\s*\\{([^}]*)}");
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
                String animName = matcher.group(1);
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

                        double percent = 0;
                        if (percentStr.equalsIgnoreCase("from")) percent = 0;
                        else if (percentStr.equalsIgnoreCase("to")) percent = 100;
                        else percent = Double.parseDouble(percentStr.replace("%", ""));

                        Animation.registerKeyframe(animName, percent, parseProperties(rules, contextPath));
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

        public static int parse(String css, Map<String, Map<String, String>> targetCache,
                                List<DebugRule> debugRules, String contextPath, int orderStart) {
            if (css == null || css.isBlank()) return orderStart;
            String normalizedCss = parseAndRegisterAnimations(css, contextPath);

            Matcher matcher = RULE_PATTERN.matcher(normalizedCss);
            int order = orderStart;

            while (matcher.find()) {
                String selector = matcher.group(1).trim();
                // 忽略空的或可能是残留的 @ 规则
                if (selector.isEmpty() || selector.startsWith("@")) continue;

                String rules = matcher.group(2).trim();
                String[] selectors = selector.split("\\s*,\\s*");
                HashMap<String, String> properties = parseProperties(rules, contextPath);

                for (String sel : selectors) {
                    String normalizedSelector = sel.trim();
                    targetCache.merge(normalizedSelector, properties, (oldMap, newMap) -> {
                        oldMap.putAll(newMap);
                        return oldMap;
                    });
                    if (debugRules != null) {
                        debugRules.add(new DebugRule(
                                normalizedSelector,
                                new HashMap<>(properties),
                                contextPath,
                                order++
                        ));
                    }
                }
            }
            return order;
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

        private static HashMap<String, String> parseProperties(String rules, String contextPath) {
            HashMap<String, String> properties = new HashMap<>();
            String[] pairs = rules.split(";");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    if (value.contains("url(")) {
                        value = normalizeUrlValue(value, contextPath);
                    }
                    if (!key.isEmpty() && !value.isEmpty()) {
                        properties.put(key, value);
                    }
                }
            }
            return properties;
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

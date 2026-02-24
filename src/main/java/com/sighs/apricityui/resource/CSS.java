package com.sighs.apricityui.resource;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.style.Animation;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CSS {
    public static void readCSS(String css, Map<String, Map<String, String>> targetCache, String contextPath) {
        readCSS(css, targetCache, contextPath, "global");
    }

    public static void readCSS(String css, Map<String, Map<String, String>> targetCache, String contextPath, String keyframeScope) {
        Parser.parse(css, targetCache, contextPath, keyframeScope);
    }

    public static class Extractor {
        private static final Pattern STYLE_TAG_PATTERN =
                Pattern.compile("(?i)<style\\b([^>]*)>(.*?)</style\\s*>", Pattern.DOTALL);
        private static final Pattern SRC_ATTR_PATTERN =
                Pattern.compile("(?i)\\bsrc\\s*=\\s*(['\"])(.*?)\\1");

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

                if (attrText != null) {
                    Matcher srcMatcher = SRC_ATTR_PATTERN.matcher(attrText);
                    while (srcMatcher.find()) {
                        String srcValue = srcMatcher.group(2);
                        if (srcValue != null && !srcValue.isEmpty()) {
                            cachedStyleSrcs.add(srcValue);
                        }
                    }
                }

                if (innerCss != null && !innerCss.isBlank()) {
                    cachedStyleContents.add(innerCss.trim());
                }
                matcher.appendReplacement(sb, "");
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        public void pushToDocument(Document document) {
            StyleAsyncHandler.INSTANCE.attach(document, contextPath, cachedStyleSrcs, cachedStyleContents);
        }
    }

    static class Parser {
        private static final Pattern URL_EXTRACTOR = Pattern.compile("url\\s*\\(\\s*['\"]?(.*?)['\"]?\\s*\\)");
        private static final Pattern KEYFRAMES_HEAD_PATTERN = Pattern.compile(
                "(?i)@(?:-webkit-)?keyframes\\s+((?:[\\w-]+)|(?:\"[^\"]+\")|(?:'[^']+'))\\s*\\{"
        );
        private static final Pattern FRAME_PATTERN = Pattern.compile(
                "((?:[\\d\\.]+%|from|to)(?:\\s*,\\s*(?:[\\d\\.]+%|from|to))*)\\s*\\{([^}]*)}",
                Pattern.CASE_INSENSITIVE
        );
        public static String parseAndRegisterAnimations(String css, String contextPath) {
            return parseAndRegisterAnimations(css, contextPath, "global");
        }

        public static String parseAndRegisterAnimations(String css, String contextPath, String keyframeScope) {
            if (css == null) return "";
            String scope = (keyframeScope == null || keyframeScope.isBlank()) ? "global" : keyframeScope;
            StringBuilder cleanCss = new StringBuilder(css.replaceAll("(?s)/\\*.*?\\*/", ""));
            Matcher matcher = KEYFRAMES_HEAD_PATTERN.matcher(cleanCss);

            int offset = 0;
            while (matcher.find(offset)) {
                String animName = matcher.group(1).trim();
                if ((animName.startsWith("\"") && animName.endsWith("\""))
                        || (animName.startsWith("'") && animName.endsWith("'"))) {
                    animName = animName.substring(1, animName.length() - 1);
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
                    Animation.clearKeyframes(scope, animName);

                    String fullContent = cleanCss.substring(blockStart, blockEnd);
                    Matcher frameMatcher = FRAME_PATTERN.matcher(fullContent);
                    while (frameMatcher.find()) {
                        String selectorList = frameMatcher.group(1);
                        String rules = frameMatcher.group(2);
                        HashMap<String, String> props = parseProperties(rules, contextPath);

                        for (String sel : selectorList.split("\\s*,\\s*")) {
                            String percentStr = sel.trim();
                            if (percentStr.isEmpty()) continue;

                            double percent;
                            if (percentStr.equalsIgnoreCase("from")) {
                                percent = 0;
                            } else if (percentStr.equalsIgnoreCase("to")) {
                                percent = 100;
                            } else {
                                try {
                                    percent = Double.parseDouble(percentStr.replace("%", ""));
                                } catch (NumberFormatException ignored) {
                                    continue;
                                }
                            }

                            if (percent < 0 || percent > 100) continue;
                            Animation.registerKeyframe(scope, animName, percent, props);
                        }
                    }

                    cleanCss.delete(matcher.start(), blockEnd + 1);
                    offset = matcher.start();
                } else {
                    offset = matcher.end();
                }
            }

            return cleanCss.toString();
        }

        public static void parse(String css, Map<String, Map<String, String>> targetCache, String contextPath) {
            parse(css, targetCache, contextPath, "global");
        }

        public static void parse(String css, Map<String, Map<String, String>> targetCache, String contextPath, String keyframeScope) {
            if (css == null || css.isBlank()) return;
            String normalizedCss = parseAndRegisterAnimations(css, contextPath, keyframeScope);

            Pattern pattern = Pattern.compile("(.*?)\\s*\\{([^}]*)}");
            Matcher matcher = pattern.matcher(normalizedCss);

            while (matcher.find()) {
                String selector = matcher.group(1).trim();
                if (selector.isEmpty() || selector.startsWith("@")) continue;

                String rules = matcher.group(2).trim();
                String[] selectors = selector.split("\\s*,\\s*");
                HashMap<String, String> properties = parseProperties(rules, contextPath);

                for (String sel : selectors) {
                    targetCache.merge(sel.trim(), properties, (oldMap, newMap) -> {
                        oldMap.putAll(newMap);
                        return oldMap;
                    });
                }
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

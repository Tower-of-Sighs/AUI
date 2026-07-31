package com.sighs.apricityui.resource;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.ClientLoader;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.util.AuiLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JS {

    public static class Extractor {
        private static final Pattern SCRIPT_TAG_PATTERN =
                Pattern.compile("(?i)<script\\b([^>]*)>(.*?)</script\\s*>", Pattern.DOTALL);

        private static final Pattern SRC_ATTR_PATTERN =
                Pattern.compile("(?i)\\bsrc\\s*=\\s*(['\"])(.*?)\\1");
        private static final Pattern SCRIPT_OPEN_MARKER = Pattern.compile("(?i)<script\\b");
        private static final Pattern SCRIPT_CLOSE_MARKER = Pattern.compile("(?i)</script\\s*>");

        private final List<String> cachedScriptSrcs = new ArrayList<>();
        private final List<String> cachedScriptContents = new ArrayList<>();
        private final String contextPath;

        public Extractor(String contextPath) {
            this.contextPath = contextPath;
        }

        public String handle(String html) {
            if (html == null || html.isEmpty()) return html;

            int openCount = countMatches(SCRIPT_OPEN_MARKER, html);
            int closeCount = countMatches(SCRIPT_CLOSE_MARKER, html);
            if (openCount != closeCount) {
                ApricityUI.LOGGER.warn(
                        "[AUI JS] unmatched script tag path={} openTags={} closeTags={}",
                        AuiLog.source(contextPath),
                        openCount,
                        closeCount
                );
            }

            Matcher matcher = SCRIPT_TAG_PATTERN.matcher(html);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String attrText = matcher.group(1); // 标签属性部分
                String innerScript = matcher.group(2); // 标签内部内容

                boolean hasSrc = false;

                // 1. 尝试提取 src
                if (attrText != null) {
                    Matcher srcMatcher = SRC_ATTR_PATTERN.matcher(attrText);
                    if (srcMatcher.find()) {
                        String srcValue = srcMatcher.group(2);
                        if (srcValue != null && !srcValue.isEmpty()) {
                            cachedScriptSrcs.add(srcValue);
                            hasSrc = true;
                        }
                    } else if (attrText.toLowerCase().contains("src")) {
                        ApricityUI.LOGGER.warn(
                                "[AUI JS] script src attribute is malformed path={} attributes={}",
                                AuiLog.source(contextPath),
                                AuiLog.compact(attrText)
                        );
                    }
                }

                // 2. 如果没有 src 或者有内部代码，则提取内部代码
                // 注意：HTML标准中如果带src通常忽略内部代码，但在你的UI引擎中可以根据需求决定是否允许两者共存
                // 这里逻辑为：如果有内容，就加入缓存
                if (innerScript != null && !innerScript.isBlank()) {
                    cachedScriptContents.add(innerScript.trim());
                    if (hasSrc) {
                        ApricityUI.LOGGER.warn(
                                "[AUI JS] script has both src and inline code; both will execute path={} src={}",
                                AuiLog.source(contextPath),
                                AuiLog.compact(attrText)
                        );
                    }
                }

                // 3. 从 HTML 中移除该标签
                matcher.appendReplacement(sb, "");
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        public void pushToDocument(Document document) {
            if (document == null) {
                ApricityUI.LOGGER.error("[AUI JS] cannot attach scripts without a document path={}", AuiLog.source(contextPath));
                return;
            }
            for (String src : cachedScriptSrcs) {
                String resolvedPath = Loader.resolve(contextPath, src);
                try (InputStream is = ClientLoader.getResourceStream(resolvedPath)) {
                    if (is == null) {
                        ApricityUI.LOGGER.error(
                                "[AUI JS] external script resource is missing document={} src={} resolved={}",
                                AuiLog.source(contextPath),
                                src,
                                resolvedPath
                        );
                        continue;
                    }
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    if (content.isBlank()) {
                        ApricityUI.LOGGER.warn("[AUI JS] external script is empty resolved={}", resolvedPath);
                    }
                    document.JSCache.add(content);
                } catch (IOException e) {
                    ApricityUI.LOGGER.error(
                            "[AUI JS] failed to read external script document={} resolved={}",
                            AuiLog.source(contextPath),
                            resolvedPath,
                            e
                    );
                }
            }

            document.JSCache.addAll(cachedScriptContents);
        }

        private static int countMatches(Pattern pattern, String value) {
            int count = 0;
            Matcher matcher = pattern.matcher(value);
            while (matcher.find()) count++;
            return count;
        }
    }
}

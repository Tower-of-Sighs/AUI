package com.sighs.apricityui.parser;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.loader.ClientLoader;
import com.sighs.apricityui.instance.loader.Loader;
import com.sighs.apricityui.util.AuiLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JS {

    public static class Extractor extends TagExtractor {
        private static final Pattern SCRIPT_TAG_PATTERN =
                Pattern.compile("(?i)<script\\b([^>]*)>(.*?)</script\\s*>", Pattern.DOTALL);

        private static final Pattern SRC_ATTR_PATTERN =
                Pattern.compile("(?i)\\bsrc\\s*=\\s*(['\"])(.*?)\\1");
        private static final Pattern SCRIPT_OPEN_MARKER = Pattern.compile("(?i)<script\\b");
        private static final Pattern SCRIPT_CLOSE_MARKER = Pattern.compile("(?i)</script\\s*>");

        public Extractor(String contextPath) {
            super(contextPath, SCRIPT_OPEN_MARKER, SCRIPT_CLOSE_MARKER, "JS", "script");
        }

        @Override
        protected String extract(String html) {
            return removeTags(html, SCRIPT_TAG_PATTERN);
        }

        @Override
        protected void onTag(String attrText, String inner) {
            boolean hasSrc = false;

            // 1. 尝试提取 src
            if (attrText != null) {
                Matcher srcMatcher = SRC_ATTR_PATTERN.matcher(attrText);
                if (srcMatcher.find()) {
                    String srcValue = srcMatcher.group(2);
                    if (srcValue != null && !srcValue.isEmpty()) {
                        cachedSrcs.add(srcValue);
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
            if (inner != null && !inner.isBlank()) {
                cachedContents.add(inner.trim());
                if (hasSrc) {
                    ApricityUI.LOGGER.warn(
                            "[AUI JS] script has both src and inline code; both will execute path={} src={}",
                            AuiLog.source(contextPath),
                            AuiLog.compact(attrText)
                    );
                }
            }
        }

        @Override
        public void pushToDocument(Document document) {
            if (document == null) {
                ApricityUI.LOGGER.error("[AUI JS] cannot attach scripts without a document path={}", AuiLog.source(contextPath));
                return;
            }
            for (String src : cachedSrcs) {
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

            document.JSCache.addAll(cachedContents);
        }
    }
}

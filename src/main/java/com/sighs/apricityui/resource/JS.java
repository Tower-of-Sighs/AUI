package com.sighs.apricityui.resource;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.util.StringUtils;

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

        private final List<String> cachedScriptSrcs = new ArrayList<>();
        private final List<String> cachedScriptContents = new ArrayList<>();
        private final String contextPath;

        public Extractor(String contextPath) {
            this.contextPath = contextPath;
        }

        public String handle(String html) {
            if (StringUtils.isNullOrEmpty(html)) return html;

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
                    }
                }

                // 2. 如果没有 src 或者有内部代码，则提取内部代码
                // 注意：HTML标准中如果带src通常忽略内部代码，但在你的UI引擎中可以根据需求决定是否允许两者共存
                // 这里逻辑为：如果有内容，就加入缓存
                if (StringUtils.isNotNullOrEmptyEx(innerScript)) {
                    cachedScriptContents.add(innerScript.trim());
                }

                // 3. 从 HTML 中移除该标签
                matcher.appendReplacement(sb, "");
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        public void pushToDocument(Document document) {
            for (String src : cachedScriptSrcs) {
                String resolvedPath = Loader.resolve(contextPath, src);
                try (InputStream is = Loader.getResourceStream(resolvedPath)) {
                    if (is != null) {
                        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        document.JSCache.add(content);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            document.JSCache.addAll(cachedScriptContents);
        }
    }
}
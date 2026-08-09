package com.sighs.apricityui.parser;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.util.AuiLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JS {

    // KubeJS 自带的 Rhino 版本不支持部分 ES6 语法（数组展开、默认参数等）。
    // 在把页面脚本交给 Rhino 求值前，先把这些语法点转写成 ES5 兼容的形式。
    private static final Pattern ARRAY_SPREAD_PATTERN =
            Pattern.compile("\\[\\.\\.\\.([A-Za-z_$][\\w$]*)\\]");
    private static final Pattern FUNCTION_HEAD_PATTERN =
            Pattern.compile("\\bfunction\\s+([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern INNER_TEXT_PATTERN =
            Pattern.compile("\\.innerText\\b");

    /**
     * Rewrites browser-ish ES6 syntax (array spread, default parameters) into
     * ES5 forms understood by the Rhino engine bundled with KubeJS, and maps
     * {@code innerText} to {@code textContent}. Loader-independent; the loader
     * script engine calls this before evaluating.
     */
    public static String rewriteForRhino(String code) {
        if (code == null) return null;
        code = ARRAY_SPREAD_PATTERN.matcher(code).replaceAll("$1.slice()");
        code = rewriteDefaultParameters(code);
        code = INNER_TEXT_PATTERN.matcher(code).replaceAll(".textContent");
        return code;
    }

    /**
     * 将函数声明中的默认参数改写为函数体内的 typeof 检查赋值。
     * 例如：function showToast(msg, isError = false) { ... }
     * 改写为：function showToast(msg, isError) { if (typeof isError === 'undefined') isError = false; ... }
     */
    private static String rewriteDefaultParameters(String code) {
        StringBuilder out = new StringBuilder();
        Matcher matcher = FUNCTION_HEAD_PATTERN.matcher(code);
        int lastEnd = 0;

        while (matcher.find()) {
            int paramsStart = matcher.end();
            int paramsEnd = findMatchingParen(code, paramsStart);
            if (paramsEnd < 0) continue;

            int bracePos = paramsEnd + 1;
            while (bracePos < code.length() && Character.isWhitespace(code.charAt(bracePos))) bracePos++;
            if (bracePos >= code.length() || code.charAt(bracePos) != '{') continue;

            String params = code.substring(paramsStart, paramsEnd);
            List<String> parts = splitTopLevel(params, ',');
            List<String> newParams = new ArrayList<>(parts.size());
            StringBuilder assignments = new StringBuilder();
            boolean hasDefault = false;

            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                int eq = findTopLevelEquals(trimmed);
                if (eq >= 0) {
                    hasDefault = true;
                    String name = trimmed.substring(0, eq).trim();
                    String expr = trimmed.substring(eq + 1).trim();
                    newParams.add(name);
                    assignments.append("if (typeof ").append(name)
                            .append(" === 'undefined') ").append(name)
                            .append(" = ").append(expr).append(";");
                } else {
                    newParams.add(trimmed);
                }
            }

            if (!hasDefault) continue;

            out.append(code, lastEnd, matcher.start());
            out.append("function ").append(matcher.group(1))
                    .append("(").append(String.join(", ", newParams)).append(") {")
                    .append(assignments);
            lastEnd = bracePos + 1; // 跳过原开括号，后续正文保持不变
        }

        out.append(code.substring(lastEnd));
        return out.toString();
    }

    /**
     * 从 openPos（'(' 后的第一个字符）开始扫描，返回与之匹配的 ')' 的索引。
     * 会跳过字符串字面量以及嵌套的括号、方括号、花括号。
     */
    private static int findMatchingParen(String text, int openPos) {
        int depth = 1;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inTemplate = false;
        boolean escape = false;
        for (int i = openPos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (inSingle) {
                if (c == '\'') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '"') inDouble = false;
                continue;
            }
            if (inTemplate) {
                if (c == '`') inTemplate = false;
                continue;
            }
            if (c == '\'') { inSingle = true; continue; }
            if (c == '"') { inDouble = true; continue; }
            if (c == '`') { inTemplate = true; continue; }
            if (c == '(') { depth++; continue; }
            if (c == ')') {
                depth--;
                if (depth == 0) return i;
                continue;
            }
        }
        return -1;
    }

    /**
     * 按顶层分隔符切分字符串，忽略引号与成对括号内的分隔符。
     */
    private static List<String> splitTopLevel(String text, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int paren = 0;
        int bracket = 0;
        int brace = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inTemplate = false;
        boolean escape = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                current.append(c);
                escape = true;
                continue;
            }
            if (inSingle) {
                current.append(c);
                if (c == '\'') inSingle = false;
                continue;
            }
            if (inDouble) {
                current.append(c);
                if (c == '"') inDouble = false;
                continue;
            }
            if (inTemplate) {
                current.append(c);
                if (c == '`') inTemplate = false;
                continue;
            }
            if (c == '\'') { current.append(c); inSingle = true; continue; }
            if (c == '"') { current.append(c); inDouble = true; continue; }
            if (c == '`') { current.append(c); inTemplate = true; continue; }
            if (c == '(') { paren++; current.append(c); continue; }
            if (c == ')') { paren--; current.append(c); continue; }
            if (c == '[') { bracket++; current.append(c); continue; }
            if (c == ']') { bracket--; current.append(c); continue; }
            if (c == '{') { brace++; current.append(c); continue; }
            if (c == '}') { brace--; current.append(c); continue; }
            if (c == delimiter && paren == 0 && bracket == 0 && brace == 0) {
                result.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        result.add(current.toString());
        return result;
    }

    /**
     * 在字符串顶层查找第一个作为默认参数赋值的 '='。
     * 会跳过 ==、!=、===、!==、<=、>=、=> 等操作符以及引号、括号内的内容。
     */
    private static int findTopLevelEquals(String text) {
        int paren = 0;
        int bracket = 0;
        int brace = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inTemplate = false;
        boolean escape = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (inSingle) { if (c == '\'') inSingle = false; continue; }
            if (inDouble) { if (c == '"') inDouble = false; continue; }
            if (inTemplate) { if (c == '`') inTemplate = false; continue; }
            if (c == '\'') { inSingle = true; continue; }
            if (c == '"') { inDouble = true; continue; }
            if (c == '`') { inTemplate = true; continue; }
            if (c == '(') { paren++; continue; }
            if (c == ')') { paren--; continue; }
            if (c == '[') { bracket++; continue; }
            if (c == ']') { bracket--; continue; }
            if (c == '{') { brace++; continue; }
            if (c == '}') { brace--; continue; }
            if (c == '=' && paren == 0 && bracket == 0 && brace == 0) {
                // 排除 ==、===、!=、!==、<=、>=、=>
                boolean prevIsOp = i > 0 && "=!<>".indexOf(text.charAt(i - 1)) >= 0;
                boolean nextIsOp = i + 1 < text.length() && text.charAt(i + 1) == '=';
                boolean nextIsArrow = i + 1 < text.length() && text.charAt(i + 1) == '>';
                if (!prevIsOp && !nextIsOp && !nextIsArrow) return i;
            }
        }
        return -1;
    }

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
            ResourceUsageIndex.recordJs(contextPath, cachedSrcs);
            document.JSCache.addAll(loadScripts());
        }

        public List<String> loadScripts() {
            ArrayList<String> scripts = new ArrayList<>();
            for (String src : cachedSrcs) {
                String resolvedPath = Loader.resolve(contextPath, src);
                if (Loader.isRemotePath(resolvedPath)) {
                    ApricityUI.LOGGER.warn(
                            "[AUI JS] remote external script is unsupported; skipped document={} src={}",
                            AuiLog.source(contextPath),
                            resolvedPath
                    );
                    continue;
                }
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
                    scripts.add(content);
                } catch (IOException e) {
                    ApricityUI.LOGGER.error(
                            "[AUI JS] failed to read external script document={} resolved={}",
                            AuiLog.source(contextPath),
                            resolvedPath,
                            e
                    );
                }
            }

            scripts.addAll(cachedContents);
            return List.copyOf(scripts);
        }
    }
}

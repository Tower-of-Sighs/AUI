package com.sighs.apricityui.resource;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Loader;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTML {
    private static final HashMap<String, String> temples = new HashMap<>();

    public static void putTemple(String path, String html) {
        temples.put(path, html);
    }

    public static String getTemple(String path) {
        return temples.get(path);
    }

    public static void scan() {
        new Loader("html").loadResources(HTML::putTemple);
    }

    public static Element create(Document document, String path) {
        String rawHtml = getTemple(path);
        if (rawHtml == null || rawHtml.isBlank()) return null;
        if (!rawHtml.trim().toLowerCase().startsWith("<body")) {
            rawHtml = "<body>" + rawHtml + "</body>";
        }

        // 1. 处理 CSS (提取 <style>)
        CSS.Extractor cssExtractor = new CSS.Extractor(path);
        String htmlAfterCss = cssExtractor.handle(rawHtml);
        cssExtractor.pushToDocument(document);

        // 2. 处理 JS (提取 <script>) -> 这里是新增逻辑
        JS.Extractor jsExtractor = new JS.Extractor(path);
        String cleanHtml = jsExtractor.handle(htmlAfterCss); // 传入经过CSS处理后的HTML
        jsExtractor.pushToDocument(document);

        // 3. 构建 DOM (传入既没有style也没有script的纯HTML)
        return buildDOM(document, cleanHtml);
    }

    public static Element createElement(Document document, String html) {
        if (html == null || html.isBlank()) return null;

        // 统一处理：确保有 body 包裹，以便 buildDOM 逻辑能正确识别根节点
        String processedHtml = html.trim();
        if (!processedHtml.toLowerCase().startsWith("<body")) {
            processedHtml = "<body>" + processedHtml + "</body>";
        }

        // 复用 buildDOM 逻辑
        return buildDOM(document, processedHtml);
    }

    private static Element buildDOM(Document document, String html) {
        List<Token> tokens = HtmlTokenizer.tokenize(html);
        if (tokens.isEmpty()) return null;

        Deque<Element> stack = new ArrayDeque<>();
        Element root = null;

        for (Token token : tokens) {
            switch (token.type) {
                case START_TAG -> {
                    Element el = document.createElement(token.tagName);
                    token.attributes.forEach(el::setAttribute);
                    if (stack.isEmpty()) {
                        if (root != null) return root;
                        root = el;
                    }
                    if (!token.selfClosing) stack.push(el);
                    else if (!stack.isEmpty()) stack.peek().append(el);
                }
                case END_TAG -> {
                    if (stack.isEmpty()) return null;
                    Element finished = stack.pop();
                    if (!stack.isEmpty()) stack.peek().append(finished);
                }
                case TEXT -> {
                    if (stack.isEmpty()) return null;
                    if (!token.content.isBlank()) stack.peek().innerText += token.content;
                }
                case COMMENT -> {}
            }
        }
        return stack.isEmpty() ? root : null;
    }


    enum TokenType {
        START_TAG,
        END_TAG,
        TEXT,
        COMMENT
    }

    static class Token {
        TokenType type;
        String tagName;
        boolean selfClosing;
        Map<String, String> attributes = new LinkedHashMap<>();
        String content;

        static Token start(String name, boolean selfClosing) {
            Token t = new Token();
            t.type = TokenType.START_TAG;
            t.tagName = name;
            t.selfClosing = selfClosing;
            return t;
        }

        static Token end(String name) {
            Token t = new Token();
            t.type = TokenType.END_TAG;
            t.tagName = name;
            return t;
        }

        static Token text(String text) {
            Token t = new Token();
            t.type = TokenType.TEXT;
            t.content = text;
            return t;
        }

        static Token comment() {
            Token t = new Token();
            t.type = TokenType.COMMENT;
            return t;
        }
    }

    static class HtmlTokenizer {

        private static final Pattern TOKEN_PATTERN =
                Pattern.compile("<!--.*?-->|</?[^>]+>|[^<]+", Pattern.DOTALL);

        private static final Pattern ATTR_PATTERN =
                Pattern.compile("([\\w-]+)(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\"'>]+))?");

        static List<Token> tokenize(String html) {
            List<Token> tokens = new ArrayList<>();
            Deque<String> tagStack = new ArrayDeque<>();

            Matcher matcher = TOKEN_PATTERN.matcher(html);

            while (matcher.find()) {
                String part = matcher.group();

                // 注释
                if (part.startsWith("<!--")) {
                    tokens.add(Token.comment());
                    continue;
                }

                // 结束标签
                if (part.startsWith("</")) {
                    String name = part.substring(2, part.length() - 1).trim();
                    tokens.add(Token.end(name));
                    if (!tagStack.isEmpty()) {
                        tagStack.pop();
                    }
                    continue;
                }

                // 开始 / 自闭合
                if (part.startsWith("<")) {
                    boolean selfClosing = part.endsWith("/>");
                    String body = part.substring(1, part.length() - (selfClosing ? 2 : 1)).trim();

                    Matcher nameMatcher = Pattern.compile("^([\\w-]+)").matcher(body);
                    if (nameMatcher.find()) {
                        String tagName = nameMatcher.group(1);
                        Token token = Token.start(tagName, selfClosing);

                        String attrSection = body.substring(nameMatcher.end()).trim();
                        if (!attrSection.isEmpty()) {
                            parseAttributes(attrSection, token.attributes);
                        }

                        tokens.add(token);
                        if (!selfClosing) {
                            tagStack.push(tagName);
                        }
                    }
                    continue;
                }

                if (part.isBlank()) continue;

                tokens.add(Token.text(part));
            }

            return tokens;
        }

        private static void parseAttributes(String src, Map<String, String> out) {
            Matcher matcher = ATTR_PATTERN.matcher(src);
            while (matcher.find()) {
                String key = matcher.group(1);
                String val = matcher.group(2);
                if (val != null) {
                    val = val.replaceAll("^['\"]|['\"]$", "");
                } else {
                    val = "";
                }
                out.put(key, val);
            }
        }
    }
}

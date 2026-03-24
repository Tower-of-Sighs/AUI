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

        CSS.Extractor cssExtractor = new CSS.Extractor(path);
        String htmlAfterCss = cssExtractor.handle(rawHtml);
        cssExtractor.pushToDocument(document);

        JS.Extractor jsExtractor = new JS.Extractor(path);
        String cleanHtml = jsExtractor.handle(htmlAfterCss);
        jsExtractor.pushToDocument(document);

        return buildDOM(document, cleanHtml);
    }

    public static Element createElement(Document document, String html) {
        return buildDOM(document, html);
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
        private static final Pattern TAG_NAME_PATTERN = Pattern.compile("^([\\w-]+)");

        private static final Pattern ATTR_PATTERN =
                Pattern.compile("([\\w-]+)(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\"'>]+))?");

        static List<Token> tokenize(String html) {
            List<Token> tokens = new ArrayList<>();

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
                    continue;
                }

                // 开始 / 自闭合
                if (part.startsWith("<")) {
                    boolean selfClosing = part.endsWith("/>");
                    String body = part.substring(1, part.length() - (selfClosing ? 2 : 1)).trim();

                    Matcher nameMatcher = TAG_NAME_PATTERN.matcher(body);
                    if (nameMatcher.find()) {
                        String tagName = nameMatcher.group(1);
                        Token token = Token.start(tagName, selfClosing);

                        String attrSection = body.substring(nameMatcher.end()).trim();
                        if (!attrSection.isEmpty()) {
                            parseAttributes(attrSection, token.attributes);
                        }

                        tokens.add(token);
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
                    if (val.length() >= 2) {
                        char first = val.charAt(0);
                        char last = val.charAt(val.length() - 1);
                        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                            val = val.substring(1, val.length() - 1);
                        }
                    }
                } else {
                    val = "";
                }
                out.put(key, val);
            }
        }
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
                    applyAttributesFast(el, token.attributes);

                    if (token.selfClosing) {
                        Element finalized = Element.init(el);
                        if (!stack.isEmpty()) {
                            attachChildFast(stack.peek(), finalized);
                        } else if (root == null) {
                            root = finalized;
                        } else {
                            return root;
                        }
                    } else {
                        stack.push(el);
                    }
                }
                case END_TAG -> {
                    if (stack.isEmpty()) return null;
                    Element finished = Element.init(stack.pop());
                    if (!stack.isEmpty()) {
                        attachChildFast(stack.peek(), finished);
                    } else if (root == null) {
                        root = finished;
                    } else {
                        return root;
                    }
                }
                case TEXT -> {
                    if (stack.isEmpty()) return null;
                    if (!token.content.isBlank()) stack.peek().innerText += token.content;
                }
                case COMMENT -> {
                }
            }
        }
        return stack.isEmpty() ? root : null;
    }

    private static void applyAttributesFast(Element element, Map<String, String> attributes) {
        if (element == null || attributes == null || attributes.isEmpty()) return;
        attributes.forEach((key, value) -> element.getAttributes().put(key, value == null ? "" : value));
    }

    private static void attachChildFast(Element parent, Element child) {
        if (parent == null || child == null) return;
        child.parentElement = parent;
        parent.children.add(child);
    }
}

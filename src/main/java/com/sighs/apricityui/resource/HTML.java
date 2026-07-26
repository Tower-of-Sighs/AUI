package com.sighs.apricityui.resource;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Head;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.init.CommentNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.init.TextNode;
import com.sighs.apricityui.instance.ClientLoader;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTML {
    private static final Pattern DOCTYPE_PATTERN = Pattern.compile("(?is)^\\s*<!doctype[^>]*>\\s*");
    private static final Pattern XML_DECL_PATTERN = Pattern.compile("(?is)^\\s*<\\?xml[^>]*\\?>\\s*");
    private static final Pattern BODY_BLOCK_PATTERN = Pattern.compile("(?is)<body\\b[^>]*>.*?</body\\s*>");
    private static final Pattern HEAD_BLOCK_PATTERN = Pattern.compile("(?is)<head\\b[^>]*>.*?</head\\s*>");
    private static final Pattern META_TAG_PATTERN = Pattern.compile("(?is)<meta\\b([^>]*)>");
    private static final Pattern HTML_OPEN_PATTERN = Pattern.compile("(?is)<html\\b[^>]*>");
    private static final Pattern HTML_CLOSE_PATTERN = Pattern.compile("(?is)</html\\s*>");

    private static final HashMap<String, String> temples = new HashMap<>();

    public static void putTemple(String path, String html) {
        temples.put(path, html);
    }

    public static String getTemple(String path) {
        return temples.get(path);
    }

    public static String findMetaContent(String path, String name) {
        return findMetaContentInMarkup(getTemple(path), name);
    }

    public static void scan() {
        new ClientLoader("html").loadResources(HTML::putTemple);
    }

    public static DocumentRoot create(Document document, String path) {
        String rawHtml = getTemple(path);
        if (rawHtml == null || rawHtml.isBlank()) return null;

        CSS.Extractor cssExtractor = new CSS.Extractor(path);
        String htmlAfterCss = cssExtractor.handle(rawHtml);
        cssExtractor.pushToDocument(document);

        JS.Extractor jsExtractor = new JS.Extractor(path);
        String cleanHtml = normalizeDocumentMarkup(jsExtractor.handle(htmlAfterCss));
        jsExtractor.pushToDocument(document);

        return buildDocument(document, cleanHtml);
    }

    public static Element createElement(Document document, String html) {
        DocumentRoot root = buildDocument(document, "<body>" + (html == null ? "" : html) + "</body>");
        if (root == null || root.body() == null) return null;
        for (Node child : root.body().getChildNodes()) {
            if (child instanceof Element element) return element;
        }
        return null;
    }

    private static String findMetaContentInMarkup(String html, String name) {
        if (html == null || html.isBlank() || name == null || name.isBlank()) return null;
        Matcher matcher = META_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            String attrText = matcher.group(1);
            String metaName = findAttrValue(attrText, "name");
            if (name.equalsIgnoreCase(metaName)) {
                return findAttrValue(attrText, "content");
            }
        }
        return null;
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

        static Token comment(String text) {
            Token t = new Token();
            t.type = TokenType.COMMENT;
            t.content = text;
            return t;
        }
    }

    static class HtmlTokenizer {
        private static final Map<String, String> NAMED_CHARACTER_REFERENCES = Map.of(
                "amp", "&",
                "apos", "'",
                "gt", ">",
                "lt", "<",
                "nbsp", "\u00A0",
                "quot", "\""
        );
        private static final Set<String> VOID_TAGS = Set.of(
                "area", "base", "br", "col", "embed", "hr", "img", "input",
                "link", "meta", "param", "source", "track", "wbr"
        );

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
                    tokens.add(Token.comment(part.length() >= 7 ? part.substring(4, part.length() - 3) : ""));
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
                    boolean explicitSelfClosing = part.endsWith("/>");
                    String body = part.substring(1, part.length() - (explicitSelfClosing ? 2 : 1)).trim();

                    Matcher nameMatcher = TAG_NAME_PATTERN.matcher(body);
                    if (nameMatcher.find()) {
                        String tagName = nameMatcher.group(1);
                        boolean selfClosing = explicitSelfClosing || isVoidTag(tagName);
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

        private static boolean isVoidTag(String tagName) {
            if (tagName == null || tagName.isBlank()) return false;
            return VOID_TAGS.contains(tagName.toLowerCase(Locale.ROOT));
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
                    val = decodeCharacterReferences(val);
                } else {
                    val = "";
                }
                out.put(key, val);
            }
        }

        static String decodeCharacterReferences(String value) {
            if (value == null || value.indexOf('&') < 0) return value;

            StringBuilder decoded = new StringBuilder(value.length());
            for (int index = 0; index < value.length();) {
                char current = value.charAt(index);
                if (current != '&') {
                    decoded.append(current);
                    index++;
                    continue;
                }

                int end = value.indexOf(';', index + 1);
                if (end < 0) {
                    decoded.append(current);
                    index++;
                    continue;
                }

                String reference = value.substring(index + 1, end);
                String named = NAMED_CHARACTER_REFERENCES.get(reference);
                if (named != null) {
                    decoded.append(named);
                    index = end + 1;
                    continue;
                }

                Integer codePoint = parseNumericCharacterReference(reference);
                if (codePoint != null) {
                    decoded.appendCodePoint(codePoint);
                    index = end + 1;
                    continue;
                }

                decoded.append(current);
                index++;
            }
            return decoded.toString();
        }

        private static Integer parseNumericCharacterReference(String reference) {
            if (reference == null || reference.length() < 2 || reference.charAt(0) != '#') return null;
            int radix = 10;
            int start = 1;
            if (reference.length() > 2 && (reference.charAt(1) == 'x' || reference.charAt(1) == 'X')) {
                radix = 16;
                start = 2;
            }
            if (start >= reference.length()) return null;
            try {
                int codePoint = Integer.parseInt(reference.substring(start), radix);
                if (!Character.isValidCodePoint(codePoint)
                        || codePoint == 0
                        || codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
                    return 0xFFFD;
                }
                return codePoint;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private static DocumentRoot buildDocument(Document document, String html) {
        List<Token> tokens = HtmlTokenizer.tokenize(html);
        if (tokens.isEmpty()) return null;

        Deque<Element> stack = new ArrayDeque<>();
        Element parsedRoot = null;

        for (Token token : tokens) {
            switch (token.type) {
                case START_TAG -> {
                    Element el = document.createElement(token.tagName);
                    applyAttributesFast(el, token.attributes);

                    if (token.selfClosing) {
                        Element finalized = Element.init(el);
                        if (!stack.isEmpty()) {
                            attachChildFast(stack.peek(), finalized);
                        } else if (parsedRoot == null) {
                            parsedRoot = finalized;
                        } else {
                            attachChildFast(parsedRoot, finalized);
                        }
                    } else {
                        stack.push(el);
                    }
                }
                case END_TAG -> {
                    if (stack.isEmpty()) continue;
                    if (!containsTag(stack, token.tagName)) continue;
                    while (!stack.isEmpty()) {
                        Element finished = Element.init(stack.pop());
                        if (!stack.isEmpty()) {
                            attachChildFast(stack.peek(), finished);
                        } else if (parsedRoot == null) {
                            parsedRoot = finished;
                        } else {
                            attachChildFast(parsedRoot, finished);
                        }
                        if (isTag(finished, token.tagName)) break;
                    }
                }
                case TEXT -> {
                    if (stack.isEmpty()) continue;
                    if (!token.content.isBlank()) {
                        Element parent = stack.peek();
                        String content = isRawTextElement(parent)
                                ? token.content
                                : HtmlTokenizer.decodeCharacterReferences(token.content);
                        attachChildFast(parent, document.createTextNode(content));
                    }
                }
                case COMMENT -> {
                    if (stack.isEmpty()) continue;
                    attachChildFast(stack.peek(), document.createComment(token.content == null ? "" : token.content));
                }
            }
        }
        while (!stack.isEmpty()) {
            Element finished = Element.init(stack.pop());
            if (!stack.isEmpty()) {
                attachChildFast(stack.peek(), finished);
            } else if (parsedRoot == null) {
                parsedRoot = finished;
            } else {
                attachChildFast(parsedRoot, finished);
            }
        }
        return toDocumentRoot(document, parsedRoot);
    }

    private static boolean containsTag(Deque<Element> stack, String tagName) {
        if (stack == null || stack.isEmpty() || tagName == null || tagName.isBlank()) return false;
        for (Element element : stack) {
            if (isTag(element, tagName)) return true;
        }
        return false;
    }

    private static boolean isRawTextElement(Element element) {
        return isTag(element, "script") || isTag(element, "style");
    }

    private static String normalizeDocumentMarkup(String html) {
        if (html == null || html.isBlank()) return "<body></body>";

        String normalized = stripDocumentPreamble(html).trim();
        if (normalized.isEmpty()) return "<body></body>";

        Matcher bodyMatcher = BODY_BLOCK_PATTERN.matcher(normalized);
        if (bodyMatcher.find()) {
            return bodyMatcher.group();
        }

        normalized = HEAD_BLOCK_PATTERN.matcher(normalized).replaceAll("");
        normalized = HTML_OPEN_PATTERN.matcher(normalized).replaceAll("");
        normalized = HTML_CLOSE_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.trim();

        if (normalized.isEmpty()) return "<body></body>";
        if (normalized.regionMatches(true, 0, "<body", 0, 5)) return normalized;
        return "<body>" + normalized + "</body>";
    }

    private static String stripDocumentPreamble(String html) {
        String normalized = html;
        boolean changed = true;
        while (changed) {
            String updated = DOCTYPE_PATTERN.matcher(XML_DECL_PATTERN.matcher(normalized).replaceFirst("")).replaceFirst("");
            changed = !updated.equals(normalized);
            normalized = updated;
        }
        return normalized;
    }

    private static void applyAttributesFast(Element element, Map<String, String> attributes) {
        if (element == null || attributes == null || attributes.isEmpty()) return;
        attributes.forEach((key, value) -> element.getAttributes().put(key, value == null ? "" : value));
    }

    private static void attachChildFast(Element parent, Element child) {
        if (parent == null || child == null) return;
        child.parentNode = parent;
        child.parentElement = parent;
        parent.childNodes.add(child);
        parent.children.add(child);
    }

    private static void attachChildFast(Element parent, Node child) {
        if (parent == null || child == null) return;
        child.parentNode = parent;
        if (child instanceof Element childElement) {
            childElement.parentElement = parent;
            parent.children.add(childElement);
        }
        parent.childNodes.add(child);
    }

    private static DocumentRoot toDocumentRoot(Document document, Element parsedRoot) {
        if (parsedRoot == null) return null;

        Html html = new Html(document);
        Head head = new Head(document);
        Body body = new Body(document);

        if (isTag(parsedRoot, "html")) {
            copyAttributes(parsedRoot, html);
            for (Node child : new ArrayList<>(parsedRoot.childNodes)) {
                detachFast(child);
                if (child instanceof Element element && isTag(element, "head") && head.getChildNodes().isEmpty()) {
                    copyAttributes(element, head);
                    moveChildren(element, head);
                    continue;
                }
                if (child instanceof Element element && isTag(element, "body") && body.getChildNodes().isEmpty()) {
                    copyAttributes(element, body);
                    moveChildren(element, body);
                    continue;
                }
                attachChildFast(body, child);
            }
        } else if (isTag(parsedRoot, "body")) {
            copyAttributes(parsedRoot, body);
            moveChildren(parsedRoot, body);
        } else {
            attachChildFast(body, parsedRoot);
        }

        attachChildFast(html, head);
        attachChildFast(html, body);

        Element.init(head);
        Element.init(body);
        Element.init(html);
        return new DocumentRoot(html, head, body);
    }

    private static void copyAttributes(Element source, Element target) {
        if (source == null || target == null) return;
        target.getAttributes().putAll(source.getAttributes());
    }

    private static void moveChildren(Element source, Element target) {
        if (source == null || target == null) return;
        for (Node child : new ArrayList<>(source.childNodes)) {
            detachFast(child);
            attachChildFast(target, child);
        }
    }

    private static void detachFast(Node child) {
        if (child == null || child.parentNode == null) return;
        Node parent = child.parentNode;
        parent.childNodes.remove(child);
        if (parent instanceof Element parentElement && child instanceof Element childElement) {
            parentElement.children.remove(childElement);
            childElement.parentElement = null;
        }
        child.parentNode = null;
    }

    private static boolean isTag(Element element, String tagName) {
        return element != null && tagName.equalsIgnoreCase(element.tagName);
    }

    public record DocumentRoot(com.sighs.apricityui.element.Html documentElement,
                               com.sighs.apricityui.element.Head head,
                               com.sighs.apricityui.element.Body body) {
    }
}

package com.sighs.apricityui.parser;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Head;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.dom.CommentNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.util.AuiLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    private static final Map<String, TemplateBlueprint> BLUEPRINTS = new HashMap<>();

    public static void putTemple(String path, String html) {
        if (path == null || path.isBlank()) {
            ApricityUI.LOGGER.warn("[AUI HTML] ignored template with an empty path");
            return;
        }
        if (html == null) {
            ApricityUI.LOGGER.warn("[AUI HTML] template content is null path={}", path);
        }
        if (temples.containsKey(path)) {
            ApricityUI.LOGGER.debug("[AUI HTML] template overridden by a later resource path={}", path);
        }
        temples.put(path, html);
        BLUEPRINTS.remove(path);
    }

    public static String getTemple(String path) {
        return temples.get(path);
    }

    public static String findMetaContent(String path, String name) {
        TemplateBlueprint blueprint = BLUEPRINTS.get(path);
        if (blueprint != null && name != null) {
            return blueprint.metaContents.get(name.trim().toLowerCase(Locale.ROOT));
        }
        return findMetaContentInMarkup(getTemple(path), name);
    }

    public static void scan() {
        BLUEPRINTS.clear();
        new ClientLoader("html").loadResources(HTML::putTemple);
    }

    /** Prepares immutable parse blueprints so document creation only instantiates mutable nodes. */
    public static int prepareTemplates() {
        int prepared = 0;
        for (Map.Entry<String, String> entry : new ArrayList<>(temples.entrySet())) {
            try {
                TemplateBlueprint blueprint = prepareTemplate(entry.getKey(), entry.getValue());
                if (blueprint != null) {
                    BLUEPRINTS.put(entry.getKey(), blueprint);
                    prepared++;
                }
            } catch (RuntimeException exception) {
                BLUEPRINTS.remove(entry.getKey());
                ApricityUI.LOGGER.warn(
                        "[AUI HTML] template warm-up failed; create will retry path={}",
                        AuiLog.source(entry.getKey()),
                        exception
                );
            }
        }
        return prepared;
    }

    static boolean prepareTemplatePath(String path) {
        String source = temples.get(path);
        TemplateBlueprint blueprint = prepareTemplate(path, source);
        if (blueprint == null) {
            BLUEPRINTS.remove(path);
            return false;
        }
        BLUEPRINTS.put(path, blueprint);
        return true;
    }

    static boolean isTemplatePrepared(String path) {
        return BLUEPRINTS.containsKey(path);
    }

    public static List<TemplateResources> preparedTemplateResources() {
        ArrayList<TemplateResources> resources = new ArrayList<>(BLUEPRINTS.size());
        for (TemplateBlueprint blueprint : BLUEPRINTS.values()) {
            resources.add(new TemplateResources(
                    blueprint.path,
                    blueprint.externalStyleSrcs,
                    blueprint.inlineStyles
            ));
        }
        return List.copyOf(resources);
    }

    public static void invalidatePreparedTemplates(Collection<String> paths) {
        if (paths == null) {
            BLUEPRINTS.clear();
            return;
        }
        for (String path : paths) {
            if (path != null) BLUEPRINTS.remove(path);
        }
    }

    /** Refreshes one resource template without rescanning or rebuilding other documents. */
    public static boolean reload(String path) {
        if (path == null || path.isBlank()) return false;
        try (InputStream stream = ClientLoader.getResourceStream(path)) {
            if (stream == null) return false;
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            putTemple(path, source);
            TemplateBlueprint blueprint = prepareTemplate(path, source);
            if (blueprint != null) BLUEPRINTS.put(path, blueprint);
            return true;
        } catch (IOException | RuntimeException exception) {
            ApricityUI.LOGGER.warn("[AUI HTML] failed to reload template path={}", AuiLog.source(path), exception);
            return false;
        }
    }

    public static DocumentRoot create(Document document, String path) {
        String rawHtml = getTemple(path);
        if (rawHtml == null) {
            ApricityUI.LOGGER.error("[AUI HTML] template resource is missing path={}", AuiLog.source(path));
            return null;
        }
        if (rawHtml.isBlank()) {
            ApricityUI.LOGGER.error("[AUI HTML] template resource is empty path={}", AuiLog.source(path));
            return null;
        }

        try {
            TemplateBlueprint blueprint = BLUEPRINTS.get(path);
            if (blueprint == null) {
                blueprint = prepareTemplate(path, rawHtml);
                if (blueprint != null) BLUEPRINTS.put(path, blueprint);
            }
            if (blueprint == null) return null;
            ResourceUsageIndex.recordCss(path, blueprint.externalStyleSrcs);
            StyleAsyncHandler.INSTANCE.attach(
                    document,
                    path,
                    blueprint.externalStyleSrcs,
                    blueprint.inlineStyles
            );
            ResourceUsageIndex.recordJs(path, blueprint.externalScriptSrcs);
            document.JSCache.addAll(blueprint.scripts);
            return buildDocument(document, blueprint.tokens, path);
        } catch (RuntimeException exception) {
            ApricityUI.LOGGER.error("[AUI HTML] document parse pipeline failed path={}", AuiLog.source(path), exception);
            throw exception;
        }
    }

    public static Element createElement(Document document, String html) {
        DocumentRoot root = buildDocument(document, "<body>" + (html == null ? "" : html) + "</body>", "<fragment>");
        if (root == null || root.body() == null) {
            ApricityUI.LOGGER.warn("[AUI HTML] createElement produced no body markup={}", AuiLog.compact(html));
            return null;
        }
        for (Node child : root.body().getChildNodes()) {
            if (child instanceof Element element) return element;
        }
        ApricityUI.LOGGER.warn("[AUI HTML] createElement produced no element markup={}", AuiLog.compact(html));
        return null;
    }

    private static TemplateBlueprint prepareTemplate(String path, String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) return null;
        CSS.Extractor cssExtractor = new CSS.Extractor(path);
        String htmlAfterCss = cssExtractor.handle(rawHtml);
        JS.Extractor jsExtractor = new JS.Extractor(path);
        String cleanHtml = normalizeDocumentMarkup(jsExtractor.handle(htmlAfterCss));
        ResourceUsageIndex.recordCss(path, cssExtractor.sourceSnapshot());
        ResourceUsageIndex.recordJs(path, jsExtractor.sourceSnapshot());
        List<Token> tokens = freezeTokens(HtmlTokenizer.tokenize(cleanHtml, path));
        if (tokens.isEmpty()) return null;
        return new TemplateBlueprint(
                path,
                tokens,
                cssExtractor.sourceSnapshot(),
                cssExtractor.contentSnapshot(),
                jsExtractor.sourceSnapshot(),
                jsExtractor.loadScripts(),
                extractMetaContents(rawHtml)
        );
    }

    private static List<Token> freezeTokens(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) return List.of();
        for (Token token : tokens) {
            if (token != null && token.attributes != null && !token.attributes.isEmpty()) {
                token.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(token.attributes));
            }
        }
        return List.copyOf(tokens);
    }

    private static Map<String, String> extractMetaContents(String html) {
        if (html == null || html.isBlank()) return Map.of();
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        Matcher matcher = META_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            String attrText = matcher.group(1);
            String name = findAttrValue(attrText, "name");
            if (name == null || name.isBlank()) continue;
            result.put(name.trim().toLowerCase(Locale.ROOT), findAttrValue(attrText, "content"));
        }
        return Collections.unmodifiableMap(result);
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

    public static String findAttrValue(String attrText, String attrName) {
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

    /** 统计正则在字符串中的匹配次数。 */
    public static int countMatches(Pattern pattern, String value) {
        int count = 0;
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) count++;
        return count;
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
                "link", "meta", "param", "source", "texture", "track", "wbr"
        );

        private static final Pattern TOKEN_PATTERN =
                Pattern.compile("<!--.*?-->|</?[^>]+>|[^<]+", Pattern.DOTALL);
        private static final Pattern TAG_NAME_PATTERN = Pattern.compile("^([\\w-]+)");

        private static final Pattern ATTR_PATTERN =
                Pattern.compile("([\\w-]+)(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\"'>]+))?");

        static List<Token> tokenize(String html) {
            return tokenize(html, "<unknown>");
        }

        static List<Token> tokenize(String html, String contextPath) {
            List<Token> tokens = new ArrayList<>();

            Matcher matcher = TOKEN_PATTERN.matcher(html);
            int cursor = 0;

            while (matcher.find()) {
                if (matcher.start() > cursor) {
                    String gap = html.substring(cursor, matcher.start());
                    if (looksLikeMalformedMarkup(gap)) {
                        ApricityUI.LOGGER.warn(
                                "[AUI HTML] malformed markup was skipped path={} fragment={}",
                                AuiLog.source(contextPath),
                                AuiLog.compact(gap)
                        );
                    }
                }
                String part = matcher.group();
                cursor = matcher.end();

                // 注释
                if (part.startsWith("<!--")) {
                    tokens.add(Token.comment(part.length() >= 7 ? part.substring(4, part.length() - 3) : ""));
                    continue;
                }

                // 结束标签
                if (part.startsWith("</")) {
                    String name = part.substring(2, part.length() - 1).trim();
                    if (name.isBlank()) {
                        ApricityUI.LOGGER.warn(
                                "[AUI HTML] empty closing tag path={} fragment={}",
                                AuiLog.source(contextPath),
                                AuiLog.compact(part)
                        );
                    } else {
                        tokens.add(Token.end(name));
                    }
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
                            parseAttributes(attrSection, token.attributes, contextPath);
                        }

                        tokens.add(token);
                    } else {
                        ApricityUI.LOGGER.warn(
                                "[AUI HTML] tag has no valid name path={} fragment={}",
                                AuiLog.source(contextPath),
                                AuiLog.compact(part)
                        );
                    }
                    continue;
                }

                if (part.isBlank()) continue;

                tokens.add(Token.text(part));
            }

            if (cursor < html.length()) {
                String tail = html.substring(cursor);
                if (looksLikeMalformedMarkup(tail)) {
                    ApricityUI.LOGGER.warn(
                            "[AUI HTML] unterminated markup was skipped path={} fragment={}",
                            AuiLog.source(contextPath),
                            AuiLog.compact(tail)
                    );
                }
            }

            return tokens;
        }

        private static boolean looksLikeMalformedMarkup(String fragment) {
            if (fragment == null || fragment.isEmpty()) return false;
            for (int index = 0; index < fragment.length() - 1; index++) {
                if (fragment.charAt(index) != '<') continue;
                char next = fragment.charAt(index + 1);
                if (next == '/' || next == '!' || next == '?' || Character.isLetter(next)) return true;
            }
            return false;
        }

        private static boolean isVoidTag(String tagName) {
            if (tagName == null || tagName.isBlank()) return false;
            return VOID_TAGS.contains(tagName.toLowerCase(Locale.ROOT));
        }

        private static void parseAttributes(String src, Map<String, String> out, String contextPath) {
            Matcher matcher = ATTR_PATTERN.matcher(src);
            int cursor = 0;
            while (matcher.find()) {
                if (matcher.start() > cursor && !src.substring(cursor, matcher.start()).isBlank()) {
                    ApricityUI.LOGGER.warn(
                            "[AUI HTML] malformed attribute fragment path={} fragment={}",
                            AuiLog.source(contextPath),
                            AuiLog.compact(src.substring(cursor, matcher.start()))
                    );
                }
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
                cursor = matcher.end();
            }
            if (cursor < src.length() && !src.substring(cursor).isBlank()) {
                ApricityUI.LOGGER.warn(
                        "[AUI HTML] trailing malformed attributes path={} fragment={}",
                        AuiLog.source(contextPath),
                        AuiLog.compact(src.substring(cursor))
                );
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

    private static DocumentRoot buildDocument(Document document, String html, String contextPath) {
        if (document == null) {
            ApricityUI.LOGGER.error("[AUI HTML] cannot build document without owner path={}", AuiLog.source(contextPath));
            return null;
        }
        List<Token> tokens = HtmlTokenizer.tokenize(html, contextPath);
        return buildDocument(document, tokens, contextPath);
    }

    private static DocumentRoot buildDocument(Document document, List<Token> tokens, String contextPath) {
        if (document == null) {
            ApricityUI.LOGGER.error("[AUI HTML] cannot build document without owner path={}", AuiLog.source(contextPath));
            return null;
        }
        if (tokens.isEmpty()) {
            ApricityUI.LOGGER.error(
                    "[AUI HTML] tokenizer produced no tokens path={}",
                    AuiLog.source(contextPath)
            );
            return null;
        }

        Deque<Element> stack = new ArrayDeque<>();
        Element parsedRoot = null;
        int tokenIndex = 0;

        for (Token token : tokens) {
            tokenIndex++;
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
                    if (stack.isEmpty()) {
                        ApricityUI.LOGGER.warn(
                                "[AUI HTML] closing tag has no open element path={} token={} tag={}",
                                AuiLog.source(contextPath),
                                tokenIndex,
                                token.tagName
                        );
                        continue;
                    }
                    if (!containsTag(stack, token.tagName)) {
                        ApricityUI.LOGGER.warn(
                                "[AUI HTML] unmatched closing tag path={} token={} tag={} openTop={}",
                                AuiLog.source(contextPath),
                                tokenIndex,
                                token.tagName,
                                stack.peek().tagName
                        );
                        continue;
                    }
                    if (!isTag(stack.peek(), token.tagName)) {
                        ApricityUI.LOGGER.warn(
                                "[AUI HTML] mismatched nesting recovered path={} token={} closing={} openTop={}",
                                AuiLog.source(contextPath),
                                tokenIndex,
                                token.tagName,
                                stack.peek().tagName
                        );
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
        if (!stack.isEmpty()) {
            ArrayList<String> unclosed = new ArrayList<>();
            for (Element element : stack) {
                if (element != null && element.tagName != null) unclosed.add(element.tagName);
            }
            ApricityUI.LOGGER.warn(
                    "[AUI HTML] unclosed tags were implicitly closed path={} tags={}",
                    AuiLog.source(contextPath),
                    String.join(",", unclosed)
            );
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
        DocumentRoot root = toDocumentRoot(document, parsedRoot);
        if (root == null) {
            ApricityUI.LOGGER.error("[AUI HTML] parsed tree has no document root path={}", AuiLog.source(contextPath));
        }
        return root;
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

    public record TemplateResources(String path, List<String> externalStyleSrcs, List<String> inlineStyles) {
    }

    private record TemplateBlueprint(
            String path,
            List<Token> tokens,
            List<String> externalStyleSrcs,
            List<String> inlineStyles,
            List<String> externalScriptSrcs,
            List<String> scripts,
            Map<String, String> metaContents
    ) {
    }
}

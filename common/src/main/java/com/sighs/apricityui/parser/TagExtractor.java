package com.sighs.apricityui.parser;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.util.AuiLog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 HTML 中提取 {@code <tag>} 块（{@code <style>}/{@code <script>}）的公共骨架：
 * 统计开闭标签、移除标签、缓存 src 与内联内容，由子类决定如何应用进文档。
 * CSS/JS 各自的 Extractor 收拢于此。
 */
abstract class TagExtractor {
    protected final String contextPath;
    protected final List<String> cachedSrcs = new ArrayList<>();
    protected final List<String> cachedContents = new ArrayList<>();
    private final Pattern openMarker;
    private final Pattern closeMarker;
    private final String logPrefix; // "CSS"/"JS"
    private final String tagName;   // "style"/"script"

    TagExtractor(String contextPath, Pattern openMarker, Pattern closeMarker, String logPrefix, String tagName) {
        this.contextPath = contextPath;
        this.openMarker = openMarker;
        this.closeMarker = closeMarker;
        this.logPrefix = logPrefix;
        this.tagName = tagName;
    }

    public String handle(String html) {
        if (html == null || html.isEmpty()) return html;
        /* 计数也走遮罩：注释与 script/style 正文里的字面标签不算（浏览器里那些不是标记） */
        int openCount = HTML.countMatchesOnMaskedMarkup(html, openMarker);
        int closeCount = HTML.countMatchesOnMaskedMarkup(html, closeMarker);
        if (openCount != closeCount) {
            ApricityUI.LOGGER.warn(
                    "[AUI {}] unmatched {} tag path={} openTags={} closeTags={}",
                    logPrefix, tagName, AuiLog.source(contextPath), openCount, closeCount
            );
        }
        return extract(html);
    }

    /**
     * 移除所有匹配的标签，逐个回调 onTag。
     *
     * <p>匹配跑在 {@link HTML#maskForTagScan(String)} 的遮罩文本上（注释与 raw text 正文不算标记），
     * 回调拿到的是**原文**里的属性/正文片段，所以注释与真实内容都不会被遮罩吃掉。</p>
     */
    protected String removeTags(String html, Pattern tagPattern) {
        Matcher matcher = HTML.matcherOnMaskedMarkup(html, tagPattern);
        StringBuilder sb = null;
        int cursor = 0;
        while (matcher.find()) {
            onTag(
                    html.substring(matcher.start(1), matcher.end(1)),
                    html.substring(matcher.start(2), matcher.end(2))
            );
            if (sb == null) sb = new StringBuilder(html.length());
            sb.append(html, cursor, matcher.start());
            cursor = matcher.end();
        }
        if (sb == null) return html;
        sb.append(html, cursor, html.length());
        return sb.toString();
    }

    protected abstract String extract(String html);

    protected abstract void onTag(String attrText, String inner);

    public List<String> sourceSnapshot() {
        return List.copyOf(cachedSrcs);
    }

    public List<String> contentSnapshot() {
        return List.copyOf(cachedContents);
    }

    public abstract void pushToDocument(Document document);
}

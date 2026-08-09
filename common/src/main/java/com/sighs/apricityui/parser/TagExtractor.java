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
    private final Pattern openMarker;
    private final Pattern closeMarker;
    private final String logPrefix; // "CSS"/"JS"
    private final String tagName;   // "style"/"script"

    protected final String contextPath;
    protected final List<String> cachedSrcs = new ArrayList<>();
    protected final List<String> cachedContents = new ArrayList<>();

    TagExtractor(String contextPath, Pattern openMarker, Pattern closeMarker, String logPrefix, String tagName) {
        this.contextPath = contextPath;
        this.openMarker = openMarker;
        this.closeMarker = closeMarker;
        this.logPrefix = logPrefix;
        this.tagName = tagName;
    }

    public String handle(String html) {
        if (html == null || html.isEmpty()) return html;
        int openCount = HTML.countMatches(openMarker, html);
        int closeCount = HTML.countMatches(closeMarker, html);
        if (openCount != closeCount) {
            ApricityUI.LOGGER.warn(
                    "[AUI {}] unmatched {} tag path={} openTags={} closeTags={}",
                    logPrefix, tagName, AuiLog.source(contextPath), openCount, closeCount
            );
        }
        return extract(html);
    }

    /** 用 tagPattern 移除所有匹配的标签，逐个回调 onTag。 */
    protected String removeTags(String html, Pattern tagPattern) {
        Matcher matcher = tagPattern.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            onTag(matcher.group(1), matcher.group(2));
            matcher.appendReplacement(sb, "");
        }
        matcher.appendTail(sb);
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

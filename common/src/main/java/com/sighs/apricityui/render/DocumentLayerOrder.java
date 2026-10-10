package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.style.Transform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Orders top-level documents by the transforms applied to their root chain. */
public final class DocumentLayerOrder {
    private DocumentLayerOrder() {
    }

    public static List<Document> backToFront(Collection<Document> documents) {
        List<Document> ordered = copyNonNull(documents);
        if (ordered.size() < 2) return ordered;
        // 先预取每份文档的 translateZ 再排序：比较器以前每次比较都会读取 computed style
        // 并解析 transform（O(N log N) 次解析与分配），预取后只解析 O(N) 次。List.sort 稳定，
        // 等 Z 的文档保持传入顺序（即注册顺序）不变。
        Map<Document, Double> translateZByDocument = new IdentityHashMap<>(ordered.size() * 2);
        for (Document document : ordered) {
            translateZByDocument.put(document, translateZ(document));
        }
        ordered.sort(Comparator.comparingDouble(translateZByDocument::get));
        return ordered;
    }

    public static List<Document> frontToBack(Collection<Document> documents) {
        List<Document> ordered = backToFront(documents);
        Collections.reverse(ordered);
        return ordered;
    }

    /**
     * Returns whether a persistent screen overlay that is rendered above the excluded
     * document intercepts the pointer at the supplied screen position.
     */
    public static boolean hasPersistentScreenDocumentAt(Collection<Document> documents,
                                                        Document excludedDocument,
                                                        Position screenPosition) {
        if (documents == null || screenPosition == null) return false;
        for (Document document : frontToBack(documents)) {
            if (document == excludedDocument
                    || document.inWorld
                    || document.isManuallyRendered()
                    || !document.isReloadPersistent()) {
                continue;
            }
            if (document.interceptsMouseEventsAt(screenPosition)) return true;
        }
        return false;
    }

    static double translateZ(Document document) {
        if (document == null) return 0.0d;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            return elementTranslateZ(document.documentElement) + elementTranslateZ(document.body);
        }
    }

    private static double elementTranslateZ(Element element) {
        if (element == null) return 0.0d;
        double value = Transform.getTranslateZ(element.getComputedStyle().transform);
        return Double.isFinite(value) ? value : 0.0d;
    }

    private static List<Document> copyNonNull(Collection<Document> documents) {
        List<Document> result = new ArrayList<>();
        if (documents == null) return result;
        for (Document document : documents) {
            if (document != null) result.add(document);
        }
        return result;
    }
}

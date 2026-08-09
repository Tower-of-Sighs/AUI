package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.style.Transform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Orders top-level documents by the transforms applied to their root chain. */
public final class DocumentLayerOrder {
    private DocumentLayerOrder() {
    }

    public static List<Document> backToFront(Collection<Document> documents) {
        List<Document> ordered = copyNonNull(documents);
        ordered.sort(Comparator.comparingDouble(DocumentLayerOrder::translateZ));
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

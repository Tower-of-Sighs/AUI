package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;

/** Optional screen-owned cache for a document's pixel output. */
@FunctionalInterface
public interface DocumentPaintCache {
    java.util.Map<Document, DocumentPaintCache> REGISTRY = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Runs the paint action when required, then presents the retained result. */
    boolean render(Document document, PoseStack poseStack, Runnable paint);

    static void register(Document document, DocumentPaintCache cache) {
        if (document == null || cache == null) return;
        REGISTRY.put(document, cache);
    }

    static void unregister(Document document, DocumentPaintCache cache) {
        if (document == null || cache == null) return;
        REGISTRY.remove(document, cache);
    }

    static DocumentPaintCache forDocument(Document document) {
        return REGISTRY.get(document);
    }
}

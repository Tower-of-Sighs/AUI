package com.sighs.apricityui.dom;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.parser.HTML;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import com.sighs.apricityui.init.Document;

/**
 * Document 的静态注册表与上下文线程变量。从 Document 拆出，
 * Document 保留同名静态方法作为薄封装，外部 138 处调用点无需改动。
 */
public final class DocumentRegistry {
    private static final List<Document> documents = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Document> contextDocument = new ThreadLocal<>();
    private static final Object createTimingLock = new Object();
    private static final Set<String> createdPaths = new HashSet<>();
    private static boolean createdAnyDocument;

    private DocumentRegistry() {
    }

    public static Document getContext() {
        return contextDocument.get();
    }

    public static void setContext(Document document) {
        if (document == null) contextDocument.remove();
        else contextDocument.set(document);
    }

    public static void refreshAll() {
        for (Document document : documents) {
            if (document == null || document.isReloadPersistent() || document.isDisposed()) continue;
            document.refresh();
        }
    }

    public static void resetCreateTimingState() {
        synchronized (createTimingLock) {
            createdPaths.clear();
            createdAnyDocument = false;
        }
    }

    // 这俩是创建UI用的，如果refresh放在构造函数里，那创建时就不会执行内嵌js，所以挪到了这里。
    public static Document create(String path) {
        if (HTML.getTemple(path) == null) {
            ApricityUI.LOGGER.error("[AUI Document] cannot create document: template is missing path={}", path);
            return null;
        }
        CreateTiming timing = beginCreateTiming(path);
        Document document = new Document(path, false);
        documents.add(document);
        try {
            document.applyViewport(false);
            document.refresh();
            document.applyViewport(false);
            return document;
        } catch (RuntimeException | LinkageError failure) {
            document.remove();
            throw failure;
        } finally {
            logCreateTiming(path, timing);
        }
    }

    public static Document createInWorld(String path) {
        if (HTML.getTemple(path) == null) {
            ApricityUI.LOGGER.error("[AUI Document] cannot create world document: template is missing path={}", path);
            return null;
        }
        CreateTiming timing = beginCreateTiming(path);
        try {
            Document document = new Document(path, true);
            documents.add(document);
            // World documents use the same viewport contract as screen documents.
            // Their world transform is applied by WorldWindow, not by layout.
            document.applyViewport(false);
            document.refresh();
            return document;
        } finally {
            logCreateTiming(path, timing);
        }
    }

    private static CreateTiming beginCreateTiming(String path) {
        synchronized (createTimingLock) {
            boolean firstGlobal = !createdAnyDocument;
            boolean firstPath = createdPaths.add(path == null ? "" : path);
            createdAnyDocument = true;
            return new CreateTiming(System.nanoTime(), firstGlobal, firstPath);
        }
    }

    private static void logCreateTiming(String path, CreateTiming timing) {
        if (timing == null) return;
        long totalMs = (System.nanoTime() - timing.startedNs) / 1_000_000L;
        if (!timing.firstGlobal && !timing.firstPath && totalMs < 50L) return;
        ApricityUI.LOGGER.info(
                "[AUI Document] create timing path={} total={}ms firstGlobal={} firstPath={}",
                path,
                totalMs,
                timing.firstGlobal,
                timing.firstPath
        );
    }

    private record CreateTiming(long startedNs, boolean firstGlobal, boolean firstPath) {
    }

    public static ArrayList<Document> get(String path) {
        ArrayList<Document> result = new ArrayList<>();
        for (Document document : documents) {
            if (!document.isDisposed() && document.getPath().equals(path)) result.add(document);
        }
        return result;
    }

    public static Document getByUUID(String uuid) {
        for (Document document : documents) {
            if (!document.isDisposed() && document.getUuid().toString().equals(uuid)) return document;
        }
        return null;
    }

    public static List<Document> getAll() {
        return documents;
    }

    public static void remove(String path) {
        documents.removeIf(document -> {
            if (!document.is(path)) return false;
            document.disposeLifecycle();
            return true;
        });
    }

    public static void remove(UUID uuid) {
        documents.removeIf(document -> {
            if (!document.is(uuid)) return false;
            document.disposeLifecycle();
            return true;
        });
    }

    public static void applyViewportForPath(String path, boolean relayout) {
        for (Document document : documents) {
            if (document == null || document.inWorld || document.isDisposed() || !document.is(path)) continue;
            document.applyViewport(relayout);
        }
    }
}

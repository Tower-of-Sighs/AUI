package com.sighs.apricityui.dom;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.sighs.apricityui.init.Document;

/**
 * Document 的 MutationObserver 注册表（创建/入队/刷新/清空）。
 * 从 Document 拆出；Document 保留同名公开方法作为薄封装。
 */
public final class MutationObserverManager {
    private final Document document;
    private final CopyOnWriteArrayList<Document.MutationObserver> observers = new CopyOnWriteArrayList<>();

    public MutationObserverManager(Document document) {
        this.document = document;
    }

    public Document.MutationObserver create(Consumer<Object> callback) {
        Document.MutationObserver observer = new Document.MutationObserver(document, callback);
        if (document.isActive()) {
            observers.add(observer);
        } else {
            observer.disconnect();
        }
        return observer;
    }

    public void remove(Document.MutationObserver observer) {
        observers.remove(observer);
    }

    public void queue(Document.MutationRecord record) {
        if (record == null || !document.isActive()) return;
        if (record.target != null) {
            record.target.invalidateSubtreeMutationVersion();
        }
        for (Document.MutationObserver observer : observers) {
            if (observer != null) observer.enqueue(record);
        }
    }

    public void flush() {
        if (!document.isActive()) return;
        for (Document.MutationObserver observer : observers) {
            if (observer == null) continue;
            observer.flush();
            if (observer.disconnected) {
                observers.remove(observer);
            }
        }
    }

    public void clearAll() {
        for (Document.MutationObserver observer : observers) {
            if (observer != null) observer.disconnect();
        }
        observers.clear();
    }
}

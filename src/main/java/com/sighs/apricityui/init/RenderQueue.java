package com.sighs.apricityui.init;

import com.sighs.apricityui.render.RenderNode;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class RenderQueue {
    private final Document owner;
    private final Set<Element> dirtyElements = ConcurrentHashMap.newKeySet();
    private ArrayList<RenderNode> paintList = new ArrayList<>();
    private int globalDirtyMask = 0;

    RenderQueue(Document owner) {
        this.owner = owner;
    }

    ArrayList<RenderNode> getPaintList() {
        return paintList;
    }

    Set<Element> getDirtyElements() {
        return dirtyElements;
    }

    void reset() {
        dirtyElements.clear();
        globalDirtyMask = 0;
        paintList = new ArrayList<>();
    }

    void rebuildPaintList() {
        if (owner.documentElement == null) {
            paintList = new ArrayList<>();
            return;
        }
        paintList = Drawer.createPaintList(owner.documentElement);
    }

    void tickElements() {
        for (Element element : new ArrayList<>(owner.getElements())) {
            element.tick();
        }
    }

    void commit() {
        applyGlobalDirty();
        Drawer.flushUpdates(owner);
    }

    void markDirty(int mask) {
        if (mask == 0) return;
        globalDirtyMask |= mask;
    }

    void markDirty(Element element, int mask) {
        if (element == null) return;
        if (!element.isConnected()) return;
        element.addDirtyFlags(mask);
        dirtyElements.add(element);
    }

    boolean hasPendingWork() {
        return globalDirtyMask != 0 || !dirtyElements.isEmpty();
    }

    int getGlobalDirtyMask() {
        return globalDirtyMask;
    }

    private void applyGlobalDirty() {
        int mask = globalDirtyMask;
        if (mask == 0) return;
        globalDirtyMask = 0;
        ArrayList<Element> snapshot = new ArrayList<>(owner.getElements());
        for (Element element : snapshot) {
            if (element == null || !element.isConnected()) continue;
            element.addDirtyFlags(mask);
            dirtyElements.add(element);
        }
    }
}

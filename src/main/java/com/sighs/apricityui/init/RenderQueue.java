package com.sighs.apricityui.init;

import com.sighs.apricityui.render.RenderNode;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class RenderQueue {
    private final Document owner;
    private final Set<Element> dirtyElements = ConcurrentHashMap.newKeySet();
    private ArrayList<RenderNode> paintList = new ArrayList<>();

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
        paintList = new ArrayList<>();
    }

    void rebuildPaintList() {
        if (owner.body == null) {
            paintList = new ArrayList<>();
            return;
        }
        paintList = Drawer.createPaintList(owner.body);
    }

    void tickElements() {
        for (Element element : owner.getElements()) {
            element.tick();
        }
    }

    void commit() {
        Drawer.flushUpdates(owner);
    }

    void markDirty(int mask) {
        owner.getElements().forEach(element -> element.addDirtyFlags(mask));
        dirtyElements.addAll(owner.getElements());
    }

    void markDirty(Element element, int mask) {
        if (element == null) return;
        element.addDirtyFlags(mask);
        dirtyElements.add(element);
    }
}

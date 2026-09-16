package com.sighs.apricityui.render;

import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.render.LayoutCommit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

public final class RenderQueue {
    private static final int VISUAL_DIRTY_MASK =
            Drawer.REPAINT
                    | Drawer.REORDER
                    | Drawer.RELAYOUT
                    | Drawer.COMMIT_LAYOUT;

    private final Document owner;
    private final Set<Element> dirtyElements = ConcurrentHashMap.newKeySet();
    private final Set<Element> hitTestDirtyRoots = Collections.newSetFromMap(new IdentityHashMap<>());
    private final HitTestCache hitTestCache;
    private ArrayList<RenderNode> paintList = new ArrayList<>();
    private int globalDirtyMask = 0;
    private boolean layoutCommitDirty = false;
    private volatile long visualVersion = 1L;
    // Roots collected by commit(boolean) when the pending geometry work is made of
    // COMMIT_LAYOUT changes only (transform/visual geometry, no relayout). null means
    // the batch needs the full LayoutCommit.commit(document) path; an empty set means
    // the geometry part of the batch turned out to be a no-op. Consumed by
    // Base.drawDocumentInContext through Document.drainStyleTransformRoots().
    private Set<Element> styleTransformRoots = null;

    public RenderQueue(Document owner) {
        this.owner = owner;
        this.hitTestCache = new HitTestCache(owner);
    }

    public ArrayList<RenderNode> getPaintList() {
        return paintList;
    }

    public long getVisualVersion() {
        return visualVersion;
    }

    public Set<Element> getDirtyElements() {
        return dirtyElements;
    }

    public void reset() {
        markVisualDirty();
        dirtyElements.clear();
        hitTestDirtyRoots.clear();
        globalDirtyMask = 0;
        layoutCommitDirty = false;
        styleTransformRoots = null;
        paintList = new ArrayList<>();
        hitTestCache.clear();
    }

    public void rebuildPaintList() {
        markVisualDirty();
        if (owner.documentElement == null) {
            paintList = new ArrayList<>();
            hitTestCache.clear();
            layoutCommitDirty = false;
            return;
        }
        paintList = Drawer.createPaintList(owner.documentElement);
        layoutCommitDirty = true;
        hitTestCache.markDirty();
    }

    public void tickElements() {
        for (Element element : new ArrayList<>(owner.getElements())) {
            element.tick();
        }
    }

    public void commit() {
        commit(true);
    }

    public boolean commit(boolean commitLayoutNow) {
        boolean hadWork = globalDirtyMask != 0 || !dirtyElements.isEmpty() || layoutCommitDirty;
        boolean hadGlobalDirty = globalDirtyMask != 0;
        boolean globalGeometryDirty = (globalDirtyMask & (Drawer.RELAYOUT | Drawer.COMMIT_LAYOUT)) != 0;
        boolean needsLayoutCommit = layoutCommitDirty || globalGeometryDirty;
        // A relayout ripples through ancestors and following siblings, and a paint
        // list rebuild creates nodes without committed geometry, so both keep the
        // full document commit. A batch made of COMMIT_LAYOUT only changes committed
        // world transforms, which no sibling can observe.
        boolean needsFullLayoutCommit = layoutCommitDirty || globalGeometryDirty;
        Set<Element> transformRoots = null;
        boolean fullHitTestRebuild = hadGlobalDirty || hitTestDirtyRoots.contains(owner.documentElement);
        Set<Element> incrementalHitRoots = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Element element : dirtyElements) {
            if (element == null || !element.isConnected()) continue;
            if (element.hasDirtyFlag(Drawer.REORDER)) {
                fullHitTestRebuild = true;
            }
            if (element.hasDirtyFlag(Drawer.RELAYOUT) || element.hasDirtyFlag(Drawer.COMMIT_LAYOUT)) {
                needsLayoutCommit = true;
            }
            if (element.hasDirtyFlag(Drawer.RELAYOUT)) {
                needsFullLayoutCommit = true;
                incrementalHitRoots.add(element.parentElement == null ? element : element.parentElement);
            } else if (element.hasDirtyFlag(Drawer.COMMIT_LAYOUT)) {
                // The element's own transformVersion changed, which invalidates the
                // committed world transform of this element and of its descendants.
                if (transformRoots == null) {
                    transformRoots = Collections.newSetFromMap(new IdentityHashMap<>());
                }
                transformRoots.add(element);
                incrementalHitRoots.add(element.parentElement == null ? element : element.parentElement);
            } else if (element.hasDirtyFlag(Drawer.HITTEST)) {
                incrementalHitRoots.add(element);
            }
        }
        if (!fullHitTestRebuild) {
            incrementalHitRoots.addAll(hitTestDirtyRoots);
        }
        styleTransformRoots = needsFullLayoutCommit ? null : transformRoots;

        applyGlobalDirty();
        Drawer.flushUpdates(owner);
        if (needsLayoutCommit && commitLayoutNow) {
            LayoutCommit.commit(owner);
        }
        if (hadWork) {
            if (needsLayoutCommit && !commitLayoutNow) {
                // Render-frame style changes commit geometry immediately after
                // this queue flush. Updating hit-test entries here would read
                // the previous committed rects and leave the cache one frame
                // behind the pixels on screen. Layout can also ripple through
                // auto-sized ancestors and following siblings, so defer a full
                // rebuild until the new committed geometry is available.
                hitTestCache.markDirty();
                hitTestDirtyRoots.clear();
            } else {
                if (fullHitTestRebuild) {
                    hitTestCache.markDirty();
                } else if (!incrementalHitRoots.isEmpty()) {
                    hitTestCache.updateSubtrees(paintList, incrementalHitRoots);
                }
                hitTestDirtyRoots.clear();
            }
            layoutCommitDirty = false;
        }
        return needsLayoutCommit;
    }

    /**
     * Roots collected by the last {@link #commit(boolean)} for a batch whose geometry
     * work only consists of COMMIT_LAYOUT changes: those elements had their
     * transformVersion bumped, so their committed world transforms (and those of their
     * descendants) must be refreshed, but nothing asked for a relayout.
     * <p>
     * Returns {@code null} when the batch requires the full
     * {@code LayoutCommit.commit(document)} path (relayout or paint list rebuild), in
     * which case the collected roots are meaningless. An empty set means the pending
     * geometry work turned out to be a no-op.
     */
    public Set<Element> drainStyleTransformRoots() {
        Set<Element> roots = styleTransformRoots;
        styleTransformRoots = null;
        return roots;
    }

    public void markDirty(int mask) {
        if (mask == 0) return;
        if ((mask & VISUAL_DIRTY_MASK) != 0) markVisualDirty();
        globalDirtyMask |= mask;
    }

    public void markDirty(Element element, int mask) {
        if (element == null || mask == 0) return;
        if (!element.isConnected()) return;
        if ((mask & VISUAL_DIRTY_MASK) != 0) markVisualDirty();
        element.addDirtyFlags(mask);
        dirtyElements.add(element);
    }

    public boolean hasPendingWork() {
        return globalDirtyMask != 0 || !dirtyElements.isEmpty() || layoutCommitDirty;
    }

    public boolean hasPendingVisualWork() {
        if (layoutCommitDirty || (globalDirtyMask & VISUAL_DIRTY_MASK) != 0) return true;
        for (Element element : dirtyElements) {
            if (element != null && element.hasDirtyFlag(VISUAL_DIRTY_MASK)) return true;
        }
        return false;
    }

    public int getGlobalDirtyMask() {
        return globalDirtyMask;
    }

    public Element hitTest(com.sighs.apricityui.layout.Position position) {
        return hitTestCache.hitTest(position, paintList);
    }

    public void markHitTestDirty() {
        hitTestCache.markDirty();
        hitTestDirtyRoots.clear();
    }

    public void markHitTestDirty(Element element) {
        if (element == null || !element.isConnected()) return;
        if (hitTestDirtyRoots.contains(owner.documentElement)) return;
        hitTestDirtyRoots.add(element);
    }

    public void updateHitTestSubtrees(Set<Element> roots) {
        Set<Element> combined = Collections.newSetFromMap(new IdentityHashMap<>());
        combined.addAll(hitTestDirtyRoots);
        if (roots != null) combined.addAll(roots);
        hitTestCache.updateSubtrees(paintList, combined);
        hitTestDirtyRoots.clear();
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

    public void markVisualDirty() {
        visualVersion++;
    }
}

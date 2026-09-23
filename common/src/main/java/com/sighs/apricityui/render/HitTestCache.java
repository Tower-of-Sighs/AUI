package com.sighs.apricityui.render;

import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

public final class HitTestCache {
    private final Document owner;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private boolean dirty = true;

    public HitTestCache(Document owner) {
        this.owner = owner;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clear() {
        entries.clear();
        dirty = true;
    }

    // clip 栈 + 有效裁剪结果的记忆：动画驱动的逐帧 rebuild 中，连续兄弟元素的
    // clip 栈几乎总是一样的，原实现却逐元素重新走栈、每层 new Bounds + intersection
    // （JFR 里 HitTestCache$Bounds 归因约 42MB）。栈 push/pop 时版本号递增，
    // 版本不变直接复用上次结果。
    private static final class ClipContext {
        final ArrayDeque<Element> stack = new ArrayDeque<>();
        long version = 0;
        long computedVersion = -1;
        Bounds result = null;

        void push(Element element) {
            stack.push(element);
            version++;
        }

        void pop() {
            stack.pop();
            version++;
        }

        boolean isEmpty() {
            return stack.isEmpty();
        }

        Element peek() {
            return stack.peek();
        }
    }

    public void rebuild(List<RenderNode> paintOrder) {
        entries.clear();
        dirty = false;
        if (owner == null || owner.body == null || paintOrder == null || paintOrder.isEmpty()) return;

        ClipContext clipContext = new ClipContext();
        Map<Element, Bounds> boundsCache = new IdentityHashMap<>();
        Set<Element> seenElements = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = paintOrder.size() - 1; i >= 0; i--) {
            RenderNode node = paintOrder.get(i);
            if (node instanceof RenderNode.ScrollbarNode scrollbarNode) {
                appendScrollbarEntries(scrollbarNode.target(), clipContext, boundsCache, entries);
                continue;
            }
            if (node instanceof RenderNode.MaskPopNode popNode) {
                Element target = popNode.target();
                if (target != null) {
                    clipContext.push(target);
                }
                continue;
            }
            if (node instanceof RenderNode.MaskPushNode pushNode) {
                if (!clipContext.isEmpty() && clipContext.peek() == pushNode.target()) {
                    clipContext.pop();
                }
                continue;
            }
            if (!(node instanceof RenderNode.ElementPhaseNode phaseNode)) continue;
            Element element = phaseNode.target();
            if (element == null || element.document != owner) continue;
            if (!seenElements.add(element)) continue;
            if (!Interaction.isDisplayed(element) || !element.isVisible || !element.isPointerEnabled) continue;

            Bounds bounds = resolveCommittedBounds(element, boundsCache);
            if (!bounds.isValid()) continue;
            Bounds clip = resolveCommittedClipBounds(clipContext, boundsCache);
            if (clip != null && clip.isEmpty()) continue;
            entries.add(new Entry(element, bounds, clip));
        }
    }

    public void updateSubtrees(List<RenderNode> paintOrder, Set<Element> dirtyRoots) {
        if (dirty || dirtyRoots == null || dirtyRoots.isEmpty()) {
            if (dirty) rebuild(paintOrder);
            return;
        }
        if (paintOrder == null || paintOrder.isEmpty()) {
            clear();
            return;
        }

        List<Element> roots = minimizeRoots(dirtyRoots);
        if (roots.isEmpty()) return;
        removeEntriesForRoots(roots);

        ClipContext clipContext = new ClipContext();
        Map<Element, Bounds> boundsCache = new IdentityHashMap<>();
        Set<Element> seenElements = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<Entry> rebuilt = new ArrayList<>();
        for (int i = paintOrder.size() - 1; i >= 0; i--) {
            RenderNode node = paintOrder.get(i);
            if (node instanceof RenderNode.ScrollbarNode scrollbarNode) {
                Element target = scrollbarNode.target();
                if (isInAnyRoot(target, roots)) {
                    appendScrollbarEntries(target, clipContext, boundsCache, rebuilt);
                }
                continue;
            }
            if (node instanceof RenderNode.MaskPopNode popNode) {
                Element target = popNode.target();
                if (target != null) {
                    clipContext.push(target);
                }
                continue;
            }
            if (node instanceof RenderNode.MaskPushNode pushNode) {
                if (!clipContext.isEmpty() && clipContext.peek() == pushNode.target()) {
                    clipContext.pop();
                }
                continue;
            }
            if (!(node instanceof RenderNode.ElementPhaseNode phaseNode)) continue;
            Element element = phaseNode.target();
            if (element == null || element.document != owner) continue;
            if (!isInAnyRoot(element, roots)) continue;
            if (!seenElements.add(element)) continue;
            if (!Interaction.isDisplayed(element) || !element.isVisible || !element.isPointerEnabled) continue;

            Bounds bounds = resolveCommittedBounds(element, boundsCache);
            if (!bounds.isValid()) continue;
            Bounds clip = resolveCommittedClipBounds(clipContext, boundsCache);
            if (clip != null && clip.isEmpty()) continue;
            rebuilt.add(new Entry(element, bounds, clip));
        }
        if (rebuilt.isEmpty()) return;

        int insertIndex = entries.size();
        for (int i = 0; i < entries.size(); i++) {
            if (comesBeforeInPaintOrder(rebuilt.get(0).element, entries.get(i).element, paintOrder)) {
                insertIndex = i;
                break;
            }
        }
        entries.addAll(insertIndex, rebuilt);
    }

    public Element hitTest(Position cursorPosition, List<RenderNode> paintOrder) {
        if (cursorPosition == null) return null;
        if (dirty) {
            rebuild(paintOrder);
        }
        for (Entry entry : entries) {
            boolean insideBounds = entry.bounds.contains(cursorPosition);
            boolean insideClip = entry.clip == null || entry.clip.contains(cursorPosition);
            if (!insideBounds) continue;
            if (!insideClip) continue;
            return entry.element;
        }
        return null;
    }

    private static Bounds resolveCommittedBounds(Element element, Map<Element, Bounds> boundsCache) {
        if (element == null) return Bounds.EMPTY;
        Bounds cached = boundsCache.get(element);
        if (cached != null) return cached;

        Rect rect = element.getRenderer().getCommittedRect();
        if (rect == null) return Bounds.EMPTY;
        Bounds bounds;
        if ("IMG".equals(element.tagName)) {
            Position position = rect.getBodyRectPosition();
            Size size = rect.getBodyRectSize();
            bounds = new Bounds(position.x, position.y, size.width(), size.height());
        } else {
            Position position = rect.position;
            Box box = rect.box;
            Size size = rect.getElementSize();
            bounds = new Bounds(
                    position.x + box.getMarginLeft(),
                    position.y + box.getMarginTop(),
                    size.width(),
                    size.height()
            );
        }
        bounds = transformBounds(element, bounds);
        boundsCache.put(element, bounds);
        return bounds;
    }

    private static Bounds transformBounds(Element element, Bounds bounds) {
        if (element == null || bounds == null || !bounds.isValid()) return bounds;
        Matrix4f transform = element.getRenderer().getCommittedWorldTransformIfValid();
        if (transform == null) {
            // A repaint-only style or asynchronous geometry publication can
            // invalidate the committed transform stamp before the next layout
            // commit. Rendering recomputes the same world transform in this
            // window; hit testing must not silently fall back to raw layout
            // coordinates or transformed controls become clickable down-right
            // of their pixels.
            transform = Base.prepareWorldTransform(element);
        }
        if (transform == null) return bounds;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double[] xs = {bounds.x, bounds.x + bounds.width};
        double[] ys = {bounds.y, bounds.y + bounds.height};
        for (double x : xs) {
            for (double y : ys) {
                Vector4f point = transform.transform(new Vector4f((float) x, (float) y, 0.0f, 1.0f));
                double w = Math.abs(point.w) < 0.000001f ? 1.0 : point.w;
                double px = point.x / w;
                double py = point.y / w;
                if (!Double.isFinite(px) || !Double.isFinite(py)) return Bounds.EMPTY;
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
        }
        return new Bounds(minX, minY, Math.max(0.0, maxX - minX), Math.max(0.0, maxY - minY));
    }

    private static Bounds resolveCommittedClipBounds(ClipContext clipContext, Map<Element, Bounds> boundsCache) {
        if (clipContext.isEmpty()) return null;
        if (clipContext.computedVersion == clipContext.version) return clipContext.result;
        Bounds effective = null;
        for (Element clip : clipContext.stack) {
            Rect rect = clip.getRenderer().getCommittedRect();
            if (rect == null) continue;
            Position position = rect.getBodyRectPosition();
            Size size = rect.getBodyRectSize();
            Bounds clipBounds = new Bounds(
                    position.x,
                    position.y,
                    Math.max(0, size.width() - clip.getVerticalScrollbarGutter()),
                    Math.max(0, size.height() - clip.getHorizontalScrollbarGutter())
            );
            clipBounds = transformBounds(clip, clipBounds);
            if (clipBounds.isEmpty()) return memoClip(clipContext, Bounds.EMPTY);
            effective = effective == null ? clipBounds : effective.intersection(clipBounds);
            if (effective.isEmpty()) return memoClip(clipContext, Bounds.EMPTY);
        }
        return memoClip(clipContext, effective);
    }

    private static Bounds memoClip(ClipContext clipContext, Bounds result) {
        clipContext.computedVersion = clipContext.version;
        clipContext.result = result;
        return result;
    }

    private static void appendScrollbarEntries(Element element,
                                               ClipContext clipContext,
                                               Map<Element, Bounds> boundsCache,
                                               List<Entry> output) {
        if (element == null || output == null || !element.isPointerEnabled || !element.isVisible) return;
        Rect rect = element.getRenderer().getCommittedRect();
        if (rect == null) return;
        Position position = rect.getBodyRectPosition();
        Size size = rect.getBodyRectSize();
        double vertical = element.getVerticalScrollbarGutter();
        double horizontal = element.getHorizontalScrollbarGutter();
        Bounds clip = resolveCommittedClipBounds(clipContext, boundsCache);
        if (clip != null && clip.isEmpty()) return;
        if (vertical > 0) {
            Bounds bounds = new Bounds(
                    position.x + size.width() - vertical,
                    position.y,
                    vertical,
                    Math.max(0, size.height() - horizontal)
            );
            if (bounds.isValid()) output.add(new Entry(element, bounds, clip));
        }
        if (horizontal > 0) {
            Bounds bounds = new Bounds(
                    position.x,
                    position.y + size.height() - horizontal,
                    Math.max(0, size.width() - vertical),
                    horizontal
            );
            if (bounds.isValid()) output.add(new Entry(element, bounds, clip));
        }
    }

    private void removeEntriesForRoots(List<Element> roots) {
        for (Iterator<Entry> iterator = entries.iterator(); iterator.hasNext(); ) {
            Entry entry = iterator.next();
            if (isInAnyRoot(entry.element, roots)) {
                iterator.remove();
            }
        }
    }

    private static List<Element> minimizeRoots(Set<Element> roots) {
        ArrayList<Element> sorted = new ArrayList<>(roots);
        sorted.sort(java.util.Comparator.comparingInt(Element::getDepth));
        // 返回 List 而非 IdentityHashMap-backed Set：isInAnyRoot 每次调用都会迭代它,
        // Set 的 for-each 每次分配一个 IdentityHashMap$KeyIterator(JFR 采样里这笔
        // 分配随动画期间的逐帧 hit-test 提交累计到数百 MB)。
        ArrayList<Element> result = new ArrayList<>();
        for (Element root : sorted) {
            if (root == null || !root.isConnected()) continue;
            boolean covered = false;
            for (int i = 0; i < result.size(); i++) {
                if (RenderNode.isSameOrDescendant(root, result.get(i))) {
                    covered = true;
                    break;
                }
            }
            if (!covered) result.add(root);
        }
        return result;
    }

    private static boolean isInAnyRoot(Element element, List<Element> roots) {
        if (element == null || roots == null || roots.isEmpty()) return false;
        for (int i = 0; i < roots.size(); i++) {
            if (RenderNode.isSameOrDescendant(element, roots.get(i))) return true;
        }
        return false;
    }


    private static boolean comesBeforeInPaintOrder(Element left, Element right, List<RenderNode> paintOrder) {
        int leftIndex = firstPaintIndex(left, paintOrder);
        int rightIndex = firstPaintIndex(right, paintOrder);
        if (leftIndex < 0) return false;
        if (rightIndex < 0) return true;
        return leftIndex > rightIndex;
    }

    private static int firstPaintIndex(Element element, List<RenderNode> paintOrder) {
        if (element == null || paintOrder == null) return -1;
        for (int i = paintOrder.size() - 1; i >= 0; i--) {
            RenderNode node = paintOrder.get(i);
            if (node instanceof RenderNode.ElementPhaseNode phaseNode && phaseNode.target() == element) {
                return i;
            }
        }
        return -1;
    }

    private record Entry(Element element, Bounds bounds, Bounds clip) {
    }

    private record Bounds(double x, double y, double width, double height) {
        private static final Bounds EMPTY = new Bounds(0, 0, -1, -1);

        private boolean isValid() {
            return width >= 0 && height >= 0;
        }

        private boolean isEmpty() {
            return width <= 0 || height <= 0;
        }

        private Bounds intersection(Bounds other) {
            if (other == null) return this;
            double left = Math.max(x, other.x);
            double top = Math.max(y, other.y);
            double right = Math.min(x + width, other.x + other.width);
            double bottom = Math.min(y + height, other.y + other.height);
            return new Bounds(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }

        private boolean contains(Position position) {
            return position.x >= x && position.x <= x + width
                    && position.y >= y && position.y <= y + height;
        }
    }
}

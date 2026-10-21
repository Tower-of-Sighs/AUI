package com.sighs.apricityui.render;

import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;

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
                appendScrollbarEntries(scrollbarNode.target(), clipContext, boundsCache, entries, i);
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
            // outline 节点排在 MaskPop 之后，拿它建条目会让元素自身被自己的 overflow 裁剪框
            // 限制住（连边框一圈都点不到）；outline 也不参与命中，命中的是 BORDER 那一份。
            if (phaseNode.phase() == Base.RenderPhase.OUTLINE) continue;
            Element element = phaseNode.target();
            if (element == null || element.document != owner) continue;
            if (!seenElements.add(element)) continue;
            if (!Interaction.isDisplayed(element) || !element.isVisible || !element.isPointerEnabled) continue;

            Bounds bounds = resolveCommittedBounds(element, boundsCache);
            if (!bounds.isValid()) continue;
            Bounds clip = resolveCommittedClipBounds(clipContext, boundsCache);
            if (clip != null && clip.isEmpty()) continue;
            entries.add(new Entry(element, bounds, clip, resolveVisualBounds(element, bounds), i));
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
                    appendScrollbarEntries(target, clipContext, boundsCache, rebuilt, i);
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
            // outline 节点排在 MaskPop 之后，拿它建条目会让元素自身被自己的 overflow 裁剪框
            // 限制住（连边框一圈都点不到）；outline 也不参与命中，命中的是 BORDER 那一份。
            if (phaseNode.phase() == Base.RenderPhase.OUTLINE) continue;
            Element element = phaseNode.target();
            if (element == null || element.document != owner) continue;
            if (!isInAnyRoot(element, roots)) continue;
            if (!seenElements.add(element)) continue;
            if (!Interaction.isDisplayed(element) || !element.isVisible || !element.isPointerEnabled) continue;

            Bounds bounds = resolveCommittedBounds(element, boundsCache);
            if (!bounds.isValid()) continue;
            Bounds clip = resolveCommittedClipBounds(clipContext, boundsCache);
            if (clip != null && clip.isEmpty()) continue;
            rebuilt.add(new Entry(element, bounds, clip, resolveVisualBounds(element, bounds), i));
        }
        if (rebuilt.isEmpty()) return;
        if (!isFrontToBack(entries)) {
            // 缓存里已存在与新 paintList 不一致的条目（元素被增量重排/移除后留下的旧条目），
            // 归并只会把错误继续搬运下去。整表重建一次，把缓存拉回一致状态。
            rebuild(paintOrder);
            return;
        }
        mergeByPaintIndex(rebuilt);
    }

    /**
     * 把重建出来的一段条目并回 {@link #entries}，两个列表都按 {@link Entry#paintIndex()}
     * 从大到小（从前往后）排列，因此一次归并即可保持次序。
     */
    private void mergeByPaintIndex(List<Entry> rebuilt) {
        ArrayList<Entry> merged = new ArrayList<>(entries.size() + rebuilt.size());
        int existing = 0;
        int pending = 0;
        while (existing < entries.size() || pending < rebuilt.size()) {
            if (pending >= rebuilt.size()) {
                merged.add(entries.get(existing++));
            } else if (existing >= entries.size()) {
                merged.add(rebuilt.get(pending++));
            } else if (entries.get(existing).paintIndex() >= rebuilt.get(pending).paintIndex()) {
                merged.add(entries.get(existing++));
            } else {
                merged.add(rebuilt.get(pending++));
            }
        }
        entries.clear();
        entries.addAll(merged);
    }

    /** Whether the entry list still holds the front-to-back order its indices describe. */
    private static boolean isFrontToBack(List<Entry> ordered) {
        int previous = Integer.MAX_VALUE;
        for (int i = 0; i < ordered.size(); i++) {
            int current = ordered.get(i).paintIndex();
            if (current > previous) return false;
            previous = current;
        }
        return true;
    }

    public Element hitTest(Position cursorPosition, List<RenderNode> paintOrder) {
        if (cursorPosition == null) return null;
        if (dirty) {
            rebuild(paintOrder);
        }
        double x = cursorPosition.x;
        double y = cursorPosition.y;
        for (Entry entry : entries) {
            // 条目命中的是"布局盒子"（元素局部坐标）。元素落在 transform 子树里时要先把光标
            // 逆变换回去：先用变换后包围盒粗筛（不含 transform 的条目 visual 为 null，直接跳），
            // 命中候选再做一次精确逆映射，旋转过的元素不会把包围盒外的那块也算进去。
            if (entry.visual != null) {
                if (x < entry.visual.x() || x > entry.visual.maxX()
                        || y < entry.visual.y() || y > entry.visual.maxY()) {
                    continue;
                }
                double[] local = Base.toLocalPoint(entry.element, x, y);
                if (!entry.bounds.contains(local[0], local[1])) continue;
            } else if (!entry.bounds.contains(x, y)) {
                continue;
            }
            // 裁剪框已经在 resolveCommittedClipBounds 里映射到文档坐标系，和光标同系。
            if (entry.clip != null && !entry.clip.contains(x, y)) continue;
            return entry.element;
        }
        return null;
    }

    /** 命中盒按元素 transform 映射后的包围盒；元素不在 transform 子树里时返回 {@code null}。 */
    private static AABB resolveVisualBounds(Element element, Bounds bounds) {
        Rect rect = element.getLayout().getCommittedRect();
        if (rect == null) return null;
        return rect.transformLocalRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    private static Bounds resolveCommittedBounds(Element element, Map<Element, Bounds> boundsCache) {
        if (element == null) return Bounds.EMPTY;
        Bounds cached = boundsCache.get(element);
        if (cached != null) return cached;

        Rect rect = element.getLayout().getCommittedRect();
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
        boundsCache.put(element, bounds);
        return bounds;
    }

    private static Bounds resolveCommittedClipBounds(ClipContext clipContext, Map<Element, Bounds> boundsCache) {
        if (clipContext.isEmpty()) return null;
        if (clipContext.computedVersion == clipContext.version) return clipContext.result;
        Bounds effective = null;
        for (Element clipElement : clipContext.stack) {
            Rect rect = clipElement.getLayout().getCommittedRect();
            if (rect == null) continue;
            // 与绘制共用同一个裁剪框：根/body 的 overflow 由视口承担，其余取自身 padding box。
            AABB box = RenderNode.overflowClipBox(rect, clipElement);
            if (box == null) continue;
            double clipX = box.x();
            double clipY = box.y();
            double clipW = box.width();
            double clipH = box.height();
            // 裁剪框本身也可能在 transform 子树里：映射到文档坐标系再和光标比较。
            AABB visual = rect.transformLocalRect(clipX, clipY, clipW, clipH);
            if (visual != null) {
                clipX = visual.x();
                clipY = visual.y();
                clipW = visual.width();
                clipH = visual.height();
            }
            Bounds clipBounds = new Bounds(clipX, clipY, clipW, clipH);
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
                                               List<Entry> output,
                                               int paintIndex) {
        if (element == null || output == null || !element.isPointerEnabled || !element.isVisible) return;
        Rect rect = element.getLayout().getCommittedRect();
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
            if (bounds.isValid()) output.add(new Entry(element, bounds, clip, resolveVisualBounds(element, bounds), paintIndex));
        }
        if (horizontal > 0) {
            Bounds bounds = new Bounds(
                    position.x,
                    position.y + size.height() - horizontal,
                    Math.max(0, size.width() - vertical),
                    horizontal
            );
            if (bounds.isValid()) output.add(new Entry(element, bounds, clip, resolveVisualBounds(element, bounds), paintIndex));
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


    /**
     * 一条命中条目。{@code paintIndex} 是**建出这条条目的那个绘制节点**在 paintList 里的下标，
     * 同时也是条目之间唯一的排序键：paintIndex 越大越靠前、越先被 {@link #hitTest} 命中。
     *
     * <p>这个键不能换成"元素最后一次出现在 paintList 里的下标"：元素的 OUTLINE 节点排在它整棵
     * 子树之后，于是父元素的最后一个节点比子元素的大，"父"会被排到"子"前面——一张
     * {@code position:fixed;inset:0} 的遮罩立刻盖过它自己的所有子元素，页面渲染照常、
     * 交互却全部被它吃掉。命中用的也是这个节点（OUTLINE 不参与命中），键与条目同源。</p>
     */
    private record Entry(Element element, Bounds bounds, Bounds clip, AABB visual, int paintIndex) {
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
            return contains(position.x, position.y);
        }

        private boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX <= x + width
                    && pointY >= y && pointY <= y + height;
        }
    }
}

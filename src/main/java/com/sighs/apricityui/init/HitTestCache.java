package com.sighs.apricityui.init;

import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class HitTestCache {
    private final Document owner;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private boolean dirty = true;

    HitTestCache(Document owner) {
        this.owner = owner;
    }

    void markDirty() {
        dirty = true;
    }

    void clear() {
        entries.clear();
        dirty = true;
    }

    void rebuild(List<RenderNode> paintOrder) {
        entries.clear();
        dirty = false;
        if (owner == null || owner.body == null || paintOrder == null || paintOrder.isEmpty()) return;

        ArrayDeque<Element> clipStack = new ArrayDeque<>();
        Map<Element, Bounds> boundsCache = new IdentityHashMap<>();
        Set<Element> seenElements = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = paintOrder.size() - 1; i >= 0; i--) {
            RenderNode node = paintOrder.get(i);
            if (node instanceof RenderNode.MaskPopNode popNode) {
                Element target = popNode.target();
                if (target != null) {
                    clipStack.push(target);
                }
                continue;
            }
            if (node instanceof RenderNode.MaskPushNode pushNode) {
                if (!clipStack.isEmpty() && clipStack.peek() == pushNode.target()) {
                    clipStack.pop();
                }
                continue;
            }
            if (!(node instanceof RenderNode.ElementPhaseNode phaseNode)) continue;
            Element element = phaseNode.target();
            if (element == null || element.document != owner) continue;
            if (!seenElements.add(element)) continue;
            if (!Interaction.isDisplayed(element) || !element.isVisible || !element.isPointerEnabled) continue;

            Bounds bounds = resolveBounds(element, boundsCache);
            if (!bounds.isValid()) continue;
            List<Bounds> clips = resolveClipBounds(clipStack, boundsCache);
            entries.add(new Entry(element, bounds, clips));
        }
    }

    Element hitTest(Position cursorPosition, List<RenderNode> paintOrder) {
        if (cursorPosition == null) return null;
        if (dirty) {
            rebuild(paintOrder);
        }
        for (Entry entry : entries) {
            if (!entry.bounds.contains(cursorPosition)) continue;
            boolean clipped = false;
            for (Bounds clip : entry.clips) {
                if (!clip.contains(cursorPosition)) {
                    clipped = true;
                    break;
                }
            }
            if (!clipped) return entry.element;
        }
        return null;
    }

    private static Bounds resolveBounds(Element element, Map<Element, Bounds> boundsCache) {
        if (element == null) return Bounds.EMPTY;
        Bounds cached = boundsCache.get(element);
        if (cached != null) return cached;

        Bounds bounds;
        if ("IMG".equals(element.tagName)) {
            Rect rect = Rect.of(element);
            Position position = rect.getBodyRectPosition();
            Size size = rect.getBodyRectSize();
            bounds = new Bounds(position.x, position.y, size.width(), size.height());
        } else {
            Position position = Position.of(element);
            Box box = Box.of(element);
            Size size = Size.of(element);
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

    private static List<Bounds> resolveClipBounds(ArrayDeque<Element> clipStack, Map<Element, Bounds> boundsCache) {
        if (clipStack.isEmpty()) return List.of();
        ArrayList<Bounds> clips = new ArrayList<>(clipStack.size());
        for (Element clip : clipStack) {
            Bounds clipBounds = resolveBounds(clip, boundsCache);
            if (clipBounds.isValid()) clips.add(clipBounds);
        }
        return clips.isEmpty() ? List.of() : clips;
    }

    private record Entry(Element element, Bounds bounds, List<Bounds> clips) {
    }

    private record Bounds(double x, double y, double width, double height) {
        private static final Bounds EMPTY = new Bounds(0, 0, -1, -1);

        private boolean isValid() {
            return width >= 0 && height >= 0;
        }

        private boolean contains(Position position) {
            return position.x >= x && position.x <= x + width
                    && position.y >= y && position.y <= y + height;
        }
    }
}

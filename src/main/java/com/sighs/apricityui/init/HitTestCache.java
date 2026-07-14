package com.sighs.apricityui.init;

import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

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

        Stack<Element> clipStack = new Stack<>();
        for (int i = paintOrder.size() - 1; i >= 0; i--) {
            RenderNode node = paintOrder.get(i);
            if (node instanceof RenderNode.MaskPopNode popNode) {
                clipStack.push(popNode.target());
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
            if (!Interaction.isDisplayed(element) || !element.isVisible || !element.isPointerEnabled) continue;

            Bounds bounds = resolveBounds(element);
            if (!bounds.isValid()) continue;
            ArrayList<Bounds> clips = new ArrayList<>(clipStack.size());
            for (Element clip : clipStack) {
                Bounds clipBounds = resolveBounds(clip);
                if (clipBounds.isValid()) clips.add(clipBounds);
            }
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

    private static Bounds resolveBounds(Element element) {
        if (element == null) return Bounds.EMPTY;
        if ("IMG".equals(element.tagName)) {
            Rect rect = Rect.of(element);
            Position position = rect.getBodyRectPosition();
            Size size = rect.getBodyRectSize();
            return new Bounds(position.x, position.y, size.width(), size.height());
        }

        Position position = Position.of(element);
        Box box = Box.of(element);
        Size size = Size.of(element);
        return new Bounds(
                position.x + box.getMarginLeft(),
                position.y + box.getMarginTop(),
                size.width(),
                size.height()
        );
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

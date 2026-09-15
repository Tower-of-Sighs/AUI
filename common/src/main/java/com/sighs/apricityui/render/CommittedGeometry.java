package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Shared access to layout geometry committed for the current document frame. */
public final class CommittedGeometry {
    private CommittedGeometry() {
    }

    public static Window.IntersectionRect borderBox(Element element) {
        Rect rect = committedRect(element);
        if (rect == null) return null;
        Position position = rect.position;
        Box box = rect.box;
        Size size = rect.getElementSize();
        return new Window.IntersectionRect(
                position.x + box.getMarginLeft(),
                position.y + box.getMarginTop(),
                size.width(),
                size.height()
        );
    }

    public static Window.IntersectionRect hitTestBounds(Element element) {
        if (element == null) return null;
        if (!"IMG".equals(element.tagName)) return borderBox(element);
        return bodyBox(element);
    }

    public static Window.IntersectionRect bodyBox(Element element) {
        Rect rect = committedRect(element);
        if (rect == null) return null;
        Position position = rect.getBodyRectPosition();
        Size size = rect.getBodyRectSize();
        return new Window.IntersectionRect(position.x, position.y, size.width(), size.height());
    }

    /** The committed padding-box overflow clip, excluding active scrollbar gutters. */
    public static Window.IntersectionRect overflowClip(Element element) {
        Window.IntersectionRect body = bodyBox(element);
        if (body == null) return null;
        return new Window.IntersectionRect(
                body.x(),
                body.y(),
                Math.max(0.0d, body.width() - element.getVerticalScrollbarGutter()),
                Math.max(0.0d, body.height() - element.getHorizontalScrollbarGutter())
        );
    }

    public static Window.IntersectionRect intersectOverflowClips(Iterable<Element> clips) {
        if (clips == null) return null;
        Window.IntersectionRect effective = null;
        for (Element clip : clips) {
            Window.IntersectionRect bounds = overflowClip(clip);
            if (bounds == null) continue;
            if (effective == null) {
                effective = bounds;
                continue;
            }
            Window.IntersectionRect.Intersection intersection = Window.IntersectionRect.intersect(effective, bounds);
            if (!intersection.intersects()) return Window.IntersectionRect.ZERO;
            effective = intersection.rect();
        }
        return effective;
    }

    /** Resolves the overflow-clip stack active when an element's border phase is painted. */
    public static PaintClip resolvePaintClip(Element target, List<RenderNode> paintOrder) {
        if (target == null || paintOrder == null || paintOrder.isEmpty()) return PaintClip.NOT_PAINTED;
        ArrayDeque<Element> clipStack = new ArrayDeque<>();
        for (int index = paintOrder.size() - 1; index >= 0; index--) {
            RenderNode node = paintOrder.get(index);
            if (node instanceof RenderNode.MaskPopNode popNode) {
                Element clipTarget = popNode.target();
                if (clipTarget != null) clipStack.push(clipTarget);
                continue;
            }
            if (node instanceof RenderNode.MaskPushNode pushNode) {
                if (!clipStack.isEmpty() && clipStack.peek() == pushNode.target()) {
                    clipStack.pop();
                }
                continue;
            }
            if (node instanceof RenderNode.ElementPhaseNode phaseNode
                    && phaseNode.target() == target
                    && phaseNode.phase() == Base.RenderPhase.BORDER) {
                return new PaintClip(true, List.copyOf(new ArrayList<>(clipStack)));
            }
        }
        return PaintClip.NOT_PAINTED;
    }

    private static Rect committedRect(Element element) {
        return element == null ? null : element.getRenderer().getCommittedRectIfValid();
    }

    public record PaintClip(boolean painted, List<Element> clips) {
        private static final PaintClip NOT_PAINTED = new PaintClip(false, List.of());

        public PaintClip {
            clips = clips == null ? List.of() : List.copyOf(clips);
        }
    }
}

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
        Window.IntersectionRect untransformed = new Window.IntersectionRect(
                position.x + box.getMarginLeft(),
                position.y + box.getMarginTop(),
                size.width(),
                size.height()
        );
        AABB transformed = rect.transformLocalRect(
                untransformed.x(), untransformed.y(), untransformed.width(), untransformed.height());
        return transformed == null
                ? untransformed
                : new Window.IntersectionRect(transformed.x(), transformed.y(), transformed.width(), transformed.height());
    }

    /** Uses the same overflow clip as painting, including viewport-propagated overflow. */
    public static Window.IntersectionRect overflowClip(Element element) {
        Rect rect = committedRect(element);
        if (rect == null) return null;
        AABB clip = RenderNode.overflowClipBox(rect, element);
        if (clip == null) return null;
        AABB transformed = rect.transformLocalRect(clip.x(), clip.y(), clip.width(), clip.height());
        AABB result = transformed == null ? clip : transformed;
        return new Window.IntersectionRect(result.x(), result.y(), result.width(), result.height());
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

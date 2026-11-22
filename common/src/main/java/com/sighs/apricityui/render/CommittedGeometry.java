package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.ScrollLayout;
import com.sighs.apricityui.layout.Size;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared access to layout geometry committed for the current document frame.
 */
public final class CommittedGeometry {
    private CommittedGeometry() {
    }

    public static Window.IntersectionRect borderBox(Element element) {
        Bounds bounds = box(element, false);
        if (bounds == null) return null;
        AABB visual = transformedBounds(element, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        return visual == null
                ? new Window.IntersectionRect(bounds.x(), bounds.y(), bounds.width(), bounds.height())
                : new Window.IntersectionRect(visual.x(), visual.y(), visual.width(), visual.height());
    }

    public static Bounds hitBox(Element element) {
        return box(element, element != null && "IMG".equals(element.tagName));
    }

    /** Untransformed CSSOM client size, without re-entering box measurement. */
    public static Size clientSize(Element element, Rect rect) {
        if (!rect.isDisplayed()) return Size.ZERO;
        var scroll = ScrollLayout.metrics(element);
        Size size = rect.getElementSize();
        Box box = rect.box;
        double paddingX = box.getPaddingHorizontal();
        double paddingY = box.getPaddingVertical();
        return new Size(
                Math.max(0, size.width() - box.getBorderHorizontal() - paddingX - scroll.verticalGutter()) + paddingX,
                Math.max(0, size.height() - box.getBorderVertical() - paddingY - scroll.horizontalGutter()) + paddingY);
    }

    private static Bounds box(Element element, boolean body) {
        Rect rect = committedRect(element);
        if (rect == null || !rect.isDisplayed()) return null;
        if (body) {
            Position pos = rect.getBodyRectPosition();
            Size size = rect.getBodyRectSize();
            return new Bounds(pos.x, pos.y, size.width(), size.height());
        }
        return new Bounds(rect.position.x + rect.box.getMarginLeft(), rect.position.y + rect.box.getMarginTop(),
                rect.getElementSize().width(), rect.getElementSize().height());
    }

    /** Inverts the published transform without consulting the layout or transform builders. */
    public static double[] toLocalPoint(Element element, double x, double y) {
        return Base.unprojectPoint(element.getLayout().getCommittedWorldTransformIfValid(), x, y);
    }

    static AABB transformedBounds(Element element, double x, double y, double width, double height) {
        var matrix = element.getLayout().getCommittedWorldTransformIfValid();
        if (matrix == null || (matrix.properties() & org.joml.Matrix4f.PROPERTY_IDENTITY) != 0) return null;
        double[] visual = Base.projectBounds(matrix, x, y, width, height);
        return new AABB((float) visual[0], (float) visual[1], (float) visual[2], (float) visual[3]);
    }

    public record Bounds(double x, double y, double width, double height) {
        public boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    /** Uses the same overflow clip as painting, including viewport-propagated overflow. */
    public static Window.IntersectionRect overflowClip(Element element) {
        Rect rect = committedRect(element);
        if (rect == null) return null;
        AABB clip = RenderNode.overflowClipBox(rect, element);
        if (clip == null) return null;
        AABB transformed = transformedBounds(element, clip.x(), clip.y(), clip.width(), clip.height());
        AABB result = transformed == null ? clip : transformed;
        return new Window.IntersectionRect(result.x(), result.y(), result.width(), result.height());
    }

    /**
     * Resolves the overflow-clip stack active when an element's border phase is painted.
     */
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

    public static Rect committedRect(Element element) {
        return element == null ? null : element.getLayout().getCommittedRectIfValid();
    }

    public record PaintClip(boolean painted, List<Element> clips) {
        private static final PaintClip NOT_PAINTED = new PaintClip(false, List.of());

        public PaintClip {
            clips = clips == null ? List.of() : List.copyOf(clips);
        }
    }
}

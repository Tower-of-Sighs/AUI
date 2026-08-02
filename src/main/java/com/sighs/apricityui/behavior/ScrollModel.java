package com.sighs.apricityui.behavior;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;

public final class ScrollModel {
    private static final double SCROLL_EASING_FACTOR = 0.2;
    private static final double SCROLL_OVERSCROLL_DAMPING = 0.4;
    private static final double SCROLL_STOP_EPSILON = 0.01;
    private static final double BASE_FRAME_MS = 16.6666666667;
    private static final double MAX_FRAME_MS = 50.0;
    /** Scrollbar dimensions are expressed in device pixels, then converted to document pixels. */
    private static final double SCROLLBAR_GUTTER = 8.0;
    private static final double SCROLLBAR_EPSILON = 0.5;
    private static final double SCROLLBAR_TRACK_SIZE = 6.0;
    private static final double SCROLLBAR_TRACK_INSET = 1.0;
    private static final double SCROLLBAR_MIN_THUMB_LENGTH = 10.0;
    private static final float SCROLLBAR_THUMB_DEPTH_FRACTION = 0.5f;

    private final Element owner;
    private long lastRenderStepNs;
    private boolean verticalScrollbarVisible;
    private boolean horizontalScrollbarVisible;
    private boolean scrollbarLayoutDirty;
    private DragAxis dragAxis = DragAxis.NONE;
    private double dragPointerOffset;
    private boolean scrollbarPointerActive;

    public ScrollModel(Element owner) {
        this.owner = owner;
    }

    public void setScrollLeft(double value) {
        owner.targetScrollLeft = applyOverscroll(value, getHorizontalScrollLimit());
    }

    public void setScrollTop(double value) {
        owner.targetScrollTop = applyOverscroll(value, getVerticalScrollLimit());
    }

    public double getScrollLeft() {
        return owner.scrollLeft;
    }

    public double getScrollTop() {
        return owner.scrollTop;
    }

    public double getTargetScrollLeft() {
        return owner.targetScrollLeft;
    }

    public double getTargetScrollTop() {
        return owner.targetScrollTop;
    }

    public boolean canScroll() {
        return canScrollVertically() || canScrollHorizontally();
    }

    public boolean canScrollVertically() {
        if (isViewportScroller()) {
            return allowsViewportUserScroll(resolveViewportOverflowY());
        }
        return Interaction.allowsUserScrollY(owner.getComputedStyle());
    }

    public boolean canScrollHorizontally() {
        if (isViewportScroller()) {
            return allowsViewportUserScroll(resolveViewportOverflowX());
        }
        return Interaction.allowsUserScrollX(owner.getComputedStyle());
    }

    public boolean hasVerticalScrollRange() {
        if (isViewportScroller() ? !canScrollVertically()
                : !Interaction.allowsUserScrollY(owner.getComputedStyle())) return false;
        commitLayoutMetrics();
        return getVerticalScrollLimitFromMetrics() > 0.5;
    }

    public boolean hasHorizontalScrollRange() {
        if (isViewportScroller() ? !canScrollHorizontally()
                : !Interaction.allowsUserScrollX(owner.getComputedStyle())) return false;
        commitLayoutMetrics();
        return getHorizontalScrollLimitFromMetrics() > 0.5;
    }

    public boolean tick() {
        if (!scrollbarLayoutDirty) return false;
        scrollbarLayoutDirty = false;
        owner.getRenderer().invalidateLayoutSubtree();
        if (owner.document != null) {
            owner.document.markDirty(owner, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.HITTEST);
        }
        return true;
    }

    public boolean stepRender() {
        if (!needsRenderStep()) {
            lastRenderStepNs = 0L;
            return false;
        }

        double previousLeft = owner.scrollLeft;
        double previousTop = owner.scrollTop;
        double frameScale = consumeFrameScale();
        stepHorizontalScroll(frameScale);
        stepVerticalScroll(frameScale);
        return Double.compare(previousLeft, owner.scrollLeft) != 0
                || Double.compare(previousTop, owner.scrollTop) != 0;
    }

    public boolean needsRenderStep() {
        return !isScrollSettled(owner.scrollLeft, owner.targetScrollLeft)
                || !isScrollSettled(owner.scrollTop, owner.targetScrollTop);
    }

    /**
     * Whether the render list should reserve a scrollbar paint node.  The
     * actual overflow metrics are refreshed by drawScrollbar(), so this check
     * intentionally also returns true for auto/scroll overflow declarations.
     */
    public boolean mayRenderScrollbar() {
        return verticalScrollbarVisible
                || horizontalScrollbarVisible
                || mayShowVerticalScrollbar()
                || mayShowHorizontalScrollbar();
    }

    public boolean handleMouseDown(MouseEvent event) {
        if (event == null || event.button != 0) return false;
        if (!mayRenderScrollbar()) return false;

        Rect rect = Rect.of(owner);
        AxisGeometry vertical = axisGeometry(true, rect);
        AxisGeometry horizontal = axisGeometry(false, rect);
        AxisGeometry hit = contains(vertical, event.clientX, event.clientY)
                ? vertical
                : contains(horizontal, event.clientX, event.clientY) ? horizontal : null;
        if (hit == null) return false;

        scrollbarPointerActive = true;
        double beforeLeft = owner.getTargetScrollLeft();
        double beforeTop = owner.getTargetScrollTop();
        double pointer = hit.vertical ? event.clientY : event.clientX;
        if (pointer >= hit.thumbStart() && pointer <= hit.thumbEnd()) {
            dragAxis = hit.vertical ? DragAxis.VERTICAL : DragAxis.HORIZONTAL;
            dragPointerOffset = pointer - hit.thumbStart();
        } else {
            double page = hit.vertical ? getScrollportHeight() : getScrollportWidth();
            double direction = pointer < hit.thumbStart() ? -1.0 : 1.0;
            if (hit.vertical) {
                setScrollTop(clampScrollTarget(owner.getTargetScrollTop() + direction * page,
                        getVerticalScrollLimitFromMetrics()));
            } else {
                setScrollLeft(clampScrollTarget(owner.getTargetScrollLeft() + direction * page,
                        getHorizontalScrollLimitFromMetrics()));
            }
            owner.dispatchScrollEventIfChanged(beforeLeft, beforeTop);
        }
        return true;
    }

    public boolean handleMouseMove(MouseEvent event) {
        if (event == null || !scrollbarPointerActive) return false;
        if (dragAxis == DragAxis.NONE) return true;
        AxisGeometry geometry = axisGeometry(dragAxis == DragAxis.VERTICAL, Rect.of(owner));
        if (geometry == null) return true;

        double pointer = geometry.vertical ? event.clientY : event.clientX;
        double travel = geometry.trackLength() - geometry.thumbLength();
        double ratio = travel <= 0 ? 0 : (pointer - geometry.trackStart() - dragPointerOffset) / travel;
        ratio = Math.max(0, Math.min(1, ratio));
        double limit = geometry.vertical ? getVerticalScrollLimitFromMetrics() : getHorizontalScrollLimitFromMetrics();
        setScrollImmediate(geometry.vertical, ratio * limit);
        return true;
    }

    public boolean handleMouseUp(MouseEvent event) {
        if (!scrollbarPointerActive) return false;
        scrollbarPointerActive = false;
        dragAxis = DragAxis.NONE;
        dragPointerOffset = 0;
        return true;
    }

    public boolean isScrollbarInteractionActive() {
        return scrollbarPointerActive;
    }

    public void drawScrollbar(PoseStack poseStack, Rect rectRenderer) {
        if (!mayShowHorizontalScrollbar() && !mayShowVerticalScrollbar()) {
            setScrollbarVisibility(false, false);
            return;
        }
        if (!verticalScrollbarVisible && !horizontalScrollbarVisible) return;

        Position bodyPos = rectRenderer.getBodyRectPosition();
        Size bodySize = rectRenderer.getBodyRectSize();
        if (verticalScrollbarVisible) drawVerticalScrollbar(poseStack, bodyPos, bodySize);
        if (horizontalScrollbarVisible) drawHorizontalScrollbar(poseStack, bodyPos, bodySize);
    }

    public double getVerticalScrollbarGutter() {
        return verticalScrollbarVisible ? scrollbarGutter() : 0;
    }

    public double getHorizontalScrollbarGutter() {
        return horizontalScrollbarVisible ? scrollbarGutter() : 0;
    }

    private boolean stepHorizontalScroll(double frameScale) {
        ScrollStep step = stepScrollAxis(owner.scrollLeft, owner.targetScrollLeft, getHorizontalScrollLimit(), frameScale);
        owner.scrollLeft = step.current();
        owner.targetScrollLeft = step.target();
        return step.moving();
    }

    private boolean stepVerticalScroll(double frameScale) {
        ScrollStep step = stepScrollAxis(owner.scrollTop, owner.targetScrollTop, getVerticalScrollLimit(), frameScale);
        owner.scrollTop = step.current();
        owner.targetScrollTop = step.target();
        return step.moving();
    }

    private ScrollStep stepScrollAxis(double current, double target, double limit, double frameScale) {
        double clampedTarget = clampScrollTarget(target, limit);
        if (target < 0 || target > limit) {
            target = easeToward(target, clampedTarget, 0.28, frameScale);
        }
        if (!isScrollSettled(current, target)) {
            current = easeToward(current, target, SCROLL_EASING_FACTOR, frameScale);
        }
        if (Math.abs(target - clampedTarget) <= SCROLL_STOP_EPSILON) {
            target = clampedTarget;
        }
        if (isScrollSettled(current, target) && isScrollSettled(target, clampedTarget)) {
            current = clampedTarget;
            target = clampedTarget;
        }
        return new ScrollStep(current, target, !isScrollSettled(current, target));
    }

    private double consumeFrameScale() {
        long now = System.nanoTime();
        if (lastRenderStepNs <= 0L) {
            lastRenderStepNs = now;
            return 1.0;
        }
        double elapsedMs = Math.max(0, Math.min(MAX_FRAME_MS, (now - lastRenderStepNs) / 1_000_000.0));
        lastRenderStepNs = now;
        return Math.max(0.25, elapsedMs / BASE_FRAME_MS);
    }

    private double easeToward(double current, double target, double factor, double frameScale) {
        if (factor <= 0) return current;
        if (factor >= 1) return target;
        double adjusted = 1.0 - Math.pow(1.0 - factor, Math.max(0.0, frameScale));
        return current + (target - current) * adjusted;
    }

    private double applyOverscroll(double value, double limit) {
        if (value < 0) return value * SCROLL_OVERSCROLL_DAMPING;
        if (value > limit) return (value - limit) * SCROLL_OVERSCROLL_DAMPING + limit;
        return value;
    }

    private double clampScrollTarget(double value, double limit) {
        if (value < 0) return 0;
        if (value > limit) return limit;
        return value;
    }

    private double getHorizontalScrollLimit() {
        return getHorizontalScrollLimitFromMetrics();
    }

    private double getHorizontalScrollLimitFromMetrics() {
        return Math.max(0, owner.scrollWidth - getScrollportWidth());
    }

    private double getVerticalScrollLimit() {
        return getVerticalScrollLimitFromMetrics();
    }

    private double getVerticalScrollLimitFromMetrics() {
        return Math.max(0, owner.scrollHeight - getScrollportHeight());
    }

    private double getScrollportWidth() {
        if (isViewportScroller()) {
            return Math.max(0, owner.document.getViewport().layoutWidth() - getVerticalScrollbarGutter());
        }
        return Box.of(owner).innerSize().width();
    }

    private double getScrollportHeight() {
        if (isViewportScroller()) {
            return Math.max(0, owner.document.getViewport().layoutHeight() - getHorizontalScrollbarGutter());
        }
        return Box.of(owner).innerSize().height();
    }

    private boolean isViewportScroller() {
        return owner.document != null
                && owner.document.documentElement != null
                && owner == owner.document.documentElement;
    }

    private String resolveViewportOverflowX() {
        return resolveViewportOverflow(true);
    }

    private String resolveViewportOverflowY() {
        return resolveViewportOverflow(false);
    }

    /** 有效 overflow：viewport 滚动器走 viewport 解析，普通元素走自身样式。 */
    private String resolveOverflowX() {
        return isViewportScroller() ? resolveViewportOverflowX() : Interaction.resolveOverflowX(owner.getComputedStyle());
    }

    private String resolveOverflowY() {
        return isViewportScroller() ? resolveViewportOverflowY() : Interaction.resolveOverflowY(owner.getComputedStyle());
    }

    /** CSS Overflow propagates body overflow to the viewport while html remains visible. */
    private String resolveViewportOverflow(boolean horizontal) {
        String rootOverflow = horizontal
                ? Interaction.resolveOverflowX(owner.getComputedStyle())
                : Interaction.resolveOverflowY(owner.getComputedStyle());
        if (!"visible".equals(rootOverflow)) return rootOverflow;

        Element body = owner.document.body;
        if (body == null) return rootOverflow;
        return horizontal
                ? Interaction.resolveOverflowX(body.getComputedStyle())
                : Interaction.resolveOverflowY(body.getComputedStyle());
    }

    private boolean allowsViewportUserScroll(String overflow) {
        String normalized = Interaction.normalizeOverflow(overflow);
        return !"hidden".equals(normalized) && !"clip".equals(normalized);
    }

    /** Commits the scroll area from used layout boxes, never from paint bounds. */
    public void commitLayoutMetrics() {
        if (!(owner instanceof AbstractText)) {
            Size contentSize = measureLayoutScrollArea();
            if (isViewportScroller() && owner.document.body != null) {
                // The viewport scrolling element's scroll area includes the body
                // box even when the layout tree does not expose it as a normal
                // child contribution of html.
                Size bodyContentSize = measureLayoutScrollArea(owner.document.body);
                contentSize = new Size(
                        Math.max(contentSize.width(), bodyContentSize.width()),
                        Math.max(contentSize.height(), bodyContentSize.height())
                );
            }
            owner.scrollWidth = contentSize.width();
            owner.scrollHeight = contentSize.height();
        }
        updateScrollbarVisibility();
    }

    private Size measureLayoutScrollArea() {
        return measureLayoutScrollArea(owner);
    }

    private static Size measureLayoutScrollArea(Element scrollport) {
        if (scrollport == null) return Size.ZERO;
        Box box = Box.of(scrollport);
        double contentOriginX = box.offset("left");
        double contentOriginY = box.offset("top");
        double width = 0;
        double height = 0;
        for (Element child : scrollport.getRenderChildren()) {
            Style style = child.getRawComputedStyle();
            if ("none".equals(style.display) || "fixed".equals(style.position)) continue;

            Position offset = Position.getOffset(child);
            Size outerSize = Box.of(child).size();
            width = Math.max(width, offset.x - contentOriginX + outerSize.width());
            height = Math.max(height, offset.y - contentOriginY + outerSize.height());
        }
        return new Size(Math.max(0, width), Math.max(0, height));
    }

    private void updateScrollbarVisibility() {
        Size rawScrollport = rawScrollportSize();
        String overflowX = resolveOverflowX();
        String overflowY = resolveOverflowY();

        boolean forceHorizontal = "scroll".equals(overflowX);
        boolean forceVertical = "scroll".equals(overflowY);
        boolean autoHorizontal = "auto".equals(overflowX) || isViewportScroller() && "visible".equals(overflowX);
        boolean autoVertical = "auto".equals(overflowY) || isViewportScroller() && "visible".equals(overflowY);

        boolean nextHorizontal = forceHorizontal;
        boolean nextVertical = forceVertical;
        double gutter = scrollbarGutter();
        for (int i = 0; i < 3; i++) {
            double availableWidth = Math.max(0, rawScrollport.width() - (nextVertical ? gutter : 0));
            double availableHeight = Math.max(0, rawScrollport.height() - (nextHorizontal ? gutter : 0));
            boolean resolvedHorizontal = forceHorizontal
                    || autoHorizontal && owner.scrollWidth > availableWidth + SCROLLBAR_EPSILON;
            boolean resolvedVertical = forceVertical
                    || autoVertical && owner.scrollHeight > availableHeight + SCROLLBAR_EPSILON;
            if (resolvedHorizontal == nextHorizontal && resolvedVertical == nextVertical) break;
            nextHorizontal = resolvedHorizontal;
            nextVertical = resolvedVertical;
        }

        setScrollbarVisibility(nextHorizontal, nextVertical);
    }

    private void setScrollbarVisibility(boolean horizontal, boolean vertical) {
        if (horizontal == horizontalScrollbarVisible && vertical == verticalScrollbarVisible) return;
        horizontalScrollbarVisible = horizontal;
        verticalScrollbarVisible = vertical;
        scrollbarLayoutDirty = true;
    }

    private boolean mayShowHorizontalScrollbar() {
        String overflow = resolveOverflowX();
        return "auto".equals(overflow) || "scroll".equals(overflow)
                || isViewportScroller() && "visible".equals(overflow);
    }

    private boolean mayShowVerticalScrollbar() {
        String overflow = resolveOverflowY();
        return "auto".equals(overflow) || "scroll".equals(overflow)
                || isViewportScroller() && "visible".equals(overflow);
    }

    private Size rawScrollportSize() {
        if (isViewportScroller()) {
            return new Size(
                    Math.max(0, owner.document.getViewport().layoutWidth()),
                    Math.max(0, owner.document.getViewport().layoutHeight())
            );
        }
        return Box.of(owner).rawInnerSize();
    }

    private void drawVerticalScrollbar(PoseStack poseStack, Position bodyPos, Size bodySize) {
        AxisGeometry geometry = axisGeometry(true, bodyPos, bodySize);
        if (geometry == null) return;
        drawScrollbarTrackAndThumb(poseStack,
                (float) geometry.trackX, (float) geometry.trackY,
                (float) geometry.trackWidth, (float) geometry.trackHeight,
                (float) geometry.thumbX, (float) geometry.thumbY,
                (float) geometry.thumbWidth, (float) geometry.thumbHeight);
    }

    private void drawHorizontalScrollbar(PoseStack poseStack, Position bodyPos, Size bodySize) {
        AxisGeometry geometry = axisGeometry(false, bodyPos, bodySize);
        if (geometry == null) return;
        drawScrollbarTrackAndThumb(poseStack,
                (float) geometry.trackX, (float) geometry.trackY,
                (float) geometry.trackWidth, (float) geometry.trackHeight,
                (float) geometry.thumbX, (float) geometry.thumbY,
                (float) geometry.thumbWidth, (float) geometry.thumbHeight);
    }

    private AxisGeometry axisGeometry(boolean vertical, Rect rect) {
        if (rect == null || (vertical ? !verticalScrollbarVisible : !horizontalScrollbarVisible)) return null;
        return axisGeometry(vertical, rect.getBodyRectPosition(), rect.getBodyRectSize());
    }

    /** 按轴参数化的轨道/滑块几何：vertical=true 对应原 verticalGeometry，否则 horizontalGeometry。 */
    private AxisGeometry axisGeometry(boolean vertical, Position bodyPos, Size bodySize) {
        double scrollport = vertical ? getScrollportHeight() : getScrollportWidth();
        double trackSize = scrollbarTrackSize();
        double trackInset = scrollbarTrackInset();
        double crossGutter = vertical ? getHorizontalScrollbarGutter() : getVerticalScrollbarGutter();
        double trackExtent = Math.max(0, (vertical ? bodySize.height() : bodySize.width()) - crossGutter - trackInset * 2);
        if (trackExtent <= 0) return null;
        double scrollExtent = vertical ? owner.scrollHeight : owner.scrollWidth;
        double thumbExtent = scrollExtent <= scrollport + SCROLLBAR_EPSILON
                ? trackExtent
                : Math.max(scrollbarMinThumbLength(),
                        trackExtent * (scrollport / Math.max(scrollport, scrollExtent)));
        thumbExtent = Math.min(trackExtent, thumbExtent);
        double maxTravel = Math.max(0, trackExtent - thumbExtent);
        double scrollLimit = Math.max(0, scrollExtent - scrollport);
        double scrollPos = vertical ? getScrollTop() : getScrollLeft();
        double thumbOffset = scrollLimit <= 0 ? 0
                : Math.max(0, Math.min(scrollPos, scrollLimit)) / scrollLimit * maxTravel;

        double trackX = vertical ? bodyPos.x + bodySize.width() - trackSize - trackInset : bodyPos.x + trackInset;
        double trackY = vertical ? bodyPos.y + trackInset : bodyPos.y + bodySize.height() - trackSize - trackInset;
        double trackWidth = vertical ? trackSize : trackExtent;
        double trackHeight = vertical ? trackExtent : trackSize;
        double thumbX = vertical ? trackX : trackX + thumbOffset;
        double thumbY = vertical ? trackY + thumbOffset : trackY;
        double thumbWidth = vertical ? trackSize : thumbExtent;
        double thumbHeight = vertical ? thumbExtent : trackSize;
        double hitX = vertical ? bodyPos.x + bodySize.width() - scrollbarGutter() : bodyPos.x;
        double hitY = vertical ? bodyPos.y : bodyPos.y + bodySize.height() - scrollbarGutter();
        double hitWidth = vertical ? scrollbarGutter() : Math.max(0, bodySize.width() - getVerticalScrollbarGutter());
        double hitHeight = vertical ? Math.max(0, bodySize.height() - getHorizontalScrollbarGutter()) : scrollbarGutter();
        return new AxisGeometry(vertical, trackX, trackY, trackWidth, trackHeight,
                thumbX, thumbY, thumbWidth, thumbHeight, hitX, hitY, hitWidth, hitHeight);
    }

    private double scrollbarGutter() {
        return devicePixelsToDocumentPixels(SCROLLBAR_GUTTER);
    }

    private double scrollbarTrackSize() {
        return devicePixelsToDocumentPixels(SCROLLBAR_TRACK_SIZE);
    }

    private double scrollbarTrackInset() {
        return devicePixelsToDocumentPixels(SCROLLBAR_TRACK_INSET);
    }

    private double scrollbarMinThumbLength() {
        return devicePixelsToDocumentPixels(SCROLLBAR_MIN_THUMB_LENGTH);
    }

    private double devicePixelsToDocumentPixels(double devicePixels) {
        double scale = owner.document == null || owner.document.getViewport() == null
                ? 1.0d : owner.document.getViewport().scissorScale();
        if (!(scale > 0) || !Double.isFinite(scale)) scale = 1.0d;
        return devicePixels / scale;
    }

    private static boolean contains(AxisGeometry geometry, double x, double y) {
        return geometry != null
                && x >= geometry.hitX && x <= geometry.hitX + geometry.hitWidth
                && y >= geometry.hitY && y <= geometry.hitY + geometry.hitHeight;
    }

    private void setScrollImmediate(boolean vertical, double value) {
        double beforeLeft = owner.getTargetScrollLeft();
        double beforeTop = owner.getTargetScrollTop();
        if (vertical) {
            double clamped = clampScrollTarget(value, getVerticalScrollLimitFromMetrics());
            owner.scrollTop = clamped;
            owner.targetScrollTop = clamped;
        } else {
            double clamped = clampScrollTarget(value, getHorizontalScrollLimitFromMetrics());
            owner.scrollLeft = clamped;
            owner.targetScrollLeft = clamped;
        }
        lastRenderStepNs = 0L;
        owner.getRenderer().invalidateScrollVersion();
        if (owner.document != null) {
            owner.document.markDirty(owner, Drawer.REPAINT | Drawer.HITTEST);
        }
        owner.dispatchScrollEventIfChanged(beforeLeft, beforeTop);
    }

    private void drawScrollbarTrackAndThumb(PoseStack poseStack,
                                            float trackX, float trackY, float trackWidth, float trackHeight,
                                            float thumbX, float thumbY, float thumbWidth, float thumbHeight) {
        float trackRadius = Math.min(trackWidth, trackHeight) / 2f;
        float thumbRadius = Math.min(thumbWidth, thumbHeight) / 2f;
        poseStack.pushPose();
        try {
            Graph.drawUnifiedRoundedRect(poseStack.last().pose(), trackX, trackY, trackWidth, trackHeight,
                    new float[]{trackRadius, trackRadius, trackRadius, trackRadius}, 0x18B96A91);
            Base.offsetPaintDepth(poseStack, SCROLLBAR_THUMB_DEPTH_FRACTION);
            Graph.drawUnifiedRoundedRect(poseStack.last().pose(), thumbX, thumbY, thumbWidth, thumbHeight,
                    new float[]{thumbRadius, thumbRadius, thumbRadius, thumbRadius}, 0xB39F9F9F);
        } finally {
            poseStack.popPose();
        }
    }

    private boolean isScrollSettled(double current, double target) {
        return Math.abs(current - target) <= SCROLL_STOP_EPSILON;
    }

    private record ScrollStep(double current, double target, boolean moving) {
    }

    private enum DragAxis {
        NONE,
        VERTICAL,
        HORIZONTAL
    }

    private record AxisGeometry(boolean vertical,
                                double trackX, double trackY, double trackWidth, double trackHeight,
                                double thumbX, double thumbY, double thumbWidth, double thumbHeight,
                                double hitX, double hitY, double hitWidth, double hitHeight) {
        public double trackStart() {
            return vertical ? trackY : trackX;
        }

        public double trackLength() {
            return vertical ? trackHeight : trackWidth;
        }

        public double thumbStart() {
            return vertical ? thumbY : thumbX;
        }

        public double thumbLength() {
            return vertical ? thumbHeight : thumbWidth;
        }

        public double thumbEnd() {
            return thumbStart() + thumbLength();
        }
    }
}

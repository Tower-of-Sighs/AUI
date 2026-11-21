package com.sighs.apricityui.behavior;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.CommittedGeometry;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.PoseMatrices;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.layout.ScrollLayout;
import com.sighs.apricityui.style.ScrollbarStyle;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.Selector.PseudoElement;

public final class ScrollModel {
    private static final double SCROLL_EASING_FACTOR = 0.2;
    private static final double SCROLL_OVERSCROLL_DAMPING = 0.4;
    private static final double SCROLL_STOP_EPSILON = 0.01;
    private static final double BASE_FRAME_MS = 16.6666666667;
    private static final double MAX_FRAME_MS = 50.0;
    private static final double SCROLLBAR_EPSILON = 0.5;
    private static final double SCROLLBAR_MIN_THUMB_LENGTH = 10.0;
    private static final float SCROLLBAR_THUMB_DEPTH_FRACTION = 0.5f;
    /**
     * 默认轨道/滑块颜色（ARGB）。与旧的硬编码值保持一致。
     */
    private static final int SCROLLBAR_DEFAULT_TRACK_COLOR = 0x18B96A91;
    private static final int SCROLLBAR_DEFAULT_THUMB_COLOR = 0xB39F9F9F;

    private final Element owner;
    private long lastRenderStepNs;
    private DragAxis dragAxis = DragAxis.NONE;
    private double dragPointerOffset;
    private boolean scrollbarPointerActive;
    private double lastRenderStepDeltaLeft;
    private double lastRenderStepDeltaTop;
    /**
     * 上一帧指针是否停在滑块上，用于驱动 ::-webkit-scrollbar-thumb:hover 重绘。
     */
    private boolean lastThumbHovered;

    public ScrollModel(Element owner) {
        this.owner = owner;
    }

    private static boolean contains(AxisGeometry geometry, double x, double y) {
        return geometry != null
                && x >= geometry.hitX && x <= geometry.hitX + geometry.hitWidth
                && y >= geometry.hitY && y <= geometry.hitY + geometry.hitHeight;
    }

    private static boolean isPointerOnThumb(AxisGeometry geometry, Position point) {
        if (geometry == null || point == null) return false;
        return point.x >= geometry.thumbX && point.x <= geometry.thumbX + geometry.thumbWidth
                && point.y >= geometry.thumbY && point.y <= geometry.thumbY + geometry.thumbHeight;
    }

    public double getScrollLeft() {
        return owner.scrollLeft;
    }

    public void setScrollLeft(double value) {
        owner.targetScrollLeft = applyOverscroll(value, getHorizontalScrollLimit());
    }

    public double getScrollTop() {
        return owner.scrollTop;
    }

    public void setScrollTop(double value) {
        owner.targetScrollTop = applyOverscroll(value, getVerticalScrollLimit());
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
        return allowsUserScroll(false);
    }

    public boolean canScrollHorizontally() {
        return allowsUserScroll(true);
    }

    private boolean allowsUserScroll(boolean horizontal) {
        var overflow = ScrollbarStyle.overflow(owner, horizontal);
        return overflow == Interaction.Overflow.AUTO || overflow == Interaction.Overflow.SCROLL
                || (overflow == Interaction.Overflow.VISIBLE && ScrollbarStyle.isViewport(owner));
    }

    public boolean hasVerticalScrollRange() {
        return canScrollVertically() && getVerticalScrollLimit() > 0.5;
    }

    public boolean hasHorizontalScrollRange() {
        return canScrollHorizontally() && getHorizontalScrollLimit() > 0.5;
    }

    public boolean stepRender() {
        if (!needsRenderStep()) {
            lastRenderStepNs = 0L;
            lastRenderStepDeltaLeft = 0;
            lastRenderStepDeltaTop = 0;
            return false;
        }

        double previousLeft = owner.scrollLeft;
        double previousTop = owner.scrollTop;
        double frameScale = consumeFrameScale();
        stepHorizontalScroll(frameScale);
        stepVerticalScroll(frameScale);
        lastRenderStepDeltaLeft = owner.scrollLeft - previousLeft;
        lastRenderStepDeltaTop = owner.scrollTop - previousTop;
        return Double.compare(previousLeft, owner.scrollLeft) != 0
                || Double.compare(previousTop, owner.scrollTop) != 0;
    }

    /**
     * 最近一次 {@link #stepRender()} 产生的 scrollLeft 位移；供滚动平移快速路径使用。
     */
    public double getLastRenderStepDeltaLeft() {
        return lastRenderStepDeltaLeft;
    }

    /**
     * 最近一次 {@link #stepRender()} 产生的 scrollTop 位移；供滚动平移快速路径使用。
     */
    public double getLastRenderStepDeltaTop() {
        return lastRenderStepDeltaTop;
    }

    public boolean needsRenderStep() {
        return !isScrollSettled(owner.scrollLeft, owner.targetScrollLeft)
                || !isScrollSettled(owner.scrollTop, owner.targetScrollTop)
                || !isScrollSettled(owner.scrollLeft, clampScrollTarget(owner.scrollLeft, getHorizontalScrollLimit()))
                || !isScrollSettled(owner.scrollTop, clampScrollTarget(owner.scrollTop, getVerticalScrollLimit()));
    }

    public boolean handleMouseDown(MouseEvent event) {
        if (event == null || event.button != 0) return false;
        if (!ScrollbarStyle.mayPaint(owner)) return false;

        Rect rect = CommittedGeometry.committedRect(owner);
        AxisGeometry vertical = axisGeometry(true, rect);
        AxisGeometry horizontal = axisGeometry(false, rect);
        double[] point = CommittedGeometry.toLocalPoint(owner, event.clientX, event.clientY);
        AxisGeometry hit = contains(vertical, point[0], point[1])
                ? vertical : contains(horizontal, point[0], point[1]) ? horizontal : null;
        if (hit == null) return false;

        scrollbarPointerActive = true;
        double beforeLeft = owner.getTargetScrollLeft();
        double beforeTop = owner.getTargetScrollTop();
        double pointer = hit.vertical ? point[1] : point[0];
        if (pointer >= hit.thumbStart() && pointer <= hit.thumbEnd()) {
            dragAxis = hit.vertical ? DragAxis.VERTICAL : DragAxis.HORIZONTAL;
            dragPointerOffset = pointer - hit.thumbStart();
        } else {
            double page = hit.vertical ? getScrollportHeight() : getScrollportWidth();
            double direction = pointer < hit.thumbStart() ? -1.0 : 1.0;
            if (hit.vertical) {
                setScrollTop(clampScrollTarget(owner.getTargetScrollTop() + direction * page,
                        getVerticalScrollLimit()));
            } else {
                setScrollLeft(clampScrollTarget(owner.getTargetScrollLeft() + direction * page,
                        getHorizontalScrollLimit()));
            }
            owner.dispatchScrollEventIfChanged(beforeLeft, beforeTop);
        }
        return true;
    }

    public boolean handleMouseMove(MouseEvent event) {
        if (event == null || !scrollbarPointerActive) return false;
        if (dragAxis == DragAxis.NONE) return true;
        AxisGeometry geometry = axisGeometry(dragAxis == DragAxis.VERTICAL, CommittedGeometry.committedRect(owner));
        if (geometry == null) return true;

        double[] point = CommittedGeometry.toLocalPoint(owner, event.clientX, event.clientY);
        double pointer = geometry.vertical ? point[1] : point[0];
        double travel = geometry.trackLength() - geometry.thumbLength();
        double ratio = travel <= 0 ? 0 : (pointer - geometry.trackStart() - dragPointerOffset) / travel;
        ratio = Math.max(0, Math.min(1, ratio));
        double limit = geometry.vertical ? getVerticalScrollLimit() : getHorizontalScrollLimit();
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
        var metrics = ScrollLayout.metrics(owner);
        if (metrics.verticalGutter() <= 0 && metrics.horizontalGutter() <= 0) return;

        trackThumbHoverRepaint();
        Position bodyPos = rectRenderer.getBodyRectPosition();
        Size bodySize = rectRenderer.getBodyRectSize();
        if (metrics.vertical() && metrics.verticalGutter() > 0) drawVerticalScrollbar(poseStack, bodyPos, bodySize);
        if (metrics.horizontal() && metrics.horizontalGutter() > 0) drawHorizontalScrollbar(poseStack, bodyPos, bodySize);
    }

    // ------------------------------------------------------------------
    // CSS 接入：scrollbar-width / scrollbar-color / ::-webkit-scrollbar*
    // ------------------------------------------------------------------

    private String pseudoValue(PseudoElement kind, String property, String fallback) {
        return ScrollbarStyle.pseudoValue(owner, kind, property, fallback);
    }

    private double scrollbarMinThumbLength() {
        return ScrollbarStyle.devicePixels(owner, SCROLLBAR_MIN_THUMB_LENGTH);
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
        return Math.max(0, owner.scrollWidth - getScrollportWidth());
    }

    private double getVerticalScrollLimit() {
        return Math.max(0, owner.scrollHeight - getScrollportHeight());
    }

    private double getScrollportWidth() { return ScrollLayout.metrics(owner).port().width(); }

    private double getScrollportHeight() { return ScrollLayout.metrics(owner).port().height(); }

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
        var metrics = ScrollLayout.metrics(owner);
        if (rect == null || (vertical ? !metrics.vertical() || metrics.verticalGutter() <= 0
                : !metrics.horizontal() || metrics.horizontalGutter() <= 0)) return null;
        return axisGeometry(vertical, rect.getBodyRectPosition(), rect.getBodyRectSize());
    }

    /**
     * 按轴参数化的轨道/滑块几何：vertical=true 对应原 verticalGeometry，否则 horizontalGeometry。
     */
    private AxisGeometry axisGeometry(boolean vertical, Position bodyPos, Size bodySize) {
        double scrollport = vertical ? getScrollportHeight() : getScrollportWidth();
        double trackSize = ScrollbarStyle.trackSize(owner);
        double trackInset = ScrollbarStyle.inset(trackSize);
        double crossGutter = vertical ? ScrollLayout.gutter(owner, true) : ScrollLayout.gutter(owner, false);
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
        double hitX = vertical ? bodyPos.x + bodySize.width() - ScrollLayout.gutter(owner, !vertical) : bodyPos.x;
        double hitY = vertical ? bodyPos.y : bodyPos.y + bodySize.height() - ScrollLayout.gutter(owner, !vertical);
        double hitWidth = vertical ? ScrollLayout.gutter(owner, !vertical) : Math.max(0, bodySize.width() - ScrollLayout.gutter(owner, false));
        double hitHeight = vertical ? Math.max(0, bodySize.height() - ScrollLayout.gutter(owner, true)) : ScrollLayout.gutter(owner, !vertical);
        return new AxisGeometry(vertical, trackX, trackY, trackWidth, trackHeight,
                thumbX, thumbY, thumbWidth, thumbHeight, hitX, hitY, hitWidth, hitHeight);
    }

    private void setScrollImmediate(boolean vertical, double value) {
        double beforeLeft = owner.getTargetScrollLeft();
        double beforeTop = owner.getTargetScrollTop();
        double previousLeft = owner.scrollLeft;
        double previousTop = owner.scrollTop;
        if (vertical) {
            double clamped = clampScrollTarget(value, getVerticalScrollLimit());
            owner.scrollTop = clamped;
            owner.targetScrollTop = clamped;
        } else {
            double clamped = clampScrollTarget(value, getHorizontalScrollLimit());
            owner.scrollLeft = clamped;
            owner.targetScrollLeft = clamped;
        }
        lastRenderStepNs = 0L;
        if (owner.document != null) {
            owner.document.commitImmediateScroll(owner, owner.scrollLeft - previousLeft, owner.scrollTop - previousTop);
        } else if (owner.scrollLeft != previousLeft || owner.scrollTop != previousTop) {
            owner.getLayout().invalidateScrollVersion();
        }
        owner.dispatchScrollEventIfChanged(beforeLeft, beforeTop);
    }

    private void drawScrollbarTrackAndThumb(PoseStack poseStack,
                                            float trackX, float trackY, float trackWidth, float trackHeight,
                                            float thumbX, float thumbY, float thumbWidth, float thumbHeight) {
        float trackRadius = scrollbarCornerRadius(trackWidth, trackHeight);
        float thumbRadius = scrollbarCornerRadius(thumbWidth, thumbHeight);
        int trackColor = scrollbarTrackColor();
        int thumbColor = scrollbarThumbColor();
        poseStack.pushPose();
        try {
            if ((trackColor >>> 24) != 0) {
                Graph.drawUnifiedRoundedRect(PoseMatrices.of(poseStack), trackX, trackY, trackWidth, trackHeight,
                        new float[]{trackRadius, trackRadius, trackRadius, trackRadius}, trackColor);
            }
            Base.offsetPaintDepth(poseStack, SCROLLBAR_THUMB_DEPTH_FRACTION);
            if ((thumbColor >>> 24) != 0) {
                Graph.drawUnifiedRoundedRect(PoseMatrices.of(poseStack), thumbX, thumbY, thumbWidth, thumbHeight,
                        new float[]{thumbRadius, thumbRadius, thumbRadius, thumbRadius}, thumbColor);
            }
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 圆角：优先 {@code ::-webkit-scrollbar-thumb { border-radius }} / track 的
     * border-radius；未声明时保持默认胶囊形（短边一半）。
     */
    private float scrollbarCornerRadius(float width, float height) {
        PseudoElement kind = PseudoElement.SCROLLBAR_THUMB;
        Double parsed = ScrollbarStyle.parseCssLength(pseudoValue(kind, "borderRadius", null));
        if (parsed == null) {
            parsed = ScrollbarStyle.parseCssLength(pseudoValue(PseudoElement.SCROLLBAR_TRACK, "borderRadius", null));
        }
        if (parsed != null) {
            double maxRadius = Math.min(width, height) / 2.0d;
            return (float) Math.max(0d, Math.min(parsed, maxRadius));
        }
        return Math.min(width, height) / 2f;
    }

    /**
     * 轨道颜色：{@code ::-webkit-scrollbar-track { background-color }} 优先，
     * 其次 {@code scrollbar-color} 的第二个 token，最后回退到默认值。
     */
    private int scrollbarTrackColor() {
        String declared = pseudoValue(PseudoElement.SCROLLBAR_TRACK, "backgroundColor", null);
        if (declared == null) {
            declared = com.sighs.apricityui.style.Interaction.scrollbarTrackColor(
                    owner.getComputedStyle().scrollbarColor);
        }
        return declared == null ? SCROLLBAR_DEFAULT_TRACK_COLOR : com.sighs.apricityui.parser.Color.parse(declared);
    }

    /**
     * 滑块颜色：悬停态 {@code ::-webkit-scrollbar-thumb:hover} 优先（当指针
     * 位于滑块上），其次 {@code ::-webkit-scrollbar-thumb { background-color }}，
     * 再次 {@code scrollbar-color} 的第一个 token，最后回退到默认值。
     */
    private int scrollbarThumbColor() {
        String declared = null;
        if (isThumbHovered()) {
            declared = pseudoValue(PseudoElement.SCROLLBAR_THUMB_HOVER, "backgroundColor", null);
        }
        if (declared == null) {
            declared = pseudoValue(PseudoElement.SCROLLBAR_THUMB, "backgroundColor", null);
        }
        if (declared == null) {
            declared = com.sighs.apricityui.style.Interaction.scrollbarThumbColor(
                    owner.getComputedStyle().scrollbarColor);
        }
        return declared == null ? SCROLLBAR_DEFAULT_THUMB_COLOR : com.sighs.apricityui.parser.Color.parse(declared);
    }

    /**
     * 悬停态只在状态翻转的那一帧标脏，避免每帧都触发重绘；离开滑块时同样
     * 需要一次重绘才能把颜色恢复成基础态。
     */
    private void trackThumbHoverRepaint() {
        boolean hovered = isThumbHovered();
        if (hovered == lastThumbHovered) return;
        lastThumbHovered = hovered;
        if (owner.document != null) {
            owner.document.markDirty(owner, Drawer.REPAINT);
        }
    }

    /**
     * 指针是否落在垂直或水平滑块上（用于 :hover 变体）。
     */
    public boolean isThumbHovered() {
        if (owner.document == null) return false;
        Position mouse = com.sighs.apricityui.render.Operation.getMousePosition();
        if (mouse == null) mouse = com.sighs.apricityui.render.Operation.getMousePositionDirectly();
        if (mouse == null) return false;
        Position local = owner.document.screenToDocumentPosition(mouse);
        if (local == null) return false;
        Rect rect = CommittedGeometry.committedRect(owner);
        return isPointerOnThumb(axisGeometry(true, rect), local) || isPointerOnThumb(axisGeometry(false, rect), local);
    }

    private boolean isScrollSettled(double current, double target) {
        return Math.abs(current - target) <= SCROLL_STOP_EPSILON;
    }

    private enum DragAxis {
        NONE,
        VERTICAL,
        HORIZONTAL
    }

    private record ScrollStep(double current, double target, boolean moving) {
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

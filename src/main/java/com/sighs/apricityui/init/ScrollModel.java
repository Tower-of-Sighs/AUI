package com.sighs.apricityui.init;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;

final class ScrollModel {
    private static final double SCROLL_EASING_FACTOR = 0.2;
    private static final double SCROLL_OVERSCROLL_DAMPING = 0.4;
    private static final double SCROLL_STOP_EPSILON = 0.01;
    private static final double BASE_FRAME_MS = 16.6666666667;
    private static final double MAX_FRAME_MS = 50.0;

    private final Element owner;
    private long lastRenderStepNs;

    ScrollModel(Element owner) {
        this.owner = owner;
    }

    void setScrollLeft(double value) {
        owner.targetScrollLeft = applyOverscroll(value, getHorizontalScrollLimit());
    }

    void setScrollTop(double value) {
        owner.targetScrollTop = applyOverscroll(value, getVerticalScrollLimit());
    }

    double getScrollLeft() {
        return owner.scrollLeft;
    }

    double getScrollTop() {
        return owner.scrollTop;
    }

    double getTargetScrollLeft() {
        return owner.targetScrollLeft;
    }

    double getTargetScrollTop() {
        return owner.targetScrollTop;
    }

    boolean canScroll() {
        return canScrollVertically() || canScrollHorizontally();
    }

    boolean canScrollVertically() {
        return Interaction.allowsUserScrollY(owner.getComputedStyle());
    }

    boolean canScrollHorizontally() {
        return Interaction.allowsUserScrollX(owner.getComputedStyle());
    }

    boolean hasVerticalScrollRange() {
        if (!Interaction.allowsUserScrollY(owner.getComputedStyle())) return false;
        refreshScrollMetrics();
        return getVerticalScrollLimitFromMetrics() > 0.5;
    }

    boolean hasHorizontalScrollRange() {
        if (!Interaction.allowsUserScrollX(owner.getComputedStyle())) return false;
        refreshScrollMetrics();
        return getHorizontalScrollLimitFromMetrics() > 0.5;
    }

    boolean tick() {
        return false;
    }

    boolean stepRender() {
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

    boolean needsRenderStep() {
        return !isScrollSettled(owner.scrollLeft, owner.targetScrollLeft)
                || !isScrollSettled(owner.scrollTop, owner.targetScrollTop);
    }

    void drawScrollbar(PoseStack poseStack, Rect rectRenderer) {
        if (!hasVerticalScrollRange()) return;
        double innerHeight = Box.of(owner).innerSize().height();
        double innerWidth = Box.of(owner).innerSize().width();
        if (owner.scrollHeight <= innerHeight + 0.5 || innerHeight <= 0 || innerWidth <= 0) return;

        Position bodyPos = rectRenderer.getBodyRectPosition();
        Size bodySize = rectRenderer.getBodyRectSize();
        float trackWidth = 4f;
        float trackPadding = 1f;
        float trackX = (float) (bodyPos.x + bodySize.width() - trackWidth - trackPadding);
        float trackY = (float) (bodyPos.y + trackPadding);
        float trackH = (float) Math.max(8, bodySize.height() - trackPadding * 2);
        float thumbH = (float) Math.max(10, trackH * (innerHeight / Math.max(innerHeight, owner.scrollHeight)));
        float maxThumbTravel = Math.max(0, trackH - thumbH);
        double scrollLimit = Math.max(1, owner.scrollHeight - innerHeight);
        float thumbY = trackY + (float) (Math.max(0, Math.min(getScrollTop(), scrollLimit)) / scrollLimit) * maxThumbTravel;

        float radius = trackWidth / 2f;
        Graph.drawUnifiedRoundedRect(poseStack.last().pose(), trackX, trackY, trackWidth, trackH,
                new float[]{radius, radius, radius, radius}, 0x18B96A91);
        Graph.drawUnifiedRoundedRect(poseStack.last().pose(), trackX, thumbY, trackWidth, thumbH,
                new float[]{radius, radius, radius, radius}, 0xB39F9F9F);
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
        refreshScrollMetrics();
        return getHorizontalScrollLimitFromMetrics();
    }

    private double getHorizontalScrollLimitFromMetrics() {
        return Math.max(0, owner.scrollWidth - Box.of(owner).innerSize().width());
    }

    private double getVerticalScrollLimit() {
        refreshScrollMetrics();
        return getVerticalScrollLimitFromMetrics();
    }

    private double getVerticalScrollLimitFromMetrics() {
        return Math.max(0, owner.scrollHeight - Box.of(owner).innerSize().height());
    }

    private void refreshScrollMetrics() {
        if (owner instanceof AbstractText) return;
        Size contentSize = Size.getContentSize(owner);
        owner.scrollWidth = contentSize.width();
        owner.scrollHeight = contentSize.height();
    }

    private boolean isScrollSettled(double current, double target) {
        return Math.abs(current - target) <= SCROLL_STOP_EPSILON;
    }

    private record ScrollStep(double current, double target, boolean moving) {
    }
}

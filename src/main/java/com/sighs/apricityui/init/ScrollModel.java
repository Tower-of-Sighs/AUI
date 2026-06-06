package com.sighs.apricityui.init;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;

final class ScrollModel {
    private static final double SCROLL_EASING_FACTOR = 0.2;
    private static final double SCROLL_OVERSCROLL_DAMPING = 0.4;
    private static final double SCROLL_INTERPOLATION_FRAME_MS = 50.0;
    private static final double SCROLL_STOP_EPSILON = 0.01;

    private final Element owner;
    private long lastTickTime;

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
        return interpolateScroll(owner.scrollLeft, owner.targetScrollLeft);
    }

    double getScrollTop() {
        return interpolateScroll(owner.scrollTop, owner.targetScrollTop);
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

    boolean tick() {
        lastTickTime = System.currentTimeMillis();
        boolean scrollingX = stepHorizontalScroll();
        boolean scrollingY = stepVerticalScroll();
        return scrollingX || scrollingY;
    }

    void drawScrollbar(PoseStack poseStack, Rect rectRenderer) {
        if (!canScrollVertically()) return;
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
        float thumbY = trackY + (float) (Math.max(0, Math.min(owner.scrollTop, scrollLimit)) / scrollLimit) * maxThumbTravel;

        float radius = trackWidth / 2f;
        Graph.drawUnifiedRoundedRect(poseStack.last().pose(), trackX, trackY, trackWidth, trackH,
                new float[]{radius, radius, radius, radius}, 0x18B96A91);
        Graph.drawUnifiedRoundedRect(poseStack.last().pose(), trackX, thumbY, trackWidth, thumbH,
                new float[]{radius, radius, radius, radius}, 0xB39F9F9F);
    }

    private boolean stepHorizontalScroll() {
        ScrollStep step = stepScrollAxis(owner.scrollLeft, owner.targetScrollLeft, getHorizontalScrollLimit());
        owner.scrollLeft = step.current();
        owner.targetScrollLeft = step.target();
        return step.moving();
    }

    private boolean stepVerticalScroll() {
        ScrollStep step = stepScrollAxis(owner.scrollTop, owner.targetScrollTop, getVerticalScrollLimit());
        owner.scrollTop = step.current();
        owner.targetScrollTop = step.target();
        return step.moving();
    }

    private ScrollStep stepScrollAxis(double current, double target, double limit) {
        double clampedTarget = clampScrollTarget(target, limit);
        if (target < 0 || target > limit) {
            target = target + (clampedTarget - target) * 0.28;
        }
        if (!isScrollSettled(current, target)) {
            current = current + (target - current) * SCROLL_EASING_FACTOR;
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

    private double interpolateScroll(double current, double target) {
        if (isScrollSettled(current, target)) return target;
        double process = (System.currentTimeMillis() - lastTickTime) / SCROLL_INTERPOLATION_FRAME_MS;
        process = Math.max(0, Math.min(1, process));
        double next = current + (target - current) * SCROLL_EASING_FACTOR;
        return current + (next - current) * process;
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
        return Math.max(0, owner.scrollWidth - Box.of(owner).innerSize().width());
    }

    private double getVerticalScrollLimit() {
        return Math.max(0, owner.scrollHeight - Box.of(owner).innerSize().height());
    }

    private boolean isScrollSettled(double current, double target) {
        return Math.abs(current - target) <= SCROLL_STOP_EPSILON;
    }

    private record ScrollStep(double current, double target, boolean moving) {
    }
}

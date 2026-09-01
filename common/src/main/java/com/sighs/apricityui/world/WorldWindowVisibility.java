package com.sighs.apricityui.world;

public final class WorldWindowVisibility {
    private WorldWindowVisibility() {
    }

    public static int resolveDisplayDistance(int configuredDefault, Integer override) {
        return override != null ? Math.max(0, override) : Math.max(0, configuredDefault);
    }

    public static boolean isWithinDisplayDistance(double distanceSquared, int maxDisplayDistance) {
        if (maxDisplayDistance == Integer.MAX_VALUE) return true;
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0d) return false;
        double limit = maxDisplayDistance;
        return distanceSquared <= limit * limit;
    }

    /**
     * 带迟滞的显示距离可见性判断。
     *
     * <p>WorldWindow 渲染有较高的固定成本（Document + stencil），玩家正好站在
     * {@code maxDisplayDistance} 边界时不应让窗口每帧在显示/消失之间抖动。
     * 当距离超出上限但在 {@code hysteresisMargin} 缓冲带内时维持上一帧状态，
     * 只有明显越界后才隐藏。</p>
     */
    public static boolean resolveDisplayVisibility(double distanceSquared, int maxDisplayDistance,
                                                   boolean previousVisible, double hysteresisMargin) {
        if (maxDisplayDistance == Integer.MAX_VALUE) return true;
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0d) return false;

        double limit = maxDisplayDistance;
        if (distanceSquared <= limit * limit) return true;

        double margin = Math.max(0.0d, hysteresisMargin);
        double bandLimit = limit + margin;
        if (distanceSquared <= bandLimit * bandLimit) return previousVisible;

        return false;
    }

    public static WorldWindowDisplayPrecision resolveDisplayPrecision(
            double distanceSquared,
            WorldWindowDisplayPrecision configured,
            int fullDetailDistance,
            int reducedDetailDistance
    ) {
        return resolveDisplayPrecision(
                distanceSquared,
                configured,
                true,
                fullDetailDistance,
                reducedDetailDistance
        );
    }

    public static WorldWindowDisplayPrecision resolveDisplayPrecision(
            double distanceSquared,
            WorldWindowDisplayPrecision configured,
            boolean automaticEnabled,
            int fullDetailDistance,
            int reducedDetailDistance
    ) {
        WorldWindowDisplayPrecision mode = configured == null
                ? WorldWindowDisplayPrecision.AUTO : configured;
        if (mode != WorldWindowDisplayPrecision.AUTO) return mode;
        if (!automaticEnabled) return WorldWindowDisplayPrecision.FULL;
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0d) {
            return WorldWindowDisplayPrecision.MINIMAL;
        }

        int fullDistance = Math.max(0, fullDetailDistance);
        int reducedDistance = Math.max(fullDistance, reducedDetailDistance);
        double distance = Math.sqrt(distanceSquared);
        if (distance <= fullDistance) return WorldWindowDisplayPrecision.FULL;
        if (distance <= reducedDistance) return WorldWindowDisplayPrecision.REDUCED;
        return WorldWindowDisplayPrecision.MINIMAL;
    }
}

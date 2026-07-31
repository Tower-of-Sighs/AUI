package com.sighs.apricityui.instance;

final class WorldWindowVisibility {
    private WorldWindowVisibility() {
    }

    static int resolveDisplayDistance(int configuredDefault, Integer override) {
        return override != null ? Math.max(0, override) : Math.max(0, configuredDefault);
    }

    static boolean isWithinDisplayDistance(double distanceSquared, int maxDisplayDistance) {
        if (maxDisplayDistance == Integer.MAX_VALUE) return true;
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0d) return false;
        double limit = maxDisplayDistance;
        return distanceSquared <= limit * limit;
    }

    static WorldWindowDisplayPrecision resolveDisplayPrecision(
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

    static WorldWindowDisplayPrecision resolveDisplayPrecision(
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

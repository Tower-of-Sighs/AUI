package com.sighs.apricityui.layout;

/** Loader-independent automatic column distribution, in border-box pixels. */
final class TableColumns {
    private TableColumns() {}

    static double[] distribute(double[] minimum, double[] preferred, double requested) {
        double min = 0, max = 0;
        for (int i = 0; i < minimum.length; i++) {
            min += minimum[i];
            max += Math.max(minimum[i], preferred[i]);
        }
        double target = Double.isNaN(requested) ? max : Math.max(min, requested);
        double[] widths = new double[minimum.length];
        for (int i = 0; i < widths.length; i++) {
            double upper = Math.max(minimum[i], preferred[i]);
            if (target <= max) {
                widths[i] = minimum[i] + (max == min ? 0
                        : (target - min) * (upper - minimum[i]) / (max - min));
            } else {
                widths[i] = upper + (target - max) * (max == 0 ? 1d / widths.length : upper / max);
            }
        }
        return widths;
    }
}

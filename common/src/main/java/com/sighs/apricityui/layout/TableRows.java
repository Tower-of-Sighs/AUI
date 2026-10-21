package com.sighs.apricityui.layout;

/** Rowspan height distribution without DOM or loader dependencies. */
final class TableRows {
    private TableRows() {}

    static void distribute(double[] heights, boolean[] constrained, boolean[] starts,
                           int first, int span, double required) {
        double total = 0, autoHeight = 0;
        int originating = 0;
        for (int i = first; i < first + span; i++) {
            total += heights[i];
            if (!constrained[i]) autoHeight += heights[i];
            if (i > first && starts[i]) originating++;
        }
        double extra = required - total;
        if (extra <= 0) return;
        if (originating > 0) {
            for (int i = first + 1; i < first + span; i++) if (starts[i]) heights[i] += extra / originating;
        } else if (autoHeight > 0) {
            for (int i = first; i < first + span; i++) if (!constrained[i]) heights[i] += extra * heights[i] / autoHeight;
        } else if (total == 0) {
            heights[first + span - 1] += extra;
        } else {
            for (int i = first; i < first + span; i++) heights[i] += extra * heights[i] / total;
        }
    }
}

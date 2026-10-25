package com.sighs.apricityui.layout;

/**
 * Loader-independent automatic table column constraints, in border-box pixels.
 */
final class TableColumns {
    private TableColumns() {
    }

    static void encompass(Column column, double minimum, double preferred, double percent, boolean constrained) {
        column.active = true;
        column.minimum = Math.max(column.minimum, minimum);
        column.preferred = Math.max(column.preferred, Math.max(minimum, preferred));
        column.percent = Math.max(column.percent, percent);
        column.constrained |= constrained;
    }

    static void span(Column[] columns, int start, int count, double minimum, double preferred,
                     double percent, boolean constrained) {
        Column[] covered = java.util.Arrays.copyOfRange(columns, start, start + count);
        double existingPercent = 0, unassignedWidth = 0;
        int unassigned = 0;
        for (Column c : covered) {
            if (!c.active) continue;
            if (c.percent >= 0) existingPercent += c.percent;
            else {
                unassigned++;
                unassignedWidth += c.preferred;
            }
        }
        if (percent > existingPercent && unassigned > 0) {
            for (Column c : covered)
                if (c.active && c.percent < 0) {
                    c.percent = (percent - existingPercent) * (unassignedWidth > 0
                            ? c.preferred / unassignedWidth : 1d / unassigned);
                }
        }
        double[] sizes = distribute(covered, minimum, true);
        for (int i = 0; i < count; i++) covered[i].minimum = Math.max(covered[i].minimum, sizes[i]);
        sizes = distribute(covered, preferred, constrained);
        for (int i = 0; i < count; i++) {
            covered[i].preferred = Math.max(covered[i].minimum, Math.max(covered[i].preferred, sizes[i]));
        }
    }

    static void clampPercentages(Column[] columns) {
        double total = 0;
        for (Column c : columns)
            if (c.percent >= 0) {
                c.percent = Math.min(c.percent, Math.max(0, 1 - total));
                total += c.percent;
            }
    }

    static double preferredWidth(Column[] columns) {
        double result = 0, percentTotal = 0, nonPercent = 0, percentWidth = 0;
        for (Column c : columns)
            if (c.active) {
                result += Math.max(c.minimum, c.preferred);
                if (c.percent > 0) {
                    percentWidth = Math.max(percentWidth, c.preferred / c.percent);
                    percentTotal += c.percent;
                } else nonPercent += c.preferred;
            }
        if (percentTotal > 0 && percentTotal < 1) result = Math.max(result, nonPercent / (1 - percentTotal));
        return Math.max(result, percentWidth);
    }

    static double[] distribute(Column[] columns, double requested, boolean constrained) {
        double target = Double.isNaN(requested) ? preferredWidth(columns) : Math.max(0, requested);
        double[][] guesses = new double[4][columns.length];
        double[] totals = new double[4];
        for (int i = 0; i < columns.length; i++) {
            Column c = columns[i];
            if (!c.active) continue;
            double max = Math.max(c.minimum, c.preferred);
            guesses[0][i] = c.minimum;
            guesses[1][i] = c.percent >= 0 ? Math.max(c.minimum, c.percent * target) : c.minimum;
            guesses[2][i] = c.percent >= 0 ? guesses[1][i] : c.constrained ? max : c.minimum;
            guesses[3][i] = c.percent >= 0 ? guesses[1][i] : max;
            for (int g = 0; g < 4; g++) totals[g] += guesses[g][i];
        }
        target = Math.max(target, totals[0]);
        if (target <= totals[0]) return guesses[0];
        for (int g = 1; g < 4; g++)
            if (target <= totals[g]) {
                double ratio = (target - totals[g - 1]) / (totals[g] - totals[g - 1]);
                for (int i = 0; i < columns.length; i++)
                    guesses[g - 1][i] += (guesses[g][i] - guesses[g - 1][i]) * ratio;
                return guesses[g - 1];
            }
        double[] result = guesses[3];
        // Blink grows automatic columns first, then fixed columns, then percentages.
        int grow = -1;
        for (Column c : columns)
            if (c.active && c.percent < 0) {
                if (!c.constrained) {
                    grow = 0;
                    break;
                }
                if (constrained) grow = 1;
            }
        if (grow == -1) grow = 2;
        double weight = 0;
        int count = 0;
        for (Column c : columns)
            if (grows(c, grow)) {
                count++;
                weight += grow == 2 ? c.percent : c.preferred;
            }
        for (int i = 0; i < columns.length; i++)
            if (grows(columns[i], grow)) {
                double share = weight > 0 ? (grow == 2 ? columns[i].percent : columns[i].preferred) / weight : 1d / count;
                result[i] += (target - totals[3]) * share;
            }
        return result;
    }

    private static boolean grows(Column c, int group) {
        return c.active && (group == 2 ? c.percent >= 0 : c.percent < 0 && c.constrained == (group == 1));
    }

    static final class Column {
        double minimum, preferred;
        double percent = -1;
        boolean constrained, active;
    }
}

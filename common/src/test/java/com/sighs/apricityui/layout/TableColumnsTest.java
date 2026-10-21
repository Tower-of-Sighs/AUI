package com.sighs.apricityui.layout;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TableColumnsTest {
    private double[] distribute(double[] minimum, double[] preferred, double width) {
        TableColumns.Column[] columns = new TableColumns.Column[minimum.length];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = new TableColumns.Column();
            TableColumns.encompass(columns[i], minimum[i], preferred[i], -1, false);
        }
        return TableColumns.distribute(columns, width, true);
    }

    @Test void distributesBetweenMinimumAndPreferred() {
        assertArrayEquals(new double[]{30, 60}, distribute(
                new double[]{20, 40}, new double[]{40, 80}, 90), 1e-9);
    }

    @Test void preservesMinimumWhenContainerIsTooNarrow() {
        assertArrayEquals(new double[]{20, 40}, distribute(
                new double[]{20, 40}, new double[]{40, 80}, 10), 1e-9);
    }

    @Test void expandsProportionallyAndHandlesEmptyColumns() {
        assertArrayEquals(new double[]{60, 120}, distribute(
                new double[]{20, 40}, new double[]{40, 80}, 180), 1e-9);
        assertArrayEquals(new double[]{50, 50}, distribute(new double[2], new double[2], 100));
        assertEquals(0, distribute(new double[0], new double[0], 100).length);
    }

    @Test void intrinsicWidthUsesPreferredButNeverBelowMinimum() {
        assertArrayEquals(new double[]{40, 60}, distribute(
                new double[]{40, 20}, new double[]{10, 60}, Double.NaN), 1e-9);
    }

    @Test void fixedAndPercentageGuessesPrecedeAutomaticExpansion() {
        TableColumns.Column[] columns = columns(3);
        TableColumns.encompass(columns[0], 10, 50, .4, false);
        TableColumns.encompass(columns[1], 10, 60, -1, true);
        TableColumns.encompass(columns[2], 10, 20, -1, false);
        assertArrayEquals(new double[]{120, 60, 120}, TableColumns.distribute(columns, 300, true), 1e-9);
        assertArrayEquals(new double[]{20, 20, 10}, TableColumns.distribute(columns, 50, true), 1e-9);
    }

    @Test void spanDistributesToStartingColumnsAndPreservesMinimums() {
        TableColumns.Column[] columns = columns(3);
        TableColumns.encompass(columns[0], 20, 20, -1, false);
        TableColumns.encompass(columns[2], 40, 40, -1, false);
        TableColumns.span(columns, 0, 3, 120, 180, -1, false);
        assertArrayEquals(new double[]{40, 0, 80}, TableColumns.distribute(columns, 0, true), 1e-9);
        assertArrayEquals(new double[]{60, 0, 120}, TableColumns.distribute(columns, Double.NaN, true), 1e-9);
    }

    @Test void percentagesFromSpansRespectExistingPercentagesAndClampInColumnOrder() {
        TableColumns.Column[] columns = columns(3);
        TableColumns.encompass(columns[0], 10, 10, .4, false);
        TableColumns.encompass(columns[1], 20, 20, -1, false);
        TableColumns.encompass(columns[2], 20, 20, .8, false);
        TableColumns.span(columns, 0, 2, 30, 30, .7, false);
        TableColumns.clampPercentages(columns);
        assertEquals(.4, columns[0].percent, 1e-9);
        assertEquals(.3, columns[1].percent, 1e-9);
        assertEquals(.3, columns[2].percent, 1e-9);
    }

    private TableColumns.Column[] columns(int count) {
        TableColumns.Column[] result = new TableColumns.Column[count];
        for (int i = 0; i < count; i++) result[i] = new TableColumns.Column();
        return result;
    }
}

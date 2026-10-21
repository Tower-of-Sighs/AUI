package com.sighs.apricityui.layout;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TableColumnsTest {
    @Test void distributesBetweenMinimumAndPreferred() {
        assertArrayEquals(new double[]{30, 60}, TableColumns.distribute(
                new double[]{20, 40}, new double[]{40, 80}, 90), 1e-9);
    }

    @Test void preservesMinimumWhenContainerIsTooNarrow() {
        assertArrayEquals(new double[]{20, 40}, TableColumns.distribute(
                new double[]{20, 40}, new double[]{40, 80}, 10), 1e-9);
    }

    @Test void expandsProportionallyAndHandlesEmptyColumns() {
        assertArrayEquals(new double[]{60, 120}, TableColumns.distribute(
                new double[]{20, 40}, new double[]{40, 80}, 180), 1e-9);
        assertArrayEquals(new double[]{50, 50}, TableColumns.distribute(new double[2], new double[2], 100));
        assertEquals(0, TableColumns.distribute(new double[0], new double[0], 100).length);
    }

    @Test void intrinsicWidthUsesPreferredButNeverBelowMinimum() {
        assertArrayEquals(new double[]{40, 60}, TableColumns.distribute(
                new double[]{40, 20}, new double[]{10, 60}, Double.NaN), 1e-9);
    }
}

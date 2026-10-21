package com.sighs.apricityui.layout;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TableRowsTest {
    @Test void growsAutomaticRowsBeforeConstrainedRows() {
        double[] heights = {30, 20, 10};
        TableRows.distribute(heights, new boolean[]{true, false, false}, new boolean[3], 0, 3, 120);
        assertArrayEquals(new double[]{30, 60, 30}, heights, 1e-9);
    }

    @Test void emptySpanGrowsLastRowAndSatisfiedSpanStaysUnchanged() {
        double[] heights = new double[3];
        TableRows.distribute(heights, new boolean[3], new boolean[3], 0, 3, 90);
        assertArrayEquals(new double[]{0, 0, 90}, heights);
        TableRows.distribute(heights, new boolean[3], new boolean[3], 0, 3, 30);
        assertArrayEquals(new double[]{0, 0, 90}, heights);
    }

    @Test void originatingSpansTakePriorityOverAutomaticRows() {
        double[] heights = {10, 20, 30};
        TableRows.distribute(heights, new boolean[3], new boolean[]{true, true, false}, 0, 3, 120);
        assertArrayEquals(new double[]{10, 80, 30}, heights);
    }
}

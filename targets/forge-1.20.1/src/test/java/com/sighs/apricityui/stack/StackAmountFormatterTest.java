package com.sighs.apricityui.stack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StackAmountFormatterTest {
    @Test
    void formatsSlotAmountsAndFluidUnits() {
        assertEquals("999", StackAmountFormatter.format(999L, 1L));
        assertEquals("1K", StackAmountFormatter.format(1_000L, 1L));
        assertEquals("1.2K", StackAmountFormatter.format(1_234L, 1L));
        assertEquals("999K", StackAmountFormatter.format(999_999L, 1L));
        assertEquals("1M", StackAmountFormatter.format(1_000_000L, 1L));
        assertEquals("1G", StackAmountFormatter.format(1_000_000_000L, 1L));
        assertEquals("9.2E", StackAmountFormatter.format(Long.MAX_VALUE, 1L));

        assertEquals(".999", StackAmountFormatter.format(999L, 1_000L));
        assertEquals("1", StackAmountFormatter.format(1_000L, 1_000L));
        assertEquals("~1", StackAmountFormatter.format(1_001L, 1_000L));
        assertEquals("1.23", StackAmountFormatter.format(1_234L, 1_000L));
        assertEquals("~999", StackAmountFormatter.format(999_999L, 1_000L));
        assertEquals("1K", StackAmountFormatter.format(1_000_000L, 1_000L));
    }
}

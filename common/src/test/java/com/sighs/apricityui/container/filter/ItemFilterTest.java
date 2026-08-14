package com.sighs.apricityui.container.filter;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemFilterTest {
    @Test
    void constantsAndTheirCombinationsHaveExpectedSemantics() {
        ItemFilter<Integer> any = ItemFilter.any();
        ItemFilter<Integer> none = ItemFilter.none();

        assertTrue(any.test(1));
        assertFalse(none.test(1));
        assertTrue(any.and(any).test(1));
        assertFalse(any.and(none).test(1));
        assertTrue(none.or(any).test(1));
        assertFalse(none.or(none).test(1));
        assertFalse(any.negate().test(1));
        assertTrue(ItemFilter.not(none).test(1));
    }

    @Test
    void emptyCombinationsMatchAnyAndNone() {
        assertTrue(ItemFilter.<Integer>allOf().test(1));
        assertFalse(ItemFilter.<Integer>anyOf().test(1));
    }

    @Test
    void combinationsShortCircuit() {
        AtomicInteger evaluated = new AtomicInteger();
        ItemFilter<Integer> counted = value -> {
            evaluated.incrementAndGet();
            return true;
        };

        assertFalse(ItemFilter.<Integer>allOf(value -> false, counted).test(1));
        assertTrue(ItemFilter.<Integer>anyOf(value -> true, counted).test(1));
        assertFalse(ItemFilter.<Integer>none().and(counted).test(1));
        assertTrue(ItemFilter.<Integer>any().or(counted).test(1));
        assertEquals(0, evaluated.get());
    }

    @Test
    void combinationsDoNotMutateInputsOrRetainMutableFilterArrays() {
        ItemFilter<Integer> positive = value -> value > 0;
        ItemFilter<Integer> even = value -> value % 2 == 0;
        ItemFilter<Integer> positiveAndEven = positive.and(even);
        @SuppressWarnings("unchecked")
        ItemFilter<Integer>[] filters = (ItemFilter<Integer>[]) new ItemFilter<?>[]{positive, even};
        ItemFilter<Integer> all = ItemFilter.allOf(filters);

        filters[0] = ItemFilter.none();

        assertTrue(positive.test(1));
        assertFalse(positiveAndEven.test(1));
        assertTrue(positiveAndEven.test(2));
        assertTrue(all.test(2));
    }
}

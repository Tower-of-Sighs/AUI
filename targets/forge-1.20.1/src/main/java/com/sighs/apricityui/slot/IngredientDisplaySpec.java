package com.sighs.apricityui.slot;

import com.sighs.apricityui.stack.GenericStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ingredient 候选展示物品及轮播配置。
 */
public record IngredientDisplaySpec(List<GenericStack> candidates, boolean cycleEnabled, long cycleIntervalMs) {
    public static final long DEFAULT_CYCLE_INTERVAL_MS = 1000L;
    public static final IngredientDisplaySpec EMPTY = new IngredientDisplaySpec(List.of(), false, DEFAULT_CYCLE_INTERVAL_MS);

    public IngredientDisplaySpec {
        ArrayList<GenericStack> safeCandidates = new ArrayList<>();
        if (candidates != null) {
            for (GenericStack stack : candidates) {
                if (stack == null || stack.amount() <= 0L) continue;
                safeCandidates.add(stack);
            }
        }
        candidates = Collections.unmodifiableList(safeCandidates);
        cycleIntervalMs = Math.max(200L, cycleIntervalMs);
    }

    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }
}

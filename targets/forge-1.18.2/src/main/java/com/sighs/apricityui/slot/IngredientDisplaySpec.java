package com.sighs.apricityui.slot;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ingredient 候选展示物品及轮播配置。
 */
public record IngredientDisplaySpec(List<ItemStack> candidates, boolean cycleEnabled, long cycleIntervalMs) {
    public static final long DEFAULT_CYCLE_INTERVAL_MS = 1000L;
    public static final IngredientDisplaySpec EMPTY = new IngredientDisplaySpec(List.of(), false, DEFAULT_CYCLE_INTERVAL_MS);

    public IngredientDisplaySpec {
        ArrayList<ItemStack> safeCandidates = new ArrayList<>();
        if (candidates != null) {
            for (ItemStack stack : candidates) {
                if (stack == null || stack.isEmpty()) continue;
                safeCandidates.add(stack.copy());
            }
        }
        candidates = Collections.unmodifiableList(safeCandidates);
        cycleIntervalMs = Math.max(200L, cycleIntervalMs);
    }

    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }
}

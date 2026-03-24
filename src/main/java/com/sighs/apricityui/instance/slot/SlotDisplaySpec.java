package com.sighs.apricityui.instance.slot;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Slot 表达式编译结果：候选展示物品 + 轮播配置。
 */
public record SlotDisplaySpec(List<ItemStack> candidates, boolean cycleEnabled, long cycleIntervalMs) {
    public static final long DEFAULT_CYCLE_INTERVAL_MS = 1000L;
    public static final SlotDisplaySpec EMPTY = new SlotDisplaySpec(List.of(), false, DEFAULT_CYCLE_INTERVAL_MS);

    public SlotDisplaySpec {
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

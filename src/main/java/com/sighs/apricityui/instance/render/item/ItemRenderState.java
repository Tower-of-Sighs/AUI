package com.sighs.apricityui.instance.render.item;

import net.minecraft.world.item.ItemStack;

public final class ItemRenderState {
    public static final ItemRenderState EMPTY = new ItemRenderState(ItemStack.EMPTY, null, false, false, -1.0F);

    private final ItemStack stack;
    private final String overlayText;
    private final boolean ghost;
    private final boolean hidden;
    private final float cooldownProgress;

    public ItemRenderState(ItemStack stack, String overlayText, boolean ghost, boolean hidden, float cooldownProgress) {
        this.stack = normalizeStack(stack);
        this.overlayText = overlayText;
        this.ghost = ghost;
        this.hidden = hidden;
        this.cooldownProgress = normalizeCooldownProgress(cooldownProgress);
    }

    private static ItemStack normalizeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        return stack.copy();
    }

    private static float normalizeCooldownProgress(float cooldownProgress) {
        if (Float.isNaN(cooldownProgress)) return -1.0F;
        return Math.max(-1.0F, Math.min(1.0F, cooldownProgress));
    }

    public ItemStack stack() {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return stack.copy();
    }

    public String overlayText() {
        return overlayText;
    }

    public boolean ghost() {
        return ghost;
    }

    public boolean hidden() {
        return hidden;
    }

    public float cooldownProgress() {
        return cooldownProgress;
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }
}

package com.sighs.apricityui.stack;

import com.sighs.apricityui.stack.BuiltinStackTypes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Objects;

public final class FluidKey implements GenericKey {
    private final FluidStack stack;

    public FluidKey(FluidStack stack) {
        this.stack = Objects.requireNonNull(stack, "stack").copyWithAmount(1);
    }

    public static FluidKey of(FluidStack stack) {
        return stack == null || stack.isEmpty() ? null : new FluidKey(stack);
    }

    public Fluid fluid() { return stack.getFluid(); }
    public FluidStack toStack(int amount) { return stack.copyWithAmount(Math.max(1, amount)); }
    @Override public GenericStackType<FluidKey> type() { return BuiltinStackTypes.FLUID; }
    @Override public String id() { return BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString(); }
    @Override public Component displayName() { return stack.getHoverName(); }
    @Override public boolean equals(Object other) { return other instanceof FluidKey key && FluidStack.isSameFluidSameComponents(stack, key.stack); }
    @Override public int hashCode() { return Objects.hash(stack.getFluid(), stack.getComponentsPatch()); }
}

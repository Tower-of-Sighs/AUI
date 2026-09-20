package com.sighs.apricityui.stack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.Objects;

public final class FluidKey implements GenericKey {
    private final Fluid fluid;
    private final CompoundTag tag;

    public FluidKey(Fluid fluid, CompoundTag tag) {
        this.fluid = Objects.requireNonNull(fluid, "fluid");
        this.tag = tag == null || tag.isEmpty() ? null : tag.copy();
    }

    public static FluidKey of(FluidStack stack) {
        return stack == null || stack.isEmpty() ? null : new FluidKey(stack.getFluid(), stack.getTag());
    }

    public Fluid fluid() {
        return fluid;
    }

    public CompoundTag tag() {
        return tag == null ? null : tag.copy();
    }

    public FluidStack toStack(int amount) {
        return new FluidStack(fluid, Math.max(1, amount), tag == null ? null : tag.copy());
    }

    @Override
    public GenericStackType<FluidKey> type() {
        return GenericStackTypes.FLUID;
    }

    @Override
    public ResourceLocation id() {
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    @Override
    public Component displayName() {
        return toStack(1).getDisplayName();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FluidKey key && fluid == key.fluid && Objects.equals(tag, key.tag);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(fluid) + Objects.hashCode(tag);
    }
}

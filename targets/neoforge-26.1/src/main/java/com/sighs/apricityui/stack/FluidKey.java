package com.sighs.apricityui.stack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.Objects;

public final class FluidKey implements GenericKey {
    private final FluidResource resource;

    public FluidKey(FluidResource resource) {
        this.resource = Objects.requireNonNull(resource, "resource");
    }

    public static FluidKey of(FluidResource resource) {
        return resource == null || resource.isEmpty() ? null : new FluidKey(resource);
    }

    public Fluid fluid() { return resource.getFluid(); }
    public FluidResource resource() { return resource; }
    @Override public GenericStackType<FluidKey> type() { return GenericStackTypes.FLUID; }
    @Override public String id() { return BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString(); }
    @Override public Component displayName() { return resource.getHoverName(); }
    @Override public boolean equals(Object other) { return other instanceof FluidKey key && resource.equals(key.resource); }
    @Override public int hashCode() { return resource.hashCode(); }
}

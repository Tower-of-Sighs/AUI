package com.sighs.apricityui.stack;


import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.Objects;

public final class FluidKey implements GenericKey {
    private final FluidVariant variant;

    public FluidKey(FluidVariant variant) {
        this.variant = Objects.requireNonNull(variant, "variant");
    }

    public FluidVariant variant() {
        return variant;
    }

    @Override
    public GenericStackType<FluidKey> type() {
        return GenericStackTypes.require(FluidStackType.class);
    }

    @Override
    public String id() {
        return BuiltInRegistries.FLUID.getKey(variant.getFluid()).toString();
    }

    @Override
    public Component displayName() {
        var bucket = variant.getFluid().getBucket();
        return bucket == Items.AIR ? Component.literal(id()) : bucket.getDescription();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FluidKey key && variant.equals(key.variant);
    }

    @Override
    public int hashCode() {
        return variant.hashCode();
    }
}




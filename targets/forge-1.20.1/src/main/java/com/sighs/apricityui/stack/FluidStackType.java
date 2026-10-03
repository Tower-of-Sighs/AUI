package com.sighs.apricityui.stack;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public final class FluidStackType implements GenericStackType<FluidKey> {
    public FluidStackType() {
    }

    @Override
    public String id() {
        return ApricityUI.MODID + ":fluid";
    }

    @Override
    public long defaultAmount() {
        return 1000L;
    }

    @Override
    public long amountPerUnit() {
        return 1000L;
    }

    @Override
    public FluidKey readKey(CompoundTag tag) {
        return FluidKey.of(FluidStack.loadFluidStackFromNBT(tag));
    }

    @Override
    public CompoundTag writeKey(FluidKey key) {
        return key.toStack(1).writeToNBT(new CompoundTag());
    }

    @Override
    public FluidKey find(String rawId) {
        if (rawId == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return null;
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        return fluid == Fluids.EMPTY ? null : new FluidKey(fluid, null);
    }

    @Override
    public List<FluidKey> findTag(String rawId) {
        if (rawId == null) return List.of();
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) return List.of();
        TagKey<Fluid> tag = TagKey.create(Registries.FLUID, id);
        ArrayList<FluidKey> result = new ArrayList<>();
        BuiltInRegistries.FLUID.getTag(tag).ifPresent(values -> values.forEach(holder -> {
            if (holder.value() != Fluids.EMPTY) result.add(new FluidKey(holder.value(), null));
        }));
        return List.copyOf(result);
    }
}

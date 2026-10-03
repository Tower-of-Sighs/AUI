package com.sighs.apricityui.stack;

import com.sighs.apricityui.ApricityUI;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

public final class FluidStackType implements GenericStackType<FluidKey> {
    public FluidStackType() {
    }

        @Override public String id() { return ApricityUI.MODID + ":fluid"; }
        @Override public long defaultAmount() { return 1000L; }
        @Override public long amountPerUnit() { return 1000L; }
        @Override public FluidKey readKey(CompoundTag tag) {
            FluidVariant variant = decode(FluidVariant.CODEC, tag, FluidVariant.blank());
            return variant.isBlank() ? null : new FluidKey(variant);
        }
        @Override public CompoundTag writeKey(FluidKey key) { return encode(FluidVariant.CODEC, key.variant()); }
        @Override public FluidKey find(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return null;
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            return fluid == Fluids.EMPTY ? null : new FluidKey(FluidVariant.of(fluid));
        }
        @Override public List<FluidKey> findTag(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            if (id == null) return List.of();
            ArrayList<FluidKey> result = new ArrayList<>();
            BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id)).ifPresent(values -> values.forEach(holder -> {
                if (holder.value() != Fluids.EMPTY) result.add(new FluidKey(FluidVariant.of(holder.value())));
            }));
            return List.copyOf(result);
        }
    

private static <T> CompoundTag encode(com.mojang.serialization.Codec<T> codec, T value) {
        RegistryOps<Tag> ops = lookupProvider().createSerializationContext(NbtOps.INSTANCE);
        Tag tag = codec.encodeStart(ops, value).result().orElse(null);
        return tag instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    private static <T> T decode(com.mojang.serialization.Codec<T> codec, CompoundTag tag, T fallback) {
        RegistryOps<Tag> ops = lookupProvider().createSerializationContext(NbtOps.INSTANCE);
        return codec.parse(ops, tag).result().orElse(fallback);
    }

    private static HolderLookup.Provider lookupProvider() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level != null
                ? minecraft.level.registryAccess()
                : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
}


package com.sighs.apricityui.stack;

import com.mojang.serialization.Codec;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.ArrayList;
import java.util.List;

public final class FluidStackType implements GenericStackType<FluidKey> {
    public FluidStackType() {
    }

        @Override public String id() { return ApricityUI.MODID + ":fluid"; }
        @Override public long defaultAmount() { return 1000L; }
        @Override public long amountPerUnit() { return 1000L; }
        @Override public FluidKey readKey(CompoundTag tag) { return FluidKey.of(decode(FluidResource.CODEC, tag, FluidResource.EMPTY)); }
        @Override public CompoundTag writeKey(FluidKey key) { return encode(FluidResource.CODEC, key.resource()); }
        @Override public FluidKey find(String rawId) {
            Identifier id = rawId == null ? null : Identifier.tryParse(rawId);
            Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.getValue(id);
            return fluid == null || fluid == Fluids.EMPTY ? null : new FluidKey(FluidResource.of(fluid));
        }
        @Override public List<FluidKey> findTag(String rawId) {
            Identifier id = rawId == null ? null : Identifier.tryParse(rawId);
            if (id == null) return List.of();
            ArrayList<FluidKey> result = new ArrayList<>();
            BuiltInRegistries.FLUID.get(TagKey.create(Registries.FLUID, id)).ifPresent(values -> values.forEach(holder -> {
                if (holder.value() != Fluids.EMPTY) result.add(new FluidKey(FluidResource.of(holder.value())));
            }));
            return List.copyOf(result);
        }
    

private static <T> CompoundTag encode(Codec<T> codec, T value) {
        RegistryOps<Tag> ops = lookupProvider().createSerializationContext(NbtOps.INSTANCE);
        Tag tag = codec.encodeStart(ops, value).result().orElse(null);
        return tag instanceof CompoundTag compound ? compound : new CompoundTag();
    }
    private static <T> T decode(Codec<T> codec, CompoundTag tag, T fallback) {
        RegistryOps<Tag> ops = lookupProvider().createSerializationContext(NbtOps.INSTANCE);
        return codec.parse(ops, tag).result().orElse(fallback);
    }
    private static HolderLookup.Provider lookupProvider() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level != null ? minecraft.level.registryAccess()
                : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
}


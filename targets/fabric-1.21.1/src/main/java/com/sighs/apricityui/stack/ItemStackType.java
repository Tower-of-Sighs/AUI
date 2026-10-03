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

public final class ItemStackType implements GenericStackType<ItemKey> {
    public ItemStackType() {
    }

        @Override public String id() { return ApricityUI.MODID + ":item"; }
        @Override public long defaultAmount() { return 1L; }
        @Override public ItemKey readKey(CompoundTag tag) {
            ItemVariant variant = decode(ItemVariant.CODEC, tag, ItemVariant.blank());
            return variant.isBlank() ? null : new ItemKey(variant);
        }
        @Override public CompoundTag writeKey(ItemKey key) { return encode(ItemVariant.CODEC, key.variant()); }
        @Override public ItemKey find(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            return id == null || !BuiltInRegistries.ITEM.containsKey(id) ? null : new ItemKey(ItemVariant.of(BuiltInRegistries.ITEM.get(id)));
        }
        @Override public List<ItemKey> findTag(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            if (id == null) return List.of();
            ArrayList<ItemKey> result = new ArrayList<>();
            BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(values ->
                    values.forEach(holder -> result.add(new ItemKey(ItemVariant.of(holder.value())))));
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


package com.sighs.apricityui.stack;

import com.mojang.serialization.Codec;
import com.sighs.apricityui.ApricityUI;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

public final class ItemStackType implements GenericStackType<ItemKey> {
    public ItemStackType() {
    }

        @Override
        public String id() {
            return ApricityUI.MODID + ":item";
        }

        @Override
        public long defaultAmount() {
            return 1L;
        }

        @Override
        public ItemKey readKey(CompoundTag tag) {
            ItemVariant variant = decode(ItemVariant.CODEC, tag, ItemVariant.blank());
            return variant.isBlank() ? null : new ItemKey(variant);
        }

        @Override
        public CompoundTag writeKey(ItemKey key) {
            return encode(ItemVariant.CODEC, key.variant());
        }

        @Override
        public ItemKey find(String rawId) {
            Identifier id = rawId == null ? null : Identifier.tryParse(rawId);
            Item item = id == null ? null : BuiltInRegistries.ITEM.get(id).map(reference -> reference.value()).orElse(null);
            return item == null ? null : new ItemKey(ItemVariant.of(item));
        }

        @Override
        public List<ItemKey> findTag(String rawId) {
            Identifier id = rawId == null ? null : Identifier.tryParse(rawId);
            if (id == null) return List.of();
            ArrayList<ItemKey> result = new ArrayList<>();
            BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id)).ifPresent(values ->
                    values.forEach(holder -> result.add(new ItemKey(ItemVariant.of(holder.value())))));
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
        return minecraft != null && minecraft.level != null
                ? minecraft.level.registryAccess()
                : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
}


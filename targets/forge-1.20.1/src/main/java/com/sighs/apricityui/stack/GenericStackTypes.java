package com.sighs.apricityui.stack;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenericStackTypes {
    public static final GenericStackType<ItemKey> ITEM = new ItemType();
    public static final GenericStackType<FluidKey> FLUID = new FluidType();

    private static final Map<ResourceLocation, GenericStackType<?>> TYPES = new LinkedHashMap<>();

    static {
        register(ITEM);
        register(FLUID);
    }

    private GenericStackTypes() {
    }

    public static synchronized void register(GenericStackType<?> type) {
        if (type == null || type.id() == null) throw new IllegalArgumentException("Generic stack type and id are required");
        if (TYPES.putIfAbsent(type.id(), type) != null) {
            throw new IllegalArgumentException("Duplicate generic stack type " + type.id());
        }
    }

    public static List<GenericStackType<?>> values() {
        return List.copyOf(TYPES.values());
    }

    public static GenericStackType<?> get(ResourceLocation id) {
        return id == null ? null : TYPES.get(id);
    }

    public static CompoundTag writeKey(GenericKey key) {
        CompoundTag result = new CompoundTag();
        if (key == null) return result;
        result.putString("type", key.type().id().toString());
        result.put("key", writeTypedKey(key));
        return result;
    }

    public static GenericKey readKey(CompoundTag tag) {
        if (tag == null) return null;
        ResourceLocation typeId = ResourceLocation.tryParse(tag.getString("type"));
        GenericStackType<?> type = get(typeId);
        return type == null ? null : type.readKey(tag.getCompound("key"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CompoundTag writeTypedKey(GenericKey key) {
        return ((GenericStackType) key.type()).writeKey(key);
    }

    private static final class ItemType implements GenericStackType<ItemKey> {
        private static final ResourceLocation ID = new ResourceLocation(ApricityUI.MODID, "item");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public long defaultAmount() {
            return 1L;
        }

        @Override
        public ItemKey readKey(CompoundTag tag) {
            return ItemKey.of(ItemStack.of(tag));
        }

        @Override
        public CompoundTag writeKey(ItemKey key) {
            CompoundTag tag = new CompoundTag();
            key.toStack(1).save(tag);
            return tag;
        }

        @Override
        public ItemKey find(ResourceLocation id) {
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemStack stack = new ItemStack(item);
            return stack.isEmpty() ? null : ItemKey.of(stack);
        }

        @Override
        public List<ItemKey> findTag(ResourceLocation id) {
            if (id == null) return List.of();
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            ArrayList<ItemKey> result = new ArrayList<>();
            BuiltInRegistries.ITEM.getTag(tag).ifPresent(values -> values.forEach(holder -> {
                ItemKey key = ItemKey.of(new ItemStack(holder.value()));
                if (key != null) result.add(key);
            }));
            return List.copyOf(result);
        }
    }

    private static final class FluidType implements GenericStackType<FluidKey> {
        private static final ResourceLocation ID = new ResourceLocation(ApricityUI.MODID, "fluid");

        @Override
        public ResourceLocation id() {
            return ID;
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
        public FluidKey find(ResourceLocation id) {
            if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return null;
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            return fluid == Fluids.EMPTY ? null : new FluidKey(fluid, null);
        }

        @Override
        public List<FluidKey> findTag(ResourceLocation id) {
            if (id == null) return List.of();
            TagKey<Fluid> tag = TagKey.create(Registries.FLUID, id);
            ArrayList<FluidKey> result = new ArrayList<>();
            BuiltInRegistries.FLUID.getTag(tag).ifPresent(values -> values.forEach(holder -> {
                if (holder.value() != Fluids.EMPTY) result.add(new FluidKey(holder.value(), null));
            }));
            return List.copyOf(result);
        }
    }
}

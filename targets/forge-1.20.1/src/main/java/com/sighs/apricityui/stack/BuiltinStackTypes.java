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
import java.util.List;

public final class BuiltinStackTypes {
    public static final GenericStackType<ItemKey> ITEM = new ItemType();
    public static final GenericStackType<FluidKey> FLUID = new FluidType();

    private BuiltinStackTypes() {
    }

    @com.sighs.apricityui.registry.annotation.GenericStackTypeProvider
    public static GenericStackType<ItemKey> item() {
        return ITEM;
    }

    @com.sighs.apricityui.registry.annotation.GenericStackTypeProvider
    public static GenericStackType<FluidKey> fluid() {
        return FLUID;
    }

    private static final class ItemType implements GenericStackType<ItemKey> {
        private static final String ID = ApricityUI.MODID + ":item";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public long defaultAmount() {
            return 1L;
        }

        @Override
        public String formatAmount(long amount) {
            return amount == 1L ? null : GenericStackType.super.formatAmount(amount);
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
        public ItemKey find(String rawId) {
            if (rawId == null) return null;
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemStack stack = new ItemStack(item);
            return stack.isEmpty() ? null : ItemKey.of(stack);
        }

        @Override
        public List<ItemKey> findTag(String rawId) {
            if (rawId == null) return List.of();
            ResourceLocation id = ResourceLocation.tryParse(rawId);
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
        private static final String ID = ApricityUI.MODID + ":fluid";

        @Override
        public String id() {
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
}

package com.sighs.apricityui.container.filter;

import com.sighs.apricityui.registry.annotation.KJSBindings;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * ItemStack 放入过滤器。
 */
@FunctionalInterface
@KJSBindings(value = "FilterUtil")
public interface FilterUtil extends ItemFilter<ItemStack> {
    FilterUtil ANY = stack -> true;
    FilterUtil NONE = stack -> false;
    FilterUtil EMPTY = stack -> stack == null || stack.isEmpty();

    static FilterUtil item(Item item) {
        return item == null ? NONE : stack -> stack != null && stack.is(item);
    }

    static FilterUtil tag(String tag) {
        if (tag == null || tag.isBlank()) return NONE;
        String value = tag.startsWith("#") ? tag.substring(1) : tag;
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? NONE : tag(TagKey.create(Registries.ITEM, id));
    }

    static FilterUtil tag(TagKey<Item> tag) {
        return tag == null ? NONE : stack -> stack != null && stack.is(tag);
    }

    static FilterUtil custom(Predicate<ItemStack> predicate) {
        return predicate == null ? NONE : stack -> stack != null && predicate.test(stack);
    }

    static FilterUtil allOf(FilterUtil... filters) {
        if (filters == null || filters.length == 0) return ANY;
        ArrayList<FilterUtil> usable = new ArrayList<>(filters.length);
        for (FilterUtil filter : filters) {
            if (filter != null) usable.add(filter);
        }
        if (usable.isEmpty()) return ANY;
        ItemFilter<ItemStack> combined = ItemFilter.allOf(usable.toArray(FilterUtil[]::new));
        return combined::test;
    }

    static FilterUtil anyOf(FilterUtil... filters) {
        if (filters == null || filters.length == 0) return NONE;
        ArrayList<FilterUtil> usable = new ArrayList<>(filters.length);
        for (FilterUtil filter : filters) {
            if (filter != null) usable.add(filter);
        }
        if (usable.isEmpty()) return NONE;
        ItemFilter<ItemStack> combined = ItemFilter.anyOf(usable.toArray(FilterUtil[]::new));
        return combined::test;
    }

    static FilterUtil not(FilterUtil filter) {
        return filter == null ? ANY : ItemFilter.not(filter)::test;
    }

    @Override
    default FilterUtil and(ItemFilter<? super ItemStack> other) {
        return other == null ? this : stack -> test(stack) && other.test(stack);
    }

    @Override
    default FilterUtil or(ItemFilter<? super ItemStack> other) {
        return other == null ? this : stack -> test(stack) || other.test(stack);
    }

    @Override
    default FilterUtil negate() {
        return not(this);
    }
}

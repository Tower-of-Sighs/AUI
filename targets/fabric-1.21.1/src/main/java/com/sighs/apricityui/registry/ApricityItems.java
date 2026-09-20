package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.item.WrappedGenericStackItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class ApricityItems {
    public static final Item WRAPPED_GENERIC_STACK = new WrappedGenericStackItem(new Item.Properties());

    private ApricityItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ApricityUI.MODID, "wrapped_generic_stack"), WRAPPED_GENERIC_STACK);
    }
}


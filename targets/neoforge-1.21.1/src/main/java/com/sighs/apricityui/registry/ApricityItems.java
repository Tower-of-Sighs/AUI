package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.item.WrappedGenericStackItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ApricityItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ApricityUI.MODID);
    public static final DeferredHolder<Item, Item> WRAPPED_GENERIC_STACK =
            ITEMS.register("wrapped_generic_stack", () -> new WrappedGenericStackItem(new Item.Properties()));
    private ApricityItems() { }
    public static void register(IEventBus eventBus) { ITEMS.register(eventBus); }
}

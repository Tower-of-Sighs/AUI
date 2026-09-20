package com.sighs.apricityui.stack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Identity of a resource independently of its stored amount. */
public interface GenericKey {
    GenericStackType<?> type();

    ResourceLocation id();

    Component displayName();
}

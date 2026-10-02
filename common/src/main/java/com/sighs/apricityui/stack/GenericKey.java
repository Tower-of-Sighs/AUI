package com.sighs.apricityui.stack;

import net.minecraft.network.chat.Component;

/** Identity of a resource independently of its stored amount. */
public interface GenericKey {
    GenericStackType<?> type();

    String id();

    Component displayName();
}

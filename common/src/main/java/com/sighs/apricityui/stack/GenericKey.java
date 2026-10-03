package com.sighs.apricityui.stack;

import net.minecraft.network.chat.Component;

/** Immutable resource identity, independent of amount; implementations require value equality and hash codes. */
public interface GenericKey {
    GenericStackType<?> type();

    String id();

    Component displayName();
}

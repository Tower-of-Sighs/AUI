package com.sighs.apricityui.stack;

import net.minecraft.nbt.CompoundTag;

import java.util.List;

/** Pluggable serializer and registry lookup for one generic resource kind. */
public interface GenericStackType<K extends GenericKey> {
    String id();

    long defaultAmount();

    default long amountPerUnit() {
        return 1L;
    }

    default String formatAmount(long amount) {
        return StackAmountFormatter.format(Math.max(0L, amount), amountPerUnit());
    }

    K readKey(CompoundTag tag);

    CompoundTag writeKey(K key);

    K find(String rawId);

    List<K> findTag(String rawId);
}

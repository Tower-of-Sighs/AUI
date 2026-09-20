package com.sighs.apricityui.container.storage;

import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;

public interface GenericStorage {
    int size();

    GenericStack get(int index);

    long insert(int index, GenericKey key, long amount, boolean simulate);

    long extract(int index, GenericKey key, long amount, boolean simulate);

    default long insertAny(GenericKey key, long amount, boolean simulate) {
        long inserted = 0L;
        for (int index = 0; index < size() && inserted < amount; index++) {
            inserted += insert(index, key, amount - inserted, simulate);
        }
        return inserted;
    }

    default long extractAny(GenericKey key, long amount, boolean simulate) {
        long extracted = 0L;
        for (int index = 0; index < size() && extracted < amount; index++) {
            extracted += extract(index, key, amount - extracted, simulate);
        }
        return extracted;
    }
}

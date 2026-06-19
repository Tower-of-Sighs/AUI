package com.sighs.apricityui.init;

final class DirtyFlags {
    private int flags = 0;

    void add(int mask) {
        flags |= mask;
    }

    boolean has(int mask) {
        return (flags & mask) != 0;
    }

    void clear() {
        flags = 0;
    }
}

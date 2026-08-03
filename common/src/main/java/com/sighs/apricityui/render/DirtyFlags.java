package com.sighs.apricityui.render;

public final class DirtyFlags {
    private int flags = 0;

    public void add(int mask) {
        flags |= mask;
    }

    public boolean has(int mask) {
        return (flags & mask) != 0;
    }

    public void clear() {
        flags = 0;
    }
}

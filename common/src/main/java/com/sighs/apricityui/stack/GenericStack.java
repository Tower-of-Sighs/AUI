package com.sighs.apricityui.stack;

import java.util.Objects;

public record GenericStack(GenericKey key, long amount) {
    public GenericStack {
        Objects.requireNonNull(key, "key");
        amount = Math.max(0L, amount);
    }
}

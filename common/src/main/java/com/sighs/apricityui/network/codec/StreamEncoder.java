package com.sighs.apricityui.network.codec;

public interface StreamEncoder<O, T> {
    void encode(O object, T object2);
}

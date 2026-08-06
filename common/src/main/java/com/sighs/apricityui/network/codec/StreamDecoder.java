package com.sighs.apricityui.network.codec;

public interface StreamDecoder<I, T> {
    T decode(I object);
}

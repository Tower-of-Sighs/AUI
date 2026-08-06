package com.sighs.apricityui.network.serialization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a custom {@code StreamCodec} for a record component.
 * <p>
 * The resolver first attempts to read the static field named by {@link #field()}
 * on the {@link #holder()} class. If not found or invalid, it falls back to
 * scanning all public static final {@code StreamCodec} fields in the holder
 * and picks the best match via type and name heuristics.
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface NetFieldCodec {

    /**
     * Type that holds the static codec field.
     */
    Class<?> holder();

    /**
     * Name of the static codec field.
     * Defaults to {@code "STREAM_CODEC"}.
     */
    String field() default "STREAM_CODEC";
}
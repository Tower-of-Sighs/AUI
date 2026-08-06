package com.sighs.apricityui.network.serialization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a record component should be encoded/decoded using a JSON Codec.
 * <p>
 * The resolver first attempts to read the static field named by {@link #field()}
 * on the {@link #holder()} class (or the component type if holder is {@code Void.class}).
 * If not found or invalid, it falls back to scanning all public static final
 * {@code Codec} fields in the target class and picks the best match.
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonCodec {

    /**
     * Type that holds the static {@code CODEC} field.
     * Use {@code Void.class} to search on the component's own type.
     */
    Class<?> holder() default Void.class;

    /**
     * Name of the static codec field.
     * Defaults to {@code "CODEC"}.
     */
    String field() default "CODEC";
}
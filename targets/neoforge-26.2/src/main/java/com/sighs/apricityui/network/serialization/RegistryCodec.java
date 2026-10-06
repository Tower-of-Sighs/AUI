package com.sighs.apricityui.network.serialization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a record component should be encoded/decoded via registry id mapping.
 * The value is a registry key in the form "namespace:path" that identifies the registry.
 * Example: "minecraft:item" or "minecraft:block".
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegistryCodec {
    String value();
}
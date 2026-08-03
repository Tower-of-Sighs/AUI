package com.sighs.apricityui.registry.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface KJSBindings {
    String value() default "";

    String modId() default "";

    boolean isClient() default false;
}
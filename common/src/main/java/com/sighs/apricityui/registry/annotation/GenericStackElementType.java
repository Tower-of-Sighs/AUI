package com.sighs.apricityui.registry.annotation;

import com.sighs.apricityui.stack.GenericStackType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GenericStackElementType {
    Class<? extends GenericStackType> value();
}

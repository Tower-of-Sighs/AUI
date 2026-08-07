package com.sighs.apricityui.util.spi;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.function.Predicate;

public interface IAnnotationScanner {
    Set<Class<?>> findAnnotatedClasses(
            Class<? extends Annotation> annotationType,
            Set<String> basePackages,
            Predicate<Class<?>> classFilter
    );
}
package com.sighs.apricityui.util;

import com.sighs.apricityui.util.spi.IAnnotationScanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;

public final class AnnotationScanUtil {
    private static final IAnnotationScanner INSTANCE = ServiceLoader.load(IAnnotationScanner.class)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No IAnnotationScanner implementation found!"));

    public static Set<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotationType, Set<String> basePackages) {
        return INSTANCE.findAnnotatedClasses(annotationType, basePackages, c -> true);
    }

    /** Scans loader metadata without a package filter. */
    public static Set<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotationType,
                                                     Predicate<Class<?>> classFilter) {
        return INSTANCE.findAnnotatedClasses(annotationType, Set.of(), classFilter);
    }

    public static Set<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotationType,
                                                     Set<String> basePackages,
                                                     Predicate<Class<?>> classFilter) {
        return INSTANCE.findAnnotatedClasses(annotationType, basePackages, classFilter);
    }

    public static Predicate<Class<?>> nonAbstractNonInterface() {
        return clazz -> !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers());
    }
}

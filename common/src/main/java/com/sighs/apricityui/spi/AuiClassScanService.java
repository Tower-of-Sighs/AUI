package com.sighs.apricityui.spi;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface AuiClassScanService {
    void addScanPackage(String basePackage);

    default void addScanPackages(String... packages) {
        if (packages != null) for (String basePackage : packages) addScanPackage(basePackage);
    }

    void scanAnnotationClasses(Class<? extends Annotation> annotationClass,
                               Predicate<Map<String, Object>> annotationPredicate,
                               Consumer<Class<?>> consumer, Runnable onFinished);

}

package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.spi.AuiClassScanService;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ClassScanService implements AuiClassScanService {
    public static final ClassScanService INSTANCE = new ClassScanService();

    private ClassScanService() {
    }

    @Override
    public void addScanPackage(String basePackage) {
        ReflectionUtils.addScanPackage(basePackage);
    }

    @Override
    public void scanAnnotationClasses(Class<? extends Annotation> annotationClass,
                                      Predicate<Map<String, Object>> predicate,
                                      Consumer<Class<?>> consumer,
                                      Runnable onFinished) {
        ReflectionUtils.findAnnotationClasses(annotationClass, predicate, consumer, onFinished);
    }

}

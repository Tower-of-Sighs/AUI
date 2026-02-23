package com.sighs.apricityui.util;

import com.sighs.apricityui.ApricityUI;
import lombok.var;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.reflect.*;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

// 代码参考ldlib2
// https://github.com/Low-Drag-MC/LDLib2/blob/1.21/src/main/java/com/lowdragmc/lowdraglib2/utils/ReflectionUtils.java
public final class ReflectionUtils {

    public static Class<?> getRawType(Type type, Class<?> fallback) {
        var rawType = getRawType(type);
        return rawType != null ? rawType : fallback;
    }

    public static Class<?> getRawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof GenericArrayType) {
            GenericArrayType genericArrayType = (GenericArrayType) type;
            return getRawType(genericArrayType.getGenericComponentType());
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return getRawType(parameterizedType.getRawType());
        } else {
            return null;
        }
    }

    public static <A extends Annotation> void findAnnotationClasses(Class<A> annotationClass,
                                                                    @Nullable Predicate<Map<String, Object>> annotationPredicate,
                                                                    Consumer<Class<?>> consumer,
                                                                    Runnable onFinished) {
        org.objectweb.asm.Type annotationType = org.objectweb.asm.Type.getType(annotationClass);
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (annotationType.equals(annotation.getAnnotationType()) && annotation.getTargetType() == ElementType.TYPE) {
                    if (annotationPredicate == null || annotationPredicate.test(annotation.getAnnotationData())) {
                        try {
                            consumer.accept(Class.forName(annotation.getMemberName(), false, ReflectionUtils.class.getClassLoader()));
                        } catch (Throwable throwable) {
                            ApricityUI.LOGGER.error("Failed to load class for notation: {}", annotation.getMemberName(), throwable);
                        }
                    }
                }
            }
        }
        onFinished.run();
    }

    public static <A extends Annotation> void findAnnotationStaticField(Class<A> annotationClass,
                                                                        @Nullable Predicate<Map<String, Object>> annotationPredicate,
                                                                        BiConsumer<Field, Object> consumer,
                                                                        Runnable onFinished) {
        org.objectweb.asm.Type annotationType = org.objectweb.asm.Type.getType(annotationClass);
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (annotationType.equals(annotation.getAnnotationType()) && annotation.getTargetType() == ElementType.FIELD) {
                    if (annotationPredicate == null || annotationPredicate.test(annotation.getAnnotationData())) {
                        var clazz = annotation.getClassType();
                        var fieldName = annotation.getMemberName();
                        try {
                            var field = Class.forName(annotation.getClassType().getClassName()).getDeclaredField(fieldName);
                            if (Modifier.isStatic(field.getModifiers())) {
                                consumer.accept(field, field.get(null));
                            } else {
                                ApricityUI.LOGGER.error("Field is not static for notation: {} in {}", fieldName, clazz);
                            }
                        } catch (Throwable throwable) {
                            ApricityUI.LOGGER.error("Failed to load static field for notation: {} in {}", fieldName, clazz, throwable);
                        }
                    }
                }
            }
        }
        onFinished.run();
    }

    public static <A extends Annotation> void findAnnotationStaticMethod(Class<A> annotationClass,
                                                                         @Nullable Predicate<Map<String, Object>> annotationPredicate,
                                                                         Consumer<Method> consumer,
                                                                         Runnable onFinished) {
        org.objectweb.asm.Type annotationType = org.objectweb.asm.Type.getType(annotationClass);
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (annotationType.equals(annotation.getAnnotationType()) && annotation.getTargetType() == ElementType.METHOD) {
                    if (annotationPredicate == null || annotationPredicate.test(annotation.getAnnotationData())) {
                        var clazz = annotation.getClassType();
                        var methodFullDesc = annotation.getMemberName();
                        var methodName = methodFullDesc.substring(0, methodFullDesc.indexOf('('));
                        var methodDesc = methodFullDesc.substring(methodFullDesc.indexOf('('));
                        try {
                            for (var method : Class.forName(annotation.getClassType().getClassName()).getDeclaredMethods()) {
                                if (method.getName().equals(methodName) &&
                                        methodDesc.equals(org.objectweb.asm.Type.getMethodDescriptor(method))) {
                                    if (Modifier.isStatic(method.getModifiers())) {
                                        consumer.accept(method);
                                    } else {
                                        ApricityUI.LOGGER.error("Method is not static for notation: {} in {}", methodDesc, clazz);
                                    }
                                }
                            }
                        } catch (Throwable throwable) {
                            ApricityUI.LOGGER.error("Failed to load static method for notation: {} in {}", methodDesc, clazz, throwable);
                        }
                    }
                }
            }
        }
        onFinished.run();
    }
}
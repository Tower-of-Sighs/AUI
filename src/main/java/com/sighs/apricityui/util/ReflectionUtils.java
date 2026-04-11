package com.sighs.apricityui.util;

import com.sighs.apricityui.ApricityUI;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.reflect.*;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

// 代码参考ldlib2
// https://github.com/Low-Drag-MC/LDLib2/blob/1.21/src/main/java/com/lowdragmc/lowdraglib2/utils/ReflectionUtils.java
public final class ReflectionUtils {
    private static final Set<String> SCAN_PACKAGES = new LinkedHashSet<>();

    public static void addScanPackage(String basePackage) {
        if (basePackage != null && !basePackage.isBlank()) {
            SCAN_PACKAGES.add(basePackage);
        }
    }

    public static void addScanPackages(String... basePackages) {
        if (basePackages != null) {
            for (String p : basePackages) {
                addScanPackage(p);
            }
        }
    }

    private static boolean isPackageAllowed(String className) {
        if (SCAN_PACKAGES.isEmpty()) {
            return true;
        }
        for (String p : SCAN_PACKAGES) {
            if (className.startsWith(p + ".")) {
                return true;
            }
        }
        return false;
    }

    public static Class<?> getRawType(Type type, Class<?> fallback) {
        var rawType = getRawType(type);
        return rawType != null ? rawType : fallback;
    }

    public static Class<?> getRawType(Type type) {
        return switch (type) {
            case Class<?> aClass -> aClass;
            case GenericArrayType genericArrayType -> getRawType(genericArrayType.getGenericComponentType());
            case ParameterizedType parameterizedType -> getRawType(parameterizedType.getRawType());
            case null, default -> null;
        };
    }

    public static <A extends Annotation> void findAnnotationClasses(Class<A> annotationClass,
                                                                    @Nullable Predicate<Map<String, Object>> annotationPredicate,
                                                                    Consumer<Class<?>> consumer,
                                                                    Runnable onFinished) {
        org.objectweb.asm.Type annotationType = org.objectweb.asm.Type.getType(annotationClass);
        for (Object data : ModList.get().getAllScanData()) {
            for (Object annotation : getAnnotations(data)) {
                if (annotationType.equals(invoke(annotation, "annotationType")) && invoke(annotation, "targetType") == ElementType.TYPE) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> annotationData = (Map<String, Object>) invoke(annotation, "annotationData");
                    if (annotationPredicate == null || annotationPredicate.test(annotationData)) {
                        String className = String.valueOf(invoke(annotation, "memberName"));
                        if (!isPackageAllowed(className)) {
                            continue;
                        }
                        try {
                            consumer.accept(Class.forName(className, false, ReflectionUtils.class.getClassLoader()));
                        } catch (Throwable throwable) {
                            ApricityUI.LOGGER.error("Failed to load class for notation: {}", className, throwable);
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
        for (Object data : ModList.get().getAllScanData()) {
            for (Object annotation : getAnnotations(data)) {
                if (annotationType.equals(invoke(annotation, "annotationType")) && invoke(annotation, "targetType") == ElementType.FIELD) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> annotationData = (Map<String, Object>) invoke(annotation, "annotationData");
                    if (annotationPredicate == null || annotationPredicate.test(annotationData)) {
                        Object clazz = invoke(annotation, "clazz");
                        String fieldName = String.valueOf(invoke(annotation, "memberName"));
                        try {
                            String className = getAsmTypeClassName(clazz);
                            if (!isPackageAllowed(className)) {
                                continue;
                            }
                            var field = Class.forName(className).getDeclaredField(fieldName);
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
        for (Object data : ModList.get().getAllScanData()) {
            for (Object annotation : getAnnotations(data)) {
                if (annotationType.equals(invoke(annotation, "annotationType")) && invoke(annotation, "targetType") == ElementType.METHOD) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> annotationData = (Map<String, Object>) invoke(annotation, "annotationData");
                    if (annotationPredicate == null || annotationPredicate.test(annotationData)) {
                        Object clazz = invoke(annotation, "clazz");
                        String methodFullDesc = String.valueOf(invoke(annotation, "memberName"));
                        String methodName = methodFullDesc.substring(0, methodFullDesc.indexOf('('));
                        String methodDesc = methodFullDesc.substring(methodFullDesc.indexOf('('));
                        try {
                            String className = getAsmTypeClassName(clazz);
                            if (!isPackageAllowed(className)) {
                                continue;
                            }
                            for (var method : Class.forName(className).getDeclaredMethods()) {
                                if (method.getName().equals(methodName)
                                        && methodDesc.equals(org.objectweb.asm.Type.getMethodDescriptor(method))) {
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

    private static Iterable<?> getAnnotations(Object scanData) {
        Object annotations = invoke(scanData, "getAnnotations");
        if (annotations instanceof Iterable<?> iterable) {
            return iterable;
        }
        return java.util.List.of();
    }

    private static Object invoke(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to invoke " + method + " on " + target, t);
        }
    }

    private static String getAsmTypeClassName(Object asmType) {
        if (asmType instanceof org.objectweb.asm.Type t) {
            return t.getClassName();
        }
        try {
            Method m = asmType.getClass().getMethod("getClassName");
            return String.valueOf(m.invoke(asmType));
        } catch (Throwable t) {
            return String.valueOf(asmType);
        }
    }
}

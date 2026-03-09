package com.sighs.apricityui.util;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ReflectionUtils {
    private static final Set<String> SCAN_PACKAGES = new LinkedHashSet<>();

    public static void addScanPackage(String basePackage) {
        if (basePackage != null && !basePackage.isBlank()) {
            SCAN_PACKAGES.add(basePackage);
        }
    }

    public static void addScanPackages(String... packages) {
        if (packages != null) {
            for (String pkg : packages) {
                addScanPackage(pkg);
            }
        }
    }

    private static <A extends Annotation> void scanDirectory(File directory, String basePackage, Class<A> annotationClass, @Nullable Predicate<Map<String, Object>> annotationPredicate, Consumer<Class<?>> consumer, ClassLoader loader) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                String subPackage = basePackage + "." + file.getName();
                scanDirectory(file, subPackage, annotationClass, annotationPredicate, consumer, loader);
            } else if (file.getName().endsWith(".class")) {
                String className = basePackage + "." + file.getName().substring(0, file.getName().length() - 6);
                maybeAddAnnotatedClass(className, annotationClass, annotationPredicate, consumer, loader);
            }
        }
    }

    private static <A extends Annotation> void scanJar(URL url, String path, Class<A> annotationClass, @Nullable Predicate<Map<String, Object>> annotationPredicate, Consumer<Class<?>> consumer, ClassLoader loader) {
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            try (JarFile jarFile = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(path) || !name.endsWith(".class") || entry.isDirectory()) {
                        continue;
                    }
                    String className = name.replace('/', '.').substring(0, name.length() - 6);
                    maybeAddAnnotatedClass(className, annotationClass, annotationPredicate, consumer, loader);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static <A extends Annotation> void maybeAddAnnotatedClass(String className, Class<A> annotationClass, @Nullable Predicate<Map<String, Object>> annotationPredicate, Consumer<Class<?>> consumer, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(className, false, loader);
            A ann = clazz.getAnnotation(annotationClass);
            if (ann == null) {
                return;
            }
            Map<String, Object> data = new HashMap<>();
            for (Method m : annotationClass.getDeclaredMethods()) {
                try {
                    Object value = m.invoke(ann);
                    data.put(m.getName(), value);
                } catch (Throwable ignored) {
                }
            }
            if (annotationPredicate == null || annotationPredicate.test(data)) {
                consumer.accept(clazz);
            }
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
        }
    }

    public static <A extends Annotation> void findAnnotationClasses(Class<A> annotationClass, @Nullable Predicate<Map<String, Object>> annotationPredicate, Consumer<Class<?>> consumer, Runnable onFinished) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ReflectionUtils.class.getClassLoader();
        }
        for (String basePackage : SCAN_PACKAGES) {
            String path = basePackage.replace('.', '/');
            try {
                Enumeration<URL> resources = loader.getResources(path);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    String protocol = url.getProtocol();
                    if ("file".equals(protocol)) {
                        scanDirectory(new File(url.getPath()), basePackage, annotationClass, annotationPredicate, consumer, loader);
                    } else if ("jar".equals(protocol)) {
                        scanJar(url, path, annotationClass, annotationPredicate, consumer, loader);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        onFinished.run();
    }
}

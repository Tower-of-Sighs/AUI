package com.sighs.apricityui.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Small Fabric package scanner used for registry discovery. */
public final class FabricReflectionUtils {
    private static final Set<String> SCAN_PACKAGES = new LinkedHashSet<>();

    private FabricReflectionUtils() {
    }

    public static void addScanPackage(String basePackage) {
        if (basePackage != null && !basePackage.isBlank()) SCAN_PACKAGES.add(basePackage);
    }

    public static void addScanPackages(String... packages) {
        if (packages != null) for (String value : packages) addScanPackage(value);
    }

    public static Class<?> getRawType(Type type) {
        if (type instanceof Class<?> clazz) return clazz;
        if (type instanceof ParameterizedType parameterized) return getRawType(parameterized.getRawType());
        return null;
    }

    public static Class<?> getRawType(Type type, Class<?> fallback) {
        Class<?> raw = getRawType(type);
        return raw == null ? fallback : raw;
    }

    public static <A extends Annotation> void findAnnotationClasses(
            Class<A> annotationClass,
            Predicate<Map<String, Object>> ignored,
            Consumer<Class<?>> consumer,
            Runnable onFinished) {
        try {
            for (String className : classNames()) {
                if (!allowed(className)) continue;
                try {
                    Class<?> clazz = Class.forName(className, false, FabricReflectionUtils.class.getClassLoader());
                    if (clazz.isAnnotationPresent(annotationClass)) {
                        consumer.accept(clazz);
                    }
                } catch (Throwable ignoredClass) {
                    // A third-party class may require a client-only dependency.
                }
            }
        } finally {
            onFinished.run();
        }
    }

    public static <A extends Annotation> void findAnnotationStaticField(Class<A> annotationClass,
                                                                         Predicate<Map<String, Object>> predicate,
                                                                         BiConsumer<Field, Object> consumer,
                                                                         Runnable onFinished) {
        try {
            for (String className : classNames()) {
                if (!allowed(className)) continue;
                try {
                    Class<?> clazz = Class.forName(className, false, FabricReflectionUtils.class.getClassLoader());
                    for (Field field : clazz.getDeclaredFields()) {
                        if (!field.isAnnotationPresent(annotationClass) || !Modifier.isStatic(field.getModifiers())) continue;
                        field.setAccessible(true);
                        consumer.accept(field, field.get(null));
                    }
                } catch (Throwable ignoredClass) {
                }
            }
        } finally {
            onFinished.run();
        }
    }

    public static <A extends Annotation> void findAnnotationStaticMethod(Class<A> annotationClass,
                                                                          Predicate<Map<String, Object>> predicate,
                                                                          Consumer<Method> consumer,
                                                                          Runnable onFinished) {
        try {
            for (String className : classNames()) {
                if (!allowed(className)) continue;
                try {
                    Class<?> clazz = Class.forName(className, false, FabricReflectionUtils.class.getClassLoader());
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(annotationClass) && Modifier.isStatic(method.getModifiers())) {
                            method.setAccessible(true);
                            consumer.accept(method);
                        }
                    }
                } catch (Throwable ignoredClass) {
                }
            }
        } finally {
            onFinished.run();
        }
    }

    private static boolean allowed(String className) {
        if (SCAN_PACKAGES.isEmpty()) return true;
        for (String packageName : SCAN_PACKAGES) if (className.startsWith(packageName + ".")) return true;
        return false;
    }

    private static Set<String> classNames() {
        Set<String> result = new LinkedHashSet<>();
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            for (Path root : mod.getRootPaths()) {
                try (var stream = Files.walk(root)) {
                    stream.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        if (relative.endsWith("module-info.class") || relative.contains("/META-INF/")) return;
                        result.add(relative.substring(0, relative.length() - 6).replace('/', '.'));
                    });
                } catch (IOException ignored) {
                }
            }
        }
        // Loom keeps common classes in a separate classes directory which is
        // available to the Knot classloader but is not always exposed through
        // a Fabric mod root. Walk package resources as a development fallback;
        // this also works for packaged jar URLs.
        for (String packageName : SCAN_PACKAGES) {
            addClassLoaderClasses(result, packageName, FabricReflectionUtils.class.getClassLoader());
            addClassLoaderClasses(result, packageName, Thread.currentThread().getContextClassLoader());
        }
        return result;
    }

    private static void addClassLoaderClasses(Set<String> result, String packageName, ClassLoader loader) {
        if (loader == null || packageName == null || packageName.isBlank()) return;
        String packagePath = packageName.replace('.', '/');
        try {
            Enumeration<URL> resources = loader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if ("file".equalsIgnoreCase(resource.getProtocol())) {
                    addFileClasses(result, packageName, Paths.get(resource.toURI()));
                } else if ("jar".equalsIgnoreCase(resource.getProtocol())) {
                    JarURLConnection connection = (JarURLConnection) resource.openConnection();
                    connection.setUseCaches(false);
                    addJarClasses(result, packageName, connection.getJarFile());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addFileClasses(Set<String> result, String packageName, Path packageRoot) {
        if (packageRoot == null || !Files.isDirectory(packageRoot)) return;
        try (var stream = Files.walk(packageRoot)) {
            stream.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                String relative = packageRoot.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith("module-info.class") || relative.contains("/META-INF/")) return;
                result.add(packageName + "." + relative.substring(0, relative.length() - 6).replace('/', '.'));
            });
        } catch (IOException ignored) {
        }
    }

    private static void addJarClasses(Set<String> result, String packageName, JarFile jar) {
        if (jar == null) return;
        String prefix = packageName.replace('.', '/') + "/";
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (!name.startsWith(prefix) || !name.endsWith(".class") || name.endsWith("module-info.class")) continue;
            result.add(name.substring(0, name.length() - 6).replace('/', '.'));
        }
    }
}

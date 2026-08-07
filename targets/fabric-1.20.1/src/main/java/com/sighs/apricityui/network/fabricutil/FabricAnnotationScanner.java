package com.sighs.apricityui.network.fabricutil;

import com.sighs.apricityui.util.spi.IAnnotationScanner;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Fabric implementation of OELib's metadata provider.
 *
 * <p>The original provider uses HKT tasks for memoized asynchronous scanning.
 * AUI keeps the same semantics with standard JDK futures and daemon executors:
 * all mod roots are scanned once, in parallel, and annotation results/classes
 * are cached for subsequent network and registry scans.</p>
 */
public final class FabricAnnotationScanner implements IAnnotationScanner {
    private static final ExecutorService SCAN_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
            runnable -> {
                Thread thread = new Thread(runnable, "aui-fabric-mod-scan");
                thread.setDaemon(true);
                return thread;
            });
    private static final CompletableFuture<ScanIndex> INDEX =
            CompletableFuture.supplyAsync(FabricAnnotationScanner::scanMods, SCAN_EXECUTOR);
    private static final ConcurrentHashMap<String, CompletableFuture<Class<?>>> CLASS_CACHE =
            new ConcurrentHashMap<>();

    private FabricAnnotationScanner() {
    }

    @Override
    public Set<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotationType,
                                              Set<String> basePackages,
                                              Predicate<Class<?>> classFilter) {
        ScanIndex index;
        try {
            index = INDEX.join();
        } catch (CompletionException failure) {
            return Set.of();
        }

        Set<String> names = index.byAnnotation().getOrDefault(Type.getDescriptor(annotationType), Set.of());
        Set<Class<?>> result = new LinkedHashSet<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String name : names) {
            if (!matchesBasePackages(name, basePackages)) continue;
            try {
                Class<?> type = CLASS_CACHE.computeIfAbsent(name,
                        ignored -> CompletableFuture.supplyAsync(() -> load(name, loader), SCAN_EXECUTOR)).join();
                if (classFilter == null || classFilter.test(type)) result.add(type);
            } catch (CompletionException ignored) {
                // Optional dependencies may make a scanned class unloadable.
            }
        }
        return Set.copyOf(result);
    }

    private static ScanIndex scanMods() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if ("builtin".equals(mod.getMetadata().getType())) continue;
            mod.getRootPaths().stream().map(path -> path.toAbsolutePath().normalize()).forEach(roots::add);
        }

        List<CompletableFuture<ScanIndex>> tasks = new ArrayList<>();
        for (Path root : roots) {
            tasks.add(CompletableFuture.supplyAsync(() -> scanRoot(root), SCAN_EXECUTOR));
        }

        Map<String, Set<String>> merged = new LinkedHashMap<>();
        for (CompletableFuture<ScanIndex> task : tasks) {
            try {
                merge(merged, task.join());
            } catch (CompletionException ignored) {
                // A broken optional mod root must not prevent AUI startup.
            }
        }
        Map<String, Set<String>> immutable = new LinkedHashMap<>();
        merged.forEach((annotation, classes) -> immutable.put(annotation, Set.copyOf(classes)));
        return new ScanIndex(Map.copyOf(immutable));
    }

    private static ScanIndex scanRoot(Path root) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .forEach(path -> scanClass(path, result));
        } catch (Exception ignored) {
        }
        return new ScanIndex(result);
    }

    private static void scanClass(Path path, Map<String, Set<String>> result) {
        try (InputStream input = Files.newInputStream(path)) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                private String className;

                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    className = Type.getObjectType(name).getClassName();
                }

                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    add(result, className, descriptor);
                    return null;
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor,
                                               String signature, Object value) {
                    return new FieldVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            add(result, className, descriptor);
                            return null;
                        }
                    };
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            add(result, className, descriptor);
                            return null;
                        }
                    };
                }

            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (Throwable ignored) {
        }
    }

    private static void add(Map<String, Set<String>> result, String className, String annotation) {
        result.computeIfAbsent(annotation, ignored -> new LinkedHashSet<>()).add(className);
    }

    private static void merge(Map<String, Set<String>> target, ScanIndex source) {
        source.byAnnotation().forEach((annotation, classes) ->
                target.computeIfAbsent(annotation, ignored -> new LinkedHashSet<>()).addAll(classes));
    }

    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException(error);
        }
    }

    private static boolean matchesBasePackages(String className, Set<String> basePackages) {
        if (basePackages == null || basePackages.isEmpty()) return true;
        for (String base : basePackages) {
            if (className.equals(base) || className.startsWith(base + ".")) return true;
        }
        return false;
    }

    private record ScanIndex(Map<String, Set<String>> byAnnotation) {
    }
}

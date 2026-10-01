package com.sighs.apricityui.network.forgeutil;

import com.sighs.apricityui.util.spi.IAnnotationScanner;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

public final class ForgeAnnotationScanner implements IAnnotationScanner {

    @Override
    public Set<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotationType,
                                              Set<String> basePackages,
                                              Predicate<Class<?>> classFilter) {
        Set<Class<?>> result = new LinkedHashSet<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Type asmType = Type.getType(annotationType);

        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : scanData.getAnnotations()) {
                ElementType target = annotation.targetType();
                if (target != ElementType.TYPE && target != ElementType.METHOD) continue;
                if (!annotation.annotationType().equals(asmType)) continue;

                String className = annotation.clazz().getClassName();
                if (!matchesBasePackages(className, basePackages)) continue;

                try {
                    Class<?> clazz = Class.forName(className, false, loader);
                    if (classFilter.test(clazz)) {
                        result.add(clazz);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {}
            }
        }
        return result;
    }

    private boolean matchesBasePackages(String className, Set<String> basePackages) {
        if (basePackages.isEmpty()) return true;
        for (String base : basePackages) {
            if (className.startsWith(base + ".") || className.equals(base)) return true;
        }
        return false;
    }
}

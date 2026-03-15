package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ApricityUIRegistry {
    public static List<Element> ELEMENTS = new ArrayList<>();
    public static void scanPackage(String basePackage) {
        ReflectionUtils.addScanPackage(basePackage);
    }

    public static void scanPackages(String... basePackages) {
        ReflectionUtils.addScanPackages(basePackages);
    }

    public static void register() {
        ReflectionUtils.findAnnotationClasses(ElementRegister.class, data -> true, clazz -> {
            if (!Element.class.isAssignableFrom(clazz)) {
                ApricityUI.LOGGER.error("Class {} has @ElementRegister but is not a subclass of Element!", clazz.getName());
                return;
            }

            ElementRegister annotation = clazz.getAnnotation(ElementRegister.class);
            String value = annotation.value();
            Element.register(value, (document, s) -> {
                try {
                    Constructor<?> constructor = clazz.getConstructor(Document.class);
                    constructor.setAccessible(true);
                    Element element = (Element) constructor.newInstance(document);
                    ELEMENTS.add(element);
                    return element;
                } catch (Throwable throwable) {
                    ApricityUI.LOGGER.error("Failed to load element {}", clazz.getName(), throwable);
                    return new Element(document, value);
                }
            });
        }, () -> {
        });
    }
}

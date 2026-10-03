package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.registry.annotation.GenericStackElementType;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.stack.GenericStackType;
import com.sighs.apricityui.stack.GenericStackTypes;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public class ApricityUIRegistry {
    public static List<Element> ELEMENTS = new ArrayList<>();
    public static void scanPackage(String basePackage) {
        AuiServices.classes().addScanPackage(basePackage);
    }

    public static void scanPackages(String... basePackages) {
        AuiServices.classes().addScanPackages(basePackages);
    }

    public static void register() {
        AuiServices.classes().scanAnnotationClasses(ElementRegister.class, data -> true, clazz -> {
            if (!Element.class.isAssignableFrom(clazz)) {
                ApricityUI.LOGGER.error("Class {} has @ElementRegister but is not a subclass of Element!", clazz.getName());
                return;
            }

            ElementRegister annotation = clazz.getAnnotation(ElementRegister.class);
            String value = annotation.value();
            GenericStackElementType typeAnnotation = clazz.getAnnotation(GenericStackElementType.class);
            Constructor<?> constructor;
            GenericStackType<?> canonicalType = null;
            try {
                if (typeAnnotation != null) {
                    if (!isTypedElement(clazz)) {
                        ApricityUI.LOGGER.error("Class {} has @GenericStackElementType but is not a TypedGenericStackElement", clazz.getName());
                        return;
                    }
                    canonicalType = GenericStackTypes.require(typeAnnotation.value());
                    constructor = clazz.getConstructor(Document.class, GenericStackType.class);
                } else {
                    if (isTypedElement(clazz)) {
                        ApricityUI.LOGGER.error("Typed element {} is missing @GenericStackElementType", clazz.getName());
                        return;
                    }
                    constructor = clazz.getConstructor(Document.class);
                }
            } catch (Throwable failure) {
                ApricityUI.LOGGER.error("Skipping invalid element declaration {}", clazz.getName(), failure);
                return;
            }
            GenericStackType<?> elementType = canonicalType;
            Element.register(value, (document, s) -> {
                try {
                    Object[] arguments = elementType == null
                            ? new Object[]{document}
                            : new Object[]{document, elementType};
                    Element element = (Element) constructor.newInstance(arguments);
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

    private static boolean isTypedElement(Class<?> clazz) {
        try {
            ClassLoader loader = clazz.getClassLoader();
            Class<?> typedBase = Class.forName("com.sighs.apricityui.element.TypedGenericStackElement", false,
                    loader == null ? ApricityUIRegistry.class.getClassLoader() : loader);
            return typedBase.isAssignableFrom(clazz);
        } catch (Throwable ignored) {
            return false;
        }
    }
}

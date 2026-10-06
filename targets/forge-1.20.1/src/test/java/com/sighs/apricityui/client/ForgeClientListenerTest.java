package com.sighs.apricityui.client;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.minecraftforge.eventbus.ClassLoaderFactory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeClientListenerTest {
    @Test
    void eventBusGeneratedListenerClassesAreDistinct() {
        ClassLoaderFactory factory = new ClassLoaderFactory();
        Set<String> generatedClasses = new HashSet<>();
        for (Method method : Client.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubscribeEvent.class)) {
                assertTrue(generatedClasses.add(factory.getUniqueName(method)),
                        () -> "Forge listener wrapper name collision: " + method);
            }
        }
    }
}

package com.sighs.apricityui.stack;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericStackTest {
    @Test
    void valueObjectRequiresAKeyAndClampsAmounts() {
        GenericStackType<?> type = type("test:fluid");
        GenericKey key = key("test:water", type);

        assertEquals(0L, new GenericStack(key, -1L).amount());
        assertThrows(NullPointerException.class, () -> new GenericStack(null, 1L));
        assertEquals(new GenericStack(key, 4L), new GenericStack(key, 4L));
    }

    @Test
    void controllerKeepsMenuPrecedenceAndTypeMismatchIndependentOfDom() {
        GenericStackType<?> type = type("test:fluid");
        GenericStackController controller = new GenericStackController();
        GenericStack local = new GenericStack(key("test:local", type), 1L);
        GenericStack menu = new GenericStack(key("test:menu", type), 1234L);

        assertEquals(local, controller.currentStack(local));
        controller.setDrivenState(type, menu, "overlay", true, false, GenericStackController.Source.MENU);
        assertEquals(menu, controller.currentStack(local));
        assertFalse(controller.shouldPaint());
        assertEquals("overlay", controller.overlayText(menu));
        controller.clearDrivenState(GenericStackController.Source.INGREDIENT);
        assertEquals(GenericStackController.Source.MENU, controller.source());
        controller.clearDrivenState(GenericStackController.Source.MENU);
        assertTrue(controller.shouldPaint());
        assertEquals(local, controller.currentStack(local));
        assertEquals("1.2K", controller.overlayText(menu));
        controller.setDrivenState(type, menu, null, false, true, GenericStackController.Source.MENU);
        assertFalse(controller.shouldPaint());
        GenericStackType<?> other = type("test:other");
        assertFalse(controller.accepts(other, menu.key()));
        controller.setDrivenState(other, menu, null, false, false, GenericStackController.Source.MENU);
        assertNull(controller.currentStack(local));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void requireReportsAnUnregisteredTypeClass() {
        Class<? extends GenericStackType> proxyClass = (Class<? extends GenericStackType>) Proxy.getProxyClass(
                GenericStackTest.class.getClassLoader(), GenericStackType.class);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> GenericStackTypes.require(proxyClass));
        assertTrue(failure.getMessage().contains(proxyClass.getName()));
    }

    private static GenericStackType<?> type(String id) {
        return (GenericStackType<?>) Proxy.newProxyInstance(
                GenericStackTest.class.getClassLoader(),
                new Class<?>[]{GenericStackType.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "id" -> id;
                    case "defaultAmount", "amountPerUnit" -> 1L;
                    case "formatAmount" -> "1.2K";
                    case "toString" -> id;
                    default -> null;
                });
    }

    private static GenericKey key(String id, GenericStackType<?> type) {
        return (GenericKey) Proxy.newProxyInstance(
                GenericStackTest.class.getClassLoader(),
                new Class<?>[]{GenericKey.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "type" -> type;
                    case "id" -> id;
                    case "toString" -> id;
                    default -> null;
                });
    }
}

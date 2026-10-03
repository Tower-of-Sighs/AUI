package com.sighs.apricityui.stack;

import com.mojang.serialization.Codec;
import com.sighs.apricityui.registry.annotation.GenericStackTypeProvider;
import com.sighs.apricityui.spi.AuiClassScanService;
import com.sighs.apricityui.spi.AuiServices;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class GenericStackTest {
    private static final TestType TYPE = new TestType("test:stack_model");

    @Test
    void valueAndCodecKeepIdentitySeparateFromNonNegativeAmount() {
        GenericStackTypes.register(TYPE);
        TestKey key = TYPE.find("test:water");
        assertEquals(new TestKey(TYPE, "test:water"), key);
        assertEquals(new TestKey(TYPE, "test:water").hashCode(), key.hashCode());
        assertEquals(0L, new GenericStack(key, -1L).amount());
        assertThrows(NullPointerException.class, () -> new GenericStack(null, 1L));
        GenericStack stack = new GenericStack(key, Long.MAX_VALUE);
        assertEquals(stack, GenericStackTypes.readStack(GenericStackTypes.writeStack(stack)));
        assertEquals(key, GenericStackTypes.readKey(GenericStackTypes.writeKey(key)));
        assertNull(GenericStackTypes.readStack(null));
        assertNull(GenericStackTypes.readStack(new CompoundTag()));
        CompoundTag legacy = new CompoundTag();
        legacy.putString("type", TYPE.id());
        legacy.put("key", TYPE.writeKey(key));
        legacy.putLong("amount", 1L);
        assertNull(GenericStackTypes.readStack(legacy));
        CompoundTag malformed = GenericStackTypes.writeStack(stack);
        malformed.putString("amount", "not-a-number");
        assertNull(GenericStackTypes.readStack(malformed));
    }

    @Test
    void controllerKeepsMenuPrecedenceAndTypeMismatchIndependentOfDom() {
        GenericStackController controller = new GenericStackController();
        GenericStack local = new GenericStack(TYPE.find("test:local"), 1L);
        GenericStack menu = new GenericStack(TYPE.find("test:menu"), 1234L);
        assertEquals(local, controller.currentStack(local));
        controller.setDrivenState(TYPE, menu, "overlay", true, false, GenericStackController.Source.MENU);
        assertEquals(menu, controller.currentStack(local));
        assertFalse(controller.shouldPaint());
        assertEquals("overlay", controller.overlayText(menu));
        controller.clearDrivenState(GenericStackController.Source.INGREDIENT);
        assertEquals(GenericStackController.Source.MENU, controller.source());
        controller.clearDrivenState(GenericStackController.Source.MENU);
        assertTrue(controller.shouldPaint());
        assertEquals(local, controller.currentStack(local));
        assertEquals("1.2K", controller.overlayText(menu));
        controller.setDrivenState(TYPE, menu, null, false, true, GenericStackController.Source.MENU);
        assertFalse(controller.shouldPaint());
        TestType other = new TestType("test:other");
        assertFalse(controller.accepts(other, menu.key()));
        controller.setDrivenState(other, menu, null, false, false, GenericStackController.Source.MENU);
        assertNull(controller.currentStack(local));
    }

    @Test
    void providerFailuresDoNotAbortScanningAndDuplicatesKeepTheFirst() {
        AuiClassScanService previous = AuiServices.classes();
        try {
            AuiServices.setClasses(new AuiClassScanService() {
                @Override public void addScanPackage(String basePackage) { }
                @Override public void scanAnnotationClasses(Class<? extends Annotation> annotation,
                        Predicate<Map<String, Object>> predicate, Consumer<Class<?>> consumer, Runnable finished) {
                    finished.run();
                }
                @Override public void scanAnnotationMethods(Class<? extends Annotation> annotation, Consumer<Method> consumer) {
                    for (String name : List.of("failing", "empty", "invalid", "valid", "duplicate")) {
                        try {
                            consumer.accept(Providers.class.getDeclaredMethod(name));
                        } catch (ReflectiveOperationException failure) {
                            throw new AssertionError(failure);
                        }
                    }
                }
            });
            assertDoesNotThrow(GenericStackTypes::scanProviders);
            assertSame(Providers.FIRST, GenericStackTypes.get("test:provider"));
            assertFalse(GenericStackTypes.register(null));
            assertFalse(GenericStackTypes.register(new TestType("")));
            assertFalse(GenericStackTypes.register(new TestType("test:provider")));
            assertTrue(GenericStackTypes.values().contains(Providers.FIRST));
        } finally {
            AuiServices.setClasses(previous);
        }
    }

    private static final class Providers {
        private static final TestType FIRST = new TestType("test:provider");
        @GenericStackTypeProvider static GenericStackType<?> failing() { throw new IllegalStateException("test failure"); }
        @GenericStackTypeProvider static GenericStackType<?> empty() { return null; }
        @GenericStackTypeProvider GenericStackType<?> invalid() { return FIRST; }
        @GenericStackTypeProvider static GenericStackType<?> valid() { return FIRST; }
        @GenericStackTypeProvider static GenericStackType<?> duplicate() { return new TestType("test:provider"); }
    }

    private record TestKey(TestType type, String id) implements GenericKey {
        @Override public Component displayName() { return null; }
    }

    private record TestType(String id) implements GenericStackType<TestKey> {
        @Override public long defaultAmount() { return 1L; }
        @Override public TestKey readKey(CompoundTag tag) {
            return Codec.STRING.parse(NbtOps.INSTANCE, tag.get("id")).result().map(this::find).orElse(null);
        }
        @Override public CompoundTag writeKey(TestKey key) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", key.id());
            return tag;
        }
        @Override public TestKey find(String id) { return new TestKey(this, id); }
        @Override public List<TestKey> findTag(String id) { return List.of(find(id)); }
    }
}

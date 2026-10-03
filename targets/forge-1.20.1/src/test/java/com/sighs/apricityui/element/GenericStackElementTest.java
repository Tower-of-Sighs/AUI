package com.sighs.apricityui.element;

import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.GenericStackElementType;
import com.sighs.apricityui.slot.GenericStackExpressionCompiler;
import com.sighs.apricityui.slot.IngredientExpressionCompiler;
import com.sighs.apricityui.spi.AuiClassScanService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.stack.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class GenericStackElementTest {
    @BeforeAll
    static void scanStackTypes() {
        AuiServices.setClasses(new AuiClassScanService() {
            @Override
            public void addScanPackage(String basePackage) {
            }

            @Override
            public void scanAnnotationClasses(Class<? extends Annotation> annotationClass,
                                              Predicate<Map<String, Object>> annotationPredicate,
                                              Consumer<Class<?>> consumer, Runnable onFinished) {
                if (annotationClass == GenericStackElementType.class) {
                    consumer.accept(Item.class);
                    consumer.accept(Fluid.class);
                    consumer.accept(TestElement.class);
                    consumer.accept(SecondTestElement.class);
                }
                onFinished.run();
            }
        });
        GenericStackTypes.scanElementTypes();
    }

    @Test
    void slotOwnsOneResourceAndIngredientOwnsNoResourceChildren() {
        Document document = new Document("test://resource-slot", false);
        document.body = new Body(document);
        Slot slot = new Slot(document);
        document.body.appendChild(slot);
        SlotContentRules.normalizeRuntimeChildren(slot);
        assertInstanceOf(Item.class, SlotContentRules.getSlotContent(slot));
        assertEquals("minecraft:air", SlotContentRules.getSlotContent(slot).getTextContent());
        SlotContentRules.getSlotContent(slot).remove();
        Fluid fluid = new Fluid(document, GenericStackTypes.require(FluidStackType.class));
        slot.appendChild(fluid);
        SlotContentRules.normalizeRuntimeChildren(slot);
        assertEquals(List.of(fluid), slot.children);
        assertEquals(GenericStackTypes.require(FluidStackType.class), fluid.type());
        assertEquals(GenericStackTypes.require(ItemStackType.class), new Item(document, GenericStackTypes.require(ItemStackType.class)).type());
        assertEquals(2, fluid.createBodyRenderNodes().size());
        fluid.remove();
        Ingredient ingredient = new Ingredient(document);
        slot.appendChild(ingredient);
        SlotContentRules.normalizeRuntimeChildren(slot);
        assertEquals(List.of(ingredient), slot.children);
        assertTrue(ingredient.children.isEmpty());
        assertEquals(2, ingredient.createBodyRenderNodes().size());
        assertThrows(IllegalArgumentException.class,
                () -> SlotContentRules.validateRuntimeInsertion(ingredient,
                        new Item(document, GenericStackTypes.require(ItemStackType.class))));
        assertThrows(IllegalArgumentException.class,
                () -> SlotContentRules.validateRuntimeInsertion(slot, new Element(document, "STACK")));
        ingredient.remove();
        SlotContentRules.restoreRequiredContent(slot);
        assertInstanceOf(Item.class, SlotContentRules.getSlotContent(slot));
    }

    @Test
    void expressionsDiscoverThirdPartyTypesWithoutWideningUnknownFilters() {
        TestType testType = GenericStackTypes.require(TestType.class);
        assertEquals(testType, GenericStackExpressionCompiler.parse("test:resource*7", testType.id(), 0L).key().type());
        assertNull(GenericStackExpressionCompiler.parse("test:resource", "test:missing", 0L));
        assertEquals(0L, GenericStackExpressionCompiler.parse("test:resource*0", testType.id(), 0L).amount());
        var mixed = IngredientExpressionCompiler.compile("test:resource", null, 0L, true, 1000L);
        assertEquals(2, mixed.candidates().size());
        assertEquals(1, IngredientExpressionCompiler.compile("#test:tag", testType.id(), 0L, false, 1000L).candidates().size());
        assertTrue(IngredientExpressionCompiler.compile("test:resource", "test:missing", 0L, false, 1000L).candidates().isEmpty());
        assertFalse(GenericStackRenderers.render(null, mixed.candidates().get(0)));
    }

    private record TestKey(TestType type, String id) implements GenericKey {
        @Override public Component displayName() { return null; }
    }

    @GenericStackElementType(TestType.class)
    public static final class TestElement {
    }

    public static class TestType implements GenericStackType<TestKey> {
        public TestType() {
        }

        @Override public String id() { return "test:custom"; }
        @Override public long defaultAmount() { return 1L; }
        @Override public TestKey readKey(CompoundTag tag) { return find("test:resource"); }
        @Override public CompoundTag writeKey(TestKey key) { return new CompoundTag(); }
        @Override public TestKey find(String id) { return "test:resource".equals(id) ? new TestKey(this, id) : null; }
        @Override public List<TestKey> findTag(String id) { return "test:tag".equals(id) ? List.of(find("test:resource")) : List.of(); }
    }

    @GenericStackElementType(SecondTestType.class)
    public static final class SecondTestElement {
    }

    public static final class SecondTestType extends TestType {
        public SecondTestType() {
        }

        @Override public String id() { return "test:second"; }
    }
}

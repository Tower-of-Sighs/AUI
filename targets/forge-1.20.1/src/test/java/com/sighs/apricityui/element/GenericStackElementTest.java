package com.sighs.apricityui.element;

import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.slot.GenericStackExpressionCompiler;
import com.sighs.apricityui.slot.IngredientExpressionCompiler;
import com.sighs.apricityui.stack.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenericStackElementTest {
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
        Fluid fluid = new Fluid(document);
        slot.appendChild(fluid);
        SlotContentRules.normalizeRuntimeChildren(slot);
        assertEquals(List.of(fluid), slot.children);
        assertEquals(BuiltinStackTypes.FLUID, fluid.type());
        assertEquals(BuiltinStackTypes.ITEM, new Item(document).type());
        assertEquals(2, fluid.createBodyRenderNodes().size());
        fluid.remove();
        Ingredient ingredient = new Ingredient(document);
        slot.appendChild(ingredient);
        SlotContentRules.normalizeRuntimeChildren(slot);
        assertEquals(List.of(ingredient), slot.children);
        assertTrue(ingredient.children.isEmpty());
        assertEquals(2, ingredient.createBodyRenderNodes().size());
        assertThrows(IllegalArgumentException.class,
                () -> SlotContentRules.validateRuntimeInsertion(ingredient, new Item(document)));
        assertThrows(IllegalArgumentException.class,
                () -> SlotContentRules.validateRuntimeInsertion(slot, new Element(document, "STACK")));
        ingredient.remove();
        SlotContentRules.restoreRequiredContent(slot);
        assertInstanceOf(Item.class, SlotContentRules.getSlotContent(slot));
    }

    @Test
    void expressionsDiscoverThirdPartyTypesWithoutWideningUnknownFilters() {
        TestType first = new TestType("test:first");
        TestType second = new TestType("test:second");
        GenericStackTypes.register(first);
        GenericStackTypes.register(second);
        assertEquals(first, GenericStackExpressionCompiler.parse("test:resource*7", first.id(), 0L).key().type());
        assertNull(GenericStackExpressionCompiler.parse("test:resource", "test:missing", 0L));
        assertEquals(0L, GenericStackExpressionCompiler.parse("test:resource*0", first.id(), 0L).amount());
        var mixed = IngredientExpressionCompiler.compile("test:resource", null, 0L, true, 1000L);
        assertEquals(2, mixed.candidates().size());
        assertEquals(1, IngredientExpressionCompiler.compile("#test:tag", first.id(), 0L, false, 1000L).candidates().size());
        assertTrue(IngredientExpressionCompiler.compile("test:resource", "test:missing", 0L, false, 1000L).candidates().isEmpty());
        assertFalse(GenericStackRenderers.render(null, mixed.candidates().get(0)));
    }

    private record TestKey(TestType type, String id) implements GenericKey {
        @Override public Component displayName() { return null; }
    }

    private record TestType(String id) implements GenericStackType<TestKey> {
        @Override public long defaultAmount() { return 1L; }
        @Override public TestKey readKey(CompoundTag tag) { return find("test:resource"); }
        @Override public CompoundTag writeKey(TestKey key) { return new CompoundTag(); }
        @Override public TestKey find(String id) { return "test:resource".equals(id) ? new TestKey(this, id) : null; }
        @Override public List<TestKey> findTag(String id) { return "test:tag".equals(id) ? List.of(find("test:resource")) : List.of(); }
    }
}

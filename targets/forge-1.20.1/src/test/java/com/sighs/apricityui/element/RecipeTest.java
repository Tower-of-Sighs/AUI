package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeTest {
    @Test
    void parsesOutputItemIdFromHtmlTextNodes() throws ReflectiveOperationException {
        Element.register(Recipe.TAG_NAME, (document, tagName) -> new Recipe(document));
        Recipe recipe = assertInstanceOf(Recipe.class, HTML.createElement(TestDocumentFactory.createDocument(), """
                <recipe type="crafting_shaped">minecraft:iron_ingot</recipe>
                """));

        assertEquals("minecraft:iron_ingot", recipe.getTextContent());
        Object outputItemId = parseOutputItemId(recipe);
        assertNotNull(outputItemId, "HTML recipe text must reach the preview resolver");
        assertEquals("minecraft:iron_ingot", outputItemId.toString());
    }

    @Test
    void preservesHtmlOutputItemIdWhenGeneratedSlotsAreRebuilt() throws ReflectiveOperationException {
        Element.register(Recipe.TAG_NAME, (document, tagName) -> new Recipe(document));
        Recipe recipe = assertInstanceOf(Recipe.class, HTML.createElement(TestDocumentFactory.createDocument(), """
                <recipe type="crafting_shaped">
                    "minecraft:iron_ingot"<!-- output item id -->
                </recipe>
                """));
        Element generatedSlot = new Element(recipe.document, "SLOT");
        generatedSlot.setAttribute("data-generated", "recipe-slot");
        generatedSlot.setTextContent("minecraft:iron_nugget");
        recipe.appendChild(generatedSlot);

        assertEquals("minecraft:iron_ingot", parseOutputItemId(recipe).toString());
        assertTrue(recipe.getRenderChildNodes().stream().noneMatch(com.sighs.apricityui.dom.TextNode.class::isInstance));
        assertTrue(recipe.clearGeneratedRecipeSlots());
        assertEquals("minecraft:iron_ingot", parseOutputItemId(recipe).toString());
        assertFalse(recipe.clearGeneratedRecipeSlots());
        assertEquals("minecraft:iron_ingot", parseOutputItemId(recipe).toString());
    }

    @Test
    void readsUpdatedTextContentAndRejectsInvalidIds() throws ReflectiveOperationException {
        Recipe recipe = new Recipe(TestDocumentFactory.createDocument());
        recipe.setTextContent(" 'minecraft:crafting_table' ");
        assertEquals("minecraft:crafting_table", parseOutputItemId(recipe).toString());

        recipe.setTextContent("minecraft:invalid recipe");
        assertNull(parseOutputItemId(recipe));
        recipe.setTextContent("");
        assertNull(parseOutputItemId(recipe));
    }

    private static Object parseOutputItemId(Recipe recipe) throws ReflectiveOperationException {
        return Recipe.class.getMethod("parseOutputItemId").invoke(recipe);
    }
}

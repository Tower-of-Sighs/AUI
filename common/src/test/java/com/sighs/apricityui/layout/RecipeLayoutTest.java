package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeLayoutTest {
    @ParameterizedTest
    @CsvSource({
            "crafting_shaped,60,21.5", "crafting_shapeless,60,21.5", "smelting_family,20,21.5",
            "stonecutting,20,3.5", "smithing,60,3.5", "fallback,78,21.5"
    })
    void everyRecipePlacesArrowBetweenInputsAndOutput(String layout, double arrowX, double arrowY) throws Exception {
        Element recipe = recipe(layout, 600);
        recipe.document.flushPendingStyleUpdates();
        recipe.document.commitRenderState();

        Element arrow = recipe.getRenderChildren().get(0);
        assertEquals(arrowX, Position.getOffset(arrow).x, .001);
        assertEquals(arrowY, Position.getOffset(arrow).y, .001);
        assertEquals(22, Size.of(arrow).width(), .001);
        assertEquals(15, Size.of(arrow).height(), .001);
        assertTrue(arrow.getComputedStyle().backgroundImage.contains("arrow.svg"));
        assertEquals("pixelated", arrow.getComputedStyle().imageRendering);
    }

    @ParameterizedTest
    @CsvSource({"crafting_shaped,600", "crafting_shaped,1600", "crafting_shapeless,1600"})
    void keepsSlotsCompactWithoutExplicitRecipeWidth(String layout, int parentWidth) throws Exception {
        Element recipe = recipe(layout, parentWidth);
        var document = recipe.document;
        Element[] inputs = new Element[9];
        for (int i = 0; i < inputs.length; i++) {
            Element slot = new Element(document, "slot") {};
            slot.setAttribute("class", "aui-recipe-slot aui-recipe-input");
            slot.setAttribute("data-i", String.valueOf(i));
            recipe.appendChild(slot);
            inputs[i] = slot;
        }
        Element output = new Element(document, "slot") {};
        output.setAttribute("class", "aui-recipe-slot aui-recipe-output");
        output.setAttribute("data-i", "0");
        recipe.appendChild(output);
        document.flushPendingStyleUpdates();
        assertEquals("pixelated", inputs[0].getComputedStyle().imageRendering);
        document.commitRenderState();
        assertLayout(inputs, output, 18, 2);
        Element arrow = recipe.getRenderChildren().get(0);
        assertEquals(60, Position.getOffset(arrow).x, .001);
        assertEquals(21.5, Position.getOffset(arrow).y, .001);
        assertEquals(22, Size.of(arrow).width(), .001);
        assertEquals(15, Size.of(arrow).height(), .001);
        assertTrue(arrow.getComputedStyle().backgroundImage.contains("arrow.svg"));

        recipe.getStyle().setProperty("--aui-recipe-slot-size", "24px");
        recipe.getStyle().setProperty("--aui-recipe-gap", "4px");
        document.flushPendingStyleUpdates();
        document.commitRenderState();
        assertLayout(inputs, output, 24, 4);
        assertEquals(84, Position.getOffset(arrow).x, .001);
        assertEquals(29.5, Position.getOffset(arrow).y, .001);
    }

    @ParameterizedTest
    @CsvSource({"600", "1600"})
    void cookingStacksInputAndFuelWithOutputBetweenThem(int parentWidth) throws Exception {
        Element recipe = recipe("smelting_family", parentWidth);
        Element input = slot(recipe, "input");
        Element fuel = slot(recipe, "fuel");
        Element output = slot(recipe, "output");
        recipe.document.flushPendingStyleUpdates();
        recipe.document.commitRenderState();

        assertEquals(Position.getOffset(input).x, Position.getOffset(fuel).x, .001, "fuel must be below input");
        assertEquals(40, Position.getOffset(fuel).y - Position.getOffset(input).y, .001);
        assertEquals(20, Position.getOffset(output).y - Position.getOffset(input).y, .001);
        assertEquals(46, Position.getOffset(output).x - Position.getOffset(input).x, .001);
        assertEquals(58, Size.of(recipe).height(), .001);

        var renderChildren = recipe.getRenderChildren();
        Element arrow = renderChildren.get(0);
        Element flame = renderChildren.get(renderChildren.size() - 1);
        assertEquals(20, Position.getOffset(arrow).x, .001);
        assertEquals(21.5, Position.getOffset(arrow).y, .001);
        assertEquals(22, Size.of(arrow).width(), .001);
        assertEquals(15, Size.of(arrow).height(), .001);
        assertEquals(2, Position.getOffset(flame).x, .001);
        assertEquals(22, Position.getOffset(flame).y, .001);
        assertEquals(13, Size.of(flame).width(), .001);
        assertEquals(13, Size.of(flame).height(), .001);
        assertTrue(arrow.getComputedStyle().backgroundImage.contains("arrow.svg"));
        assertTrue(flame.getComputedStyle().backgroundImage.contains("flame.svg"));
        assertEquals("pixelated", flame.getComputedStyle().imageRendering);
    }

    private Element recipe(String layout, int parentWidth) throws Exception {
        var document = TestDocumentFactory.createDocument();
        var rules = new HashMap<String, Map<String, CSS.Declaration>>();
        try (var stream = getClass().getResourceAsStream("/assets/apricityui/apricity/global.css")) {
            assertNotNull(stream);
            CSS.readCSS(new String(stream.readAllBytes(), StandardCharsets.UTF_8), rules, "global.css");
        }
        document.CSSCache.putAll(rules);
        document.rebuildSelectorIndex();
        document.body.setAttribute("style", "width:" + parentWidth + "px;height:400px");
        Element recipe = new Element(document, "recipe") {};
        recipe.setAttribute("data-recipe-layout", layout);
        document.body.appendChild(recipe);
        return recipe;
    }

    private Element slot(Element recipe, String role) {
        Element slot = new Element(recipe.document, "slot") {};
        slot.setAttribute("class", "aui-recipe-slot aui-recipe-" + role);
        slot.setAttribute("data-role", role);
        slot.setAttribute("data-i", "0");
        recipe.appendChild(slot);
        return slot;
    }

    private void assertLayout(Element[] inputs, Element output, int size, int gap) {
        int pitch = size + gap;
        for (int i = 0; i < inputs.length; i++) {
            assertEquals(i % 3 * pitch, Position.getOffset(inputs[i]).x, .001, "input " + i + " x");
            assertEquals(i / 3 * pitch, Position.getOffset(inputs[i]).y, .001, "input " + i + " y");
            assertEquals(size, Size.of(inputs[i]).width(), .001);
            assertEquals(size, Size.of(inputs[i]).height(), .001);
        }
        assertEquals(3 * size + 4 * gap + 24, Position.getOffset(output).x, .001, "output x");
        assertEquals(pitch, Position.getOffset(output).y, .001, "output y");
        assertEquals(size, Size.of(output).width(), .001);
        assertEquals(size, Size.of(output).height(), .001);
    }
}

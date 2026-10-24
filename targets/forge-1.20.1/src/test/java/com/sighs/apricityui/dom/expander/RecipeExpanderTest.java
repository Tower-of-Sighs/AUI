package com.sighs.apricityui.dom.expander;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import com.sighs.apricityui.element.Recipe;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeExpanderTest {
    private static java.util.Map<?, ?> registrations;

    @BeforeAll
    static void bootstrap() throws Exception {
        Field registry = Element.class.getDeclaredField("REGISTRY");
        registry.setAccessible(true);
        registrations = new java.util.HashMap<>((java.util.Map<?, ?>) registry.get(null));
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        Class<?> serializerType = Class.forName("net.minecraftforge.common.crafting.IIngredientSerializer");
        Object serializer = Class.forName("net.minecraftforge.common.crafting.VanillaIngredientSerializer").getField("INSTANCE").get(null);
        Class.forName("net.minecraftforge.common.crafting.CraftingHelper")
                .getMethod("register", itemId("minecraft:item").getClass(), serializerType)
                .invoke(null, itemId("minecraft:item"), serializer);
    }

    @AfterAll
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void restoreRegistrations() throws Exception {
        Field registry = Element.class.getDeclaredField("REGISTRY");
        registry.setAccessible(true);
        java.util.Map current = (java.util.Map) registry.get(null);
        current.clear();
        current.putAll(registrations);
    }

    @Test
    void matchesOutputItemsAndTypesAndKeepsRecipeOrderAndCounts() throws Exception {
        Object recipe = recipe("test:not_the_output_id", """
                {"type":"minecraft:crafting_shaped","pattern":["#"],
                 "key":{"#":{"item":"minecraft:iron_block"}},
                 "result":{"item":"minecraft:iron_ingot","count":9}}
                """);
        Object second = recipe("test:aaa", """
                {"type":"minecraft:crafting_shaped","pattern":["#"],
                 "key":{"#":{"item":"minecraft:iron_nugget"}},
                 "result":{"item":"minecraft:iron_ingot","count":1}}
                """);
        Object cooking = recipe("test:cooking", """
                {"type":"minecraft:smelting","ingredient":{"item":"minecraft:iron_ore"},
                 "result":"minecraft:iron_ingot","experience":0.7,"cookingtime":200}
                """);
        List<?> recipes = List.of(recipe, cooking, second);
        List<?> previews = (List<?>) invoke(find(recipes, "minecraft:iron_ingot", "CRAFTING_SHAPED"), "previews");
        assertEquals(2, previews.size());
        assertTrue(expressions(previews.get(0)).get(0).contains("iron_nugget"));
        assertTrue(expressions(previews.get(1)).get(0).contains("iron_block"));
        assertStack(expressions(previews.get(1)).get(9), "minecraft:iron_ingot", 9);
        Recipe element = new Recipe(TestDocumentFactory.createDocument());
        element.setAttribute("type", "crafting_shaped");
        element.setTextContent("minecraft:iron_ingot");
        element.previewState.update(element, 0L, false);
        set(element.previewState, "candidates", previews);
        element.previewState.update(element, 1000L, false);
        assertTrue(element.children.get(0).children.get(0).innerText.contains("iron_block"));
        assertStack(element.children.get(9).children.get(0).getTextContent(), "minecraft:iron_ingot", 9);
        assertEquals("minecraft:iron_ingot", element.getOutputItemExpression());
        element.previewState.update(element, 2000L, false);
        assertTrue(element.children.get(0).children.get(0).innerText.contains("iron_nugget"));
        assertStack(element.children.get(9).children.get(0).getTextContent(), "minecraft:iron_ingot", 1);
        element.setTextContent("minecraft:iron_ingot");
        element.previewState.update(element, 2100L, false);
        assertTrue(((List<?>) get(element.previewState, "candidates")).isEmpty(), "setting text must invalidate even the same query");
        assertEquals(1, ((List<?>) invoke(find(recipes, "minecraft:iron_ingot", "SMELTING"), "previews")).size());
        assertTrue(((List<?>) invoke(find(recipes, "minecraft:gold_ingot", "CRAFTING_SHAPED"), "previews")).isEmpty());
    }

    @Test
    void stonecuttingOnlyIncludesRequestedOutput() throws Exception {
        Object slab = recipe("test:slab", """
                {"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:stone"},
                 "result":"minecraft:stone_slab","count":2}
                """);
        Object stairs = recipe("test:stairs", """
                {"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:stone"},
                 "result":"minecraft:stone_stairs","count":1}
                """);
        List<?> previews = (List<?>) invoke(find(List.of(slab, stairs), "minecraft:stone_slab", "STONECUTTING"), "previews");
        assertEquals(1, previews.size());
        assertEquals(2, expressions(previews.get(0)).size());
        assertStack(expressions(previews.get(0)).get(1), "minecraft:stone_slab", 2);
    }

    @Test
    void smithingIncludesTemplateBaseAndAddition() throws Exception {
        Object smithing = recipe("test:upgrade_sword", """
                {"type":"minecraft:smithing_transform",
                 "template":{"item":"minecraft:netherite_upgrade_smithing_template"},
                 "base":{"item":"minecraft:diamond_sword"},
                 "addition":{"item":"minecraft:netherite_ingot"},
                 "result":{"item":"minecraft:netherite_sword"}}
                """);
        List<?> previews = (List<?>) invoke(find(List.of(smithing), "minecraft:netherite_sword", "SMITHING"), "previews");
        assertEquals(1, previews.size());
        List<?> entries = (List<?>) invoke(previews.get(0), "entries");
        assertEquals(4, entries.size(), "smithing must include all three ingredients and its output");
        String[] roles = {"TEMPLATE", "INPUT", "ADDITION", "OUTPUT"};
        String[] items = {"netherite_upgrade_smithing_template", "diamond_sword", "netherite_ingot", "netherite_sword"};
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(roles[i], invoke(entries.get(i), "role").toString());
            assertTrue(expressions(previews.get(0)).get(i).contains("minecraft:" + items[i]));
        }
    }

    @Test
    void rotatesWholeRecipesAndWaitsAFullSecondAfterHover() throws Exception {
        RecipeExpander.PreviewState state = new RecipeExpander.PreviewState();
        set(state, "candidates", List.of("first", "second"));
        set(state, "nextRotateAt", 1000L);
        assertFalse(state.advance(999L, false));
        assertTrue(state.advance(1000L, false));
        assertEquals(1, get(state, "index"));
        assertFalse(state.advance(2000L, true));
        assertFalse(state.advance(4000L, true));
        assertFalse(state.advance(5000L, false));
        assertFalse(state.advance(5999L, false));
        assertTrue(state.advance(6000L, false));
        assertEquals(0, get(state, "index"));
        set(state, "candidates", List.of("only"));
        assertFalse(state.advance(9000L, false));
        set(state, "candidates", List.of());
        assertFalse(state.advance(10000L, false));
    }

    @Test
    void runtimeChangesAndRecipeReloadResetCandidatesWithoutRepeatedRebuilds() throws Exception {
        Recipe recipe = new Recipe(TestDocumentFactory.createDocument());
        recipe.setAttribute("type", "crafting_shaped");
        recipe.setTextContent("minecraft:iron_ingot");
        recipe.previewState.update(recipe, 0L, false);
        Element slot = new Element(recipe.document, "DIV");
        slot.setAttribute("data-generated", "recipe-slot");
        slot.setTextContent("minecraft:gold_ingot");
        recipe.appendChild(slot);
        recipe.previewState.update(recipe, 500L, false);
        assertTrue(recipe.children.contains(slot), "unchanged query must not rebuild slots");
        recipe.setAttribute("type", "smelting");
        recipe.previewState.update(recipe, 600L, false);
        assertFalse(recipe.children.contains(slot));
        assertEquals(0, get(recipe.previewState, "index"));
        recipe.appendChild(slot);
        Class<?> resolver = Class.forName(RecipeExpander.class.getName() + "$RecipeResolver");
        Method clear = resolver.getDeclaredMethod("clearCache");
        clear.setAccessible(true);
        clear.invoke(null);
        recipe.previewState.update(recipe, 700L, false);
        assertFalse(recipe.children.contains(slot));
        recipe.setTextContent("minecraft:gold_ingot");
        recipe.previewState.update(recipe, 800L, false);
        assertEquals("minecraft:gold_ingot", get(recipe.previewState, "expression"));
        assertFalse(recipe.getAttribute("data-recipe-error").isEmpty());
    }

    private static void assertStack(String expression, String item, int count) throws Exception {
        Object stack = Class.forName("com.sighs.apricityui.slot.ItemStackExpressionCompiler")
                .getMethod("parse", String.class).invoke(null, expression);
        assertEquals(count, stack.getClass().getMethod("getCount").invoke(stack));
        assertTrue(expression.contains(item));
    }

    private static List<String> expressions(Object preview) throws Exception {
        java.util.ArrayList<String> expressions = new java.util.ArrayList<>();
        for (Object entry : (List<?>) invoke(preview, "entries")) expressions.add((String) invoke(entry, "slotExpression"));
        return expressions;
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object recipe(String id, String json) throws Exception {
        return Class.forName("net.minecraft.world.item.crafting.RecipeManager")
                .getMethod("fromJson", Class.forName("net.minecraft.resources.ResourceLocation"), com.google.gson.JsonObject.class)
                .invoke(null, itemId(id), JsonParser.parseString(json).getAsJsonObject());
    }

    private static Object itemId(String id) throws Exception {
        return Class.forName("net.minecraft.resources.ResourceLocation").getConstructor(String.class).newInstance(id);
    }

    private static Object find(List<?> recipes, String output, String type) throws Exception {
        Class<?> resolver = Class.forName(RecipeExpander.class.getName() + "$RecipeResolver");
        Class<?> declaredType = Class.forName(resolver.getName() + "$DeclaredType");
        Method valueOf = declaredType.getMethod("valueOf", String.class);
        valueOf.setAccessible(true);
        Method find = resolver.getDeclaredMethod("findPreviews", java.util.Collection.class,
                Class.forName("net.minecraft.core.RegistryAccess"), itemId(output).getClass(), declaredType);
        find.setAccessible(true);
        Object registryAccess = Class.forName("net.minecraft.core.RegistryAccess").getField("EMPTY").get(null);
        return find.invoke(null, recipes, registryAccess, itemId(output), valueOf.invoke(null, type));
    }

    private static Object invoke(Object target, String method) throws Exception {
        Method accessor = target.getClass().getMethod(method);
        accessor.setAccessible(true);
        return accessor.invoke(target);
    }
}

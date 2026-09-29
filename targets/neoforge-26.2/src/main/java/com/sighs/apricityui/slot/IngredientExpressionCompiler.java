package com.sighs.apricityui.slot;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仅解析 Ingredient 候选语法；具体 ItemStack 解析委托给 ItemStackExpressionCompiler。
 */
public final class IngredientExpressionCompiler {
    public static final int MAX_CANDIDATES = 128;
    public static final Identifier FURNACE_FUEL_TAG =
            Identifier.fromNamespaceAndPath(ApricityUI.MODID, "furnace_fuels");

    private static final Map<Identifier, List<ItemStack>> TAG_CACHE = new ConcurrentHashMap<>();

    private IngredientExpressionCompiler() {
    }

    public static IngredientDisplaySpec compile(String rawExpression, boolean cycleEnabled, long cycleIntervalMs) {
        String normalized = ItemStackExpressionCompiler.normalize(rawExpression);
        if (normalized.isBlank()) return IngredientDisplaySpec.EMPTY;

        List<ItemStack> candidates = compileCandidates(normalized, MAX_CANDIDATES);
        return new IngredientDisplaySpec(candidates, cycleEnabled && candidates.size() > 1, cycleIntervalMs);
    }

    public static void clearTagCache() {
        TAG_CACHE.clear();
    }

    public static String furnaceFuelTagLiteral() {
        return "#" + FURNACE_FUEL_TAG;
    }

    private static List<ItemStack> compileCandidates(String expression, int maxCandidates) {
        if (expression.contains("|")) return compilePipe(expression, maxCandidates);
        if (expression.startsWith("#")) return tagCandidates(parseTag(expression), maxCandidates);
        if (expression.startsWith("{") || expression.startsWith("[")) {
            return jsonCandidates(expression, maxCandidates);
        }

        ItemStack stack = ItemStackExpressionCompiler.parse(expression);
        return stack.isEmpty() ? List.of() : List.of(stack);
    }

    private static List<ItemStack> compilePipe(String expression, int maxCandidates) {
        LinkedHashMap<String, ItemStack> candidates = new LinkedHashMap<>();
        for (String part : expression.split("\\|")) {
            if (candidates.size() >= maxCandidates) break;
            String normalized = ItemStackExpressionCompiler.normalize(part);
            if (normalized.isBlank()) continue;

            if (normalized.startsWith("#")) {
                append(candidates, tagCandidates(parseTag(normalized), maxCandidates - candidates.size()), maxCandidates);
            } else {
                ItemStack stack = ItemStackExpressionCompiler.parse(normalized);
                if (!stack.isEmpty()) append(candidates, List.of(stack), maxCandidates);
            }
        }
        return List.copyOf(candidates.values());
    }

    private static List<ItemStack> jsonCandidates(String expression, int maxCandidates) {
        try {
            JsonElement json = JsonParser.parseString(expression);
            com.mojang.datafixers.util.Pair<Ingredient, JsonElement> pair =
                    Ingredient.CODEC.decode(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            List<ItemStack> items = pair.getFirst().items().map(ItemStack::new).toList();
            if (items.isEmpty()) return List.of();

            LinkedHashMap<String, ItemStack> candidates = new LinkedHashMap<>();
            for (ItemStack stack : items) {
                append(candidates, List.of(stack), maxCandidates);
            }
            return List.copyOf(candidates.values());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static void append(Map<String, ItemStack> output, List<ItemStack> stacks, int maxCandidates) {
        if (stacks == null) return;
        for (ItemStack stack : stacks) {
            if (output.size() >= maxCandidates) return;
            if (stack == null || stack.isEmpty()) continue;

            ItemStack copy = stack.copy();
            if (copy.getCount() <= 0) copy.setCount(1);
            output.putIfAbsent(ItemStackExpressionCompiler.serialize(copy), copy);
        }
    }

    private static Identifier parseTag(String expression) {
        return expression == null || expression.length() < 2
                ? null
                : Identifier.tryParse(expression.substring(1));
    }

    private static List<ItemStack> tagCandidates(Identifier id, int maxCandidates) {
        if (id == null || maxCandidates <= 0) return List.of();
        List<ItemStack> cached = TAG_CACHE.computeIfAbsent(id, IngredientExpressionCompiler::buildTagCandidates);
        return cached.size() <= maxCandidates ? cached : cached.subList(0, maxCandidates);
    }

    private static List<ItemStack> buildTagCandidates(Identifier id) {
        ArrayList<ItemStack> candidates = new ArrayList<>();
        if (FURNACE_FUEL_TAG.equals(id)) {
            FuelValues fuelValues = null;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.level != null) {
                fuelValues = minecraft.level.fuelValues();
            }
            if (fuelValues == null) return List.of();
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (stack.getBurnTime(RecipeType.SMELTING, fuelValues) <= 0) continue;
                candidates.add(stack);
                if (candidates.size() >= MAX_CANDIDATES) break;
            }
        } else {
            TagKey<Item> key = TagKey.create(Registries.ITEM, id);
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (!stack.is(key)) continue;
                candidates.add(stack);
                if (candidates.size() >= MAX_CANDIDATES) break;
            }
        }
        candidates.sort(Comparator.comparing(stack -> {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return itemId == null ? "" : itemId.toString();
        }));
        return List.copyOf(candidates);
    }
}

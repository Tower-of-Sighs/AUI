package com.sighs.apricityui.slot;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.GenericStackType;
import com.sighs.apricityui.stack.GenericStackTypes;
import com.sighs.apricityui.stack.ItemKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IngredientExpressionCompiler {
    public static final int MAX_CANDIDATES = 128;
    public static final ResourceLocation FURNACE_FUEL_TAG = new ResourceLocation(ApricityUI.MODID, "furnace_fuels");
    private static final Map<String, List<GenericStack>> TAG_CACHE = new ConcurrentHashMap<>();

    private IngredientExpressionCompiler() {
    }

    public static IngredientDisplaySpec compile(String expression, boolean cycle, long interval) {
        return compile(expression, "all", 0L, cycle, interval);
    }

    public static IngredientDisplaySpec compile(String rawExpression, String typeFilter, long defaultAmount,
                                                boolean cycleEnabled, long cycleIntervalMs) {
        String normalized = ItemStackExpressionCompiler.normalize(rawExpression);
        if (normalized.isBlank()) return IngredientDisplaySpec.EMPTY;
        List<GenericStack> candidates = compileCandidates(normalized, typeFilter, defaultAmount, MAX_CANDIDATES);
        return new IngredientDisplaySpec(candidates, cycleEnabled && candidates.size() > 1, cycleIntervalMs);
    }

    public static void clearTagCache() {
        TAG_CACHE.clear();
    }

    public static String furnaceFuelTagLiteral() {
        return "#" + FURNACE_FUEL_TAG;
    }

    private static List<GenericStack> compileCandidates(String expression, String typeFilter,
                                                        long defaultAmount, int limit) {
        if (expression.startsWith("{") || expression.startsWith("[")) {
            return jsonCandidates(expression, typeFilter, defaultAmount, limit);
        }
        LinkedHashMap<String, GenericStack> output = new LinkedHashMap<>();
        for (String part : expression.split("\\|")) {
            if (output.size() >= limit) break;
            String normalized = ItemStackExpressionCompiler.normalize(part);
            if (normalized.isBlank()) continue;
            List<GenericStack> candidates = normalized.startsWith("#")
                    ? tagCandidates(normalized.substring(1), typeFilter, defaultAmount)
                    : directCandidates(normalized, typeFilter, defaultAmount);
            append(output, candidates, limit);
        }
        return List.copyOf(output.values());
    }

    private static List<GenericStack> directCandidates(String expression, String typeFilter, long defaultAmount) {
        GenericStackExpressionCompiler.ParsedLiteral literal = GenericStackExpressionCompiler.splitAmount(expression);
        ResourceLocation id = ResourceLocation.tryParse(literal.value());
        if (id == null) {
            GenericStack stack = GenericStackExpressionCompiler.parse(expression, typeFilter, defaultAmount);
            return stack == null ? List.of() : List.of(stack);
        }

        ArrayList<GenericStack> result = new ArrayList<>();
        for (GenericStackType<?> type : GenericStackTypes.values()) {
            if (!GenericStackExpressionCompiler.allows(typeFilter, type)) continue;
            GenericKey key = type.find(id);
            if (key == null) continue;
            long amount = literal.amount() != null
                    ? literal.amount()
                    : defaultAmount > 0L ? defaultAmount : type.defaultAmount();
            result.add(new GenericStack(key, amount));
        }
        return result;
    }

    private static List<GenericStack> tagCandidates(String literal, String typeFilter, long defaultAmount) {
        GenericStackExpressionCompiler.ParsedLiteral parsed = GenericStackExpressionCompiler.splitAmount(literal);
        ResourceLocation id = ResourceLocation.tryParse(parsed.value());
        if (id == null) return List.of();
        String cacheKey = typeFilter + "|" + id + "|" + defaultAmount + "|" + parsed.amount();
        return TAG_CACHE.computeIfAbsent(cacheKey, ignored -> buildTagCandidates(
                id, typeFilter, parsed.amount() != null ? parsed.amount() : defaultAmount));
    }

    private static List<GenericStack> buildTagCandidates(ResourceLocation id, String typeFilter,
                                                          long requestedAmount) {
        ArrayList<GenericStack> result = new ArrayList<>();
        for (GenericStackType<?> type : GenericStackTypes.values()) {
            if (!GenericStackExpressionCompiler.allows(typeFilter, type)) continue;
            if (type == GenericStackTypes.ITEM && FURNACE_FUEL_TAG.equals(id)) {
                for (Item item : BuiltInRegistries.ITEM) {
                    ItemStack stack = new ItemStack(item);
                    if (ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) <= 0) continue;
                    ItemKey key = ItemKey.of(stack);
                    if (key != null) result.add(new GenericStack(key, amountFor(type, requestedAmount)));
                    if (result.size() >= MAX_CANDIDATES) break;
                }
            } else {
                for (GenericKey key : type.findTag(id)) {
                    result.add(new GenericStack(key, amountFor(type, requestedAmount)));
                    if (result.size() >= MAX_CANDIDATES) break;
                }
            }
            if (result.size() >= MAX_CANDIDATES) break;
        }
        result.sort(Comparator.comparing(IngredientExpressionCompiler::sortKey));
        return List.copyOf(result);
    }

    private static List<GenericStack> jsonCandidates(String expression, String typeFilter,
                                                      long defaultAmount, int limit) {
        if (!GenericStackExpressionCompiler.allows(typeFilter, GenericStackTypes.ITEM)) return List.of();
        try {
            JsonElement json = JsonParser.parseString(expression);
            ItemStack[] items = Ingredient.fromJson(json).getItems();
            LinkedHashMap<String, GenericStack> output = new LinkedHashMap<>();
            if (items != null) {
                for (ItemStack item : items) {
                    ItemKey key = ItemKey.of(item);
                    if (key != null) append(output, List.of(new GenericStack(
                            key, defaultAmount > 0L ? defaultAmount : Math.max(1, item.getCount()))), limit);
                }
            }
            return List.copyOf(output.values());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static void append(Map<String, GenericStack> output, List<GenericStack> stacks, int limit) {
        for (GenericStack stack : stacks) {
            if (output.size() >= limit) return;
            if (stack == null || stack.amount() <= 0L) continue;
            output.putIfAbsent(GenericStack.writeTag(stack).toString(), stack);
        }
    }

    private static long amountFor(GenericStackType<?> type, long requested) {
        return requested > 0L ? requested : type.defaultAmount();
    }

    private static String sortKey(GenericStack stack) {
        return stack.what().type().id() + "|" + stack.what().id() + "|" + GenericStack.writeTag(stack);
    }
}

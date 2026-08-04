package com.sighs.apricityui.slot;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将 slot innerText 编译为可展示候选集合。
 */
public final class SlotExpressionCompiler {
    public static final int MAX_CANDIDATES = 128;
    public static final Identifier FURNACE_FUEL_TAG = Identifier.fromNamespaceAndPath(ApricityUI.MODID, "furnace_fuels");

    private static final Map<Identifier, List<ItemStack>> TAG_CACHE = new ConcurrentHashMap<>();

    public static SlotDisplaySpec compile(String rawExpression, boolean cycleEnabled, long cycleIntervalMs) {
        String normalized = normalize(rawExpression);
        if (normalized.isBlank()) return SlotDisplaySpec.EMPTY;

        List<ItemStack> candidates = compileCandidates(normalized, MAX_CANDIDATES);
        boolean shouldCycle = cycleEnabled && candidates.size() > 1;
        return new SlotDisplaySpec(candidates, shouldCycle, cycleIntervalMs);
    }

    public static void clearTagCache() {
        TAG_CACHE.clear();
    }

    public static String furnaceFuelTagLiteral() {
        return "#" + FURNACE_FUEL_TAG;
    }

    public static String buildLiteralWithCount(String rawLiteral, int requestedCount) {
        String normalized = normalize(rawLiteral);
        if (normalized.isBlank()) return "";
        ItemStack parsed = parseItemStackLiteral(normalized);
        if (parsed.isEmpty()) return normalized;

        int safeCount = Math.max(1, Math.min(parsed.getMaxStackSize(), requestedCount));
        parsed.setCount(safeCount);
        return stackToSnbt(parsed);
    }

    private static List<ItemStack> compileCandidates(String normalized, int maxCandidates) {
        if (normalized.contains("|")) {
            return compileShorthandIngredient(normalized, maxCandidates);
        }
        if (normalized.startsWith("{") || normalized.startsWith("[")) {
            List<ItemStack> fromIngredient = compileJsonIngredient(normalized, maxCandidates);
            if (!fromIngredient.isEmpty()) return fromIngredient;
        }
        if (normalized.startsWith("#")) {
            Identifier tagId = parseTagId(normalized);
            if (tagId == null) return List.of();
            return getTagCandidates(tagId, maxCandidates);
        }

        ItemStack parsed = parseItemStackLiteral(normalized);
        if (parsed.isEmpty()) return List.of();
        return List.of(parsed);
    }

    private static List<ItemStack> compileShorthandIngredient(String normalized, int maxCandidates) {
        LinkedHashMap<String, ItemStack> dedup = new LinkedHashMap<>();
        String[] tokens = normalized.split("\\|");
        for (String token : tokens) {
            if (dedup.size() >= maxCandidates) break;
            String part = normalize(token);
            if (part.isBlank()) continue;
            if (part.startsWith("#")) {
                Identifier tagId = parseTagId(part);
                if (tagId == null) continue;
                List<ItemStack> tagCandidates = getTagCandidates(tagId, maxCandidates - dedup.size());
                appendDeduplicated(dedup, tagCandidates, maxCandidates);
                continue;
            }
            ItemStack parsed = parseItemStackLiteral(part);
            if (!parsed.isEmpty()) {
                appendDeduplicated(dedup, List.of(parsed), maxCandidates);
            }
        }
        return List.copyOf(dedup.values());
    }

    private static List<ItemStack> compileJsonIngredient(String normalized, int maxCandidates) {
        try {
            JsonElement jsonElement = JsonParser.parseString(normalized);
            com.mojang.datafixers.util.Pair<Ingredient, JsonElement> pair =
                    Ingredient.CODEC.decode(com.mojang.serialization.JsonOps.INSTANCE, jsonElement).getOrThrow();
            Ingredient ingredient = pair.getFirst();
            List<ItemStack> items = ingredient.items().map(ItemStack::new).toList();
            if (items.isEmpty()) return List.of();

            LinkedHashMap<String, ItemStack> dedup = new LinkedHashMap<>();
            for (ItemStack stack : items) {
                if (dedup.size() >= maxCandidates) break;
                if (stack == null || stack.isEmpty()) continue;
                appendDeduplicated(dedup, List.of(stack), maxCandidates);
            }
            return List.copyOf(dedup.values());
        } catch (JsonSyntaxException ignored) {
            return List.of();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static void appendDeduplicated(Map<String, ItemStack> dedup, List<ItemStack> stacks, int maxCandidates) {
        for (ItemStack stack : stacks) {
            if (dedup.size() >= maxCandidates) return;
            if (stack == null || stack.isEmpty()) continue;
            ItemStack copy = stack.copy();
            if (copy.getCount() <= 0) copy.setCount(1);
            String key = stackIdentity(copy);
            dedup.putIfAbsent(key, copy);
        }
    }

    private static String stackIdentity(ItemStack stack) {
        return stackToSnbt(stack);
    }

    private static List<ItemStack> getTagCandidates(Identifier tagId, int maxCandidates) {
        List<ItemStack> cached = TAG_CACHE.computeIfAbsent(tagId, SlotExpressionCompiler::buildTagCandidates);
        if (cached.size() <= maxCandidates) return cached;
        return cached.subList(0, Math.max(0, maxCandidates));
    }

    private static List<ItemStack> buildTagCandidates(Identifier tagId) {
        ArrayList<ItemStack> result = new ArrayList<>();
        if (FURNACE_FUEL_TAG.equals(tagId)) {
            FuelValues fuelValues = null;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.level != null) {
                fuelValues = minecraft.level.fuelValues();
            }
            if (fuelValues == null) return List.of();
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (stack.getBurnTime(RecipeType.SMELTING, fuelValues) <= 0) continue;
                result.add(stack);
                if (result.size() >= MAX_CANDIDATES) break;
            }
        } else {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (!stack.is(tagKey)) continue;
                result.add(stack);
                if (result.size() >= MAX_CANDIDATES) break;
            }
        }
        result.sort(Comparator.comparing(stack -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id == null ? "" : id.toString();
        }));
        return List.copyOf(result);
    }

    private static Identifier parseTagId(String normalized) {
        if (normalized == null || normalized.length() < 2) return null;
        return Identifier.tryParse(normalized.substring(1));
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String normalized = raw.trim();
        if (normalized.isBlank()) return "";
        if (normalized.length() >= 2) {
            char first = normalized.charAt(0);
            char last = normalized.charAt(normalized.length() - 1);
            boolean quoted = (first == '"' && last == '"') || (first == '\'' && last == '\'');
            if (quoted) normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static ItemStack parseItemStackLiteral(String literal) {
        if (literal == null || literal.isBlank()) return ItemStack.EMPTY;
        HolderLookup.Provider lookup = lookupProvider();

        if (literal.startsWith("{") && literal.endsWith("}")) {
            try {
                CompoundTag stackTag = TagParser.parseCompoundFully(literal);
                return parseStackSnbt(stackTag);
            } catch (CommandSyntaxException ignored) {
                return ItemStack.EMPTY;
            }
        }

        int nbtStart = literal.indexOf('{');
        String itemLiteral = nbtStart >= 0 ? literal.substring(0, nbtStart).trim() : literal.trim();
        if (itemLiteral.isBlank()) return ItemStack.EMPTY;

        Identifier itemId = Identifier.tryParse(itemLiteral.toLowerCase(Locale.ROOT));
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(itemId).map(ref -> ref.value()).orElse(null);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        if (nbtStart < 0) return stack;

        String nbtLiteral = literal.substring(nbtStart).trim();
        try {
            CompoundTag nbtTag = TagParser.parseCompoundFully(nbtLiteral);
            return parseStackSnbt(nbtTag);
        } catch (CommandSyntaxException ignored) {
            return ItemStack.EMPTY;
        }
    }

    /** Serializes an ItemStack to its SNBT representation via the 26.1 codec. */
    private static String stackToSnbt(ItemStack stack) {
        try {
            RegistryOps<Tag> ops = lookupProvider().createSerializationContext(NbtOps.INSTANCE);
            Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            return tag.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    /** Parses an ItemStack SNBT compound (must contain an "id") via the 26.1 codec. */
    private static ItemStack parseStackSnbt(CompoundTag tag) {
        try {
            RegistryOps<Tag> ops = lookupProvider().createSerializationContext(NbtOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static HolderLookup.Provider lookupProvider() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null) {
            return minecraft.level.registryAccess();
        }
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
}

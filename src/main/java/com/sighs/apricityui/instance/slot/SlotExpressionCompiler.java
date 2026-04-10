package com.sighs.apricityui.instance.slot;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.element.Recipe;
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
import net.minecraft.client.Minecraft;
import com.mojang.serialization.JsonOps;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将 slot innerText 编译为可展示候选集合。
 */
public final class SlotExpressionCompiler {
    public static final int MAX_CANDIDATES = 128;
    public static final Identifier FURNACE_FUEL_TAG = Identifier.fromNamespaceAndPath(ApricityUI.MODID, "furnace_fuels");

    private static final Map<Identifier, List<ItemStack>> TAG_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> DEFERRED = ThreadLocal.withInitial(() -> false);

    private static void markDeferred() {
        DEFERRED.set(true);
    }

    private static boolean consumeDeferredFlag() {
        boolean deferred = Boolean.TRUE.equals(DEFERRED.get());
        DEFERRED.set(false);
        return deferred;
    }

    public static SlotDisplaySpec compile(String rawExpression, boolean cycleEnabled, long cycleIntervalMs) {
        DEFERRED.set(false);
        String normalized = normalize(rawExpression);
        if (normalized.isBlank()) return SlotDisplaySpec.EMPTY;

        List<ItemStack> candidates = compileCandidates(normalized, MAX_CANDIDATES);
        if (consumeDeferredFlag()) {
            // Some items/tags cannot be resolved yet (e.g. ItemStack components not bound during early ticks).
            // Signal caller to retry later without caching an empty/partial result.
            return null;
        }
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
        RegistryOps<net.minecraft.nbt.Tag> ops = currentNbtOps();
        if (ops == null) {
            Identifier id = BuiltInRegistries.ITEM.getKey(parsed.getItem());
            return id.toString();
        }
        return ItemStack.CODEC.encodeStart(ops, parsed)
                .result()
                .map(Object::toString)
                .orElse(normalized);
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
            RegistryOps<JsonElement> ops = currentJsonOps();
            if (ops == null) return List.of();

            Ingredient ingredient = Ingredient.CODEC.parse(ops, jsonElement).result().orElse(null);
            if (ingredient == null || ingredient.isEmpty()) return List.of();

            LinkedHashMap<String, ItemStack> dedup = new LinkedHashMap<>();
            ingredient.items()
                    .map(holder -> new ItemStack(holder.value()))
                    .limit(Math.max(0, maxCandidates))
                    .forEach(stack -> appendDeduplicated(dedup, List.of(stack), maxCandidates));
            return List.copyOf(dedup.values());
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
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = id == null ? "" : id.toString();
        return itemId + "#" + stack.getCount() + "#" + stack.getComponentsPatch();
    }

    private static List<ItemStack> getTagCandidates(Identifier tagId, int maxCandidates) {
        List<ItemStack> cached = TAG_CACHE.get(tagId);
        if (cached == null) {
            List<ItemStack> built = buildTagCandidates(tagId);
            if (built == null) {
                // Deferred (e.g. level/components not ready) - do not cache.
                return List.of();
            }
            TAG_CACHE.put(tagId, built);
            cached = built;
        }
        if (cached.size() <= maxCandidates) return cached;
        return cached.subList(0, Math.max(0, maxCandidates));
    }

    /**
     * Builds candidates for a tag. Returns {@code null} when resolution should be retried later.
     */
    private static List<ItemStack> buildTagCandidates(Identifier tagId) {
        ArrayList<ItemStack> result = new ArrayList<>();
        if (FURNACE_FUEL_TAG.equals(tagId)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack;
                try {
                    stack = new ItemStack(item);
                } catch (RuntimeException e) {
                    if (isComponentsNotBoundYet(e)) {
                        markDeferred();
                        return null;
                    }
                    continue;
                }
                if (stack.getBurnTime(RecipeType.SMELTING, mc.level.fuelValues()) <= 0) continue;
                result.add(stack);
                if (result.size() >= MAX_CANDIDATES) break;
            }
        } else {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack;
                try {
                    stack = new ItemStack(item);
                } catch (RuntimeException e) {
                    if (isComponentsNotBoundYet(e)) {
                        markDeferred();
                        return null;
                    }
                    continue;
                }
                if (!stack.is(tagKey)) continue;
                result.add(stack);
                if (result.size() >= MAX_CANDIDATES) break;
            }
        }
        result.sort(Comparator.comparing(stack -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id.toString();
        }));
        return List.copyOf(result);
    }

    private static Identifier parseTagId(String normalized) {
        if (normalized == null || normalized.length() < 2) return null;
        return Identifier.tryParse(normalized.substring(1));
    }

    private static String normalize(String raw) {
        return Recipe.normalizeRecipeIdLiteral(raw);
    }

    private static ItemStack parseItemStackLiteral(String literal) {
        if (literal == null || literal.isBlank()) return ItemStack.EMPTY;

        if (literal.startsWith("{") && literal.endsWith("}")) {
            try {
                CompoundTag stackTag = TagParser.parseCompoundFully(literal);
                ItemStack parsed = decodeStackFromTag(stackTag);
                return parsed == null ? ItemStack.EMPTY : parsed;
            } catch (CommandSyntaxException ignored) {
                return ItemStack.EMPTY;
            }
        }

        int nbtStart = literal.indexOf('{');
        String itemLiteral = nbtStart >= 0 ? literal.substring(0, nbtStart).trim() : literal.trim();
        if (itemLiteral.isBlank()) return ItemStack.EMPTY;

        Identifier itemId = Identifier.tryParse(itemLiteral.toLowerCase(Locale.ROOT));
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(itemId);
        ItemStack stack;
        try {
            stack = new ItemStack(item);
        } catch (RuntimeException ignored) {
            // certain registry-backed items may not have their DataComponents bound yet
            // (e.g. during very early client ticks / title screen). Avoid crashing; render empty until ready.
            if (isComponentsNotBoundYet(ignored)) {
                markDeferred();
            }
            return ItemStack.EMPTY;
        }

        // Legacy "item{...}" stack NBT is no longer directly supported by ItemStack.
        // We keep the item portion to ensure existing templates continue to render something sensible.
        return stack;
    }

    private static RegistryOps<Tag> currentNbtOps() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    private static RegistryOps<JsonElement> currentJsonOps() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    private static ItemStack decodeStackFromTag(CompoundTag tag) {
        RegistryOps<Tag> ops = currentNbtOps();
        if (ops != null) {
            ItemStack decoded = ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
            if (!decoded.isEmpty()) return decoded;
        }

        // Compatibility fallback: legacy ItemStack NBT
        String idString = tag.getStringOr("id", "");
        if (idString.isBlank()) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(idString);
        if (id == null) return ItemStack.EMPTY;

        Item item = BuiltInRegistries.ITEM.getValue(id);

        int count = tag.getIntOr("count", -1);
        if (count <= 0) {
            count = Byte.toUnsignedInt(tag.getByteOr("Count", (byte) 1));
        }
        try {
            return new ItemStack(item, Math.max(1, count));
        } catch (RuntimeException ignored) {
            if (isComponentsNotBoundYet(ignored)) {
                markDeferred();
            }
            return ItemStack.EMPTY;
        }
    }

    private static boolean isComponentsNotBoundYet(RuntimeException exception) {
        if (exception == null) return false;
        // Vanilla throws NPE via Objects.requireNonNull(components, "Components not bound yet")
        if (exception instanceof NullPointerException) {
            String message = exception.getMessage();
            return message != null && message.contains("Components not bound yet");
        }
        return false;
    }
}

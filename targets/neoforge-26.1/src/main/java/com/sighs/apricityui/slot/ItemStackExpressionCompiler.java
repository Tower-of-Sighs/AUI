package com.sighs.apricityui.slot;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * 仅解析单一 ItemStack 的文本表示。
 */
public final class ItemStackExpressionCompiler {
    private ItemStackExpressionCompiler() {
    }

    public static ItemStack parse(String rawLiteral) {
        String literal = normalize(rawLiteral);
        if (literal.isBlank() || "minecraft:air".equals(literal)) return ItemStack.EMPTY;
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
        String itemLiteral = nbtStart >= 0 ? literal.substring(0, nbtStart).trim() : literal;
        Identifier itemId = Identifier.tryParse(itemLiteral.toLowerCase(Locale.ROOT));
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(itemId).map(ref -> ref.value()).orElse(null);
        if (item == null) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(item);
        if (nbtStart < 0) return stack;

        try {
            CompoundTag nbtTag = TagParser.parseCompoundFully(literal.substring(nbtStart).trim());
            nbtTag.putString("id", itemId.toString());
            return parseStackSnbt(nbtTag);
        } catch (CommandSyntaxException ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static String serialize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:air";
        return stackToSnbt(stack);
    }

    public static String withCount(String rawLiteral, int requestedCount) {
        ItemStack stack = parse(rawLiteral);
        if (stack.isEmpty()) return normalize(rawLiteral);
        stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), requestedCount)));
        return serialize(stack);
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String normalized = raw.trim();
        if (normalized.length() >= 2) {
            char first = normalized.charAt(0);
            char last = normalized.charAt(normalized.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
            }
        }
        return normalized;
    }

    private static String stackToSnbt(ItemStack stack) {
        try {
            RegistryOps<Tag> ops = lookupProvider().createSerializationContext(NbtOps.INSTANCE);
            Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            return tag.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

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

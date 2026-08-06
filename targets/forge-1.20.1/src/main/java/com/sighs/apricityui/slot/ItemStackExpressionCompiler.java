package com.sighs.apricityui.slot;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
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

        if (literal.startsWith("{") && literal.endsWith("}")) {
            try {
                CompoundTag stackTag = TagParser.parseTag(literal);
                ItemStack parsed = ItemStack.of(stackTag);
                return parsed == null ? ItemStack.EMPTY : parsed;
            } catch (CommandSyntaxException ignored) {
                return ItemStack.EMPTY;
            }
        }

        int nbtStart = literal.indexOf('{');
        String itemLiteral = nbtStart >= 0 ? literal.substring(0, nbtStart).trim() : literal;
        ResourceLocation itemId = ResourceLocation.tryParse(itemLiteral.toLowerCase(Locale.ROOT));
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return ItemStack.EMPTY;

        Item item = BuiltInRegistries.ITEM.get(itemId);
        ItemStack stack = new ItemStack(item);
        if (nbtStart < 0) return stack;

        try {
            stack.setTag(TagParser.parseTag(literal.substring(nbtStart).trim()));
            return stack;
        } catch (CommandSyntaxException ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static String serialize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:air";
        CompoundTag tag = new CompoundTag();
        stack.save(tag);
        return tag.toString();
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
}

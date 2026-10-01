package com.sighs.apricityui.slot;

import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
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
        HolderLookup.Provider lookup = lookupProvider();

        if (literal.startsWith("{") && literal.endsWith("}")) {
            try {
                CompoundTag stackTag = TagParser.parseTag(literal);
                return ItemStack.parseOptional(lookup, stackTag);
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
            CompoundTag stackTag = TagParser.parseTag(literal.substring(nbtStart).trim());
            stackTag.putString("id", itemId.toString());
            return ItemStack.parseOptional(lookup, stackTag);
        } catch (CommandSyntaxException ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static String serialize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:air";
        // 1.21.1 的 save(provider, prefix) 只把 prefix 当"前缀"读，返回的是新建的 tag：
        // 丢掉返回值的话，任何非空堆都会序列化成 "{}"（issue #99）。
        return stack.save(lookupProvider(), new CompoundTag()).toString();
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

    /**
     * 解析 ingredient JSON 用的 ops。vanilla 的 {@code HolderSetCodec} 只在 {@code RegistryOps}
     * 下能拿到注册表（{@code JsonOps} 走 decodeWithoutRegistry），所以 {@code items} 是
     * HolderSet 的 {@code neoforge:components} 一类 ingredient 在 JsonOps 下直接解析失败、
     * 被静默吞成"没有候选"（issue #99）。
     */
    static DynamicOps<JsonElement> ingredientOps() {
        return net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, lookupProvider());
    }

    private static HolderLookup.Provider lookupProvider() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null) {
            return minecraft.level.registryAccess();
        }
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
}

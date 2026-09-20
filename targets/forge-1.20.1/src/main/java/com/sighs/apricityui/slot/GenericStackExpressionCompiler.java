package com.sighs.apricityui.slot;

import com.sighs.apricityui.stack.FluidKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.GenericStackType;
import com.sighs.apricityui.stack.GenericStackTypes;
import com.sighs.apricityui.stack.ItemKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class GenericStackExpressionCompiler {
    private GenericStackExpressionCompiler() {
    }

    public static GenericStack parse(String raw, String typeFilter, long groupDefaultAmount) {
        ParsedLiteral literal = splitAmount(ItemStackExpressionCompiler.normalize(raw));
        if (literal.value().isBlank() || "minecraft:air".equals(literal.value())) return null;

        String type = normalizeType(typeFilter);
        if (allows(type, GenericStackTypes.ITEM)) {
            ItemStack itemStack = ItemStackExpressionCompiler.parse(literal.value());
            ItemKey key = ItemKey.of(itemStack);
            if (key != null) {
                long amount = literal.amount() != null
                        ? literal.amount()
                        : groupDefaultAmount > 0 ? groupDefaultAmount : itemStack.getCount();
                return new GenericStack(key, amount);
            }
        }

        if (allows(type, GenericStackTypes.FLUID)) {
            ResourceLocation id = ResourceLocation.tryParse(literal.value().toLowerCase(Locale.ROOT));
            FluidKey key = GenericStackTypes.FLUID.find(id);
            if (key != null) {
                long amount = literal.amount() != null
                        ? literal.amount()
                        : groupDefaultAmount > 0 ? groupDefaultAmount : GenericStackTypes.FLUID.defaultAmount();
                return new GenericStack(key, amount);
            }
        }
        return null;
    }

    public static String serialize(GenericStack stack) {
        if (stack == null) return "minecraft:air";
        if (stack.what() instanceof ItemKey itemKey) {
            ItemStack itemStack = itemKey.toStack((int) Math.max(1L, Math.min(Integer.MAX_VALUE, stack.amount())));
            return ItemStackExpressionCompiler.serialize(itemStack);
        }
        return stack.what().id() + "*" + stack.amount();
    }

    public static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) return "all";
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("item".equals(normalized) || GenericStackTypes.ITEM.id().toString().equals(normalized)) return "item";
        if ("fluid".equals(normalized) || GenericStackTypes.FLUID.id().toString().equals(normalized)) return "fluid";
        return "all";
    }

    public static boolean allows(String filter, GenericStackType<?> type) {
        String normalized = normalizeType(filter);
        return "all".equals(normalized)
                || ("item".equals(normalized) && type == GenericStackTypes.ITEM)
                || ("fluid".equals(normalized) && type == GenericStackTypes.FLUID);
    }

    public static ParsedLiteral splitAmount(String expression) {
        if (expression == null) return new ParsedLiteral("", null);
        int separator = expression.lastIndexOf('*');
        if (separator <= 0 || separator >= expression.length() - 1) {
            return new ParsedLiteral(expression, null);
        }
        try {
            long amount = Long.parseLong(expression.substring(separator + 1).trim());
            return amount > 0
                    ? new ParsedLiteral(expression.substring(0, separator).trim(), amount)
                    : new ParsedLiteral(expression, null);
        } catch (NumberFormatException ignored) {
            return new ParsedLiteral(expression, null);
        }
    }

    public record ParsedLiteral(String value, Long amount) {
    }
}

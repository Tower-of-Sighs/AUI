package com.sighs.apricityui.slot;

import com.sighs.apricityui.stack.FluidStackType;
import com.sighs.apricityui.stack.ItemStackType;
import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.GenericStackType;
import com.sighs.apricityui.stack.GenericStackTypes;
import com.sighs.apricityui.stack.ItemKey;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class GenericStackExpressionCompiler {
    private GenericStackExpressionCompiler() { }
    public static GenericStack parse(String raw, String typeFilter, long groupDefaultAmount) {
        ParsedLiteral literal = splitAmount(ItemStackExpressionCompiler.normalize(raw));
        if (literal.value().isBlank() || "minecraft:air".equals(literal.value())) return null;
        String type = normalizeType(typeFilter);
        if (allows(type, GenericStackTypes.require(ItemStackType.class))) {
            ItemKey key = ItemKey.of(ItemStackExpressionCompiler.parse(literal.value()));
            if (key != null) return new GenericStack(key, literal.amount() != null ? literal.amount() : groupDefaultAmount > 0 ? groupDefaultAmount : 1L);
        }
        for (GenericStackType<?> resourceType : GenericStackTypes.values()) {
            if (resourceType == GenericStackTypes.require(ItemStackType.class) || !allows(type, resourceType)) continue;
            GenericKey key = resourceType.find(literal.value());
            if (key != null) return new GenericStack(key, literal.amount() != null ? literal.amount() : groupDefaultAmount > 0 ? groupDefaultAmount : resourceType.defaultAmount());
        }
        return null;
    }
    public static String serialize(GenericStack stack) {
        if (stack == null) return "minecraft:air";
        if (stack.key() instanceof ItemKey itemKey) return ItemStackExpressionCompiler.serialize(itemKey.toStack((int) Math.max(1, Math.min(Integer.MAX_VALUE, stack.amount()))));
        return stack.key().id() + "*" + stack.amount();
    }
    public static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) return "all";
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("item".equals(normalized)) return GenericStackTypes.require(ItemStackType.class).id();
        if ("fluid".equals(normalized)) return GenericStackTypes.require(FluidStackType.class).id();
        return normalized;
    }
    public static boolean allows(String filter, GenericStackType<?> type) { return "all".equals(normalizeType(filter)) || type.id().equals(normalizeType(filter)); }
    public static ParsedLiteral splitAmount(String expression) {
        if (expression == null) return new ParsedLiteral("", null);
        int separator = expression.lastIndexOf('*');
        if (separator <= 0 || separator >= expression.length() - 1) return new ParsedLiteral(expression, null);
        try { long amount = Long.parseLong(expression.substring(separator + 1).trim()); return amount >= 0 ? new ParsedLiteral(expression.substring(0, separator).trim(), amount) : new ParsedLiteral(expression, null); }
        catch (NumberFormatException ignored) { return new ParsedLiteral(expression, null); }
    }
    public record ParsedLiteral(String value, Long amount) { }
}



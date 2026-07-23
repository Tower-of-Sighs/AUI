package com.sighs.apricityui.instance.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.init.TextNode;
import com.sighs.apricityui.instance.dom.SlotContentRules;
import com.sighs.apricityui.instance.slot.IngredientDisplaySpec;
import com.sighs.apricityui.instance.slot.IngredientExpressionCompiler;
import com.sighs.apricityui.instance.slot.ItemStackExpressionCompiler;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * 候选 ItemStack 集合；由一个受控 Item 显示当前候选。
 */
@ElementRegister(Ingredient.TAG_NAME)
public class Ingredient extends MinecraftElement {
    public static final String TAG_NAME = "INGREDIENT";

    private String compiledSignature = "";
    private IngredientDisplaySpec displaySpec = IngredientDisplaySpec.EMPTY;
    private int candidateIndex;
    private long nextRotateAtMillis;

    public Ingredient(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public void tick() {
        super.tick();
        refreshIfNeeded();
        Item item = SlotContentRules.ensureControlledItem(this);
        if (!displaySpec.hasCandidates()) {
            item.setIngredientStack(ItemStack.EMPTY);
            item.setTextContent("minecraft:air");
            return;
        }

        int size = displaySpec.candidates().size();
        if (candidateIndex < 0 || candidateIndex >= size) candidateIndex = 0;
        long now = System.currentTimeMillis();
        if (displaySpec.cycleEnabled() && size > 1 && !isHover && !item.isHover) {
            if (nextRotateAtMillis <= 0L) {
                nextRotateAtMillis = now + displaySpec.cycleIntervalMs();
            } else if (now >= nextRotateAtMillis) {
                candidateIndex = (candidateIndex + 1) % size;
                nextRotateAtMillis = now + displaySpec.cycleIntervalMs();
            }
        }

        ItemStack selected = displaySpec.candidates().get(candidateIndex).copy();
        item.setIngredientStack(selected);
        item.setTextContent(ItemStackExpressionCompiler.serialize(selected));
    }

    public String getCandidateExpression() {
        StringBuilder builder = new StringBuilder();
        for (Node child : childNodes) {
            if (child instanceof TextNode textNode) builder.append(textNode.getTextContent());
        }
        return builder.isEmpty() ? (innerText == null ? "" : innerText) : builder.toString();
    }

    private void refreshIfNeeded() {
        String expression = getCandidateExpression();
        boolean cycle = cycleEnabled();
        long interval = cycleInterval();
        String signature = expression + "|" + cycle + "|" + interval;
        if (signature.equals(compiledSignature)) return;
        compiledSignature = signature;
        displaySpec = IngredientExpressionCompiler.compile(expression, cycle, interval);
        candidateIndex = 0;
        nextRotateAtMillis = 0L;
    }

    private boolean cycleEnabled() {
        String raw = getAttribute("cycle");
        if (raw == null || raw.isBlank()) return true;
        return !switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "0", "false", "no", "off", "disabled", "none" -> true;
            default -> false;
        };
    }

    private long cycleInterval() {
        String raw = getAttribute("cycle-interval");
        if (raw == null || raw.isBlank()) return IngredientDisplaySpec.DEFAULT_CYCLE_INTERVAL_MS;
        try {
            return Math.max(200L, Long.parseLong(raw.trim()));
        } catch (NumberFormatException ignored) {
            return IngredientDisplaySpec.DEFAULT_CYCLE_INTERVAL_MS;
        }
    }
}

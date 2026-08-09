package com.sighs.apricityui.element;

import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.BodyRenderNodeProvider;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.slot.IngredientDisplaySpec;
import com.sighs.apricityui.slot.IngredientExpressionCompiler;
import com.sighs.apricityui.slot.ItemStackExpressionCompiler;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * 候选 ItemStack 集合；由一个受控 Item 显示当前候选。
 */
@ElementRegister(Ingredient.TAG_NAME)
public class Ingredient extends MinecraftElement implements BodyRenderNodeProvider {
    public static final String TAG_NAME = "INGREDIENT";

    static {
        Element.register(TAG_NAME, (document, tagName) -> new Ingredient(document));
    }

    private String compiledSignature = "";
    private IngredientDisplaySpec displaySpec = IngredientDisplaySpec.EMPTY;
    private int candidateIndex;
    private long nextRotateAtMillis;

    public Ingredient(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public List<RenderNode> createBodyRenderNodes() {
        return List.of(new RenderNode.ElementBackgroundNode(this));
    }

    @Override
    public void tick() {
        super.tick();
        refreshIfNeeded();

        Item item = SlotContentRules.ensureControlledItem(this);
        if (item == null) return;
        if (!displaySpec.hasCandidates()) {
            item.setIngredientStack(ItemStack.EMPTY);
            updateControlledItemText(item, "minecraft:air");
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
        updateControlledItemText(item, ItemStackExpressionCompiler.serialize(selected));
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
        boolean cycleEnabled = resolveCycleEnabled();
        long cycleInterval = resolveCycleIntervalMs();
        String signature = expression + "|cycle=" + cycleEnabled + "|interval=" + cycleInterval;
        if (signature.equals(compiledSignature)) return;

        compiledSignature = signature;
        displaySpec = IngredientExpressionCompiler.compile(expression, cycleEnabled, cycleInterval);
        candidateIndex = 0;
        nextRotateAtMillis = 0L;
    }

    private boolean resolveCycleEnabled() {
        Boolean cssFlag = parseBooleanLike(getCustomPropertyInherit("--aui-ingredient-cycle"));
        if (cssFlag == null) cssFlag = parseBooleanLike(getCustomPropertyInherit("--aui-slot-cycle"));
        if (cssFlag != null) return cssFlag;

        Boolean attrFlag = parseBooleanLike(getAttribute("cycle"));
        return attrFlag == null || attrFlag;
    }

    private long resolveCycleIntervalMs() {
        Long cssInterval = parsePositiveLong(getCustomPropertyInherit("--aui-ingredient-cycle-interval"));
        if (cssInterval == null) cssInterval = parsePositiveLong(getCustomPropertyInherit("--aui-slot-cycle-interval"));
        if (cssInterval != null) return Math.max(200L, cssInterval);

        Long attrInterval = parsePositiveLong(getFirstNonBlankAttribute("cycle-interval", "rotate-interval"));
        if (attrInterval != null) return Math.max(200L, attrInterval);
        return IngredientDisplaySpec.DEFAULT_CYCLE_INTERVAL_MS;
    }

    private void updateControlledItemText(Item item, String value) {
        if (item == null || value == null || value.equals(item.getTextContent())) return;
        item.setTextContent(value);
    }

    private String getFirstNonBlankAttribute(String... keys) {
        if (keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            String value = getAttribute(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static Boolean parseBooleanLike(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "unset".equals(normalized) || "auto".equals(normalized)) return null;
        return switch (normalized) {
            case "1", "true", "yes", "on", "enabled" -> true;
            case "0", "false", "no", "off", "disabled", "none" -> false;
            default -> null;
        };
    }

    private static Long parsePositiveLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0L ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

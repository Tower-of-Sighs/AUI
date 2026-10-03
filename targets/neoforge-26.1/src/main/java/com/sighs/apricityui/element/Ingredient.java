package com.sighs.apricityui.element;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.slot.IngredientDisplaySpec;
import com.sighs.apricityui.slot.IngredientExpressionCompiler;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.GenericStackType;
import com.sighs.apricityui.stack.GenericStackController.Source;

import java.util.List;
import java.util.Locale;

@ElementRegister(Ingredient.TAG_NAME)
public class Ingredient extends GenericStackElement {
    public static final String TAG_NAME = "INGREDIENT";

    private String compiledSignature = "";
    private IngredientDisplaySpec displaySpec = IngredientDisplaySpec.EMPTY;
    private int candidateIndex;
    private long nextRotateAtMillis;

    public Ingredient(Document document) {
        super(document, TAG_NAME);
        controller.setDrivenState(null, null, null, false, false, Source.INGREDIENT);
    }

    @Override
    public GenericStackType<?> type() {
        return null;
    }

    public List<GenericStack> candidates() {
        refreshIfNeeded();
        return displaySpec.candidates();
    }

    @Override
    public void tick() {
        super.tick();
        refreshIfNeeded();

        if (!displaySpec.hasCandidates()) {
            setDrivenState(null, null, false, false, Source.INGREDIENT);
            return;
        }

        int size = displaySpec.candidates().size();
        if (candidateIndex < 0 || candidateIndex >= size) candidateIndex = 0;

        long now = System.currentTimeMillis();
        Slot slot = findAncestor(Slot.class);
        if (displaySpec.cycleEnabled() && size > 1 && !isHover && (slot == null || !slot.isHover)) {
            if (nextRotateAtMillis <= 0L) {
                nextRotateAtMillis = now + displaySpec.cycleIntervalMs();
            } else if (now >= nextRotateAtMillis) {
                candidateIndex = (candidateIndex + 1) % size;
                nextRotateAtMillis = now + displaySpec.cycleIntervalMs();
            }
        }

        GenericStack selected = displaySpec.candidates().get(candidateIndex);
        setDrivenState(selected, null, false, false, Source.INGREDIENT);
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
        String type = getAttribute("type");
        long amount = resolveDefaultAmount();
        String signature = expression + "|type=" + type + "|amount=" + amount
                + "|cycle=" + cycleEnabled + "|interval=" + cycleInterval;
        if (signature.equals(compiledSignature)) return;

        compiledSignature = signature;
        displaySpec = IngredientExpressionCompiler.compile(expression, type, amount, cycleEnabled, cycleInterval);
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

    private long resolveDefaultAmount() {
        String raw = getAttribute("amount");
        if (raw == null || raw.isBlank()) return 0L;
        try {
            return Math.max(0L, Long.parseLong(raw.trim()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
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

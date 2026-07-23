package com.sighs.apricityui.instance.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.slot.IngredientExpressionCompiler;
import com.sighs.apricityui.instance.slot.ItemStackExpressionCompiler;
import com.sighs.apricityui.registry.annotation.ElementRegister;

import java.util.Map;

/**
 * 真实 Menu Slot 的背景壳与绑定索引；物品内容属于直接 Item 子节点。
 */
@ElementRegister(Slot.TAG_NAME)
public class Slot extends MinecraftElement {
    public static final String TAG_NAME = "SLOT";

    private boolean bound;

    public Slot(Document document) {
        super(document, TAG_NAME);
    }

    public static String furnaceFuelVirtualTagLiteral() {
        return IngredientExpressionCompiler.furnaceFuelTagLiteral();
    }

    public static void clearCandidateCache() {
        IngredientExpressionCompiler.clearTagCache();
    }

    public static String buildLiteralWithCount(String rawLiteral, int requestedCount) {
        return ItemStackExpressionCompiler.withCount(rawLiteral, requestedCount);
    }

    public boolean isBound() {
        return bound;
    }

    public void bindToMenuSlot() {
        bound = true;
    }

    public void clearMenuSlotBinding() {
        bound = false;
    }

    public int getRepeatCount() {
        try {
            return Math.max(1, Integer.parseInt(getAttribute("repeat").trim()));
        } catch (Exception ignored) {
            return 1;
        }
    }

    public int getSlotIndex() {
        try {
            return Integer.parseInt(getAttribute("slot-index").trim());
        } catch (Exception ignored) {
            return -1;
        }
    }

    public void applyRecipeSlotMeta(String className, String generatedTag) {
        setAttributesBatch(Map.of(
                "class", className == null ? "" : className,
                "data-generated", generatedTag == null ? "" : generatedTag
        ), true);
    }
}

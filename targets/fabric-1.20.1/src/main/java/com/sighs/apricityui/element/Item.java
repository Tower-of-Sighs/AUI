package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.slot.GenericStackExpressionCompiler;
import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.ItemKey;
import net.minecraft.world.item.ItemStack;

/** Item-only view of a generic stack. */
@ElementRegister(Item.TAG_NAME)
public class Item extends Stack {
    public static final String TAG_NAME = "ITEM";

    static {
        Element.register(TAG_NAME, (document, tagName) -> new Item(document));
    }

    public Item(Document document) {
        super(document, TAG_NAME);
    }

    public void setDrivenState(ItemStack stack, String overlayText, boolean hidden,
                               boolean menuDisabled, Source source) {
        super.setDrivenState(GenericStack.fromItemStack(stack), overlayText, hidden, menuDisabled, source);
    }

    public void setIngredientStack(ItemStack stack) {
        super.setIngredientStack(GenericStack.fromItemStack(stack));
    }

    public boolean canShowItemTooltip() {
        return canShowStackTooltip();
    }

    public boolean shouldPaintItem() {
        return shouldPaintStack();
    }

    @Override
    public boolean accepts(GenericKey key) {
        return key instanceof ItemKey;
    }

    @Override
    protected GenericStack parseLocalStack(String expression) {
        return GenericStackExpressionCompiler.parse(expression, "item", parseAmountAttribute());
    }

    @Override
    protected ItemStack toDisplayItemStack(GenericStack stack) {
        if (!(stack.what() instanceof ItemKey itemKey) || stack.amount() <= 0L) return ItemStack.EMPTY;
        return itemKey.toStack((int) Math.min(Integer.MAX_VALUE, stack.amount()));
    }

    @Override
    protected String defaultOverlayText(GenericStack stack) {
        return stack.overlayText();
    }
}

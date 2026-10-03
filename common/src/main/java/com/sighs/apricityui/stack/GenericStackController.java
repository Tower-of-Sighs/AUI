package com.sighs.apricityui.stack;

public final class GenericStackController {
    private Source source = Source.NONE;
    private GenericStack drivenStack;
    private String overlayText;
    private boolean hidden;
    private boolean menuDisabled;

    public boolean accepts(GenericStackType<?> type, GenericKey key) {
        return key != null && (type == null || type.id().equals(key.type().id()));
    }

    public void setDrivenState(GenericStackType<?> type, GenericStack stack, String overlay,
                               boolean hidden, boolean menuDisabled, Source source) {
        drivenStack = stack != null && accepts(type, stack.key()) ? stack : null;
        overlayText = overlay;
        this.hidden = hidden;
        this.menuDisabled = menuDisabled;
        this.source = source == null ? Source.NONE : source;
    }

    public void clearDrivenState(Source expectedSource) {
        if (expectedSource != null && source != expectedSource) return;
        source = Source.NONE;
        drivenStack = null;
        overlayText = null;
        hidden = false;
        menuDisabled = false;
    }

    public Source source() {
        return source;
    }

    public boolean shouldPaint() {
        return !hidden && (source != Source.MENU || !menuDisabled);
    }

    public GenericStack currentStack(GenericStack localStack) {
        return source == Source.NONE ? localStack : drivenStack;
    }

    public String overlayText(GenericStack stack) {
        return overlayText != null ? overlayText : stack == null ? null : stack.key().type().formatAmount(stack.amount());
    }

    public enum Source {
        NONE,
        INGREDIENT,
        MENU
    }
}

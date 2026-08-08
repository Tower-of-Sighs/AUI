package com.sighs.apricityui.render;

/** Pure JVM depth ledger for flat GUI item rendering. */
public final class GuiItemDepths {
    public static final float SCREEN_ITEM_MODEL_Z = 150.0F;
    public static final float SCREEN_ITEM_DECORATION_Z = 200.0F;
    public static final float SCREEN_ITEM_FOREGROUND_Z = SCREEN_ITEM_DECORATION_Z + 0.125F;
    public static final float SCREEN_FLOATING_ITEM_MODEL_Z = SCREEN_ITEM_FOREGROUND_Z + 0.125F;
    public static final float SCREEN_FLOATING_ITEM_DECORATION_Z = SCREEN_FLOATING_ITEM_MODEL_Z
            + (SCREEN_ITEM_DECORATION_Z - SCREEN_ITEM_MODEL_Z);
    public static final float FLAT_DOCUMENT_LAYER_STEP = SCREEN_FLOATING_ITEM_DECORATION_Z + 1.0F;

    private GuiItemDepths() {
    }

    public static float foregroundZ(float decorationZ, boolean accumulateDepth) {
        if (accumulateDepth) return 0.0F;
        return decorationZ + (SCREEN_ITEM_FOREGROUND_Z - SCREEN_ITEM_DECORATION_Z);
    }
}

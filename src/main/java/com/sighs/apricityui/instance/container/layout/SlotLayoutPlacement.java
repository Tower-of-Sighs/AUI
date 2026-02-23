package com.sighs.apricityui.instance.container.layout;

enum SlotLayoutMode {
    GRID,
    MANUAL
}

public record SlotLayoutPlacement(
        int screenX,
        int screenY,
        int menuX,
        int menuY,
        int slotSize,
        boolean hidden,
        SlotLayoutMode mode
) {
    public static final int OFFSCREEN = -10000;

    public static SlotLayoutPlacement hiddenGrid() {
        return hidden(SlotLayoutMode.GRID);
    }

    public static SlotLayoutPlacement hiddenManual() {
        return hidden(SlotLayoutMode.MANUAL);
    }

    public static SlotLayoutPlacement grid(int screenX, int screenY, int menuX, int menuY, int slotSize) {
        return new SlotLayoutPlacement(screenX, screenY, menuX, menuY, slotSize, false, SlotLayoutMode.GRID);
    }

    public static SlotLayoutPlacement manual(int screenX, int screenY, int menuX, int menuY, int slotSize) {
        return new SlotLayoutPlacement(screenX, screenY, menuX, menuY, slotSize, false, SlotLayoutMode.MANUAL);
    }

    public static SlotLayoutPlacement hidden(SlotLayoutMode mode) {
        return new SlotLayoutPlacement(OFFSCREEN, OFFSCREEN, OFFSCREEN, OFFSCREEN, 16, true, mode);
    }
}

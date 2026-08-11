package com.sighs.apricityui.container;

/**
 * 玩家背包逻辑槽位与菜单注册顺序之间的映射。
 */
public final class PlayerInventorySlotOrder {
    private static final int HOTBAR_SLOT_COUNT = 9;

    private PlayerInventorySlotOrder() {
    }

    /**
     * 将菜单中的相对槽位位置映射为玩家背包逻辑索引。
     */
    public static int menuRelativeIndexToPlayerInventoryIndex(int menuRelativeIndex, int capacity) {
        if (!isValidIndex(menuRelativeIndex, capacity)) return -1;
        if (capacity <= HOTBAR_SLOT_COUNT) return menuRelativeIndex;

        int mainInventorySlotCount = capacity - HOTBAR_SLOT_COUNT;
        return menuRelativeIndex < mainInventorySlotCount
                ? menuRelativeIndex + HOTBAR_SLOT_COUNT
                : menuRelativeIndex - mainInventorySlotCount;
    }

    /**
     * 将玩家背包逻辑索引映射为菜单中的相对槽位位置。
     */
    public static int playerInventoryIndexToMenuRelativeIndex(int playerInventoryIndex, int capacity) {
        if (!isValidIndex(playerInventoryIndex, capacity)) return -1;
        if (capacity <= HOTBAR_SLOT_COUNT) return playerInventoryIndex;

        int mainInventorySlotCount = capacity - HOTBAR_SLOT_COUNT;
        return playerInventoryIndex < HOTBAR_SLOT_COUNT
                ? mainInventorySlotCount + playerInventoryIndex
                : playerInventoryIndex - HOTBAR_SLOT_COUNT;
    }

    private static boolean isValidIndex(int index, int capacity) {
        return capacity > 0 && index >= 0 && index < capacity;
    }
}

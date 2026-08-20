package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.config.ApricitySavedData;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * SavedData 物品槽数据源，支持扩缩容（截断策略）。
 */
@SuppressWarnings("removal")
public final class SavedDataDataSource implements ContainerDataSource {
    private final ContainerBindType bindType;
    private final ApricitySavedData savedData;
    private final String inventoryKey;
    private ItemStackHandler handler;

    public SavedDataDataSource(ContainerBindType bindType,
                               ApricitySavedData savedData,
                               String inventoryKey,
                               ItemStackHandler handler) {
        this.bindType = bindType;
        this.savedData = savedData;
        this.inventoryKey = inventoryKey;
        this.handler = handler;
    }

    @Override
    public ContainerBindType bindType() {
        return bindType;
    }

    @Override
    public int capacity() {
        return handler.getSlots();
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y, FilterUtil filter) {
        return new MenuFilteredSlotItemHandler(handler, slotIndex, x, y, filter);
    }

    @Override
    public boolean supportsResize() {
        return true;
    }

    @Override
    public int resize(int newCapacity) {
        int normalized = Math.max(1, newCapacity);
        handler = savedData.getOrCreate(inventoryKey, normalized);
        return handler.getSlots();
    }

    @Override
    public void onClose(ServerPlayer player) {
        savedData.setDirty();
    }
}

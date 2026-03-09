package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.ApricitySavedData;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

/**
 * SavedData 物品槽数据源，支持扩缩容。
 */
public final class SavedDataDataSource implements ContainerDataSource {
    private final ContainerBindType bindType;
    private final ApricitySavedData savedData;
    private final String inventoryKey;
    private Container container;

    public SavedDataDataSource(ContainerBindType bindType,
                               ApricitySavedData savedData,
                               String inventoryKey,
                               Container container) {
        this.bindType = bindType;
        this.savedData = savedData;
        this.inventoryKey = inventoryKey;
        this.container = container;
    }

    @Override
    public ContainerBindType bindType() {
        return bindType;
    }

    @Override
    public int capacity() {
        return container.getContainerSize();
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y) {
        return new Slot(container, slotIndex, x, y);
    }

    @Override
    public boolean supportsResize() {
        return true;
    }

    @Override
    public int resize(int newCapacity, OpenBindPlan.ResizePolicy policy) {
        int normalized = Math.max(1, newCapacity);
        OpenBindPlan.ResizePolicy effectivePolicy = policy == null
                ? OpenBindPlan.ResizePolicy.KEEP_OVERFLOW
                : policy;
        container = savedData.getOrCreate(inventoryKey, normalized, effectivePolicy);
        return container.getContainerSize();
    }

    @Override
    public void onClose(ServerPlayer player) {
        savedData.setDirty();
    }

    public InventoryStorage storage() {
        return InventoryStorage.of(container, null);
    }
}

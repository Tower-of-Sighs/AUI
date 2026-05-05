package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.ApricitySavedData;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * SavedData 物品槽数据源，支持扩缩容（截断策略）。
 */
public final class SavedDataDataSource implements ContainerDataSource {
    private final ContainerBindType bindType;
    private final ApricitySavedData savedData;
    private final String inventoryKey;
    private ItemStacksResourceHandler handler;

    public SavedDataDataSource(ContainerBindType bindType,
                               ApricitySavedData savedData,
                               String inventoryKey,
                               ItemStacksResourceHandler handler) {
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
        return handler.size();
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y) {
        return new ResourceHandlerSlot(handler, handler::set, slotIndex, x, y);
    }

    @Override
    public boolean supportsResize() {
        return true;
    }

    @Override
    public int resize(int newCapacity) {
        int normalized = Math.max(1, newCapacity);
        handler = savedData.getOrCreate(inventoryKey, normalized);
        return handler.size();
    }

    @Override
    public void onClose(ServerPlayer player) {
        savedData.setDirty();
    }
}

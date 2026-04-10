package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.ApricitySavedData;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * SavedData 物品槽数据源，支持扩缩容。
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
    public int resize(int newCapacity, OpenBindPlan.ResizePolicy policy) {
        int normalized = Math.max(1, newCapacity);
        OpenBindPlan.ResizePolicy effectivePolicy = policy == null
                ? OpenBindPlan.ResizePolicy.KEEP_OVERFLOW
                : policy;
        handler = savedData.getOrCreate(inventoryKey, normalized, effectivePolicy);
        return handler.size();
    }

    @Override
    public void onClose(ServerPlayer player) {
        savedData.setDirty();
    }
}

package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.config.ApricitySavedData;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.container.storage.GenericStorage;
import com.sighs.apricityui.container.storage.GenericStorages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;

public final class SavedDataDataSource implements ContainerDataSource {
    private final ContainerBindType bindType;
    private final ApricitySavedData savedData;
    private final String inventoryKey;
    private SimpleContainer container;
    private final boolean merge;
    public SavedDataDataSource(ContainerBindType bindType, ApricitySavedData savedData, String inventoryKey, SimpleContainer container) { this(bindType, savedData, inventoryKey, container, false); }
    public SavedDataDataSource(ContainerBindType bindType, ApricitySavedData savedData, String inventoryKey, SimpleContainer container, boolean merge) { this.bindType = bindType; this.savedData = savedData; this.inventoryKey = inventoryKey; this.container = container; this.merge = merge; }
    public ContainerBindType bindType() { return bindType; }
    public int capacity() { return container.getContainerSize(); }
    public GenericStorage genericStorage() { return GenericStorages.view(GenericStorages.container(container), merge, container.getContainerSize()); }
    public Slot createSlot(int slotIndex, int x, int y, FilterUtil filter) {
        return new Slot(FilteredContainer.of(container, filter), slotIndex, x, y);
    }
    public boolean supportsResize() { return true; }
    public int resize(int newCapacity) { container = savedData.getOrCreate(inventoryKey, Math.max(1, newCapacity)); return capacity(); }
    public void onClose(ServerPlayer player) { savedData.setDirty(); }
}

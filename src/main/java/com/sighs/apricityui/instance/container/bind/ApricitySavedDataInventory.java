package com.sighs.apricityui.instance.container.bind;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用世界级库存 SavedData。
 */
public class ApricitySavedDataInventory extends SavedData {
    private static final String INVENTORIES_KEY = "inventories";

    private final LinkedHashMap<String, ItemStackHandler> inventories = new LinkedHashMap<>();

    public static ApricitySavedDataInventory get(MinecraftServer server, String dataName) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ApricitySavedDataInventory::new, ApricitySavedDataInventory::load),
                dataName
        );
    }

    public static ApricitySavedDataInventory load(CompoundTag tag, HolderLookup.Provider provider) {
        ApricitySavedDataInventory data = new ApricitySavedDataInventory();
        CompoundTag allInventories = tag.getCompound(INVENTORIES_KEY);
        for (String key : allInventories.getAllKeys()) {
            CompoundTag serialized = allInventories.getCompound(key);
            int slotCount = Math.max(1, serialized.getInt("Size"));
            ItemStackHandler handler = data.createTrackedHandler(slotCount);
            handler.deserializeNBT(provider, serialized);
            data.inventories.put(key, handler);
        }
        return data;
    }

    public ItemStackHandler getOrCreate(String inventoryKey, int slotCount) {
        ItemStackHandler existing = inventories.get(inventoryKey);
        if (existing == null) {
            ItemStackHandler created = createTrackedHandler(slotCount);
            inventories.put(inventoryKey, created);
            setDirty();
            return created;
        }

        if (existing.getSlots() >= slotCount) return existing;

        ItemStackHandler expanded = createTrackedHandler(slotCount);
        int copyCount = Math.min(existing.getSlots(), expanded.getSlots());
        for (int i = 0; i < copyCount; i++) {
            expanded.setStackInSlot(i, existing.getStackInSlot(i).copy());
        }
        inventories.put(inventoryKey, expanded);
        setDirty();
        return expanded;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        CompoundTag allInventories = new CompoundTag();
        for (Map.Entry<String, ItemStackHandler> entry : inventories.entrySet()) {
            allInventories.put(entry.getKey(), entry.getValue().serializeNBT(provider));
        }
        tag.put(INVENTORIES_KEY, allInventories);
        return tag;
    }

    private ItemStackHandler createTrackedHandler(int slotCount) {
        int normalized = Math.max(1, slotCount);
        return new ItemStackHandler(normalized) {
            @Override
            protected void onContentsChanged(int slot) {
                setDirty();
            }
        };
    }
}

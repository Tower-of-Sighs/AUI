package com.sighs.apricityui.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApricitySavedData extends SavedData {
    private static final String INVENTORIES_KEY = "inventories";
    private final LinkedHashMap<String, SimpleContainer> inventories = new LinkedHashMap<>();

    public static ApricitySavedData get(MinecraftServer server, String dataName) {
        return server.overworld().getDataStorage().computeIfAbsent(ApricitySavedData::load, ApricitySavedData::new, dataName);
    }

    public static ApricitySavedData load(CompoundTag tag) {
        ApricitySavedData data = new ApricitySavedData();
        CompoundTag all = tag.getCompound(INVENTORIES_KEY);
        for (String key : all.getAllKeys()) {
            CompoundTag serialized = all.getCompound(key);
            int size = Math.max(1, serialized.getInt("Size"));
            SimpleContainer container = data.createContainer(size);
            ListTag items = serialized.getList("Items", Tag.TAG_COMPOUND);
            for (int index = 0; index < items.size(); index++) {
                CompoundTag item = items.getCompound(index);
                int slot = item.getInt("Slot");
                if (slot >= 0 && slot < size) container.setItem(slot, ItemStack.of(item));
            }
            data.inventories.put(key, container);
        }
        return data;
    }

    public SimpleContainer getOrCreate(String inventoryKey, int slotCount) {
        String key = inventoryKey == null || inventoryKey.isBlank() ? "__default__" : inventoryKey.trim();
        int size = Math.max(1, slotCount);
        SimpleContainer existing = inventories.get(key);
        if (existing != null && existing.getContainerSize() == size) return existing;
        SimpleContainer resized = createContainer(size);
        if (existing != null) {
            for (int i = 0; i < Math.min(existing.getContainerSize(), size); i++) resized.setItem(i, existing.getItem(i).copy());
        }
        inventories.put(key, resized);
        setDirty();
        return resized;
    }

    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<String, SimpleContainer> entry : inventories.entrySet()) {
            CompoundTag serialized = new CompoundTag();
            serialized.putInt("Size", entry.getValue().getContainerSize());
            ListTag items = new ListTag();
            for (int slot = 0; slot < entry.getValue().getContainerSize(); slot++) {
                ItemStack stack = entry.getValue().getItem(slot);
                if (stack.isEmpty()) continue;
                CompoundTag item = stack.save(new CompoundTag());
                item.putInt("Slot", slot);
                items.add(item);
            }
            serialized.put("Items", items);
            all.put(entry.getKey(), serialized);
        }
        tag.put(INVENTORIES_KEY, all);
        return tag;
    }

    private SimpleContainer createContainer(int size) {
        return new SimpleContainer(size) {
            public void setChanged() { ApricitySavedData.this.setDirty(); }
        };
    }
}

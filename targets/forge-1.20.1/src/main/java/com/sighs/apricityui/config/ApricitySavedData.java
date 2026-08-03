package com.sighs.apricityui.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用世界级库存 SavedData。
 * 热重载时直接截断：按目标容量物理重建，超出部分丢弃。
 */
public class ApricitySavedData extends SavedData {
    private static final String INVENTORIES_KEY = "inventories";

    private final LinkedHashMap<String, ItemStackHandler> inventories = new LinkedHashMap<>();

    public static ApricitySavedData get(MinecraftServer server, String dataName) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ApricitySavedData::load,
                ApricitySavedData::new,
                dataName
        );
    }

    public static ApricitySavedData load(CompoundTag tag) {
        ApricitySavedData data = new ApricitySavedData();
        CompoundTag allInventories = tag.getCompound(INVENTORIES_KEY);
        for (String key : allInventories.getAllKeys()) {
            CompoundTag serialized = allInventories.getCompound(key);
            int slotCount = Math.max(1, serialized.getInt("Size"));
            ItemStackHandler handler = data.createTrackedHandler(slotCount);
            handler.deserializeNBT(serialized);
            data.inventories.put(key, handler);
        }
        return data;
    }

    /**
     * 获取或创建指定容量的库存。
     * 若已存在且容量不同，按目标容量截断重建。
     */
    public ItemStackHandler getOrCreate(String inventoryKey, int slotCount) {
        String key = normalizeInventoryKey(inventoryKey);
        int normalizedSlotCount = Math.max(1, slotCount);

        ItemStackHandler existing = inventories.get(key);
        if (existing == null) {
            ItemStackHandler created = createTrackedHandler(normalizedSlotCount);
            inventories.put(key, created);
            setDirty();
            return created;
        }

        if (existing.getSlots() == normalizedSlotCount) {
            return existing;
        }

        // 截断：按目标容量物理重建，保留可容纳的物品，超出部分丢弃。
        ItemStackHandler resized = createTrackedHandler(normalizedSlotCount);
        int copyCount = Math.min(existing.getSlots(), normalizedSlotCount);
        for (int i = 0; i < copyCount; i++) {
            ItemStack stack = existing.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            resized.setStackInSlot(i, stack.copy());
        }
        inventories.put(key, resized);
        setDirty();
        return resized;
    }

    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag) {
        CompoundTag allInventories = new CompoundTag();
        for (Map.Entry<String, ItemStackHandler> entry : inventories.entrySet()) {
            allInventories.put(entry.getKey(), entry.getValue().serializeNBT());
        }
        tag.put(INVENTORIES_KEY, allInventories);
        return tag;
    }

    private String normalizeInventoryKey(String inventoryKey) {
        if (inventoryKey == null || inventoryKey.trim().isEmpty()) {
            return "__default__";
        }
        return inventoryKey.trim();
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

package com.sighs.apricityui.instance.container.bind;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.WorldCapabilityData;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用世界级库存 SavedData。
 */
public class ApricitySavedData extends WorldCapabilityData {
    private static final String INVENTORIES_KEY = "inventories";

    private final LinkedHashMap<String, ItemStackHandler> inventories = new LinkedHashMap<>();

    public ApricitySavedData() {
        super(INVENTORIES_KEY);
    }

    public static ApricitySavedData get(MinecraftServer server, String dataName) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ApricitySavedData::new,
                dataName
        );
    }

    public void load(CompoundNBT tag) {
        ApricitySavedData data = new ApricitySavedData();
        CompoundNBT allInventories = tag.getCompound(INVENTORIES_KEY);
        for (String key : allInventories.getAllKeys()) {
            CompoundNBT serialized = allInventories.getCompound(key);
            int slotCount = Math.max(1, serialized.getInt("Size"));
            ItemStackHandler handler = data.createTrackedHandler(slotCount);
            handler.deserializeNBT(serialized);
            data.inventories.put(key, handler);
        }
    }

    public ItemStackHandler getOrCreate(String inventoryKey, int slotCount) {
        int normalizedSlotCount = Math.max(1, slotCount);
        ItemStackHandler existing = inventories.get(inventoryKey);
        if (existing == null) {
            ItemStackHandler created = createTrackedHandler(normalizedSlotCount);
            inventories.put(inventoryKey, created);
            setDirty();
            return created;
        }

        if (existing.getSlots() == normalizedSlotCount) return existing;

        ItemStackHandler resized = createTrackedHandler(normalizedSlotCount);
        if (normalizedSlotCount >= existing.getSlots()) {
            // 扩容：原位置不变，直接复制已有槽位。
            for (int i = 0; i < existing.getSlots(); i++) {
                resized.setStackInSlot(i, existing.getStackInSlot(i).copy());
            }
        } else {
            // 缩容：先淘汰空槽位；只有非空槽位超过目标容量时，才从尾部截断。
            int writeIndex = 0;
            for (int i = 0; i < existing.getSlots(); i++) {
                ItemStack stack = existing.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (writeIndex >= normalizedSlotCount) break;
                resized.setStackInSlot(writeIndex, stack.copy());
                writeIndex++;
            }
        }
        inventories.put(inventoryKey, resized);
        setDirty();
        return resized;
    }

    @Override
    @Nonnull
    public CompoundNBT save(CompoundNBT tag) {
        CompoundNBT allInventories = new CompoundNBT();
        for (Map.Entry<String, ItemStackHandler> entry : inventories.entrySet()) {
            allInventories.put(entry.getKey(), entry.getValue().serializeNBT());
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

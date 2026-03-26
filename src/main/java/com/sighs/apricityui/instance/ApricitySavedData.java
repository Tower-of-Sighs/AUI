package com.sighs.apricityui.instance;

import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 通用世界级库存 SavedData。
 */
public class ApricitySavedData extends SavedData {
    private static final String INVENTORIES_KEY = "inventories";
    private static final String OVERFLOWS_KEY = "overflows";
    private static final String OVERFLOW_SLOT_KEY = "Slot";
    private static final String OVERFLOW_STACK_KEY = "Stack";

    private final LinkedHashMap<String, ItemStackHandler> inventories = new LinkedHashMap<>();
    private final LinkedHashMap<String, TreeMap<Integer, ItemStack>> overflows = new LinkedHashMap<>();

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

        CompoundTag allOverflows = tag.getCompound(OVERFLOWS_KEY);
        for (String key : allOverflows.getAllKeys()) {
            ListTag serializedOverflow = allOverflows.getList(key, Tag.TAG_COMPOUND);
            TreeMap<Integer, ItemStack> overflow = new TreeMap<>();
            for (int i = 0; i < serializedOverflow.size(); i++) {
                CompoundTag overflowEntry = serializedOverflow.getCompound(i);
                int slot = overflowEntry.getInt(OVERFLOW_SLOT_KEY);
                if (slot < 0) continue;
                if (!overflowEntry.contains(OVERFLOW_STACK_KEY, Tag.TAG_COMPOUND)) continue;
                ItemStack stack = ItemStack.of(overflowEntry.getCompound(OVERFLOW_STACK_KEY));
                if (stack.isEmpty()) continue;
                overflow.put(slot, stack);
            }
            if (!overflow.isEmpty()) {
                data.overflows.put(key, overflow);
            }
        }
        return data;
    }

    public ItemStackHandler getOrCreate(String inventoryKey, int slotCount) {
        return getOrCreate(inventoryKey, slotCount, OpenBindPlan.ResizePolicy.KEEP_OVERFLOW);
    }

    public ItemStackHandler getOrCreate(String inventoryKey, int slotCount, OpenBindPlan.ResizePolicy resizePolicy) {
        String key = normalizeInventoryKey(inventoryKey);
        int normalizedSlotCount = Math.max(1, slotCount);
        OpenBindPlan.ResizePolicy effectivePolicy = resizePolicy == null
                ? OpenBindPlan.ResizePolicy.KEEP_OVERFLOW
                : resizePolicy;

        ItemStackHandler existing = inventories.get(key);
        if (existing == null) {
            ItemStackHandler created = createTrackedHandler(normalizedSlotCount);
            inventories.put(key, created);
            if (effectivePolicy == OpenBindPlan.ResizePolicy.KEEP_OVERFLOW) {
                restoreOverflow(key, created, created.getSlots());
            } else {
                overflows.remove(key);
            }
            setDirty();
            return created;
        }

        if (effectivePolicy == OpenBindPlan.ResizePolicy.KEEP_OVERFLOW) {
            // KEEP_OVERFLOW：物理容量只增不减。
            if (normalizedSlotCount <= existing.getSlots()) {
                if (restoreOverflow(key, existing, existing.getSlots())) {
                    setDirty();
                }
                return existing;
            }

            ItemStackHandler resized = createTrackedHandler(normalizedSlotCount);
            for (int i = 0; i < existing.getSlots(); i++) {
                ItemStack stack = existing.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                resized.setStackInSlot(i, stack.copy());
            }
            restoreOverflow(key, resized, normalizedSlotCount);
            inventories.put(key, resized);
            setDirty();
            return resized;
        }

        // TRUNCATE：按目标容量物理重建并清空 overflow。
        if (existing.getSlots() == normalizedSlotCount) {
            if (overflows.remove(key) != null) {
                setDirty();
            }
            return existing;
        }

        ItemStackHandler resized = createTrackedHandler(normalizedSlotCount);
        int copyCount = Math.min(existing.getSlots(), normalizedSlotCount);
        for (int i = 0; i < copyCount; i++) {
            ItemStack stack = existing.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            resized.setStackInSlot(i, stack.copy());
        }
        overflows.remove(key);
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

        CompoundTag allOverflows = new CompoundTag();
        for (Map.Entry<String, TreeMap<Integer, ItemStack>> entry : overflows.entrySet()) {
            TreeMap<Integer, ItemStack> overflow = entry.getValue();
            if (overflow == null || overflow.isEmpty()) continue;
            ListTag serializedOverflow = new ListTag();
            for (Map.Entry<Integer, ItemStack> overflowEntry : overflow.entrySet()) {
                Integer slot = overflowEntry.getKey();
                ItemStack stack = overflowEntry.getValue();
                if (slot == null || slot < 0 || stack == null || stack.isEmpty()) continue;
                CompoundTag record = new CompoundTag();
                record.putInt(OVERFLOW_SLOT_KEY, slot);
                record.put(OVERFLOW_STACK_KEY, stack.save(new CompoundTag()));
                serializedOverflow.add(record);
            }
            if (!serializedOverflow.isEmpty()) {
                allOverflows.put(entry.getKey(), serializedOverflow);
            }
        }
        if (!allOverflows.isEmpty()) {
            tag.put(OVERFLOWS_KEY, allOverflows);
        }
        return tag;
    }

    private String normalizeInventoryKey(String inventoryKey) {
        if (inventoryKey == null || inventoryKey.trim().isEmpty()) {
            return "__default__";
        }
        return inventoryKey.trim();
    }

    private boolean restoreOverflow(String inventoryKey, ItemStackHandler target, int capacity) {
        TreeMap<Integer, ItemStack> overflow = overflows.get(inventoryKey);
        if (overflow == null || overflow.isEmpty()) return false;

        boolean changed = false;
        ArrayList<Integer> consumedSlots = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> entry : overflow.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= capacity) continue;
            ItemStack existing = target.getStackInSlot(slot);
            if (!existing.isEmpty()) continue;
            ItemStack restoreStack = entry.getValue();
            if (restoreStack == null || restoreStack.isEmpty()) {
                consumedSlots.add(slot);
                changed = true;
                continue;
            }
            target.setStackInSlot(slot, restoreStack.copy());
            consumedSlots.add(slot);
            changed = true;
        }
        for (Integer slot : consumedSlots) {
            overflow.remove(slot);
        }
        if (overflow.isEmpty()) {
            overflows.remove(inventoryKey);
        }
        return changed;
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

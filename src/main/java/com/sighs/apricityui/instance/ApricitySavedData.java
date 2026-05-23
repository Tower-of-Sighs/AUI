package com.sighs.apricityui.instance;

import com.mojang.serialization.Codec;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApricitySavedData extends SavedData {
    private static final Codec<Map<String, List<ItemStack>>> INVENTORIES_CODEC =
            Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf());
    private static final Codec<ApricitySavedData> CODEC =
            INVENTORIES_CODEC.xmap(ApricitySavedData::fromSerialized, ApricitySavedData::toSerialized);

    private final LinkedHashMap<String, ItemStacksResourceHandler> inventories = new LinkedHashMap<>();

    public static ApricitySavedData get(MinecraftServer server, String dataName) {
        String normalizedName = normalizeDataName(dataName);
        SavedDataType<ApricitySavedData> type = new SavedDataType<>(
                Identifier.fromNamespaceAndPath(ApricityUI.MODID, normalizedName),
                ApricitySavedData::new,
                CODEC,
                null
        );
        return server.getDataStorage().computeIfAbsent(type);
    }

    public ItemStacksResourceHandler getOrCreate(String inventoryKey, int slotCount) {
        String key = normalizeInventoryKey(inventoryKey);
        int normalizedSlotCount = Math.max(1, slotCount);

        ItemStacksResourceHandler existing = inventories.get(key);
        if (existing == null) {
            ItemStacksResourceHandler created = createTrackedHandler(normalizedSlotCount);
            inventories.put(key, created);
            setDirty();
            return created;
        }

        if (existing.size() == normalizedSlotCount) {
            return existing;
        }

        ItemStacksResourceHandler resized = createTrackedHandler(normalizedSlotCount);
        NonNullList<ItemStack> existingStacks = existing.copyToList();
        int copyCount = Math.min(existingStacks.size(), normalizedSlotCount);
        for (int i = 0; i < copyCount; i++) {
            ItemStack stack = existingStacks.get(i);
            if (stack.isEmpty()) continue;
            resized.set(i, resized.getResourceFrom(stack), stack.getCount());
        }
        inventories.put(key, resized);
        setDirty();
        return resized;
    }

    private static ApricitySavedData fromSerialized(Map<String, List<ItemStack>> serialized) {
        ApricitySavedData data = new ApricitySavedData();
        if (serialized == null) return data;

        for (Map.Entry<String, List<ItemStack>> entry : serialized.entrySet()) {
            String key = normalizeInventoryKey(entry.getKey());
            List<ItemStack> stacks = entry.getValue();
            int size = Math.max(1, stacks == null ? 1 : stacks.size());
            ItemStacksResourceHandler handler = data.createTrackedHandler(size);
            if (stacks != null) {
                for (int i = 0; i < stacks.size(); i++) {
                    ItemStack stack = stacks.get(i);
                    if (stack == null || stack.isEmpty()) continue;
                    handler.set(i, handler.getResourceFrom(stack), stack.getCount());
                }
            }
            data.inventories.put(key, handler);
        }
        return data;
    }

    private static Map<String, List<ItemStack>> toSerialized(ApricitySavedData data) {
        LinkedHashMap<String, List<ItemStack>> serialized = new LinkedHashMap<>();
        if (data == null) return serialized;
        for (Map.Entry<String, ItemStacksResourceHandler> entry : data.inventories.entrySet()) {
            serialized.put(entry.getKey(), List.copyOf(entry.getValue().copyToList()));
        }
        return serialized;
    }

    private static String normalizeDataName(String dataName) {
        if (dataName == null || dataName.isBlank()) return "apricityui_data";
        return dataName.trim().replace('\\', '/').replaceAll("[^a-z0-9_./-]", "_");
    }

    private static String normalizeInventoryKey(String inventoryKey) {
        if (inventoryKey == null || inventoryKey.trim().isEmpty()) return "__default__";
        return inventoryKey.trim();
    }

    private ItemStacksResourceHandler createTrackedHandler(int slotCount) {
        return new TrackingItemStacksResourceHandler(Math.max(1, slotCount));
    }

    private final class TrackingItemStacksResourceHandler extends ItemStacksResourceHandler {
        private TrackingItemStacksResourceHandler(int slots) {
            super(slots);
        }

        @Override
        protected void onContentsChanged(int slot, ItemStack stack) {
            setDirty();
        }
    }
}

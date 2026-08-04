package com.sighs.apricityui.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用世界级库存 SavedData。
 * 热重载时直接截断：按目标容量物理重建，超出部分丢弃。
 *
 * <p>NeoForge 26.1 使用 codec 驱动的 {@link SavedDataType}，因此序列化改为
 * {@link #CODEC}，数据格式为 {@code Map<String, List<ItemStack>>}（每个库存一个列表）。</p>
 */
public class ApricitySavedData extends SavedData {
    private static final String INVENTORIES_KEY = "inventories";

    public static final Codec<ApricitySavedData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf())
                            .fieldOf(INVENTORIES_KEY)
                            .forGetter(ApricitySavedData::exportInventories)
            ).apply(instance, ApricitySavedData::fromInventories));

    private final LinkedHashMap<String, ItemStackHandler> inventories = new LinkedHashMap<>();

    public static ApricitySavedData get(MinecraftServer server, String dataName) {
        String name = dataName == null || dataName.isBlank() ? "apricityui_data" : dataName.trim();
        Identifier id;
        try {
            id = Identifier.fromNamespaceAndPath(ApricityUI.MODID, name);
        } catch (Exception e) {
            id = Identifier.fromNamespaceAndPath(ApricityUI.MODID, "apricityui_data");
        }
        SavedDataType<ApricitySavedData> type = new SavedDataType<>(id, ApricitySavedData::new, CODEC);
        return server.overworld().getDataStorage().computeIfAbsent(type);
    }

    private Map<String, List<ItemStack>> exportInventories() {
        LinkedHashMap<String, List<ItemStack>> result = new LinkedHashMap<>();
        for (Map.Entry<String, ItemStackHandler> entry : inventories.entrySet()) {
            ItemStackHandler handler = entry.getValue();
            ArrayList<ItemStack> stacks = new ArrayList<>(handler.getSlots());
            for (int i = 0; i < handler.getSlots(); i++) {
                stacks.add(handler.getStackInSlot(i));
            }
            result.put(entry.getKey(), stacks);
        }
        return result;
    }

    private static ApricitySavedData fromInventories(Map<String, List<ItemStack>> map) {
        ApricitySavedData data = new ApricitySavedData();
        if (map != null) {
            map.forEach((key, stacks) -> {
                if (key == null || stacks == null) return;
                ItemStackHandler handler = data.createTrackedHandler(stacks.size());
                for (int i = 0; i < stacks.size(); i++) {
                    ItemStack stack = stacks.get(i);
                    if (stack != null && !stack.isEmpty()) {
                        handler.setStackInSlot(i, stack.copy());
                    }
                }
                data.inventories.put(key, handler);
            });
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

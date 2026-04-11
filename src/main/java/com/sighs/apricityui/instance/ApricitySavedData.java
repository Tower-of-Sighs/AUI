package com.sighs.apricityui.instance;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用世界级库存 SavedData。
 */
public class ApricitySavedData extends SavedData {
    private static final String INVENTORIES_KEY = "inventories";
    private static final String OVERFLOWS_KEY = "overflows";
    private static final String OVERFLOW_SLOT_KEY = "Slot";
    private static final String OVERFLOW_STACK_KEY = "Stack";

    private static final Map<String, SavedDataType<ApricitySavedData>> TYPES = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private static final Codec<ApricitySavedData> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<ApricitySavedData, T>> decode(DynamicOps<T> ops, T input) {
            Object raw = input;
            if (!(raw instanceof Tag tag)) {
                return DataResult.error(() -> "ApricitySavedData expects NBT Tag input, got: " + raw);
            }
            if (!(tag instanceof CompoundTag compoundTag)) {
                return DataResult.error(() -> "ApricitySavedData expects CompoundTag input, got: " + tag.getType());
            }

            DynamicOps<Tag> tagOps = (DynamicOps<Tag>) ops;
            ApricitySavedData parsed = ApricitySavedData.load(tagOps, compoundTag);

            T remainder = input;
            return DataResult.success(Pair.of(parsed, remainder));
        }

        @Override
        public <T> DataResult<T> encode(ApricitySavedData input, DynamicOps<T> ops, T prefix) {
            DynamicOps<Tag> tagOps = (DynamicOps<Tag>) ops;
            CompoundTag tag = input.save(tagOps);

            T casted = (T) tag;
            return DataResult.success(casted);
        }
    };

    private final LinkedHashMap<String, ItemStacksResourceHandler> inventories = new LinkedHashMap<>();
    private final LinkedHashMap<String, TreeMap<Integer, ItemStack>> overflows = new LinkedHashMap<>();

    public static ApricitySavedData get(MinecraftServer server, String dataName) {
        if (server == null) {
            return new ApricitySavedData();
        }

        String normalizedName = normalizeDataName(dataName);
        SavedDataType<ApricitySavedData> type = TYPES.computeIfAbsent(normalizedName, ApricitySavedData::createType);
        return server.overworld().getDataStorage().computeIfAbsent(type);
    }

    private static SavedDataType<ApricitySavedData> createType(String normalizedName) {
        Identifier id = Identifier.fromNamespaceAndPath(ApricityUI.MODID, "saved_data/" + normalizedName);
        return new SavedDataType<>(id, ignored -> new ApricitySavedData(), ignored -> CODEC, null);
    }

    private static String normalizeDataName(String dataName) {
        String raw = dataName == null ? "" : dataName.trim();
        if (raw.isEmpty()) {
            return "apricityui_saved";
        }

        String lower = raw.toLowerCase();
        StringBuilder sanitized = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '/';
            sanitized.append(ok ? c : '_');
        }

        String result = sanitized.toString();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        if (result.isEmpty()) {
            return "apricityui_saved";
        }
        return result;
    }

    private static HolderLookup.Provider lookupProvider(DynamicOps<Tag> ops) {
        if (ops instanceof RegistryOps<Tag> registryOps
                && registryOps.lookupProvider instanceof RegistryOps.HolderLookupAdapter adapter) {
            return adapter.lookupProvider;
        }
        return null;
    }

    private static ApricitySavedData load(DynamicOps<Tag> ops, CompoundTag tag) {
        ApricitySavedData data = new ApricitySavedData();

        HolderLookup.Provider provider = lookupProvider(ops);
        if (provider == null) {
            return data;
        }

        CompoundTag allInventories = tag.getCompoundOrEmpty(INVENTORIES_KEY);
        for (String key : allInventories.keySet()) {
            CompoundTag serialized = allInventories.getCompoundOrEmpty(key);
            ItemStacksResourceHandler handler = data.createTrackedHandler(1);
            ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, provider, serialized);
            handler.deserialize(input);
            data.inventories.put(key, handler);
        }

        CompoundTag allOverflows = tag.getCompoundOrEmpty(OVERFLOWS_KEY);
        for (String key : allOverflows.keySet()) {
            Optional<ListTag> optionalOverflow = allOverflows.getList(key);
            if (optionalOverflow.isEmpty()) continue;

            ListTag serializedOverflow = optionalOverflow.get();
            TreeMap<Integer, ItemStack> overflow = new TreeMap<>();
            for (Tag value : serializedOverflow) {
                if (!(value instanceof CompoundTag overflowEntry)) continue;
                int slot = overflowEntry.getIntOr(OVERFLOW_SLOT_KEY, -1);
                if (slot < 0) continue;
                Tag stackTag = overflowEntry.get(OVERFLOW_STACK_KEY);
                if (stackTag == null) continue;

                ItemStack stack = ItemStack.CODEC.parse(ops, stackTag).result().orElse(ItemStack.EMPTY);
                if (stack.isEmpty()) continue;
                overflow.put(slot, stack);
            }
            if (!overflow.isEmpty()) {
                data.overflows.put(key, overflow);
            }
        }

        return data;
    }

    public ItemStacksResourceHandler getOrCreate(String inventoryKey, int slotCount) {
        return getOrCreate(inventoryKey, slotCount, OpenBindPlan.ResizePolicy.KEEP_OVERFLOW);
    }

    public ItemStacksResourceHandler getOrCreate(String inventoryKey, int slotCount, OpenBindPlan.ResizePolicy resizePolicy) {
        String key = normalizeInventoryKey(inventoryKey);
        int normalizedSlotCount = Math.max(1, slotCount);
        OpenBindPlan.ResizePolicy effectivePolicy = resizePolicy == null
                ? OpenBindPlan.ResizePolicy.KEEP_OVERFLOW
                : resizePolicy;

        ItemStacksResourceHandler existing = inventories.get(key);
        if (existing == null) {
            ItemStacksResourceHandler created = createTrackedHandler(normalizedSlotCount);
            inventories.put(key, created);
            if (effectivePolicy == OpenBindPlan.ResizePolicy.KEEP_OVERFLOW) {
                restoreOverflow(key, created, created.size());
            } else {
                overflows.remove(key);
            }
            setDirty();
            return created;
        }

        if (effectivePolicy == OpenBindPlan.ResizePolicy.KEEP_OVERFLOW) {
            // KEEP_OVERFLOW：物理容量只增不减。
            if (normalizedSlotCount <= existing.size()) {
                if (restoreOverflow(key, existing, existing.size())) {
                    setDirty();
                }
                return existing;
            }

            ItemStacksResourceHandler resized = createTrackedHandler(normalizedSlotCount);
            for (int i = 0; i < existing.size(); i++) {
                ItemStack stack = ItemUtil.getStack(existing, i);
                if (stack.isEmpty()) continue;
                resized.set(i, ItemResource.of(stack), stack.getCount());
            }
            restoreOverflow(key, resized, normalizedSlotCount);
            inventories.put(key, resized);
            setDirty();
            return resized;
        }

        // TRUNCATE：按目标容量物理重建并清空 overflow。
        if (existing.size() == normalizedSlotCount) {
            if (overflows.remove(key) != null) {
                setDirty();
            }
            return existing;
        }

        ItemStacksResourceHandler resized = createTrackedHandler(normalizedSlotCount);
        int copyCount = Math.min(existing.size(), normalizedSlotCount);
        for (int i = 0; i < copyCount; i++) {
            ItemStack stack = ItemUtil.getStack(existing, i);
            if (stack.isEmpty()) continue;
            resized.set(i, ItemResource.of(stack), stack.getCount());
        }
        overflows.remove(key);
        inventories.put(key, resized);
        setDirty();
        return resized;
    }

    private CompoundTag save(DynamicOps<Tag> ops) {
        CompoundTag tag = new CompoundTag();

        HolderLookup.Provider provider = lookupProvider(ops);
        if (provider == null) {
            return tag;
        }

        CompoundTag allInventories = new CompoundTag();
        for (Map.Entry<String, ItemStacksResourceHandler> entry : inventories.entrySet()) {
            ItemStacksResourceHandler handler = entry.getValue();
            if (handler == null) continue;

            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider);
            handler.serialize(output);
            allInventories.put(entry.getKey(), output.buildResult());
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
                ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(encoded -> record.put(OVERFLOW_STACK_KEY, encoded));
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

    private boolean restoreOverflow(String inventoryKey, ItemStacksResourceHandler target, int capacity) {
        TreeMap<Integer, ItemStack> overflow = overflows.get(inventoryKey);
        if (overflow == null || overflow.isEmpty()) return false;

        boolean changed = false;
        ArrayList<Integer> consumedSlots = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> entry : overflow.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= capacity) continue;
            ItemStack existing = ItemUtil.getStack(target, slot);
            if (!existing.isEmpty()) continue;
            ItemStack restoreStack = entry.getValue();
            if (restoreStack == null || restoreStack.isEmpty()) {
                consumedSlots.add(slot);
                changed = true;
                continue;
            }
            ItemStack copied = restoreStack.copy();
            target.set(slot, ItemResource.of(copied), copied.getCount());
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

    private ItemStacksResourceHandler createTrackedHandler(int slotCount) {
        int normalized = Math.max(1, slotCount);
        return new ItemStacksResourceHandler(normalized) {
            @Override
            protected void onContentsChanged(int index, ItemStack previousContents) {
                setDirty();
            }

            @Override
            public void serialize(@NonNull ValueOutput output) {
                super.serialize(output);
            }

            @Override
            public void deserialize(@NonNull ValueInput input) {
                super.deserialize(input);
            }
        };
    }
}

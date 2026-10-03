package com.sighs.apricityui.container.storage;

import com.sighs.apricityui.stack.GenericStackAdapters;

import com.sighs.apricityui.stack.FluidKey;
import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.GenericStackTypes;
import com.sighs.apricityui.stack.ItemKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public final class GenericStorages {
    private GenericStorages() { }

    public static GenericStorage itemHandler(IItemHandler handler) { return new LegacyItemStorage(handler); }
    public static GenericStorage itemHandler(ResourceHandler<ItemResource> handler) { return new ItemStorage(handler); }
    public static GenericStorage fluidHandler(ResourceHandler<FluidResource> handler) { return new FluidStorage(handler); }

    public static GenericStorage combine(List<GenericStorage> storages) {
        ArrayList<GenericStorage> usable = new ArrayList<>();
        if (storages != null) {
            for (GenericStorage storage : storages) {
                if (storage != null && storage.size() > 0) usable.add(storage);
            }
        }
        if (usable.isEmpty()) return null;
        return usable.size() == 1 ? usable.get(0) : new CombinedStorage(List.copyOf(usable));
    }

    public static GenericStorage view(GenericStorage storage, boolean merge, int requestedCapacity) {
        if (storage == null) return null;
        int capacity = requestedCapacity > 0 ? requestedCapacity : storage.size();
        return merge ? new MergedStorage(storage, capacity) : new FixedStorage(storage, capacity);
    }

    private record LegacyItemStorage(IItemHandler handler) implements GenericStorage {
        @Override public int size() { return handler.getSlots(); }
        @Override public GenericStack get(int index) { return valid(index) ? GenericStackAdapters.fromItemStack(handler.getStackInSlot(index)) : null; }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof ItemKey itemKey) || amount <= 0L) return 0L;
            int requested = clamp(amount);
            ItemStack remainder = handler.insertItem(index, itemKey.toStack(requested), simulate);
            return requested - (remainder.isEmpty() ? 0 : remainder.getCount());
        }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof ItemKey) || amount <= 0L) return 0L;
            GenericStack current = get(index);
            if (current == null || !current.key().equals(key)) return 0L;
            return handler.extractItem(index, clamp(amount), simulate).getCount();
        }
        private boolean valid(int index) { return index >= 0 && index < size(); }
    }

    private record ItemStorage(ResourceHandler<ItemResource> handler) implements GenericStorage {
        @Override public int size() { return handler.size(); }
        @Override public GenericStack get(int index) {
            return valid(index) ? GenericStackAdapters.fromItemResource(handler.getResource(index), handler.getAmountAsLong(index)) : null;
        }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) {
            return valid(index) && key instanceof ItemKey itemKey
                    ? transfer(handler, index, ItemResource.of(itemKey.toStack(1)), amount, simulate, true) : 0L;
        }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) {
            return valid(index) && key instanceof ItemKey itemKey
                    ? transfer(handler, index, ItemResource.of(itemKey.toStack(1)), amount, simulate, false) : 0L;
        }
        private boolean valid(int index) { return index >= 0 && index < size(); }
    }

    private record FluidStorage(ResourceHandler<FluidResource> handler) implements GenericStorage {
        @Override public int size() { return handler.size(); }
        @Override public GenericStack get(int index) {
            return valid(index) ? GenericStackAdapters.fromFluidResource(handler.getResource(index), handler.getAmountAsLong(index)) : null;
        }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) {
            return valid(index) && key instanceof FluidKey fluidKey
                    ? transfer(handler, index, fluidKey.resource(), amount, simulate, true) : 0L;
        }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) {
            return valid(index) && key instanceof FluidKey fluidKey
                    ? transfer(handler, index, fluidKey.resource(), amount, simulate, false) : 0L;
        }
        private boolean valid(int index) { return index >= 0 && index < size(); }
    }

    private static <T extends Resource> long transfer(ResourceHandler<T> handler, int index, T resource,
                                                       long amount, boolean simulate, boolean insert) {
        if (resource.isEmpty() || amount <= 0L) return 0L;
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = insert
                    ? handler.insert(index, resource, clamp(amount), transaction)
                    : handler.extract(index, resource, clamp(amount), transaction);
            if (!simulate) transaction.commit();
            return moved;
        }
    }

    private record CombinedStorage(List<GenericStorage> storages) implements GenericStorage {
        @Override public int size() { int size = 0; for (GenericStorage storage : storages) size += storage.size(); return size; }
        @Override public GenericStack get(int index) { Location location = locate(index); return location == null ? null : location.storage().get(location.index()); }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) { Location location = locate(index); return location == null ? 0L : location.storage().insert(location.index(), key, amount, simulate); }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) { Location location = locate(index); return location == null ? 0L : location.storage().extract(location.index(), key, amount, simulate); }
        private Location locate(int index) {
            if (index < 0) return null;
            int cursor = index;
            for (GenericStorage storage : storages) {
                if (cursor < storage.size()) return new Location(storage, cursor);
                cursor -= storage.size();
            }
            return null;
        }
    }

    private record FixedStorage(GenericStorage delegate, int capacity) implements GenericStorage {
        private FixedStorage { capacity = Math.max(0, capacity); }
        @Override public int size() { return capacity; }
        @Override public GenericStack get(int index) { return index >= 0 && index < delegate.size() && index < capacity ? delegate.get(index) : null; }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) {
            if (index < 0 || index >= capacity) return 0L;
            return index < delegate.size() ? delegate.insert(index, key, amount, simulate) : delegate.insertAny(key, amount, simulate);
        }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) {
            return index >= 0 && index < delegate.size() && index < capacity ? delegate.extract(index, key, amount, simulate) : 0L;
        }
    }

    private record MergedStorage(GenericStorage delegate, int capacity) implements GenericStorage {
        private MergedStorage { capacity = Math.max(0, capacity); }
        @Override public int size() { return capacity; }
        @Override public GenericStack get(int index) {
            List<GenericStack> snapshot = snapshot();
            return index >= 0 && index < snapshot.size() && index < capacity ? snapshot.get(index) : null;
        }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) {
            GenericStack current = get(index);
            if (current != null && !current.key().equals(key)) return 0L;
            return index >= 0 && index < capacity ? delegate.insertAny(key, amount, simulate) : 0L;
        }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) {
            GenericStack current = get(index);
            return current == null || !current.key().equals(key) ? 0L : delegate.extractAny(key, amount, simulate);
        }
        private List<GenericStack> snapshot() {
            // ponytail: rebuild is O(n) per access; add a tick cache only if large handlers show up in profiling.
            LinkedHashMap<GenericKey, Long> totals = new LinkedHashMap<>();
            for (int index = 0; index < delegate.size(); index++) {
                GenericStack stack = delegate.get(index);
                if (stack != null && stack.amount() > 0L) totals.merge(stack.key(), stack.amount(), GenericStorages::saturatedAdd);
            }
            ArrayList<GenericStack> result = new ArrayList<>(totals.size());
            totals.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
            result.sort(Comparator.comparing(GenericStorages::sortKey));
            return result.size() > capacity ? List.copyOf(result.subList(0, capacity)) : List.copyOf(result);
        }
    }

    private static int clamp(long amount) { return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, amount)); }
    private static long saturatedAdd(long left, long right) { return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right; }
    private static String sortKey(GenericStack stack) { return stack.key().type().id() + "|" + stack.key().id() + "|" + GenericStackTypes.writeKey(stack.key()); }
    private record Location(GenericStorage storage, int index) { }
}

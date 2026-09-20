package com.sighs.apricityui.container.storage;

import com.sighs.apricityui.stack.FluidKey;
import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.GenericStackTypes;
import com.sighs.apricityui.stack.ItemKey;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public final class GenericStorages {
    private static final long FLUID_UNIT = FluidConstants.BUCKET / 1000L;

    private GenericStorages() {
    }

    public static GenericStorage itemStorage(SlottedStorage<ItemVariant> storage) {
        return storage == null ? null : new ItemStorageAdapter(storage);
    }

    public static GenericStorage fluidStorage(Storage<FluidVariant> storage) {
        return storage == null ? null : new FluidStorageAdapter(storage);
    }

    public static GenericStorage container(Container container) {
        return container == null ? null : new ContainerStorage(container);
    }

    public static GenericStorage combine(List<GenericStorage> storages) {
        ArrayList<GenericStorage> usable = new ArrayList<>();
        if (storages != null) for (GenericStorage storage : storages) if (storage != null && storage.size() > 0) usable.add(storage);
        if (usable.isEmpty()) return null;
        return usable.size() == 1 ? usable.get(0) : new CombinedStorage(List.copyOf(usable));
    }

    public static GenericStorage view(GenericStorage storage, boolean merge, int requestedCapacity) {
        if (storage == null) return null;
        int capacity = requestedCapacity > 0 ? requestedCapacity : storage.size();
        return merge ? new MergedStorage(storage, capacity) : new FixedStorage(storage, capacity);
    }

    private record ItemStorageAdapter(SlottedStorage<ItemVariant> storage) implements GenericStorage {
        @Override public int size() { return storage.getSlotCount(); }
        @Override public GenericStack get(int index) {
            StorageView<ItemVariant> view = valid(index) ? storage.getSlot(index) : null;
            return view == null || view.isResourceBlank() ? null : new GenericStack(new ItemKey(view.getResource()), view.getAmount());
        }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof ItemKey itemKey) || amount <= 0L) return 0L;
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = storage.getSlot(index).insert(itemKey.variant(), amount, transaction);
                if (!simulate && inserted > 0L) transaction.commit();
                return inserted;
            }
        }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof ItemKey itemKey) || amount <= 0L) return 0L;
            try (Transaction transaction = Transaction.openOuter()) {
                long extracted = storage.getSlot(index).extract(itemKey.variant(), amount, transaction);
                if (!simulate && extracted > 0L) transaction.commit();
                return extracted;
            }
        }
        private boolean valid(int index) { return index >= 0 && index < size(); }
    }

    private record FluidStorageAdapter(Storage<FluidVariant> storage) implements GenericStorage {
        @Override public int size() { return views().size(); }
        @Override public GenericStack get(int index) {
            StorageView<FluidVariant> view = view(index);
            return view == null || view.isResourceBlank() ? null
                    : GenericStack.fromFluidVariant(view.getResource(), view.getAmount() / FLUID_UNIT);
        }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) {
            if (!(key instanceof FluidKey fluidKey) || amount <= 0L || view(index) == null) return 0L;
            long transferAmount = scale(amount);
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = storage instanceof SlottedStorage<?> slotted
                        ? fluidSlot(slotted, index).insert(fluidKey.variant(), transferAmount, transaction)
                        : storage.insert(fluidKey.variant(), transferAmount, transaction);
                if (!simulate && inserted > 0L) transaction.commit();
                return inserted / FLUID_UNIT;
            }
        }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) {
            StorageView<FluidVariant> view = view(index);
            if (view == null || !(key instanceof FluidKey fluidKey) || amount <= 0L) return 0L;
            try (Transaction transaction = Transaction.openOuter()) {
                long extracted = view.extract(fluidKey.variant(), scale(amount), transaction);
                if (!simulate && extracted > 0L) transaction.commit();
                return extracted / FLUID_UNIT;
            }
        }
        @SuppressWarnings("unchecked")
        private static Storage<FluidVariant> fluidSlot(SlottedStorage<?> storage, int index) {
            return (Storage<FluidVariant>) storage.getSlot(index);
        }
        private StorageView<FluidVariant> view(int index) {
            List<StorageView<FluidVariant>> views = views();
            return index >= 0 && index < views.size() ? views.get(index) : null;
        }
        private List<StorageView<FluidVariant>> views() {
            ArrayList<StorageView<FluidVariant>> result = new ArrayList<>();
            storage.iterator().forEachRemaining(result::add);
            return result;
        }
        private static long scale(long amount) { return amount > Long.MAX_VALUE / FLUID_UNIT ? Long.MAX_VALUE : amount * FLUID_UNIT; }
    }

    private record ContainerStorage(Container container) implements GenericStorage {
        @Override public int size() { return container.getContainerSize(); }
        @Override public GenericStack get(int index) { return valid(index) ? GenericStack.fromItemStack(container.getItem(index)) : null; }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof ItemKey itemKey) || amount <= 0L) return 0L;
            ItemStack current = container.getItem(index);
            ItemStack incoming = itemKey.toStack(1);
            if (!container.canPlaceItem(index, incoming) || (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, incoming))) return 0L;
            int accepted = (int) Math.min(amount, Math.max(0, Math.min(container.getMaxStackSize(), incoming.getMaxStackSize()) - current.getCount()));
            if (!simulate && accepted > 0) {
                if (current.isEmpty()) container.setItem(index, itemKey.toStack(accepted)); else current.grow(accepted);
                container.setChanged();
            }
            return accepted;
        }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) {
            GenericStack current = get(index);
            if (current == null || !current.what().equals(key) || amount <= 0L) return 0L;
            int extracted = (int) Math.min(current.amount(), Math.min(Integer.MAX_VALUE, amount));
            if (!simulate && extracted > 0) container.removeItem(index, extracted);
            return extracted;
        }
        private boolean valid(int index) { return index >= 0 && index < size(); }
    }

    private record CombinedStorage(List<GenericStorage> storages) implements GenericStorage {
        @Override public int size() { int size = 0; for (GenericStorage storage : storages) size += storage.size(); return size; }
        @Override public GenericStack get(int index) { Location location = locate(index); return location == null ? null : location.storage.get(location.index); }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) { Location l = locate(index); return l == null ? 0L : l.storage.insert(l.index, key, amount, simulate); }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) { Location l = locate(index); return l == null ? 0L : l.storage.extract(l.index, key, amount, simulate); }
        private Location locate(int index) { if (index < 0) return null; for (GenericStorage storage : storages) { if (index < storage.size()) return new Location(storage, index); index -= storage.size(); } return null; }
    }

    private record FixedStorage(GenericStorage delegate, int capacity) implements GenericStorage {
        private FixedStorage { capacity = Math.max(0, capacity); }
        @Override public int size() { return capacity; }
        @Override public GenericStack get(int index) { return index >= 0 && index < capacity && index < delegate.size() ? delegate.get(index) : null; }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) { return index < 0 || index >= capacity ? 0L : index < delegate.size() ? delegate.insert(index, key, amount, simulate) : delegate.insertAny(key, amount, simulate); }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) { return index >= 0 && index < capacity && index < delegate.size() ? delegate.extract(index, key, amount, simulate) : 0L; }
    }

    private record MergedStorage(GenericStorage delegate, int capacity) implements GenericStorage {
        private MergedStorage { capacity = Math.max(0, capacity); }
        @Override public int size() { return capacity; }
        @Override public GenericStack get(int index) { List<GenericStack> stacks = snapshot(); return index >= 0 && index < stacks.size() && index < capacity ? stacks.get(index) : null; }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) { GenericStack current = get(index); return index < 0 || index >= capacity || current != null && !current.what().equals(key) ? 0L : delegate.insertAny(key, amount, simulate); }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) { GenericStack current = get(index); return current == null || !current.what().equals(key) ? 0L : delegate.extractAny(key, amount, simulate); }
        private List<GenericStack> snapshot() {
            LinkedHashMap<GenericKey, Long> totals = new LinkedHashMap<>();
            for (int i = 0; i < delegate.size(); i++) { GenericStack stack = delegate.get(i); if (stack != null && stack.amount() > 0L) totals.merge(stack.what(), stack.amount(), GenericStorages::saturatedAdd); }
            ArrayList<GenericStack> result = new ArrayList<>();
            totals.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
            result.sort(Comparator.comparing(GenericStorages::sortKey));
            return result.size() > capacity ? List.copyOf(result.subList(0, capacity)) : List.copyOf(result);
        }
    }

    private static long saturatedAdd(long left, long right) { return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right; }
    private static String sortKey(GenericStack stack) { return stack.what().type().id() + "|" + stack.what().id() + "|" + GenericStackTypes.writeKey(stack.what()); }
    private record Location(GenericStorage storage, int index) {}
}

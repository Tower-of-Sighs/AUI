package com.sighs.apricityui.container.storage;

import com.sighs.apricityui.stack.GenericStackAdapters;

import com.sighs.apricityui.stack.FluidKey;
import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.ItemKey;
import com.sighs.apricityui.stack.GenericStackTypes;
import com.sighs.apricityui.stack.ItemStackType;
import com.sighs.apricityui.stack.FluidStackType;
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

    public static GenericStorage view(GenericStorage storage, int requestedCapacity) {
        if (storage == null) return null;
        int capacity = requestedCapacity > 0 ? requestedCapacity : storage.size();
        return new FixedStorage(storage, capacity);
    }

    private record ItemStorageAdapter(SlottedStorage<ItemVariant> storage) implements GenericStorage {
        @Override public String resourceType(int index) {
            return index >= 0 && index < size() ? GenericStackTypes.require(ItemStackType.class).id() : null;
        }
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
        @Override public Storage<FluidVariant> fluidSlot(int index) {
            if (index < 0 || index >= size()) return null;
            if (storage instanceof SlottedStorage<?> slotted) return fluidSlot(slotted, index);
            return size() == 1 && index == 0 ? storage : null;
        }
        @Override public String resourceType(int index) {
            return index >= 0 && index < size() ? GenericStackTypes.require(FluidStackType.class).id() : null;
        }
        @Override public int size() {
            return storage instanceof SlottedStorage<?> slotted ? slotted.getSlotCount() : views().size();
        }
        @Override public GenericStack get(int index) {
            StorageView<FluidVariant> view = view(index);
            return view == null || view.isResourceBlank() ? null
                    : GenericStackAdapters.fromFluidVariant(view.getResource(), view.getAmount() / FLUID_UNIT);
        }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) {
            if (!(key instanceof FluidKey fluidKey) || amount <= 0L || view(index) == null
                    || (!(storage instanceof SlottedStorage<?>) && size() != 1)) return 0L;
            long transferAmount = scale(amount);
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = storage instanceof SlottedStorage<?> slotted
                        ? fluidSlot(slotted, index).insert(fluidKey.variant(), transferAmount, transaction)
                        : storage.insert(fluidKey.variant(), transferAmount, transaction);
                if (inserted % FLUID_UNIT != 0L) return 0L;
                if (!simulate && inserted > 0L) transaction.commit();
                return inserted / FLUID_UNIT;
            }
        }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) {
            StorageView<FluidVariant> view = view(index);
            if (view == null || !(key instanceof FluidKey fluidKey) || amount <= 0L
                    || (!(storage instanceof SlottedStorage<?>) && size() != 1)) return 0L;
            try (Transaction transaction = Transaction.openOuter()) {
                long extracted = view.extract(fluidKey.variant(), scale(amount), transaction);
                if (extracted % FLUID_UNIT != 0L) return 0L;
                if (!simulate && extracted > 0L) transaction.commit();
                return extracted / FLUID_UNIT;
            }
        }
        @SuppressWarnings("unchecked")
        private static Storage<FluidVariant> fluidSlot(SlottedStorage<?> storage, int index) {
            return (Storage<FluidVariant>) storage.getSlot(index);
        }
        private StorageView<FluidVariant> view(int index) {
            if (storage instanceof SlottedStorage<?> slotted) {
                return index >= 0 && index < slotted.getSlotCount() ? fluidSlot(slotted, index) : null;
            }
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
        @Override public String resourceType(int index) {
            return index >= 0 && index < size() ? GenericStackTypes.require(ItemStackType.class).id() : null;
        }
        @Override public int size() { return container.getContainerSize(); }
        @Override public GenericStack get(int index) { return valid(index) ? GenericStackAdapters.fromItemStack(container.getItem(index)) : null; }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof ItemKey itemKey) || amount <= 0L) return 0L;
            ItemStack current = container.getItem(index);
            ItemStack incoming = itemKey.toStack(1);
            if (!container.canPlaceItem(index, incoming) || (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, incoming))) return 0L;
            int accepted = (int) Math.min(amount, Math.max(0, Math.min(container.getMaxStackSize(), incoming.getMaxStackSize()) - current.getCount()));
            if (!simulate && accepted > 0) {
                if (current.isEmpty()) container.setItem(index, itemKey.toStack(accepted)); else current.grow(accepted);
                container.setChanged();
            }
            return accepted;
        }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) {
            GenericStack current = get(index);
            if (current == null || !current.key().equals(key) || amount <= 0L) return 0L;
            int extracted = (int) Math.min(current.amount(), Math.min(Integer.MAX_VALUE, amount));
            if (!simulate && extracted > 0) container.removeItem(index, extracted);
            return extracted;
        }
        private boolean valid(int index) { return index >= 0 && index < size(); }
    }

    private record CombinedStorage(List<GenericStorage> storages) implements GenericStorage {
        @Override public Storage<FluidVariant> fluidSlot(int index) {
            Location location = locate(index);
            return location == null ? null : location.storage.fluidSlot(location.index);
        }
        @Override public String resourceType(int index) {
            Location location = locate(index);
            return location == null ? null : location.storage.resourceType(location.index);
        }
        @Override public int size() { int size = 0; for (GenericStorage storage : storages) size += storage.size(); return size; }
        @Override public GenericStack get(int index) { Location location = locate(index); return location == null ? null : location.storage.get(location.index); }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) { Location l = locate(index); return l == null ? 0L : l.storage.insert(l.index, key, amount, simulate); }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) { Location l = locate(index); return l == null ? 0L : l.storage.extract(l.index, key, amount, simulate); }
        private Location locate(int index) { if (index < 0) return null; for (GenericStorage storage : storages) { if (index < storage.size()) return new Location(storage, index); index -= storage.size(); } return null; }
    }

    private record FixedStorage(GenericStorage delegate, int capacity) implements GenericStorage {
        private FixedStorage { capacity = Math.max(0, capacity); }
        @Override public Storage<FluidVariant> fluidSlot(int index) {
            return index >= 0 && index < capacity ? delegate.fluidSlot(index) : null;
        }
        @Override public String resourceType(int index) {
            return index >= 0 && index < capacity ? delegate.resourceType(index) : null;
        }
        @Override public int size() { return capacity; }
        @Override public GenericStack get(int index) { return index >= 0 && index < capacity && index < delegate.size() ? delegate.get(index) : null; }
        @Override public long insert(int index, GenericKey key, long amount, boolean simulate) { return index < 0 || index >= capacity || index >= delegate.size() ? 0L : delegate.insert(index, key, amount, simulate); }
        @Override public long extract(int index, GenericKey key, long amount, boolean simulate) { return index >= 0 && index < capacity && index < delegate.size() ? delegate.extract(index, key, amount, simulate) : 0L; }
    }

    private record Location(GenericStorage storage, int index) {}
}

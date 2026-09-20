package com.sighs.apricityui.container.storage;

import com.sighs.apricityui.stack.FluidKey;
import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.GenericStackTypes;
import com.sighs.apricityui.stack.ItemKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public final class GenericStorages {
    private GenericStorages() {
    }

    public static GenericStorage itemHandler(IItemHandler handler) {
        return new ItemHandlerStorage(handler);
    }

    public static GenericStorage container(Container container) {
        return new ContainerStorage(container);
    }

    public static GenericStorage fluidHandler(IFluidHandler handler) {
        return new FluidHandlerStorage(handler);
    }

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

    private record ItemHandlerStorage(IItemHandler handler) implements GenericStorage {
        @Override
        public int size() {
            return handler.getSlots();
        }

        @Override
        public GenericStack get(int index) {
            return valid(index) ? GenericStack.fromItemStack(handler.getStackInSlot(index)) : null;
        }

        @Override
        public long insert(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof ItemKey itemKey) || amount <= 0L) return 0L;
            int requested = (int) Math.min(Integer.MAX_VALUE, amount);
            ItemStack remainder = handler.insertItem(index, itemKey.toStack(requested), simulate);
            return requested - (remainder.isEmpty() ? 0 : remainder.getCount());
        }

        @Override
        public long extract(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof ItemKey) || amount <= 0L) return 0L;
            GenericStack current = get(index);
            if (current == null || !current.what().equals(key)) return 0L;
            return handler.extractItem(index, (int) Math.min(Integer.MAX_VALUE, amount), simulate).getCount();
        }

        private boolean valid(int index) {
            return index >= 0 && index < size();
        }
    }

    private record FluidHandlerStorage(IFluidHandler handler) implements GenericStorage {
        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public GenericStack get(int index) {
            return valid(index) ? GenericStack.fromFluidStack(handler.getFluidInTank(index)) : null;
        }

        @Override
        public long insert(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof FluidKey fluidKey) || amount <= 0L) return 0L;
            GenericStack current = get(index);
            if (current != null && !current.what().equals(key)) return 0L;
            return handler.fill(fluidKey.toStack((int) Math.min(Integer.MAX_VALUE, amount)), action(simulate));
        }

        @Override
        public long extract(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof FluidKey fluidKey) || amount <= 0L) return 0L;
            GenericStack current = get(index);
            if (current == null || !current.what().equals(key)) return 0L;
            FluidStack drained = handler.drain(
                    fluidKey.toStack((int) Math.min(Integer.MAX_VALUE, amount)), action(simulate));
            return drained.getAmount();
        }

        private static IFluidHandler.FluidAction action(boolean simulate) {
            return simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        }

        private boolean valid(int index) {
            return index >= 0 && index < size();
        }
    }

    private record ContainerStorage(Container container) implements GenericStorage {
        @Override
        public int size() {
            return container.getContainerSize();
        }

        @Override
        public GenericStack get(int index) {
            return valid(index) ? GenericStack.fromItemStack(container.getItem(index)) : null;
        }

        @Override
        public long insert(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof ItemKey itemKey) || amount <= 0L) return 0L;
            ItemStack current = container.getItem(index);
            ItemStack incoming = itemKey.toStack(1);
            if (!container.canPlaceItem(index, incoming)) return 0L;
            if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, incoming)) return 0L;
            int limit = Math.min(container.getMaxStackSize(), incoming.getMaxStackSize());
            int accepted = (int) Math.min(amount, Math.max(0, limit - current.getCount()));
            if (!simulate && accepted > 0) {
                if (current.isEmpty()) container.setItem(index, itemKey.toStack(accepted));
                else current.grow(accepted);
                container.setChanged();
            }
            return accepted;
        }

        @Override
        public long extract(int index, GenericKey key, long amount, boolean simulate) {
            if (!valid(index) || !(key instanceof ItemKey) || amount <= 0L) return 0L;
            GenericStack current = get(index);
            if (current == null || !current.what().equals(key)) return 0L;
            int extracted = (int) Math.min(current.amount(), Math.min(Integer.MAX_VALUE, amount));
            if (!simulate && extracted > 0) container.removeItem(index, extracted);
            return extracted;
        }

        private boolean valid(int index) {
            return index >= 0 && index < size();
        }
    }

    private record CombinedStorage(List<GenericStorage> storages) implements GenericStorage {
        @Override
        public int size() {
            int size = 0;
            for (GenericStorage storage : storages) size += storage.size();
            return size;
        }

        @Override
        public GenericStack get(int index) {
            Location location = locate(index);
            return location == null ? null : location.storage().get(location.index());
        }

        @Override
        public long insert(int index, GenericKey key, long amount, boolean simulate) {
            Location location = locate(index);
            return location == null ? 0L : location.storage().insert(location.index(), key, amount, simulate);
        }

        @Override
        public long extract(int index, GenericKey key, long amount, boolean simulate) {
            Location location = locate(index);
            return location == null ? 0L : location.storage().extract(location.index(), key, amount, simulate);
        }

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
        private FixedStorage {
            capacity = Math.max(0, capacity);
        }

        @Override
        public int size() {
            return capacity;
        }

        @Override
        public GenericStack get(int index) {
            return index >= 0 && index < delegate.size() && index < capacity ? delegate.get(index) : null;
        }

        @Override
        public long insert(int index, GenericKey key, long amount, boolean simulate) {
            if (index < 0 || index >= capacity) return 0L;
            return index < delegate.size()
                    ? delegate.insert(index, key, amount, simulate)
                    : delegate.insertAny(key, amount, simulate);
        }

        @Override
        public long extract(int index, GenericKey key, long amount, boolean simulate) {
            return index >= 0 && index < delegate.size() && index < capacity
                    ? delegate.extract(index, key, amount, simulate) : 0L;
        }
    }

    private record MergedStorage(GenericStorage delegate, int capacity) implements GenericStorage {
        private MergedStorage {
            capacity = Math.max(0, capacity);
        }

        @Override
        public int size() {
            return capacity;
        }

        @Override
        public GenericStack get(int index) {
            List<GenericStack> snapshot = snapshot();
            return index >= 0 && index < snapshot.size() && index < capacity ? snapshot.get(index) : null;
        }

        @Override
        public long insert(int index, GenericKey key, long amount, boolean simulate) {
            GenericStack current = get(index);
            if (current != null && !current.what().equals(key)) return 0L;
            return index >= 0 && index < capacity ? delegate.insertAny(key, amount, simulate) : 0L;
        }

        @Override
        public long extract(int index, GenericKey key, long amount, boolean simulate) {
            GenericStack current = get(index);
            if (current == null || !current.what().equals(key)) return 0L;
            return delegate.extractAny(key, amount, simulate);
        }

        private List<GenericStack> snapshot() {
            // ponytail: rebuild is O(n) per access; add a tick cache only if large handlers show up in profiling.
            LinkedHashMap<GenericKey, Long> totals = new LinkedHashMap<>();
            for (int index = 0; index < delegate.size(); index++) {
                GenericStack stack = delegate.get(index);
                if (stack == null || stack.amount() <= 0L) continue;
                totals.merge(stack.what(), stack.amount(), GenericStorages::saturatedAdd);
            }
            ArrayList<GenericStack> result = new ArrayList<>(totals.size());
            totals.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
            result.sort(Comparator.comparing(GenericStorages::sortKey));
            if (result.size() > capacity) return List.copyOf(result.subList(0, capacity));
            return List.copyOf(result);
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static String sortKey(GenericStack stack) {
        String nbt = GenericStackTypes.writeKey(stack.what()).toString();
        return stack.what().type().id() + "|" + stack.what().id() + "|" + nbt;
    }

    private record Location(GenericStorage storage, int index) {
    }
}

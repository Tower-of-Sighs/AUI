package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Bridges a Fabric Transfer API inventory to the vanilla Container contract
 * expected by AbstractContainerMenu and Slot.
 */
public final class FabricStorageDataSource implements ContainerDataSource {
    private final ContainerBindType bindType;
    private final TransferContainer container;
    private final Predicate<ServerPlayer> validity;

    public FabricStorageDataSource(ContainerBindType bindType,
                                   SlottedStorage<ItemVariant> storage,
                                   int capacity,
                                   Predicate<ServerPlayer> validity) {
        this.bindType = Objects.requireNonNull(bindType, "bindType");
        this.container = new TransferContainer(storage, capacity);
        this.validity = validity == null ? player -> true : validity;
    }

    @Override
    public ContainerBindType bindType() {
        return bindType;
    }

    @Override
    public int capacity() {
        return container.getContainerSize();
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y, SlotFilter filter) {
        return new Slot(new TransferContainer(container.storage, container.capacity, filter), slotIndex, x, y);
    }

    @Override
    public boolean stillValid(ServerPlayer player) {
        return validity.test(player);
    }

    private static final class TransferContainer implements Container {
        private final SlottedStorage<ItemVariant> storage;
        private final int capacity;
        private final SlotFilter filter;

        private TransferContainer(SlottedStorage<ItemVariant> storage, int capacity) {
            this(storage, capacity, new SlotFilter());
        }

        private TransferContainer(SlottedStorage<ItemVariant> storage, int capacity, SlotFilter filter) {
            this.storage = Objects.requireNonNull(storage, "storage");
            this.capacity = Math.max(0, Math.min(storage.getSlotCount(), capacity));
            this.filter = Objects.requireNonNull(filter, "filter");
        }

        @Override
        public int getContainerSize() {
            return capacity;
        }

        @Override
        public boolean isEmpty() {
            for (int index = 0; index < capacity; index++) {
                if (!getItem(index).isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            StorageView<ItemVariant> view = view(slot);
            if (view == null || view.isResourceBlank() || view.getAmount() <= 0) return ItemStack.EMPTY;
            int amount = (int) Math.min(Integer.MAX_VALUE, view.getAmount());
            return view.getResource().toStack(amount);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (amount <= 0) return ItemStack.EMPTY;
            StorageView<ItemVariant> view = view(slot);
            if (view == null || view.isResourceBlank() || view.getAmount() <= 0) return ItemStack.EMPTY;

            ItemVariant variant = view.getResource();
            long extracted;
            try (Transaction transaction = Transaction.openOuter()) {
                extracted = view.extract(variant, amount, transaction);
                if (extracted > 0) transaction.commit();
            }
            return extracted <= 0
                    ? ItemStack.EMPTY
                    : variant.toStack((int) Math.min(Integer.MAX_VALUE, extracted));
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack existing = getItem(slot);
            return existing.isEmpty() ? ItemStack.EMPTY : removeItem(slot, existing.getCount());
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            StorageView<ItemVariant> view = view(slot);
            if (view == null || (stack != null && !stack.isEmpty() && !accepts(stack))) return;

            try (Transaction transaction = Transaction.openOuter()) {
                if (!clearSlot(view, transaction)) return;
                if (stack != null && !stack.isEmpty()
                        && storage.getSlot(slot).insert(
                        ItemVariant.of(stack), stack.getCount(), transaction) != stack.getCount()) {
                    return;
                }
                transaction.commit();
            }
        }

        @Override
        public void setChanged() {
            // Transfer participants receive the change notification when the
            // outer transaction commits.
        }

        @Override
        public boolean stillValid(Player player) {
            return player != null && player.isAlive();
        }

        @Override
        public void startOpen(Player player) {
        }

        @Override
        public void stopOpen(Player player) {
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack != null && !stack.isEmpty() && accepts(stack) && canInsert(slot, stack);
        }

        @Override
        public void clearContent() {
            for (int index = 0; index < capacity; index++) removeItemNoUpdate(index);
        }

        private boolean accepts(ItemStack stack) {
            return filter.accepts(stack);
        }

        private boolean canInsert(int slot, ItemStack stack) {
            try (Transaction transaction = Transaction.openOuter()) {
                StorageView<ItemVariant> view = view(slot);
                if (view == null || !clearSlot(view, transaction)) return false;
                return storage.getSlot(slot).insert(ItemVariant.of(stack), stack.getCount(), transaction)
                        == stack.getCount();
            }
        }

        private static boolean clearSlot(StorageView<ItemVariant> view, Transaction transaction) {
            return view.isResourceBlank() || view.getAmount() <= 0
                    || view.extract(view.getResource(), view.getAmount(), transaction) == view.getAmount();
        }

        private StorageView<ItemVariant> view(int slot) {
            if (slot < 0 || slot >= capacity) return null;
            return storage.getSlot(slot);
        }
    }
}

package com.sighs.apricityui.screen;

import com.sighs.apricityui.container.PlayerInventorySlotOrder;
import com.sighs.apricityui.container.SlotLayout;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.ContainerSlotSelector;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.container.datasource.ContainerDataSource;
import com.sighs.apricityui.container.storage.GenericStorage;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.stack.FluidKey;
import com.sighs.apricityui.stack.GenericKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.ItemKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Apricity 容器菜单，使用 SlotLayout 描述布局。
 */
public class ApricityContainerMenu extends AbstractContainerMenu {
    private final SlotLayout layout;
    private final Inventory playerInventory;
    private final ArrayList<ContainerDataSource> activeSources = new ArrayList<>();
    private final ServerPlayer owner;
    private final Map<ContainerSlotSelector, FilterUtil> selectorFilters;
    private final Map<String, Map<Integer, FilterUtil>> installedFiltersByLocalIndex = new LinkedHashMap<>();

    private int customSlotCount = 0;
    private int playerSlotStart = -1;
    private int playerSlotEnd = -1;

    /**
     * 客户端反序列化构造。
     */
    public ApricityContainerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, readLayout(extraData), Map.of(), Map.of(), null);
    }

    public ApricityContainerMenu(int containerId, Inventory playerInventory, SlotLayout layout) {
        this(containerId, playerInventory, layout, Map.of(), Map.of(), null);
    }

    public ApricityContainerMenu(int containerId,
                                 Inventory playerInventory,
                                 SlotLayout layout,
                                 Map<String, ContainerDataSource> containerSources,
                                 Map<ContainerSlotSelector, FilterUtil> selectorFilters,
                                 ServerPlayer owner) {
        super(ApricityMenus.APRICITY_CONTAINER.get(), containerId);
        this.playerInventory = playerInventory;
        this.layout = Objects.requireNonNull(layout, "SlotLayout 不能为空");
        this.owner = owner;
        this.selectorFilters = selectorFilters == null ? Map.of() : Map.copyOf(selectorFilters);
        initializeSlots(containerSources == null ? Map.of() : containerSources);
    }

    private static SlotLayout readLayout(FriendlyByteBuf extraData) {
        if (extraData == null) {
            throw new IllegalStateException("容器打开失败：服务端未提供 SlotLayout（extraData 为空）");
        }
        return SlotLayout.read(extraData);
    }

    public static ApricityContainerMenu createClientOnly(Inventory playerInventory, String templatePath) {
        return new ApricityContainerMenu(-1, playerInventory, SlotLayout.createUiOnly(templatePath));
    }

    private void initializeSlots(Map<String, ContainerDataSource> containerSources) {
        activeSources.clear();
        customSlotCount = 0;
        playerSlotStart = -1;
        playerSlotEnd = -1;

        if (layout.isUiOnly()) return;

        LinkedHashSet<String> initializedCustomPools = new LinkedHashSet<>();
        ArrayList<SlotLayout.ContainerEntry> sortedEntries = new ArrayList<>(layout.containers());
        sortedEntries.sort(Comparator.comparingInt(SlotLayout.ContainerEntry::baseIndex));

        for (SlotLayout.ContainerEntry entry : sortedEntries) {
            if (ContainerBindType.isPlayer(entry.bindType())) continue;
            if (entry.capacity() <= 0) continue;

            String customPoolKey = entry.baseIndex() + ":" + entry.capacity();
            if (!initializedCustomPools.add(customPoolKey)) continue;

            ContainerDataSource source = containerSources.get(entry.id());
            GenericStorage genericStorage = source == null ? null : source.genericStorage();
            int resolvedCapacity = entry.capacity();
            SimpleContainer fallback = source == null ? new SimpleContainer(Math.max(1, resolvedCapacity)) : null;

            for (int localIndex = 0; localIndex < resolvedCapacity; localIndex++) {
                int slotLocalIndex = localIndex;
                Slot slot;
                if (entry.generic()) {
                    slot = new GenericMenuSlot(genericStorage, slotLocalIndex, 0, 0,
                            () -> filterAt(entry.id(), slotLocalIndex));
                } else {
                    slot = source == null
                            ? new UiSlot(fallback, slotLocalIndex, 0, 0)
                            : source.createSlot(slotLocalIndex, 0, 0,
                            () -> filterAt(entry.id(), slotLocalIndex));
                }
                addSlot(slot);
            }

            if (source != null && !activeSources.contains(source)) {
                activeSources.add(source);
            }
        }

        customSlotCount = slots.size();

        int playerPoolCapacity = resolvePlayerPoolCapacity(layout.containers());
        if (playerPoolCapacity > 0) {
            playerSlotStart = slots.size();
            addPlayerInventorySlots(playerInventory, playerPoolCapacity);
            playerSlotEnd = slots.size();
        }
    }

    private int resolvePlayerPoolCapacity(List<SlotLayout.ContainerEntry> entries) {
        int max = 0;
        for (SlotLayout.ContainerEntry entry : entries) {
            if (!ContainerBindType.isPlayer(entry.bindType())) continue;
            max = Math.max(max, entry.capacity());
        }
        return Math.min(ContainerBindType.PLAYER_SLOT_COUNT, Math.max(0, max));
    }

    private void addPlayerInventorySlots(Inventory playerInventory, int capacity) {
        int normalized = Math.max(0, Math.min(ContainerBindType.PLAYER_SLOT_COUNT, capacity));
        for (int menuRelativeIndex = 0; menuRelativeIndex < normalized; menuRelativeIndex++) {
            int playerInventoryIndex = PlayerInventorySlotOrder.menuRelativeIndexToPlayerInventoryIndex(
                    menuRelativeIndex, normalized);
            addSlot(new UiSlot(playerInventory, playerInventoryIndex, 0, 0));
        }
    }

    public SlotLayout getLayout() {
        return layout;
    }

    public String getTemplatePath() {
        return layout.templatePath();
    }

    public Inventory getPlayerInventory() {
        return playerInventory;
    }

    public boolean hasContainer(String containerId) {
        return layout.findContainer(containerId) != null;
    }

    public Integer resolveGlobalSlotIndex(String containerId, int localSlotIndex) {
        SlotLayout.ContainerEntry entry = layout.findContainer(containerId);
        if (entry == null) return null;

        if (ContainerBindType.isPlayer(entry.bindType())) {
            if (localSlotIndex < 0 || localSlotIndex >= entry.capacity()) return null;
            int playerPoolCapacity = playerSlotEnd - playerSlotStart;
            int menuRelativeIndex = PlayerInventorySlotOrder.playerInventoryIndexToMenuRelativeIndex(
                    localSlotIndex, playerPoolCapacity);
            if (menuRelativeIndex < 0) return null;
            int resolved = playerSlotStart + menuRelativeIndex;
            return resolved >= 0 && resolved < slots.size() ? resolved : null;
        }

        Integer resolved = entry.resolveGlobalSlotIndex(localSlotIndex);
        if (resolved == null) return null;
        if (resolved < 0 || resolved >= slots.size()) return null;
        return resolved;
    }

    public List<ContainerSlotRef> getContainerSlotRefs(String containerId) {
        SlotLayout.ContainerEntry entry = layout.findContainer(containerId);
        if (entry == null || entry.capacity() <= 0) return List.of();
        ArrayList<ContainerSlotRef> refs = new ArrayList<>(entry.capacity());
        for (int localIndex = 0; localIndex < entry.capacity(); localIndex++) {
            Integer globalIndex = resolveGlobalSlotIndex(containerId, localIndex);
            if (globalIndex == null) continue;
            refs.add(new ContainerSlotRef(localIndex, globalIndex));
        }
        return List.copyOf(refs);
    }

    public Map<ContainerSlotSelector, FilterUtil> selectorFilters() {
        return selectorFilters;
    }

    /**
     * 在服务器验证客户端解析结果后，为当前菜单安装最终的本地槽位过滤规则。
     */
    public void installSlotFilters(Map<String, Map<Integer, FilterUtil>> filtersByLocalIndex) {
        if (owner == null) return;
        installedFiltersByLocalIndex.clear();
        if (filtersByLocalIndex == null || filtersByLocalIndex.isEmpty()) return;
        filtersByLocalIndex.forEach((containerId, filters) -> {
            SlotLayout.ContainerEntry entry = layout.findContainer(containerId);
            if (entry == null || ContainerBindType.isPlayer(entry.bindType()) || filters == null) return;
            filters.forEach((localIndex, filter) -> {
                if (localIndex == null || filter == null || resolveGlobalSlotIndex(containerId, localIndex) == null) return;
                installedFiltersByLocalIndex
                        .computeIfAbsent(containerId, ignored -> new LinkedHashMap<>())
                        .merge(localIndex, filter, FilterUtil::and);
            });
        });
    }

    private FilterUtil filterAt(String containerId, int localIndex) {
        return installedFiltersByLocalIndex.getOrDefault(containerId, Map.of()).get(localIndex);
    }

    private void copyInstalledFilters(Map<String, Map<Integer, FilterUtil>> filtersByLocalIndex) {
        if (filtersByLocalIndex == null || filtersByLocalIndex.isEmpty()) return;
        filtersByLocalIndex.forEach((containerId, filters) -> {
            if (containerId == null || filters == null || filters.isEmpty()) return;
            installedFiltersByLocalIndex.put(containerId, new LinkedHashMap<>(filters));
        });
    }

    @Override
    public @Nonnull ItemStack quickMoveStack(@Nonnull Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;

        Slot sourceSlot = slots.get(slotIndex);
        if (sourceSlot instanceof GenericMenuSlot) return ItemStack.EMPTY;
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copied = sourceStack.copy();

        SlotLayout.ContainerEntry primaryEntry = layout.findContainer(layout.primaryContainerId());
        int primaryStart = -1;
        int primaryEnd = -1;
        if (primaryEntry != null
                && !ContainerBindType.isPlayer(primaryEntry.bindType())
                && primaryEntry.capacity() > 0) {
            primaryStart = primaryEntry.baseIndex();
            primaryEnd = primaryStart + primaryEntry.capacity();
        }

        boolean moved;
        if (isPlayerSlot(slotIndex)) {
            if (primaryStart >= 0 && primaryEnd > primaryStart) {
                moved = moveItemStackTo(sourceStack, primaryStart, primaryEnd, false);
            } else {
                moved = customSlotCount > 0 && moveItemStackTo(sourceStack, 0, customSlotCount, false);
            }
        } else if (primaryStart >= 0 && slotIndex >= primaryStart && slotIndex < primaryEnd) {
            moved = hasPlayerPool() && moveItemStackTo(sourceStack, playerSlotStart, playerSlotEnd, true);
        } else {
            moved = hasPlayerPool() && moveItemStackTo(sourceStack, playerSlotStart, playerSlotEnd, true);
        }

        if (!moved) return ItemStack.EMPTY;

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        if (sourceStack.getCount() == copied.getCount()) {
            return ItemStack.EMPTY;
        }

        sourceSlot.onTake(player, sourceStack);
        return copied;
    }

    private boolean isPlayerSlot(int slotIndex) {
        return hasPlayerPool() && slotIndex >= playerSlotStart && slotIndex < playerSlotEnd;
    }

    private boolean hasPlayerPool() {
        return playerSlotStart >= 0 && playerSlotEnd > playerSlotStart;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof GenericMenuSlot genericSlot) {
            if (!player.level().isClientSide && clickType == ClickType.PICKUP && (button == 0 || button == 1)) {
                handleGenericClick(genericSlot, button, player);
                broadcastChanges();
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean canDragTo(@Nonnull Slot slot) {
        return !(slot instanceof GenericMenuSlot) && super.canDragTo(slot);
    }

    private void handleGenericClick(GenericMenuSlot slot, int button, Player player) {
        if (slot.storage == null) return;
        ItemStack carried = getCarried();
        GenericStack selected = slot.genericStack();

        if (!carried.isEmpty() && tryFluidContainer(slot, selected, carried, player)) return;

        if (carried.isEmpty()) {
            if (selected == null || !(selected.what() instanceof ItemKey itemKey)) return;
            long requested = button == 0 ? Math.min(selected.amount(), itemKey.toStack(1).getMaxStackSize()) : 1L;
            long extracted = slot.extract(itemKey, requested);
            if (extracted > 0L) setCarried(itemKey.toStack((int) extracted));
            return;
        }

        GenericStack carriedGeneric = GenericStack.fromItemStack(carried);
        if (carriedGeneric == null || !(carriedGeneric.what() instanceof ItemKey itemKey)) return;
        if (selected != null && !selected.what().equals(itemKey)) return;
        long requested = button == 0 ? carried.getCount() : 1L;
        long inserted = slot.insert(itemKey, requested);
        if (inserted > 0L) {
            carried.shrink((int) inserted);
            if (carried.isEmpty()) setCarried(ItemStack.EMPTY);
        }
    }

    private boolean tryFluidContainer(GenericMenuSlot slot, GenericStack selected, ItemStack carried, Player player) {
        ItemStack oneContainer = carried.copyWithCount(1);
        IFluidHandlerItem handler = oneContainer.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (handler == null) return false;

        FluidStack contained = FluidStack.EMPTY;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack candidate = handler.getFluidInTank(tank);
            if (!candidate.isEmpty()) {
                contained = candidate.copy();
                break;
            }
        }

        if (!contained.isEmpty()) {
            FluidKey key = FluidKey.of(contained);
            long accepted = slot.simulateInsert(key, contained.getAmount());
            if (accepted <= 0L) return false;
            FluidStack drained = handler.drain(
                    key.toStack((int) Math.min(Integer.MAX_VALUE, accepted)), IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) return false;
            long inserted = slot.executeInsert(key, drained.getAmount());
            if (inserted <= 0L) return false;
            replaceOneCarried(carried, handler.getContainer(), player);
            return true;
        }

        if (selected == null || !(selected.what() instanceof FluidKey fluidKey)) return false;
        int fillable = handler.fill(
                fluidKey.toStack((int) Math.min(Integer.MAX_VALUE, selected.amount())),
                IFluidHandler.FluidAction.SIMULATE);
        long extractable = slot.simulateExtract(fluidKey, fillable);
        int amount = (int) Math.min(fillable, extractable);
        if (amount <= 0) return false;
        long extracted = slot.executeExtract(fluidKey, amount);
        if (extracted <= 0L) return false;
        int filled = handler.fill(fluidKey.toStack((int) extracted), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) return false;
        replaceOneCarried(carried, handler.getContainer(), player);
        return true;
    }

    private void replaceOneCarried(ItemStack original, ItemStack result, Player player) {
        if (original.getCount() <= 1) {
            setCarried(result);
            return;
        }
        original.shrink(1);
        player.getInventory().placeItemBackInInventory(result);
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return true;
        if (owner != null && owner != serverPlayer) return false;
        for (ContainerDataSource source : activeSources) {
            if (!source.stillValid(serverPlayer)) return false;
        }
        return true;
    }

    @Override
    public void removed(@Nonnull Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) {
            for (ContainerDataSource source : activeSources) {
                source.onClose(serverPlayer);
            }
        }
    }

    public record ContainerSlotRef(int localSlotIndex, int globalSlotIndex) {
    }

    /**
     * UI 槽位，支持禁用/隐藏/尺寸控制。
     */
    public static class UiSlot extends Slot {
        private boolean uiDisabled = false;
        private boolean uiHidden = false;
        private int uiSlotSize = 16;

        public UiSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(@Nonnull ItemStack stack) {
            if (uiDisabled) return false;
            return super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(@Nonnull Player player) {
            if (uiDisabled) return false;
            return super.mayPickup(player);
        }

        public int getUiSlotSize() {
            return uiSlotSize;
        }

        public boolean isUiDisabled() {
            return uiDisabled;
        }

        public void setUiDisabled(boolean uiDisabled) {
            this.uiDisabled = uiDisabled;
        }

        public boolean isUiHidden() {
            return uiHidden;
        }

        public void setUiHidden(boolean uiHidden) {
            this.uiHidden = uiHidden;
        }

        public void setUiSlotSize(int uiSlotSize) {
            this.uiSlotSize = Math.max(1, uiSlotSize);
        }
    }

    /** Snapshot-only slot used for generic resources. */
    public static final class GenericMenuSlot extends UiSlot {
        private static final SimpleContainer PLACEHOLDER = new SimpleContainer(1);
        private final GenericStorage storage;
        private final int storageIndex;
        private final java.util.function.Supplier<FilterUtil> filterSupplier;
        private ItemStack clientSnapshot = ItemStack.EMPTY;

        public GenericMenuSlot(GenericStorage storage, int storageIndex, int x, int y,
                               java.util.function.Supplier<FilterUtil> filterSupplier) {
            super(PLACEHOLDER, 0, x, y);
            this.storage = storage;
            this.storageIndex = storageIndex;
            this.filterSupplier = filterSupplier;
        }

        public GenericStack genericStack() {
            return storage == null ? GenericStack.unwrapItemStack(clientSnapshot) : storage.get(storageIndex);
        }

        @Override
        public ItemStack getItem() {
            return storage == null ? clientSnapshot : GenericStack.wrapInItemStack(genericStack());
        }

        @Override
        public boolean hasItem() {
            return genericStack() != null;
        }

        @Override
        public void set(@Nonnull ItemStack stack) {
            if (storage == null) clientSnapshot = stack == null ? ItemStack.EMPTY : stack.copy();
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean mayPlace(@Nonnull ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(@Nonnull Player player) {
            return false;
        }

        @Override
        public void setChanged() {
        }

        private boolean accepts(GenericKey key) {
            if (!(key instanceof ItemKey itemKey) || filterSupplier == null) return true;
            FilterUtil filter = filterSupplier.get();
            return filter == null || filter.test(itemKey.toStack(1));
        }

        private long simulateInsert(GenericKey key, long amount) {
            return storage == null || !accepts(key) ? 0L : storage.insert(storageIndex, key, amount, true);
        }

        private long executeInsert(GenericKey key, long amount) {
            return storage == null || !accepts(key) ? 0L : storage.insert(storageIndex, key, amount, false);
        }

        private long insert(GenericKey key, long amount) {
            long accepted = simulateInsert(key, amount);
            return accepted <= 0L ? 0L : executeInsert(key, accepted);
        }

        private long simulateExtract(GenericKey key, long amount) {
            return storage == null ? 0L : storage.extract(storageIndex, key, amount, true);
        }

        private long executeExtract(GenericKey key, long amount) {
            return storage == null ? 0L : storage.extract(storageIndex, key, amount, false);
        }

        private long extract(GenericKey key, long amount) {
            long available = simulateExtract(key, amount);
            return available <= 0L ? 0L : executeExtract(key, available);
        }
    }
}

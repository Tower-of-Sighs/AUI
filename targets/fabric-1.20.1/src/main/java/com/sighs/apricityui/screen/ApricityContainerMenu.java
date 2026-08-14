package com.sighs.apricityui.screen;

import com.sighs.apricityui.container.PlayerInventorySlotOrder;
import com.sighs.apricityui.container.SlotLayout;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.datasource.ContainerDataSource;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.registry.ApricityMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Apricity 容器菜单，使用 SlotLayout 描述布局。
 */
public class ApricityContainerMenu extends AbstractContainerMenu {
    private final SlotLayout layout;
    private final Inventory playerInventory;
    private final ArrayList<ContainerDataSource> activeSources = new ArrayList<>();
    private final LinkedHashMap<String, Map<String, FilterUtil>> declaredFiltersByContainer = new LinkedHashMap<>();
    private final LinkedHashMap<String, Map<Integer, FilterUtil>> installedFiltersByContainer = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<Slot>> slotsByContainer = new LinkedHashMap<>();
    private final ServerPlayer owner;

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
                                 ServerPlayer owner) {
        this(containerId, playerInventory, layout, containerSources, Map.of(), owner);
    }

    public ApricityContainerMenu(int containerId,
                                 Inventory playerInventory,
                                 SlotLayout layout,
                                 Map<String, ContainerDataSource> containerSources,
                                 Map<String, Map<String, FilterUtil>> declaredFiltersByContainer,
                                 ServerPlayer owner) {
        super(ApricityMenus.APRICITY_CONTAINER, containerId);
        this.playerInventory = playerInventory;
        this.layout = Objects.requireNonNull(layout, "SlotLayout 不能为空");
        this.owner = owner;
        copyDeclaredFilters(declaredFiltersByContainer);
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

    private void copyDeclaredFilters(Map<String, Map<String, FilterUtil>> filtersByContainer) {
        if (filtersByContainer == null) return;
        filtersByContainer.forEach((containerId, filters) -> {
            if (containerId == null || containerId.isBlank() || filters == null || filters.isEmpty()) return;
            LinkedHashMap<String, FilterUtil> usable = new LinkedHashMap<>();
            filters.forEach((selector, filter) -> {
                if (selector != null && !selector.isBlank() && filter != null) usable.put(selector, filter);
            });
            if (!usable.isEmpty()) declaredFiltersByContainer.put(containerId, Map.copyOf(usable));
        });
    }

    private void initializeSlots(Map<String, ContainerDataSource> containerSources) {
        activeSources.clear();
        installedFiltersByContainer.clear();
        slotsByContainer.clear();
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
            int resolvedCapacity = entry.capacity();
            SimpleContainer fallback = source == null ? new SimpleContainer(Math.max(1, resolvedCapacity)) : null;
            ArrayList<Slot> entrySlots = new ArrayList<>(resolvedCapacity);

            for (int localIndex = 0; localIndex < resolvedCapacity; localIndex++) {
                Slot slot = source == null
                        ? new UiSlot(fallback, localIndex, 0, 0)
                        : source.createSlot(localIndex, 0, 0);
                addSlot(slot);
                entrySlots.add(slot);
            }
            slotsByContainer.put(entry.id(), entrySlots);

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

    /** 当前菜单持有的服务端 selector 声明；客户端数据不能创建或替换此映射。 */
    public FilterUtil declaredFilter(String containerId, String selector) {
        if (containerId == null || selector == null) return null;
        return declaredFiltersByContainer.getOrDefault(containerId, Map.of()).get(selector);
    }

    /** 服务端按经过验证的本地索引安装当前菜单已声明的过滤器。 */
    public boolean installDeclaredFilter(String containerId, int localIndex, FilterUtil filter) {
        if (owner == null || filter == null || localIndex < 0) return false;
        SlotLayout.ContainerEntry entry = layout.findContainer(containerId);
        if (entry == null || ContainerBindType.isPlayer(entry.bindType()) || localIndex >= entry.capacity()) return false;
        Slot declaredSlot = declaredSlot(containerId, localIndex);
        if (declaredSlot == null) return false;

        Integer globalIndex = resolveGlobalSlotIndex(containerId, localIndex);
        if (globalIndex == null || globalIndex < 0 || globalIndex >= slots.size()) return false;
        FilterUtil combined = installedFiltersByContainer
                .computeIfAbsent(containerId, ignored -> new LinkedHashMap<>())
                .merge(localIndex, filter, FilterUtil::and);
        slots.set(globalIndex, new FilteredSlot(declaredSlot, combined));
        return true;
    }

    private Slot declaredSlot(String containerId, int localIndex) {
        List<Slot> entrySlots = slotsByContainer.get(containerId);
        if (entrySlots == null || localIndex >= entrySlots.size()) return null;
        return entrySlots.get(localIndex);
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

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;

        Slot sourceSlot = slots.get(slotIndex);
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
    public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return true;
        if (owner != null && owner != serverPlayer) return false;
        for (ContainerDataSource source : activeSources) {
            if (!source.stillValid(serverPlayer)) return false;
        }
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) {
            for (ContainerDataSource source : activeSources) {
                source.onClose(serverPlayer);
            }
        }
    }

    public record ContainerSlotRef(int localSlotIndex, int globalSlotIndex) {
    }

    private static final class FilteredSlot extends Slot {
        private final Slot delegate;
        private final FilterUtil filter;

        private FilteredSlot(Slot delegate, FilterUtil filter) {
            super(delegate.container, delegate.getContainerSlot(), delegate.x, delegate.y);
            this.delegate = delegate;
            this.filter = filter;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return delegate.mayPlace(stack) && filter.test(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return delegate.mayPickup(player);
        }

        @Override
        public ItemStack getItem() {
            return delegate.getItem();
        }

        @Override
        public void set(ItemStack stack) {
            if (stack == null || stack.isEmpty() || mayPlace(stack)) delegate.set(stack);
        }

        @Override
        public void setChanged() {
            delegate.setChanged();
        }

        @Override
        public boolean hasItem() {
            return delegate.hasItem();
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            delegate.onTake(player, stack);
        }

        @Override
        public int getMaxStackSize() {
            return delegate.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return delegate.getMaxStackSize(stack);
        }

        @Override
        public ItemStack remove(int amount) {
            return delegate.remove(amount);
        }
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
        public boolean mayPlace(ItemStack stack) {
            if (uiDisabled) return false;
            return super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
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
}

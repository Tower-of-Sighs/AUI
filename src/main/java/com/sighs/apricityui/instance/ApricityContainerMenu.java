package com.sighs.apricityui.instance;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.datasource.ContainerDataSource;
import com.sighs.apricityui.instance.container.layout.MenuLayoutSpec;
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

public class ApricityContainerMenu extends AbstractContainerMenu {
    private final MenuLayoutSpec layoutSpec;
    private final Inventory playerInventory;
    private final LinkedHashMap<String, LinkedHashMap<Integer, Integer>> containerLocalToGlobal = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<ContainerSlotRef>> containerSlotRefs = new LinkedHashMap<>();
    private final ArrayList<ContainerDataSource> activeSources = new ArrayList<>();
    private final ServerPlayer owner;

    private int customSlotCount = 0;
    private int playerSlotStart = -1;
    private int playerSlotEnd = -1;

    public ApricityContainerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, readLayoutSpec(extraData), Map.of(), null);
    }

    public ApricityContainerMenu(int containerId, Inventory playerInventory, MenuLayoutSpec layoutSpec) {
        this(containerId, playerInventory, layoutSpec, Map.of(), null);
    }

    public ApricityContainerMenu(int containerId,
                                 Inventory playerInventory,
                                 MenuLayoutSpec layoutSpec,
                                 Map<String, ContainerDataSource> containerSources,
                                 ServerPlayer owner) {
        super(ApricityMenus.APRICITY_CONTAINER.get(), containerId);
        this.playerInventory = playerInventory;
        this.layoutSpec = Objects.requireNonNull(layoutSpec, "Menu layout spec cannot be null");
        this.owner = owner;
        initializeSlots(containerSources == null ? Map.of() : containerSources);
    }

    private static MenuLayoutSpec readLayoutSpec(FriendlyByteBuf extraData) {
        if (extraData == null) {
            throw new IllegalStateException("Container open failed: missing layoutSpec in menu extra data");
        }
        return MenuLayoutSpec.read(extraData);
    }

    public static ApricityContainerMenu createClientOnly(Inventory playerInventory, String templatePath) {
        return new ApricityContainerMenu(-1, playerInventory, MenuLayoutSpec.createUiOnly(templatePath));
    }

    private void initializeSlots(Map<String, ContainerDataSource> containerSources) {
        containerLocalToGlobal.clear();
        containerSlotRefs.clear();
        activeSources.clear();
        customSlotCount = 0;
        playerSlotStart = -1;
        playerSlotEnd = -1;

        if (layoutSpec.isUiOnly()) return;

        for (MenuLayoutSpec.ContainerLayout containerLayout : layoutSpec.containers()) {
            if (containerLayout == null) continue;
            if (ContainerBindType.isPlayer(containerLayout.bindType())) continue;
            if (ContainerBindType.isVirtualUi(containerLayout.bindType())) continue;

            int requiredPoolSize = Math.max(0, containerLayout.capacity());
            ContainerDataSource source = containerSources.get(containerLayout.id());
            SimpleContainer fallback = source == null ? new SimpleContainer(Math.max(1, requiredPoolSize)) : null;

            for (int localIndex = 0; localIndex < requiredPoolSize; localIndex++) {
                Slot slot = source == null
                        ? new UiSlot(fallback, localIndex, 0, 0)
                        : source.createSlot(localIndex, 0, 0);
                addSlot(slot);
            }

            if (source != null && !activeSources.contains(source)) {
                activeSources.add(source);
            }
        }

        customSlotCount = slots.size();

        int playerPoolCapacity = resolvePlayerPoolCapacity();
        if (playerPoolCapacity > 0) {
            playerSlotStart = slots.size();
            addPlayerInventorySlots(playerInventory, playerPoolCapacity);
            playerSlotEnd = slots.size();
        }

        buildContainerMappings();
    }

    private int resolvePlayerPoolCapacity() {
        int capacity = 0;
        for (MenuLayoutSpec.ContainerLayout containerLayout : layoutSpec.containers()) {
            if (containerLayout == null) continue;
            if (!ContainerBindType.isPlayer(containerLayout.bindType())) continue;
            capacity = Math.max(capacity, Math.max(0, containerLayout.capacity()));
        }
        return Math.min(ContainerBindType.PLAYER_SLOT_COUNT, capacity);
    }

    private void buildContainerMappings() {
        for (MenuLayoutSpec.ContainerLayout containerLayout : layoutSpec.containers()) {
            if (containerLayout == null) continue;

            int capacity = Math.max(0, containerLayout.capacity());
            LinkedHashMap<Integer, Integer> containerMapping = new LinkedHashMap<>();
            ArrayList<ContainerSlotRef> refs = new ArrayList<>(capacity);
            for (int localSlotIndex = 0; localSlotIndex < capacity; localSlotIndex++) {
                int globalSlotIndex = containerLayout.baseIndex() + localSlotIndex;
                if (globalSlotIndex < 0 || globalSlotIndex >= slots.size()) continue;
                containerMapping.put(localSlotIndex, globalSlotIndex);
                refs.add(new ContainerSlotRef(localSlotIndex, globalSlotIndex));
            }

            containerLocalToGlobal.put(containerLayout.id(), containerMapping);
            containerSlotRefs.put(containerLayout.id(), List.copyOf(refs));
        }
    }

    private void addPlayerInventorySlots(Inventory playerInventory, int slotCount) {
        int remaining = Math.max(0, slotCount);
        if (remaining <= 0) return;

        for (int row = 0; row < 3 && remaining > 0; row++) {
            for (int col = 0; col < 9 && remaining > 0; col++) {
                int index = col + row * 9 + 9;
                addSlot(new UiSlot(playerInventory, index, 0, 0));
                remaining--;
            }
        }
        for (int hotbar = 0; hotbar < 9 && remaining > 0; hotbar++) {
            addSlot(new UiSlot(playerInventory, hotbar, 0, 0));
            remaining--;
        }
    }

    public MenuLayoutSpec getLayoutSpec() {
        return layoutSpec;
    }

    public String getTemplatePath() {
        return layoutSpec.templatePath();
    }

    public Inventory getPlayerInventory() {
        return playerInventory;
    }

    public boolean hasContainer(String containerId) {
        return containerLocalToGlobal.containsKey(containerId);
    }

    public Integer resolveGlobalSlotIndex(String containerId, int localSlotIndex) {
        LinkedHashMap<Integer, Integer> mapping = containerLocalToGlobal.get(containerId);
        if (mapping == null) return null;
        return mapping.get(localSlotIndex);
    }

    public List<ContainerSlotRef> getContainerSlotRefs(String containerId) {
        return containerSlotRefs.getOrDefault(containerId, List.of());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;

        Slot sourceSlot = slots.get(slotIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copied = sourceStack.copy();

        String primaryContainerId = layoutSpec.primaryContainerId();
        Integer primaryStart = null;
        Integer primaryEnd = null;

        if (primaryContainerId != null && !primaryContainerId.isEmpty()) {
            MenuLayoutSpec.ContainerLayout primaryLayout = layoutSpec.findContainer(primaryContainerId);
            if (primaryLayout != null
                    && !ContainerBindType.isPlayer(primaryLayout.bindType())
                    && primaryLayout.capacity() > 0) {
                primaryStart = primaryLayout.baseIndex();
                primaryEnd = primaryLayout.baseIndex() + primaryLayout.capacity();
            }
        }

        boolean moved;
        if (isPlayerSlot(slotIndex)) {
            // Shift-click from player inventory
            if (primaryStart != null && primaryEnd != null && primaryEnd > primaryStart) {
                moved = moveItemStackTo(sourceStack, primaryStart, primaryEnd, false);
            } else {
                moved = customSlotCount > 0 && moveItemStackTo(sourceStack, 0, customSlotCount, false);
            }
        } else if (primaryStart != null && slotIndex >= primaryStart && slotIndex < primaryEnd) {
            // Shift-click from primary container
            moved = hasPlayerPool() && moveItemStackTo(sourceStack, playerSlotStart, playerSlotEnd, true);
        } else {
            // Shift-click from other containers
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

    public static class UiSlot extends Slot {
        private int uiSlotSize = 16;
        private boolean uiDisabled = false;
        private boolean uiHidden = false;
        private boolean uiAcceptPointer = true;
        private boolean uiRenderBackground = true;
        private boolean uiRenderItem = true;
        private float uiIconScale = 1.0F;
        private int uiPadding = 0;
        private int uiZIndex = 0;

        public UiSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean isActive() {
            return !uiHidden && super.isActive();
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

        public void setUiSlotSize(int uiSlotSize) {
            this.uiSlotSize = Math.max(1, uiSlotSize);
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

        public boolean isUiAcceptPointer() {
            return uiAcceptPointer;
        }

        public void setUiAcceptPointer(boolean uiAcceptPointer) {
            this.uiAcceptPointer = uiAcceptPointer;
        }

        public boolean isUiRenderBackground() {
            return uiRenderBackground;
        }

        public void setUiRenderBackground(boolean uiRenderBackground) {
            this.uiRenderBackground = uiRenderBackground;
        }

        public boolean isUiRenderItem() {
            return uiRenderItem;
        }

        public void setUiRenderItem(boolean uiRenderItem) {
            this.uiRenderItem = uiRenderItem;
        }

        public float getUiIconScale() {
            return uiIconScale;
        }

        public void setUiIconScale(float uiIconScale) {
            this.uiIconScale = Math.max(0.01F, uiIconScale);
        }

        public int getUiPadding() {
            return uiPadding;
        }

        public void setUiPadding(int uiPadding) {
            this.uiPadding = Math.max(0, uiPadding);
        }

        public int getUiZIndex() {
            return uiZIndex;
        }

        public void setUiZIndex(int uiZIndex) {
            this.uiZIndex = uiZIndex;
        }
    }
}

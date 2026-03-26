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

import javax.annotation.Nonnull;
import java.util.*;

public class ApricityContainerMenu extends AbstractContainerMenu {
    private final MenuLayoutSpec layoutSpec;
    private final Inventory playerInventory;
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
        this.layoutSpec = Objects.requireNonNull(layoutSpec, "Menu layout spec 不能为空");
        this.owner = owner;
        initializeSlots(containerSources == null ? Map.of() : containerSources);
    }

    private static MenuLayoutSpec readLayoutSpec(FriendlyByteBuf extraData) {
        if (extraData == null) {
            throw new IllegalStateException("容器打开失败：服务端未提供 layoutSpec（extraData 为空）");
        }
        return MenuLayoutSpec.read(extraData);
    }

    public static ApricityContainerMenu createClientOnly(Inventory playerInventory, String templatePath) {
        return new ApricityContainerMenu(-1, playerInventory, MenuLayoutSpec.createUiOnly(templatePath));
    }

    private void initializeSlots(Map<String, ContainerDataSource> containerSources) {
        activeSources.clear();
        customSlotCount = 0;
        playerSlotStart = -1;
        playerSlotEnd = -1;

        if (layoutSpec.isUiOnly()) return;

        LinkedHashSet<String> initializedCustomPools = new LinkedHashSet<>();
        ArrayList<MenuLayoutSpec.ContainerLayout> sortedContainers = new ArrayList<>(layoutSpec.containers());
        sortedContainers.sort(Comparator.comparingInt(MenuLayoutSpec.ContainerLayout::baseIndex));

        for (MenuLayoutSpec.ContainerLayout layout : sortedContainers) {
            if (ContainerBindType.isPlayer(layout.bindType())) continue;
            if (layout.capacity() <= 0) continue;

            String customPoolKey = layout.baseIndex() + ":" + layout.capacity();
            if (!initializedCustomPools.add(customPoolKey)) continue;

            ContainerDataSource source = containerSources.get(layout.id());
            int resolvedCapacity = layout.capacity();
            SimpleContainer fallback = source == null ? new SimpleContainer(Math.max(1, resolvedCapacity)) : null;

            for (int localIndex = 0; localIndex < resolvedCapacity; localIndex++) {
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

        int playerPoolCapacity = resolvePlayerPoolCapacity(layoutSpec.containers());
        if (playerPoolCapacity > 0) {
            playerSlotStart = slots.size();
            addPlayerInventorySlots(playerInventory, playerPoolCapacity);
            playerSlotEnd = slots.size();
        }
    }

    private int resolvePlayerPoolCapacity(List<MenuLayoutSpec.ContainerLayout> layouts) {
        int max = 0;
        for (MenuLayoutSpec.ContainerLayout layout : layouts) {
            if (!ContainerBindType.isPlayer(layout.bindType())) continue;
            max = Math.max(max, layout.capacity());
        }
        return Math.min(ContainerBindType.PLAYER_SLOT_COUNT, Math.max(0, max));
    }

    private void addPlayerInventorySlots(Inventory playerInventory, int capacity) {
        int normalized = Math.max(0, Math.min(ContainerBindType.PLAYER_SLOT_COUNT, capacity));
        for (int localIndex = 0; localIndex < normalized; localIndex++) {
            int inventoryIndex = localIndex;
            addSlot(new UiSlot(playerInventory, inventoryIndex, 0, 0));
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
        return layoutSpec.findContainer(containerId) != null;
    }

    public Integer resolveGlobalSlotIndex(String containerId, int localSlotIndex) {
        MenuLayoutSpec.ContainerLayout container = layoutSpec.findContainer(containerId);
        if (container == null) return null;
        Integer resolved = container.resolveGlobalSlotIndex(localSlotIndex);
        if (resolved == null) return null;
        if (resolved < 0 || resolved >= slots.size()) return null;
        return resolved;
    }

    public List<ContainerSlotRef> getContainerSlotRefs(String containerId) {
        MenuLayoutSpec.ContainerLayout container = layoutSpec.findContainer(containerId);
        if (container == null || container.capacity() <= 0) return List.of();
        ArrayList<ContainerSlotRef> refs = new ArrayList<>(container.capacity());
        for (int localIndex = 0; localIndex < container.capacity(); localIndex++) {
            Integer globalIndex = container.resolveGlobalSlotIndex(localIndex);
            if (globalIndex == null || globalIndex < 0 || globalIndex >= slots.size()) continue;
            refs.add(new ContainerSlotRef(localIndex, globalIndex));
        }
        return List.copyOf(refs);
    }

    @Override
    public @Nonnull ItemStack quickMoveStack(@Nonnull Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;

        Slot sourceSlot = slots.get(slotIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copied = sourceStack.copy();

        MenuLayoutSpec.ContainerLayout primaryLayout = layoutSpec.findContainer(layoutSpec.primaryContainerId());
        int primaryStart = -1;
        int primaryEnd = -1;
        if (primaryLayout != null
                && !ContainerBindType.isPlayer(primaryLayout.bindType())
                && primaryLayout.capacity() > 0) {
            primaryStart = primaryLayout.baseIndex();
            primaryEnd = primaryStart + primaryLayout.capacity();
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

        public boolean isUiAcceptPointer() {
            return true;
        }

        public boolean isUiRenderBackground() {
            return true;
        }

        public boolean isUiRenderItem() {
            return true;
        }

        public float getUiIconScale() {
            return 1.0F;
        }

        public int getUiPadding() {
            return 0;
        }

        public int getUiZIndex() {
            return 0;
        }
    }
}

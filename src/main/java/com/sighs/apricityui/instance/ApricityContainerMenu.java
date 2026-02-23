package com.sighs.apricityui.instance;

import com.sighs.apricityui.instance.container.bind.ApricityMenuSlotSource;
import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import com.sighs.apricityui.registry.ApricityMenus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import java.util.*;

public class ApricityContainerMenu extends Container {
    @Getter
    private final ContainerSchema.Descriptor descriptor;
    @Getter
    private final IInventory playerInventory;
    private final LinkedHashMap<String, LinkedHashMap<Integer, Integer>> containerLocalToGlobal = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<ContainerSlotRef>> containerSlotRefs = new LinkedHashMap<>();
    private final ArrayList<ApricityMenuSlotSource> activeSources = new ArrayList<>();
    private final ServerPlayerEntity owner;

    private int customSlotCount = 0;
    private int playerSlotStart = -1;
    private int playerSlotEnd = -1;

    public ApricityContainerMenu(int containerId, IInventory playerInventory, PacketBuffer extraData) {
        this(containerId, playerInventory, readDescriptor(extraData), new HashMap<>(), null);
    }

    public ApricityContainerMenu(int containerId, IInventory playerInventory, ContainerSchema.Descriptor descriptor) {
        this(containerId, playerInventory, descriptor, new HashMap<>(), null);
    }

    public ApricityContainerMenu(int containerId,
                                 IInventory playerInventory,
                                 ContainerSchema.Descriptor descriptor,
                                 Map<String, ApricityMenuSlotSource> containerSources,
                                 ServerPlayerEntity owner) {
        super(ApricityMenus.APRICITY_CONTAINER.get(), containerId);
        this.playerInventory = playerInventory;
        this.descriptor = Objects.requireNonNull(descriptor, "Container descriptor 不能为空");
        this.owner = owner;
        initializeSlots(containerSources == null ? new HashMap<>() : containerSources);
    }

    private static ContainerSchema.Descriptor readDescriptor(PacketBuffer extraData) {
        if (extraData == null) {
            throw new IllegalStateException("容器打开失败：服务端未提供 descriptor（extraData 为空）");
        }
        return ContainerSchema.Descriptor.read(extraData);
    }

    public static ApricityContainerMenu createClientOnly(IInventory playerInventory, String templatePath) {
        return new ApricityContainerMenu(-1, playerInventory, ContainerSchema.Descriptor.createUiOnly(templatePath));
    }

    private void initializeSlots(Map<String, ApricityMenuSlotSource> containerSources) {
        containerLocalToGlobal.clear();
        containerSlotRefs.clear();
        activeSources.clear();
        customSlotCount = 0;
        playerSlotStart = -1;
        playerSlotEnd = -1;

        if (descriptor.isUiOnly()) return;

        LinkedHashMap<String, List<Integer>> sourcePoolByContainer = new LinkedHashMap<>();

        for (String containerId : descriptor.getContainerIds()) {
            ContainerSchema.Descriptor.BindType bindType = descriptor.getContainerBindType(containerId);
            if (ContainerSchema.Descriptor.isPlayerBind(bindType)) continue;
            if (ContainerSchema.Descriptor.isVirtualUiBind(bindType)) continue;

            int requiredPoolSize = ContainerSchema.Descriptor.requiredPoolSize(descriptor.getContainerSlots(containerId));
            ArrayList<Integer> pool = new ArrayList<>(requiredPoolSize);
            ApricityMenuSlotSource source = containerSources.get(containerId);
            IInventory fallback = source == null ? new Inventory(Math.max(1, requiredPoolSize)) : null;

            for (int localIndex = 0; localIndex < requiredPoolSize; localIndex++) {
                Slot slot = source == null
                        ? new UiSlot(fallback, localIndex, 0, 0)
                        : source.createSlot(localIndex, 0, 0);
                addSlot(slot);
                pool.add(slots.size() - 1);
            }

            if (source != null && !activeSources.contains(source)) {
                activeSources.add(source);
            }
            sourcePoolByContainer.put(containerId, pool);
        }

        customSlotCount = slots.size();

        boolean hasPlayerBinding = false;
        for (String containerId : descriptor.getContainerIds()) {
            if (ContainerSchema.Descriptor.isPlayerBind(descriptor.getContainerBindType(containerId))) {
                hasPlayerBinding = true;
                break;
            }
        }

        List<Integer> playerPool = Collections.emptyList();
        if (hasPlayerBinding) {
            playerSlotStart = slots.size();
            addPlayerInventorySlots(playerInventory);
            playerSlotEnd = slots.size();
            ArrayList<Integer> generatedPlayerPool = new ArrayList<>(playerSlotEnd - playerSlotStart);
            for (int i = playerSlotStart; i < playerSlotEnd; i++) {
                generatedPlayerPool.add(i);
            }
            playerPool = new ArrayList<>(generatedPlayerPool);
        }

        for (String containerId : descriptor.getContainerIds()) {
            ContainerSchema.Descriptor.BindType bindType = descriptor.getContainerBindType(containerId);
            if (ContainerSchema.Descriptor.isPlayerBind(bindType)) {
                sourcePoolByContainer.put(containerId, playerPool);
            }
        }

        buildContainerMappings(sourcePoolByContainer);
        applySlotVisualProfiles();
    }

    private void buildContainerMappings(Map<String, List<Integer>> sourcePoolByContainer) {
        LinkedHashSet<Integer> globallyUsedSlots = new LinkedHashSet<>();

        for (String containerId : descriptor.getContainerIds()) {
            List<Integer> localSlots = descriptor.getContainerSlots(containerId);
            List<Integer> sourcePool = sourcePoolByContainer.getOrDefault(containerId, Collections.emptyList());

            LinkedHashMap<Integer, Integer> containerMapping = new LinkedHashMap<>();
            ArrayList<ContainerSlotRef> refs = new ArrayList<>();
            for (Integer localSlotIndex : localSlots) {
                if (localSlotIndex == null || localSlotIndex < 0 || localSlotIndex >= sourcePool.size()) continue;

                int globalSlotIndex = sourcePool.get(localSlotIndex);
                if (!globallyUsedSlots.add(globalSlotIndex)) continue;

                containerMapping.put(localSlotIndex, globalSlotIndex);
                refs.add(new ContainerSlotRef(localSlotIndex, globalSlotIndex));
            }

            containerLocalToGlobal.put(containerId, containerMapping);
            containerSlotRefs.put(containerId, new ArrayList<>(refs));
        }
    }

    private void applySlotVisualProfiles() {
        for (String containerId : descriptor.getContainerIds()) {
            Map<Integer, ContainerSchema.Descriptor.SlotVisualProfile> visuals = descriptor.getContainerSlotVisuals(containerId);
            if (visuals.isEmpty()) continue;

            List<ContainerSlotRef> refs = getContainerSlotRefs(containerId);
            for (ContainerSlotRef ref : refs) {
                if (ref == null) continue;
                if (ref.globalSlotIndex < 0 || ref.globalSlotIndex >= slots.size()) continue;
                net.minecraft.inventory.container.Slot rawSlot = slots.get(ref.globalSlotIndex);
                if (!(rawSlot instanceof UiSlot)) continue;
                UiSlot uiSlot = (UiSlot) rawSlot;

                ContainerSchema.Descriptor.SlotVisualProfile profile = visuals.get(ref.localSlotIndex);
                if (profile == null) continue;
                uiSlot.applyProfile(profile);
            }
        }
    }

    private void addPlayerInventorySlots(IInventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9 + 9;
                addSlot(new UiSlot(playerInventory, index, 0, 0));
            }
        }
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            addSlot(new UiSlot(playerInventory, hotbar, 0, 0));
        }
    }

    public String getTemplatePath() {
        return descriptor.getTemplatePath();
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
        return containerSlotRefs.getOrDefault(containerId, Collections.emptyList());
    }

    @Override
    public ItemStack quickMoveStack(PlayerEntity player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;

        Slot sourceSlot = slots.get(slotIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copied = sourceStack.copy();

        if (slotIndex < customSlotCount) {
            if (playerSlotStart < 0 || playerSlotEnd <= playerSlotStart) return ItemStack.EMPTY;
            if (!moveItemStackTo(sourceStack, playerSlotStart, playerSlotEnd, true)) return ItemStack.EMPTY;
        } else {
            if (customSlotCount <= 0) return ItemStack.EMPTY;
            if (!moveItemStackTo(sourceStack, 0, customSlotCount, false)) return ItemStack.EMPTY;
        }

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

    @Override
    public boolean stillValid(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity)) return true;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        if (owner != null && owner != serverPlayer) return false;
        for (ApricityMenuSlotSource source : activeSources) {
            if (!source.stillValid(serverPlayer)) return false;
        }
        return true;
    }

    @Override
    public void removed(PlayerEntity player) {
        super.removed(player);
        if (player instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            for (ApricityMenuSlotSource source : activeSources) {
                source.onClose(serverPlayer);
            }
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class ContainerSlotRef {
        private int localSlotIndex;
        private int globalSlotIndex;
    }

    @Getter
    public static class UiSlot extends Slot {
        private int uiSlotSize = 16;
        @Setter
        private boolean uiDisabled = false;
        @Setter
        private boolean uiHidden = false;
        @Setter
        private boolean uiAcceptPointer = true;
        @Setter
        private boolean uiRenderBackground = true;
        @Setter
        private boolean uiRenderItem = true;
        private float uiIconScale = 1.0F;
        private int uiPadding = 0;
        @Setter
        private int uiZIndex = 0;

        public UiSlot(IInventory container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        public void applyProfile(ContainerSchema.Descriptor.SlotVisualProfile profile) {
            if (profile == null) return;
            uiSlotSize = Math.max(1, profile.resolveSlotSize(uiSlotSize));
            uiDisabled = profile.resolveDisabled(uiDisabled);
            uiAcceptPointer = profile.resolveAcceptPointer(uiAcceptPointer);
            uiRenderBackground = profile.resolveRenderBackground(uiRenderBackground);
            uiRenderItem = profile.resolveRenderItem(uiRenderItem);
            uiIconScale = Math.max(0.01F, profile.resolveIconScale(uiIconScale));
            uiPadding = Math.max(0, profile.resolvePadding(uiPadding));
            uiZIndex = profile.resolveZIndex(uiZIndex);
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
        public boolean mayPickup(PlayerEntity player) {
            if (uiDisabled) return false;
            return super.mayPickup(player);
        }

        public void setUiSlotSize(int uiSlotSize) {
            this.uiSlotSize = Math.max(1, uiSlotSize);
        }

        public void setUiIconScale(float uiIconScale) {
            this.uiIconScale = Math.max(0.01F, uiIconScale);
        }

        public void setUiPadding(int uiPadding) {
            this.uiPadding = Math.max(0, uiPadding);
        }

    }
}

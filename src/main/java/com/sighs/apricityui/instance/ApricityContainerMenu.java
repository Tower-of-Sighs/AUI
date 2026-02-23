package com.sighs.apricityui.instance;

import com.sighs.apricityui.instance.container.bind.ApricityMenuSlotSource;
import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import com.sighs.apricityui.registry.ApricityMenus;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ApricityContainerMenu extends AbstractContainerMenu {
    @Getter
    private final ContainerSchema.Descriptor descriptor;
    @Getter
    private final Inventory playerInventory;
    private final LinkedHashMap<String, LinkedHashMap<Integer, Integer>> containerLocalToGlobal = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<ContainerSlotRef>> containerSlotRefs = new LinkedHashMap<>();
    private final ArrayList<ApricityMenuSlotSource> activeSources = new ArrayList<>();
    private final ServerPlayer owner;

    private int customSlotCount = 0;
    private int playerSlotStart = -1;
    private int playerSlotEnd = -1;

    public ApricityContainerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, readDescriptor(extraData), Map.of(), null);
    }

    public ApricityContainerMenu(int containerId, Inventory playerInventory, ContainerSchema.Descriptor descriptor) {
        this(containerId, playerInventory, descriptor, Map.of(), null);
    }

    public ApricityContainerMenu(int containerId,
                                 Inventory playerInventory,
                                 ContainerSchema.Descriptor descriptor,
                                 Map<String, ApricityMenuSlotSource> containerSources,
                                 ServerPlayer owner) {
        super(ApricityMenus.APRICITY_CONTAINER.get(), containerId);
        this.playerInventory = playerInventory;
        this.descriptor = Objects.requireNonNull(descriptor, "Container descriptor 不能为空");
        this.owner = owner;
        initializeSlots(containerSources == null ? Map.of() : containerSources);
    }

    private static ContainerSchema.Descriptor readDescriptor(FriendlyByteBuf extraData) {
        if (extraData == null) {
            throw new IllegalStateException("容器打开失败：服务端未提供 descriptor（extraData 为空）");
        }
        return ContainerSchema.Descriptor.read(extraData);
    }

    public static ApricityContainerMenu createClientOnly(Inventory playerInventory, String templatePath) {
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
            SimpleContainer fallback = source == null ? new SimpleContainer(Math.max(1, requiredPoolSize)) : null;

            for (int localIndex = 0; localIndex < requiredPoolSize; localIndex++) {
                Slot slot = source == null
                        ? new Slot(fallback, localIndex, 0, 0)
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

        List<Integer> playerPool = List.of();
        if (hasPlayerBinding) {
            playerSlotStart = slots.size();
            addPlayerInventorySlots(playerInventory);
            playerSlotEnd = slots.size();
            ArrayList<Integer> generatedPlayerPool = new ArrayList<>(playerSlotEnd - playerSlotStart);
            for (int i = playerSlotStart; i < playerSlotEnd; i++) {
                generatedPlayerPool.add(i);
            }
            playerPool = List.copyOf(generatedPlayerPool);
        }

        for (String containerId : descriptor.getContainerIds()) {
            ContainerSchema.Descriptor.BindType bindType = descriptor.getContainerBindType(containerId);
            if (ContainerSchema.Descriptor.isPlayerBind(bindType)) {
                sourcePoolByContainer.put(containerId, playerPool);
            }
        }

        buildContainerMappings(sourcePoolByContainer);
    }

    private void buildContainerMappings(Map<String, List<Integer>> sourcePoolByContainer) {
        LinkedHashSet<Integer> globallyUsedSlots = new LinkedHashSet<>();

        for (String containerId : descriptor.getContainerIds()) {
            List<Integer> localSlots = descriptor.getContainerSlots(containerId);
            List<Integer> sourcePool = sourcePoolByContainer.getOrDefault(containerId, List.of());

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
            containerSlotRefs.put(containerId, List.copyOf(refs));
        }
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9 + 9;
                addSlot(new Slot(playerInventory, index, 0, 0));
            }
        }
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            addSlot(new Slot(playerInventory, hotbar, 0, 0));
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
        return containerSlotRefs.getOrDefault(containerId, List.of());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
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
    public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return true;
        if (owner != null && owner != serverPlayer) return false;
        for (ApricityMenuSlotSource source : activeSources) {
            if (!source.stillValid(serverPlayer)) return false;
        }
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) {
            for (ApricityMenuSlotSource source : activeSources) {
                source.onClose(serverPlayer);
            }
        }
    }

    public record ContainerSlotRef(int localSlotIndex, int globalSlotIndex) {
    }
}

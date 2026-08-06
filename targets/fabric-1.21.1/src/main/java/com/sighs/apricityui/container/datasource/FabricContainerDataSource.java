package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import java.util.Objects;
import java.util.function.Predicate;

public final class FabricContainerDataSource implements ContainerDataSource {
    private final ContainerBindType bindType;
    private final Container container;
    private final int capacity;
    private final Predicate<ServerPlayer> validity;

    public FabricContainerDataSource(ContainerBindType bindType, Container container, Predicate<ServerPlayer> validity) {
        this(bindType, container, 0, validity);
    }

    public FabricContainerDataSource(ContainerBindType bindType, Container container, int requestedCapacity, Predicate<ServerPlayer> validity) {
        this.bindType = Objects.requireNonNull(bindType);
        this.container = Objects.requireNonNull(container);
        this.capacity = requestedCapacity <= 0
                ? container.getContainerSize()
                : Math.min(Math.max(1, requestedCapacity), container.getContainerSize());
        this.validity = validity == null ? player -> true : validity;
    }
    public ContainerBindType bindType() { return bindType; }
    public int capacity() { return capacity; }
    public Slot createSlot(int slotIndex, int x, int y) { return new Slot(container, slotIndex, x, y); }
    public boolean stillValid(ServerPlayer player) { return validity.test(player); }
}

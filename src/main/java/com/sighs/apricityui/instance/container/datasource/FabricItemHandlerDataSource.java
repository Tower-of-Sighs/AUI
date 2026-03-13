package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 基于通用 Container 的物品槽数据源（block_entity/entity）。
 * 若底层方块实体同时实现了 InventoryStorage 支持，则可通过 Fabric Transfer API 访问。
 */
public final class FabricItemHandlerDataSource implements ContainerDataSource {
    private final ContainerBindType bindType;
    private final Container container;
    private final Predicate<ServerPlayer> validityChecker;

    public FabricItemHandlerDataSource(ContainerBindType bindType,
                                       Container container,
                                       Predicate<ServerPlayer> validityChecker) {
        this.bindType = Objects.requireNonNull(bindType, "bindType");
        this.container = Objects.requireNonNull(container, "container");
        this.validityChecker = validityChecker == null ? player -> true : validityChecker;
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
    public Slot createSlot(int slotIndex, int x, int y) {
        return new Slot(container, slotIndex, x, y);
    }

    @Override
    public boolean stillValid(ServerPlayer player) {
        return validityChecker.test(player);
    }

    public InventoryStorage storage() {
        return InventoryStorage.of(container, null);
    }

    public InventoryStorage storage(Direction side) {
        return InventoryStorage.of(container, side);
    }
}

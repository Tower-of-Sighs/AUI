package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 基于 Forge IItemHandler 的通用数据源（block_entity/entity）。
 */
public final class ForgeItemHandlerDataSource implements ContainerDataSource {
    private final ContainerBindType bindType;
    private final IItemHandler handler;
    private final Predicate<ServerPlayer> validityChecker;

    public ForgeItemHandlerDataSource(ContainerBindType bindType,
                                      IItemHandler handler,
                                      Predicate<ServerPlayer> validityChecker) {
        this.bindType = Objects.requireNonNull(bindType, "bindType");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.validityChecker = validityChecker == null ? player -> true : validityChecker;
    }

    @Override
    public ContainerBindType bindType() {
        return bindType;
    }

    @Override
    public int capacity() {
        return handler.getSlots();
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y) {
        return new SlotItemHandler(handler, slotIndex, x, y);
    }

    @Override
    public boolean stillValid(ServerPlayer player) {
        return validityChecker.test(player);
    }
}

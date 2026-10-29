package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 基于 transfer API {@link ResourceHandler} 的通用数据源（block_entity/entity），
 * 用于调用方已经解析好 capability 的情形。
 */
public final class ForgeItemHandlerDataSource implements ContainerDataSource {
    private final ContainerBindType bindType;
    private final ResourceHandler<ItemResource> handler;
    private final Predicate<ServerPlayer> validityChecker;

    public ForgeItemHandlerDataSource(ContainerBindType bindType,
                                      ResourceHandler<ItemResource> handler,
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
        return handler.size();
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y, FilterUtil filter) {
        return new MenuFilteredResourceHandlerSlot(handler, slotIndex, x, y, filter);
    }

    @Override
    public boolean stillValid(ServerPlayer player) {
        return validityChecker.test(player);
    }
}

package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;

/**
 * 服务端容器数据源统一抽象。
 */
public interface ContainerDataSource {
    ContainerBindType bindType();

    int capacity();

    Slot createSlot(int slotIndex, int x, int y);

    default boolean stillValid(ServerPlayer player) {
        return true;
    }

    default void onClose(ServerPlayer player) {
    }

    default boolean supportsResize() {
        return false;
    }

    default int resize(int newCapacity, OpenBindPlan.ResizePolicy policy) {
        return capacity();
    }
}

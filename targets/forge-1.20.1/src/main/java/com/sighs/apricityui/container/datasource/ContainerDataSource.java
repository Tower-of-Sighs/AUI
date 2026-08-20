package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;

/**
 * 服务端容器数据源统一抽象。
 */
public interface ContainerDataSource {
    ContainerBindType bindType();

    int capacity();

    default Slot createSlot(int slotIndex, int x, int y) {
        return createSlot(slotIndex, x, y, (FilterUtil) null);
    }

    default Slot createSlot(int slotIndex, int x, int y, FilterUtil filter) {
        return createSlot(slotIndex, x, y);
    }

    /**
     * 菜单在客户端确认 DOM 槽位后可增量更新过滤规则；默认数据源没有专用过滤视图。
     */
    default Slot createSlot(int slotIndex, int x, int y, java.util.function.Supplier<FilterUtil> filterSupplier) {
        return createSlot(slotIndex, x, y, filterSupplier == null ? null : filterSupplier.get());
    }

    default boolean stillValid(ServerPlayer player) {
        return true;
    }

    default void onClose(ServerPlayer player) {
    }

    default boolean supportsResize() {
        return false;
    }

    /**
     * 调整容量，直接截断。返回调整后的实际容量。
     */
    default int resize(int newCapacity) {
        return capacity();
    }
}

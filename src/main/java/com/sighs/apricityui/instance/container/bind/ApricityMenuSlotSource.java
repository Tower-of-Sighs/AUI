package com.sighs.apricityui.instance.container.bind;

import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;

/**
 * 菜单运行时槽位源。
 */
public interface ApricityMenuSlotSource {
    ContainerSchema.Descriptor.BindType bindType();

    int slotCount();

    Slot createSlot(int slotIndex, int x, int y);

    default boolean stillValid(ServerPlayer player) {
        return true;
    }

    default void onClose(ServerPlayer player) {
    }
}

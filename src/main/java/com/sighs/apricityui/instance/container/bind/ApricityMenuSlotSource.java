package com.sighs.apricityui.instance.container.bind;

import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Slot;

/**
 * 菜单运行时槽位源。
 */
public interface ApricityMenuSlotSource {
    ContainerSchema.Descriptor.BindType bindType();

    int slotCount();

    Slot createSlot(int slotIndex, int x, int y);

    default boolean stillValid(ServerPlayerEntity player) {
        return true;
    }

    default void onClose(ServerPlayerEntity player) {
    }
}

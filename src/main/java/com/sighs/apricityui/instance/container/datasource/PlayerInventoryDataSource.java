package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 玩家背包数据源。
 */
public final class PlayerInventoryDataSource implements ContainerDataSource {
    private final ServerPlayer owner;
    private final Inventory inventory;

    public PlayerInventoryDataSource(ServerPlayer owner) {
        this.owner = owner;
        this.inventory = owner.getInventory();
    }

    @Override
    public ContainerBindType bindType() {
        return ContainerBindType.PLAYER;
    }

    @Override
    public int capacity() {
        return ContainerBindType.PLAYER_SLOT_COUNT;
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y) {
        return new Slot(inventory, slotIndex, x, y);
    }

    @Override
    public boolean stillValid(ServerPlayer player) {
        return player != null && player == owner && player.isAlive();
    }
}

package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 实体物品槽数据源。
 * 通过 Forge IItemHandler capability 访问实体的物品存储。
 */
public final class EntityDataSource implements ContainerDataSource {
    private final Entity entity;
    private final IItemHandler itemHandler;
    private final int capacity;

    public EntityDataSource(Entity entity, IItemHandler itemHandler, int capacity) {
        this.entity = entity;
        this.itemHandler = itemHandler;
        this.capacity = Math.max(0, capacity);
    }

    @Override
    public ContainerBindType bindType() {
        return ContainerBindType.ENTITY;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y) {
        return new SlotItemHandler(itemHandler, slotIndex, x, y);
    }

    @Override
    public boolean stillValid(ServerPlayer player) {
        if (!entity.isAlive()) return false;
        return player.distanceToSqr(entity) <= 64.0;
    }

    /**
     * 从实体 ID 解析数据源。
     *
     * @param player   服务端玩家
     * @param entityId 实体的网络 ID
     * @param capacity 请求容量（实际容量取 handler 与请求的较小值）
     * @return 数据源实例，无法解析时返回 null
     */
    public static EntityDataSource resolve(ServerPlayer player, int entityId, int capacity) {
        if (player == null) return null;

        Entity entity = player.serverLevel().getEntity(entityId);
        if (entity == null) return null;

        IItemHandler handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElse(null);
        if (handler == null) return null;

        int handlerSlots = Math.max(0, handler.getSlots());
        int resolvedCapacity = capacity <= 0 ? handlerSlots : Math.min(Math.max(1, capacity), handlerSlots);
        return new EntityDataSource(entity, handler, resolvedCapacity);
    }
}

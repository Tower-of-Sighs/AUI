package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * 实体物品槽数据源。
 * 通过 Forge IItemHandler capability 访问实体的物品存储。
 */
@SuppressWarnings("removal")
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
    public Slot createSlot(int slotIndex, int x, int y, FilterUtil filter) {
        return new MenuFilteredSlotItemHandler(itemHandler, slotIndex, x, y, filter);
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

        Entity entity = player.level().getEntity(entityId);
        if (entity == null) return null;

        ResourceHandler<ItemResource> handler = entity.getCapability(Capabilities.Item.ENTITY);
        if (handler == null) return null;

        int handlerSlots = Math.max(0, handler.size());
        int resolvedCapacity = capacity <= 0 ? handlerSlots : Math.min(Math.max(1, capacity), handlerSlots);
        return new EntityDataSource(entity, IItemHandler.of(handler), resolvedCapacity);
    }
}

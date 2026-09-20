package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.storage.GenericStorage;
import com.sighs.apricityui.container.storage.GenericStorages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;

/**
 * 实体物品槽数据源。
 * 通过 Forge IItemHandler capability 访问实体的物品存储。
 */
@SuppressWarnings("removal")
public final class EntityDataSource implements ContainerDataSource {
    private final Entity entity;
    private final ResourceHandler<ItemResource> itemHandler;
    private final int capacity;

    public EntityDataSource(Entity entity, ResourceHandler<ItemResource> itemHandler, int capacity) {
        this.entity = entity;
        this.storage = storage;
    }

    @Override
    public ContainerBindType bindType() {
        return ContainerBindType.ENTITY;
    }

    @Override
    public int capacity() {
        return storage == null ? 0 : storage.size();
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y, FilterUtil filter) {
        return new MenuFilteredResourceHandlerSlot(itemHandler, slotIndex, x, y, filter);
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
        return resolve(player, entityId, capacity, "all", false);
    }

    public static EntityDataSource resolve(ServerPlayer player, int entityId, int capacity,
                                           String resourceType, boolean merge) {
        if (player == null) return null;

        Entity entity = player.level().getEntity(entityId);
        if (entity == null) return null;

        ResourceHandler<ItemResource> handler = entity.getCapability(Capabilities.Item.ENTITY);
        if (handler == null) return null;

        int handlerSlots = Math.max(0, handler.size());
        int resolvedCapacity = capacity <= 0 ? handlerSlots : Math.min(Math.max(1, capacity), handlerSlots);
        return new EntityDataSource(entity, handler, resolvedCapacity);
    }
}

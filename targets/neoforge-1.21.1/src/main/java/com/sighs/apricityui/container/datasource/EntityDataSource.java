package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.container.storage.GenericStorage;
import com.sighs.apricityui.container.storage.GenericStorages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;

/**
 * 实体物品槽数据源。
 * 通过 Forge IItemHandler capability 访问实体的物品存储。
 */
public final class EntityDataSource implements ContainerDataSource {
    private final Entity entity;
    private final GenericStorage storage;

    public EntityDataSource(Entity entity, IItemHandler itemHandler, int capacity) {
        this(entity, GenericStorages.view(GenericStorages.itemHandler(itemHandler), false, capacity));
    }

    private EntityDataSource(Entity entity, GenericStorage storage) {
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
    public GenericStorage genericStorage() {
        return storage;
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

        Entity entity = player.serverLevel().getEntity(entityId);
        if (entity == null) return null;

        ArrayList<GenericStorage> storages = new ArrayList<>(2);
        if (!"fluid".equals(resourceType)) {
            IItemHandler items = entity.getCapability(Capabilities.ItemHandler.ENTITY);
            if (items != null) storages.add(GenericStorages.itemHandler(items));
        }
        if (!"item".equals(resourceType)) {
            IFluidHandler fluids = entity.getCapability(Capabilities.FluidHandler.ENTITY, null);
            if (fluids != null) storages.add(GenericStorages.fluidHandler(fluids));
        }
        GenericStorage storage = GenericStorages.view(GenericStorages.combine(storages), merge, capacity);
        return storage == null ? null : new EntityDataSource(entity, storage);
    }
}

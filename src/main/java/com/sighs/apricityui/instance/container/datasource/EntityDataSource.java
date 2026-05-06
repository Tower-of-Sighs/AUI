package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.UUID;

/**
 * 实体物品槽数据源。
 * 通过 NeoForge IItemHandler capability 访问实体的物品存储。
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
        if (player == null || !entity.isAlive()) return false;
        return player.distanceToSqr(entity) <= 64.0;
    }

    /**
     * 从实体 UUID 解析数据源。
     *
     * @param player   服务端玩家
     * @param uuid     实体 UUID
     * @param capacity 请求容量；小于等于 0 时自动使用 handler 的完整容量
     * @return 数据源实例，无法解析时返回 null
     */
    public static EntityDataSource resolve(ServerPlayer player, UUID uuid, int capacity) {
        Entity entity = findEntityByUuid(player, uuid);
        if (!(entity instanceof LivingEntity livingEntity)) return null;

        IItemHandler handler = livingEntity.getCapability(Capabilities.ItemHandler.ENTITY);
        if (handler == null || handler.getSlots() <= 0) return null;

        int handlerSlots = Math.max(0, handler.getSlots());
        int resolvedCapacity = capacity <= 0 ? handlerSlots : Math.min(Math.max(1, capacity), handlerSlots);
        return new EntityDataSource(entity, handler, resolvedCapacity);
    }

    private static Entity findEntityByUuid(ServerPlayer player, UUID uuid) {
        if (player == null || player.server == null || uuid == null) return null;
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }
}

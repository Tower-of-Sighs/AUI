package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

public final class EntityDataSource implements ContainerDataSource {
    private final Entity entity;
    private final NeoForgeItemHandlerDataSource delegate;

    public EntityDataSource(Entity entity, ResourceHandler<ItemResource> itemHandler) {
        this.entity = entity;
        this.delegate = new NeoForgeItemHandlerDataSource(ContainerBindType.ENTITY, itemHandler, this::stillValid);
    }

    @Override
    public ContainerBindType bindType() {
        return ContainerBindType.ENTITY;
    }

    @Override
    public int capacity() {
        return delegate.capacity();
    }

    @Override
    public net.minecraft.world.inventory.Slot createSlot(int slotIndex, int x, int y) {
        return delegate.createSlot(slotIndex, x, y);
    }

    @Override
    public boolean stillValid(ServerPlayer player) {
        if (!entity.isAlive()) return false;
        return player.distanceToSqr(entity) <= 64.0;
    }

    public static EntityDataSource resolve(ServerPlayer player, int entityId, int capacity) {
        if (player == null) return null;

        Entity entity = player.level().getEntity(entityId);
        if (entity == null) return null;

        ResourceHandler<ItemResource> handler = Capabilities.Item.ENTITY.getCapability(entity, null);
        if (handler == null) return null;

        int handlerSlots = Math.max(0, handler.size());
        int resolvedCapacity = capacity <= 0 ? handlerSlots : Math.min(Math.max(1, capacity), handlerSlots);
        if (resolvedCapacity <= 0) return null;
        return new EntityDataSource(entity, handler);
    }
}

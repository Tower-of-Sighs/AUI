package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.container.storage.GenericStorage;
import com.sighs.apricityui.container.storage.GenericStorages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;

/** Item and fluid capability view of an entity. */
@SuppressWarnings("removal")
public final class EntityDataSource implements ContainerDataSource {
    private final Entity entity;
    private final ResourceHandler<ItemResource> itemHandler;
    private final GenericStorage storage;

    private EntityDataSource(Entity entity,
                             ResourceHandler<ItemResource> itemHandler,
                             GenericStorage storage) {
        this.entity = entity;
        this.itemHandler = itemHandler;
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
    public Slot createSlot(int slotIndex, int x, int y, FilterUtil filter) {
        return new MenuFilteredResourceHandlerSlot(itemHandler, slotIndex, x, y, filter);
    }

    @Override
    public boolean stillValid(ServerPlayer player) {
        if (!entity.isAlive()) return false;
        return player.distanceToSqr(entity) <= 64.0;
    }

    public static EntityDataSource resolve(ServerPlayer player, int entityId, int capacity) {
        return resolve(player, entityId, capacity, "all", false);
    }

    public static EntityDataSource resolve(ServerPlayer player, int entityId, int capacity,
                                           String resourceType, boolean merge) {
        if (player == null) return null;
        Entity entity = player.level().getEntity(entityId);
        if (entity == null) return null;

        ArrayList<GenericStorage> storages = new ArrayList<>(2);
        ResourceHandler<ItemResource> itemHandler = null;
        if (!"fluid".equals(resourceType)) {
            ResourceHandler<ItemResource> items = entity.getCapability(Capabilities.Item.ENTITY);
            if (items != null) {
                itemHandler = items;
                storages.add(GenericStorages.itemHandler(items));
            }
        }
        if (!"item".equals(resourceType)) {
            ResourceHandler<FluidResource> fluids = entity.getCapability(Capabilities.Fluid.ENTITY, null);
            if (fluids != null) storages.add(GenericStorages.fluidHandler(fluids));
        }
        GenericStorage storage = GenericStorages.view(GenericStorages.combine(storages), merge, capacity);
        return storage == null ? null : new EntityDataSource(entity, itemHandler, storage);
    }
}

package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.storage.GenericStorage;
import com.sighs.apricityui.container.storage.GenericStorages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;

/** Item and fluid capability view of an entity. */
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
        return entity.isAlive() && player.distanceToSqr(entity) <= 64.0;
    }

    public static EntityDataSource resolve(ServerPlayer player, int entityId, int capacity) {
        return resolve(player, entityId, capacity, "all", false);
    }

    public static EntityDataSource resolve(ServerPlayer player, int entityId, int capacity,
                                           String resourceType, boolean merge) {
        if (player == null) return null;
        Entity entity = player.serverLevel().getEntity(entityId);
        if (entity == null) return null;

        ArrayList<GenericStorage> adapters = new ArrayList<>(2);
        if (!"fluid".equals(resourceType)) {
            IItemHandler items = entity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (items != null) adapters.add(GenericStorages.itemHandler(items));
        }
        if (!"item".equals(resourceType)) {
            IFluidHandler fluids = entity.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
            if (fluids != null) adapters.add(GenericStorages.fluidHandler(fluids));
        }

        GenericStorage storage = GenericStorages.view(GenericStorages.combine(adapters), merge, capacity);
        return storage == null ? null : new EntityDataSource(entity, storage);
    }
}

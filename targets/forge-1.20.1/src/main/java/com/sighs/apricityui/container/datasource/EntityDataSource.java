package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.container.storage.GenericStorage;
import com.sighs.apricityui.container.storage.GenericStorages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;

/** Item and fluid capability view of an entity. */
public final class EntityDataSource implements ContainerDataSource {
    private final Entity entity;
    private final IItemHandler itemHandler;
    private final GenericStorage storage;

    public EntityDataSource(Entity entity, IItemHandler itemHandler, int capacity) {
        this(entity, itemHandler, GenericStorages.view(GenericStorages.itemHandler(itemHandler), false, capacity));
    }

    private EntityDataSource(Entity entity, IItemHandler itemHandler, GenericStorage storage) {
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
        return createSlot(slotIndex, x, y, () -> filter);
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y, java.util.function.Supplier<FilterUtil> filterSupplier) {
        return new FilterableSlotItemHandler(itemHandler, slotIndex, x, y, filterSupplier);
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
        IItemHandler itemHandler = null;
        if (!"fluid".equals(resourceType)) {
            IItemHandler items = entity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (items != null) {
                itemHandler = items;
                adapters.add(GenericStorages.itemHandler(items));
            }
        }
        if (!"item".equals(resourceType)) {
            IFluidHandler fluids = entity.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
            if (fluids != null) adapters.add(GenericStorages.fluidHandler(fluids));
        }

        GenericStorage storage = GenericStorages.view(GenericStorages.combine(adapters), merge, capacity);
        return storage == null ? null : new EntityDataSource(entity, itemHandler, storage);
    }
}

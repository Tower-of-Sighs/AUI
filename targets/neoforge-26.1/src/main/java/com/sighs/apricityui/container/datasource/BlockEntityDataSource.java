package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.container.storage.GenericStorage;
import com.sighs.apricityui.container.storage.GenericStorages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;

/** Item and fluid capability view of a block entity. */
@SuppressWarnings("removal")
public final class BlockEntityDataSource implements ContainerDataSource {
    private final BlockEntity blockEntity;
    private final ResourceHandler<ItemResource> itemHandler;
    private final GenericStorage storage;

    private BlockEntityDataSource(BlockEntity blockEntity,
                                  ResourceHandler<ItemResource> itemHandler,
                                  GenericStorage storage) {
        this.blockEntity = blockEntity;
        this.itemHandler = itemHandler;
        this.storage = storage;
    }

    /**
     * 从方块坐标解析数据源。
     *
     * @param player   服务端玩家
     * @param pos      方块坐标
     * @param capacity 请求容量；小于等于 0 时自动使用 handler 的完整容量
     * @return 数据源实例，无法解析时返回 null
     */
    public static BlockEntityDataSource resolve(ServerPlayer player, BlockPos pos, int capacity) {
        if (player == null || pos == null) return null;
        ServerLevel level = player.level();
        if (!level.isLoaded(pos)) return null;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;

        ResourceHandler<ItemResource> handler = level.getCapability(
                Capabilities.Item.BLOCK, pos, blockEntity.getBlockState(), blockEntity, Direction.UP);
        if (handler == null) return null;

        int handlerSlots = Math.max(0, handler.size());
        int resolvedCapacity = capacity <= 0 ? handlerSlots : Math.min(Math.max(1, capacity), handlerSlots);
        return new BlockEntityDataSource(blockEntity, handler, resolvedCapacity);
    }

    @Override
    public ContainerBindType bindType() {
        return ContainerBindType.BLOCK_ENTITY;
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
        if (blockEntity.isRemoved()) return false;
        BlockPos pos = blockEntity.getBlockPos();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    public static BlockEntityDataSource resolve(ServerPlayer player, BlockPos pos, int capacity) {
        return resolve(player, pos, capacity, "all", false);
    }

    public static BlockEntityDataSource resolve(ServerPlayer player, BlockPos pos, int capacity,
                                                String resourceType) {
        if (player == null || pos == null) return null;
        ServerLevel level = player.level();
        if (!level.isLoaded(pos)) return null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;

        ArrayList<GenericStorage> storages = new ArrayList<>(2);
        ResourceHandler<ItemResource> itemHandler = null;
        if (!"fluid".equals(resourceType)) {
            ResourceHandler<ItemResource> items = level.getCapability(
                    Capabilities.Item.BLOCK, pos, blockEntity.getBlockState(), blockEntity, Direction.UP);
            if (items == null) items = level.getCapability(
                    Capabilities.Item.BLOCK, pos, blockEntity.getBlockState(), blockEntity, null);
            if (items != null) {
                itemHandler = items;
                storages.add(GenericStorages.itemHandler(items));
            }
        }
        if (!"item".equals(resourceType)) {
            ResourceHandler<FluidResource> fluids = level.getCapability(
                    Capabilities.Fluid.BLOCK, pos, blockEntity.getBlockState(), blockEntity, Direction.UP);
            if (fluids == null) fluids = level.getCapability(
                    Capabilities.Fluid.BLOCK, pos, blockEntity.getBlockState(), blockEntity, null);
            if (fluids != null) storages.add(GenericStorages.fluidHandler(fluids));
        }
        GenericStorage storage = GenericStorages.view(GenericStorages.combine(storages), capacity);
        return storage == null ? null : new BlockEntityDataSource(blockEntity, itemHandler, storage);
    }
}

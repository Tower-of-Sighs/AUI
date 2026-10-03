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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;

/**
 * 方块实体物品槽数据源。
 * 通过 Forge IItemHandler capability 访问方块实体的物品存储。
 */
public final class BlockEntityDataSource implements ContainerDataSource {
    private final BlockEntity blockEntity;
    private final GenericStorage storage;

    public BlockEntityDataSource(BlockEntity blockEntity, IItemHandler itemHandler, int capacity) {
        this(blockEntity, GenericStorages.view(GenericStorages.itemHandler(itemHandler), false, capacity));
    }

    private BlockEntityDataSource(BlockEntity blockEntity, GenericStorage storage) {
        this.blockEntity = blockEntity;
        this.storage = storage;
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
    public boolean stillValid(ServerPlayer player) {
        if (blockEntity.isRemoved()) return false;
        BlockPos pos = blockEntity.getBlockPos();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    /**
     * 从方块坐标解析数据源。
     *
     * @param player 服务端玩家
     * @param pos    方块坐标
     * @param capacity 请求容量；小于等于 0 时自动使用 handler 的完整容量
     * @return 数据源实例，无法解析时返回 null
     */
    public static BlockEntityDataSource resolve(ServerPlayer player, BlockPos pos, int capacity) {
        return resolve(player, pos, capacity, "all", false);
    }

    public static BlockEntityDataSource resolve(ServerPlayer player, BlockPos pos, int capacity,
                                                String resourceType, boolean merge) {
        if (player == null || pos == null) return null;
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) return null;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;

        ArrayList<GenericStorage> storages = new ArrayList<>(2);
        if (!"fluid".equals(resourceType)) {
            IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos,
                    blockEntity.getBlockState(), blockEntity, Direction.UP);
            if (items == null) items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos,
                    blockEntity.getBlockState(), blockEntity, null);
            if (items != null) storages.add(GenericStorages.itemHandler(items));
        }
        if (!"item".equals(resourceType)) {
            IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos,
                    blockEntity.getBlockState(), blockEntity, Direction.UP);
            if (fluids == null) fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos,
                    blockEntity.getBlockState(), blockEntity, null);
            if (fluids != null) storages.add(GenericStorages.fluidHandler(fluids));
        }
        GenericStorage storage = GenericStorages.view(GenericStorages.combine(storages), merge, capacity);
        return storage == null ? null : new BlockEntityDataSource(blockEntity, storage);
    }
}

package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 方块实体物品槽数据源。
 * 通过 NeoForge IItemHandler capability 访问方块实体的物品存储。
 */
public final class BlockEntityDataSource implements ContainerDataSource {
    private final BlockEntity blockEntity;
    private final IItemHandler itemHandler;
    private final int capacity;

    public BlockEntityDataSource(BlockEntity blockEntity, IItemHandler itemHandler, int capacity) {
        this.blockEntity = blockEntity;
        this.itemHandler = itemHandler;
        this.capacity = Math.max(0, capacity);
    }

    @Override
    public ContainerBindType bindType() {
        return ContainerBindType.BLOCK_ENTITY;
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
        if (player == null || blockEntity.isRemoved()) return false;
        BlockPos pos = blockEntity.getBlockPos();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    /**
     * 从方块坐标解析数据源。
     *
     * @param player   服务端玩家
     * @param pos      方块坐标
     * @param side     可选朝向
     * @param capacity 请求容量（实际容量取 handler 与请求的较小值）
     * @return 数据源实例，无法解析时返回 null
     */
    public static BlockEntityDataSource resolve(ServerPlayer player, BlockPos pos, Direction side, int capacity) {
        if (player == null || pos == null) return null;
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) return null;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;

        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, blockEntity.getBlockState(), blockEntity, side);
        if (handler == null || handler.getSlots() <= 0) return null;

        int requestedCapacity = capacity <= 0 ? handler.getSlots() : capacity;
        int resolvedCapacity = Math.min(Math.max(1, requestedCapacity), handler.getSlots());
        return new BlockEntityDataSource(blockEntity, handler, resolvedCapacity);
    }

    public static BlockEntityDataSource resolve(ServerPlayer player, BlockPos pos, int capacity) {
        return resolve(player, pos, null, capacity);
    }
}

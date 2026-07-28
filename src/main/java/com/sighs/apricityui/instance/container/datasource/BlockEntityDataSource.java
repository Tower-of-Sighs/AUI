package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 方块实体物品槽数据源。
 * 支持 Forge {@link IItemHandler} capability，以及原版 {@link Container} 存储。
 */
public final class BlockEntityDataSource implements ContainerDataSource {
    private final BlockEntity blockEntity;
    private final IItemHandler itemHandler;
    private final Container container;
    private final int capacity;

    public BlockEntityDataSource(BlockEntity blockEntity, IItemHandler itemHandler, int capacity) {
        this(blockEntity, itemHandler, null, capacity);
    }

    public BlockEntityDataSource(BlockEntity blockEntity, Container container, int capacity) {
        this(blockEntity, null, container, capacity);
    }

    private BlockEntityDataSource(BlockEntity blockEntity, IItemHandler itemHandler, Container container, int capacity) {
        this.blockEntity = blockEntity;
        this.itemHandler = itemHandler;
        this.container = container;
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

    /**
     * 从方块坐标解析数据源。
     *
     * @param player 服务端玩家
     * @param pos    方块坐标
     * @param capacity 请求容量；小于等于 0 时自动使用存储的完整容量
     * @return 数据源实例，无法解析时返回 null
     */
    public static BlockEntityDataSource resolve(ServerPlayer player, BlockPos pos, int capacity) {
        if (player == null || pos == null) return null;
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) return null;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;

        IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElse(null);
        if (handler == null) {
            // 尝试无方向获取
            handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .orElse(null);
        }
        if (handler != null) {
            int handlerSlots = Math.max(0, handler.getSlots());
            int resolvedCapacity = capacity <= 0 ? handlerSlots : Math.min(Math.max(1, capacity), handlerSlots);
            return new BlockEntityDataSource(blockEntity, handler, resolvedCapacity);
        }

        Container container = resolveContainer(blockEntity);
        if (container == null) return null;

        int containerSlots = Math.max(0, container.getContainerSize());
        int resolvedCapacity = capacity <= 0 ? containerSlots : Math.min(Math.max(1, capacity), containerSlots);
        return new BlockEntityDataSource(blockEntity, container, resolvedCapacity);
    }

    @Override
    public boolean stillValid(ServerPlayer player) {
        if (blockEntity.isRemoved()) return false;
        BlockPos pos = blockEntity.getBlockPos();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    private static Container resolveContainer(BlockEntity blockEntity) {
        if (blockEntity instanceof Container container) return container;

        try {
            Object inventory = blockEntity.getClass().getField("inventory").get(blockEntity);
            return inventory instanceof Container container ? container : null;
        } catch (NoSuchFieldException | IllegalAccessException | SecurityException ignored) {
            return null;
        }
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y) {
        return itemHandler != null
                ? new SlotItemHandler(itemHandler, slotIndex, x, y)
                : new Slot(container, slotIndex, x, y);
    }
}

package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * 方块实体物品槽数据源。
 * 通过 Forge IItemHandler capability 访问方块实体的物品存储。
 */
public final class BlockEntityDataSource implements ContainerDataSource {
    private final BlockEntity blockEntity;
    private final IItemHandler itemHandler;
    private final Container container;
    private final int capacity;

    public BlockEntityDataSource(BlockEntity blockEntity, IItemHandler itemHandler, int capacity) {
        this(blockEntity, itemHandler, null, capacity);
    }

    private BlockEntityDataSource(BlockEntity blockEntity,
                                  IItemHandler itemHandler,
                                  Container container,
                                  int capacity) {
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

    @Override
    public Slot createSlot(int slotIndex, int x, int y, FilterUtil filter) {
        return createSlot(slotIndex, x, y, () -> filter);
    }

    @Override
    public Slot createSlot(int slotIndex, int x, int y, java.util.function.Supplier<FilterUtil> filterSupplier) {
        return itemHandler != null
                ? new FilterableSlotItemHandler(itemHandler, slotIndex, x, y, filterSupplier)
                : new Slot(FilteredContainer.of(container, filterSupplier), slotIndex, x, y);
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
        if (player == null || pos == null) return null;
        ServerLevel level = player.getLevel();
        if (!level.isLoaded(pos)) return null;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;

        // 1.18.2 还没有 ForgeCapabilities（1.19.3 才加），物品能力直接取 CapabilityItemHandler。
        IItemHandler handler = blockEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, Direction.UP)
                .orElse(null);
        if (handler == null) {
            // 尝试无方向获取
            handler = blockEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
                    .orElse(null);
        }
        if (handler != null) {
            int handlerSlots = Math.max(0, handler.getSlots());
            int resolvedCapacity = capacity <= 0 ? handlerSlots : Math.min(Math.max(1, capacity), handlerSlots);
            return new BlockEntityDataSource(blockEntity, handler, resolvedCapacity);
        }

        // 这里没有 1.20.1/1.19.2 那条 KubeJS 兜底分支：KubeJS 1.18.2 还没有
        // BlockEntityJS / InventoryKJS（1.19 才引入脚本方块实体与它的库存抽象），
        // 脚本方块在 1.18.2 也无法持有库存，所以通用 IItemHandler capability 就是全部来源。
        return null;
    }
}

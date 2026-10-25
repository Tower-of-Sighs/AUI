package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import dev.latvian.mods.kubejs.block.entity.BlockEntityJS;
import dev.latvian.mods.kubejs.core.InventoryKJS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
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

        if (blockEntity instanceof BlockEntityJS kubeBlockEntity && kubeBlockEntity.inventory != null) {
            // 1.19.2 的 KubeJS InventoryKJS 还没有 kjs$asContainer()（1.20 才补上），
            // 用它的逐槽 API 包一层 Container；包装对象不会为 null，故不再做空判。
            Container container = new InventoryKJSContainer(kubeBlockEntity.inventory);
            int containerSlots = Math.max(0, container.getContainerSize());
            int resolvedCapacity = capacity <= 0
                    ? containerSlots
                    : Math.min(Math.max(1, capacity), containerSlots);
            return new BlockEntityDataSource(blockEntity, null, container, resolvedCapacity);
        }

        return null;
    }

    /**
     * 把 KubeJS 的 {@link InventoryKJS} 适配成 {@link Container}。
     *
     * <p>1.19.2 的 KubeJS 只在 {@code InventoryKJS} 上提供逐槽读写（{@code kjs$getSlots} /
     * {@code kjs$getStackInSlot} / {@code kjs$setStackInSlot} / {@code kjs$extractItem} /
     * {@code kjs$isItemValid}），没有 1.20 那个 {@code kjs$asContainer()} 便捷转换。</p>
     */
    private record InventoryKJSContainer(InventoryKJS inventory) implements Container {
        @Override
        public int getContainerSize() {
            return inventory.kjs$getSlots();
        }

        @Override
        public boolean isEmpty() {
            return inventory.kjs$isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return inventory.kjs$getStackInSlot(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return inventory.kjs$extractItem(slot, amount, false);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = inventory.kjs$getStackInSlot(slot);
            if (!stack.isEmpty()) inventory.kjs$setStackInSlot(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            inventory.kjs$setStackInSlot(slot, stack);
        }

        @Override
        public void setChanged() {
            inventory.kjs$setChanged();
        }

        @Override
        public void clearContent() {
            inventory.kjs$clear();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return inventory.kjs$isItemValid(slot, stack);
        }
    }
}

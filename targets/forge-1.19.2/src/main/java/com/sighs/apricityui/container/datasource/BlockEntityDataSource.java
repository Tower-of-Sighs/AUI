package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.container.storage.GenericStorage;
import com.sighs.apricityui.container.storage.GenericStorages;
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
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;

/**
 * 方块实体物品槽数据源。
 * 通过 Forge IItemHandler capability 访问方块实体的物品存储。
 */
public final class BlockEntityDataSource implements ContainerDataSource {
    private final BlockEntity blockEntity;
    private final IItemHandler itemHandler;
    private final Container container;
    private final GenericStorage storage;

    public BlockEntityDataSource(BlockEntity blockEntity, IItemHandler itemHandler, int capacity) {
        this(blockEntity, itemHandler, null, GenericStorages.view(GenericStorages.itemHandler(itemHandler), capacity));
    }

    private BlockEntityDataSource(BlockEntity blockEntity,
                                  IItemHandler itemHandler,
                                  Container container,
                                  GenericStorage storage) {
        this.blockEntity = blockEntity;
        this.itemHandler = itemHandler;
        this.container = container;
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
    public GenericStorage genericStorage() { return storage; }

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
        return resolve(player, pos, capacity, "all");
    }

    public static BlockEntityDataSource resolve(ServerPlayer player, BlockPos pos, int capacity, String resourceType) {
        if (player == null || pos == null) return null;
        ServerLevel level = player.getLevel();
        if (!level.isLoaded(pos)) return null;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;

        ArrayList<GenericStorage> adapters = new ArrayList<>(2);
        IItemHandler itemHandler = null;
        Container container = null;
        if (!"fluid".equals(resourceType)) {
            itemHandler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
            if (itemHandler == null) itemHandler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (itemHandler != null) adapters.add(GenericStorages.itemHandler(itemHandler));
            else if (blockEntity instanceof BlockEntityJS kube && kube.inventory != null) {
                container = new InventoryKJSContainer(kube.inventory);
                adapters.add(GenericStorages.container(container));
            }
        }
        if (!"item".equals(resourceType)) {
            IFluidHandler fluids = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElse(null);
            if (fluids == null) fluids = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
            if (fluids != null) adapters.add(GenericStorages.fluidHandler(fluids));
        }
        GenericStorage storage = GenericStorages.view(GenericStorages.combine(adapters), capacity);
        return storage == null ? null : new BlockEntityDataSource(blockEntity, itemHandler, container, storage);
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

package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.storage.GenericStorage;
import com.sighs.apricityui.container.storage.GenericStorages;
import dev.latvian.mods.kubejs.block.entity.BlockEntityJS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;

/** Item and fluid capability view of a block entity. */
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

        ArrayList<GenericStorage> adapters = new ArrayList<>(2);
        if (!"fluid".equals(resourceType)) {
            IItemHandler items = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
            if (items == null) items = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (items != null) {
                adapters.add(GenericStorages.itemHandler(items));
            } else if (blockEntity instanceof BlockEntityJS kube && kube.inventory != null) {
                Container container = kube.inventory.kjs$asContainer();
                if (container != null) adapters.add(GenericStorages.container(container));
            }
        }
        if (!"item".equals(resourceType)) {
            IFluidHandler fluids = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElse(null);
            if (fluids == null) fluids = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
            if (fluids != null) adapters.add(GenericStorages.fluidHandler(fluids));
        }

        GenericStorage storage = GenericStorages.view(GenericStorages.combine(adapters), merge, capacity);
        return storage == null ? null : new BlockEntityDataSource(blockEntity, storage);
    }
}

package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

public final class BlockEntityDataSource implements ContainerDataSource {
    private final BlockEntity blockEntity;
    private final NeoForgeItemHandlerDataSource delegate;

    public BlockEntityDataSource(BlockEntity blockEntity, ResourceHandler<ItemResource> itemHandler) {
        this.blockEntity = blockEntity;
        this.delegate = new NeoForgeItemHandlerDataSource(ContainerBindType.BLOCK_ENTITY, itemHandler, this::stillValid);
    }

    @Override
    public ContainerBindType bindType() {
        return ContainerBindType.BLOCK_ENTITY;
    }

    @Override
    public int capacity() {
        return delegate.capacity();
    }

    @Override
    public net.minecraft.world.inventory.Slot createSlot(int slotIndex, int x, int y) {
        return delegate.createSlot(slotIndex, x, y);
    }

    @Override
    public boolean stillValid(ServerPlayer player) {
        if (blockEntity.isRemoved()) return false;
        BlockPos pos = blockEntity.getBlockPos();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    public static BlockEntityDataSource resolve(ServerPlayer player, BlockPos pos, int capacity) {
        if (player == null || pos == null) return null;
        ServerLevel level = player.level();
        if (!level.isLoaded(pos)) return null;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;

        ResourceHandler<ItemResource> handler = Capabilities.Item.BLOCK.getCapability(
                level,
                pos,
                blockEntity.getBlockState(),
                blockEntity,
                Direction.UP
        );
        if (handler == null) {
            handler = Capabilities.Item.BLOCK.getCapability(level, pos, blockEntity.getBlockState(), blockEntity, null);
        }
        if (handler == null) return null;

        int handlerSlots = Math.max(0, handler.size());
        int resolvedCapacity = capacity <= 0 ? handlerSlots : Math.min(Math.max(1, capacity), handlerSlots);
        if (resolvedCapacity <= 0) return null;
        return new BlockEntityDataSource(blockEntity, handler);
    }
}

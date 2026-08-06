package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.config.ApricitySavedData;
import com.sighs.apricityui.container.bind.ContainerBindType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;

public final class DataSourceFactory {
    private DataSourceFactory() { }
    public static ContainerDataSource resolve(ServerPlayer player, String containerId, ContainerBindType bindType, Map<String, String> args, int capacity) {
        if (player == null || bindType == null || bindType == ContainerBindType.PLAYER) return null;
        return switch (bindType) {
            case SAVED_DATA -> {
                if (player.getServer() == null) yield null;
                String name = arg(args, "data_name", "apricityui_data");
                String key = containerId == null || containerId.isBlank() ? "__default__" : containerId;
                ApricitySavedData saved = ApricitySavedData.get(player.getServer(), name);
                yield new SavedDataDataSource(bindType, saved, key, saved.getOrCreate(key, Math.max(1, capacity)));
            }
            case BLOCK_ENTITY -> {
                BlockPos pos = new BlockPos(integer(args, "x"), integer(args, "y"), integer(args, "z"));
                BlockEntity entity = player.serverLevel().getBlockEntity(pos);
                if (!(entity instanceof Container container)) yield null;
                int slots = resolveCapacity(container.getContainerSize(), capacity);
                yield new FabricContainerDataSource(bindType, container, p -> !entity.isRemoved() && p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64);
            }
            case ENTITY -> {
                Entity entity = player.serverLevel().getEntity(integer(args, "entity_id"));
                if (!(entity instanceof Container container)) yield null;
                yield new FabricContainerDataSource(bindType, container, p -> entity.isAlive() && p.distanceToSqr(entity) <= 64);
            }
            default -> null;
        };
    }
    private static int resolveCapacity(int actual, int requested) { return requested <= 0 ? actual : Math.min(Math.max(1, requested), actual); }
    private static int integer(Map<String, String> args, String key) { try { return Integer.parseInt(args.getOrDefault(key, "0")); } catch (NumberFormatException e) { return 0; } }
    private static String arg(Map<String, String> args, String key, String fallback) { String value = args == null ? null : args.get(key); return value == null || value.isBlank() ? fallback : value.trim(); }
}

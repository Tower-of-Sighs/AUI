package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.config.ApricitySavedData;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.storage.GenericStorage;
import com.sighs.apricityui.container.storage.GenericStorages;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Predicate;

public final class DataSourceFactory {
    private DataSourceFactory() {
    }

    public static ContainerDataSource resolve(ServerPlayer player, String containerId, ContainerBindType bindType,
                                              Map<String, String> args, int capacity) {
        return resolve(player, containerId, bindType, args, capacity, "all", false);
    }

    public static ContainerDataSource resolve(ServerPlayer player, String containerId, ContainerBindType bindType,
                                              Map<String, String> args, int capacity,
                                              String resourceType, boolean merge) {
        if (player == null || bindType == null || bindType == ContainerBindType.PLAYER) return null;
        String type = normalizeResourceType(resourceType);
        return switch (bindType) {
            case SAVED_DATA -> {
                if ("fluid".equals(type) || player.getServer() == null) yield null;
                String name = arg(args, "data_name", "apricityui_data");
                String key = containerId == null || containerId.isBlank() ? "__default__" : containerId;
                ApricitySavedData saved = ApricitySavedData.get(player.getServer(), name);
                yield new SavedDataDataSource(bindType, saved, key,
                        saved.getOrCreate(key, Math.max(1, capacity)), merge);
            }
            case BLOCK_ENTITY -> resolveBlockEntity(player, args, capacity, type, merge);
            case ENTITY -> resolveEntity(player, args, capacity, type, merge);
            default -> null;
        };
    }

    private static ContainerDataSource resolveBlockEntity(ServerPlayer player, Map<String, String> args,
                                                          int capacity, String type, boolean merge) {
        BlockPos pos = new BlockPos(integer(args, "x"), integer(args, "y"), integer(args, "z"));
        if (!player.serverLevel().isLoaded(pos)) return null;
        BlockEntity blockEntity = player.serverLevel().getBlockEntity(pos);
        if (blockEntity == null) return null;

        ArrayList<GenericStorage> storages = new ArrayList<>(2);
        if (!"fluid".equals(type)) {
            var items = ItemStorage.SIDED.find(player.serverLevel(), pos, blockEntity.getBlockState(), blockEntity, Direction.UP);
            if (items instanceof SlottedStorage<?> slotted) {
                @SuppressWarnings("unchecked") SlottedStorage<ItemVariant> itemStorage = (SlottedStorage<ItemVariant>) slotted;
                storages.add(GenericStorages.itemStorage(itemStorage));
            } else if (blockEntity instanceof Container container) {
                storages.add(GenericStorages.container(container));
            }
        }
        if (!"item".equals(type)) {
            var fluids = FluidStorage.SIDED.find(player.serverLevel(), pos, blockEntity.getBlockState(), blockEntity, Direction.UP);
            if (fluids != null) storages.add(GenericStorages.fluidStorage(fluids));
        }
        GenericStorage storage = GenericStorages.view(GenericStorages.combine(storages), merge, capacity);
        Predicate<ServerPlayer> validity = p -> !blockEntity.isRemoved()
                && p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
        return storage == null ? null : new FabricGenericDataSource(ContainerBindType.BLOCK_ENTITY, storage, validity);
    }

    private static ContainerDataSource resolveEntity(ServerPlayer player, Map<String, String> args,
                                                      int capacity, String type, boolean merge) {
        Entity entity = player.serverLevel().getEntity(integer(args, "entity_id"));
        if ("fluid".equals(type) || !(entity instanceof Container container)) return null;
        GenericStorage storage = GenericStorages.view(GenericStorages.container(container), merge, capacity);
        return new FabricGenericDataSource(ContainerBindType.ENTITY, storage,
                p -> entity.isAlive() && p.distanceToSqr(entity) <= 64);
    }

    private static int integer(Map<String, String> args, String key) {
        try { return Integer.parseInt(args == null ? "0" : args.getOrDefault(key, "0")); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static String arg(Map<String, String> args, String key, String fallback) {
        String value = args == null ? null : args.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeResourceType(String raw) {
        if (raw == null) return "all";
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return "item".equals(normalized) || "fluid".equals(normalized) ? normalized : "all";
    }
}

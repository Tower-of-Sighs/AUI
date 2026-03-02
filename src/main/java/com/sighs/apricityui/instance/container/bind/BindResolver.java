package com.sighs.apricityui.instance.container.bind;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.container.datasource.ContainerDataSource;
import com.sighs.apricityui.instance.container.datasource.ForgeItemHandlerDataSource;
import com.sighs.apricityui.instance.container.datasource.PlayerInventoryDataSource;
import com.sighs.apricityui.instance.container.datasource.SavedDataDataSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 新绑定解析器：输出统一数据源抽象。
 */
public final class BindResolver {
    public static boolean has(ContainerBindType bindType) {
        return bindType != null && bindType != ContainerBindType.VIRTUAL_UI;
    }

    public static BindResult resolve(BindRequest request) {
        if (request == null) {
            return BindResult.failure(BindingFailureReason.INVALID_REQUEST, "request is null");
        }
        if (request.player() == null) {
            return BindResult.failure(BindingFailureReason.INVALID_REQUEST, "player is required");
        }
        if (request.bindType() == null) {
            return BindResult.failure(BindingFailureReason.INVALID_REQUEST, "bindType is required");
        }
        if (!has(request.bindType())) {
            return BindResult.failure(
                    BindingFailureReason.UNSUPPORTED_BIND_TYPE,
                    "unsupported bind type: " + request.bindType().id()
            );
        }

        try {
            ContainerDataSource dataSource = switch (request.bindType()) {
                case PLAYER -> new PlayerInventoryDataSource(request.player());
                case SAVED_DATA -> resolveSavedData(request);
                case BLOCK_ENTITY -> resolveBlockEntity(request);
                case ENTITY -> resolveEntity(request);
                case VIRTUAL_UI -> null;
            };
            if (dataSource == null) {
                return BindResult.failure(
                        BindingFailureReason.TARGET_NOT_FOUND,
                        "target not found or missing capability for bind type: " + request.bindType().id()
                );
            }
            return ensureCapacity(request, dataSource);
        } catch (Exception exception) {
            ApricityUI.LOGGER.warn(
                    "Bind resolve failed: container={} bindType={} reason={}",
                    request.containerId(),
                    request.bindType().id(),
                    exception.getMessage()
            );
            return BindResult.failure(BindingFailureReason.INTERNAL_ERROR, exception.getMessage());
        }
    }

    private static BindResult ensureCapacity(BindRequest request, ContainerDataSource dataSource) {
        int required = Math.max(0, request.requiredCapacity());
        if (required <= 0) return BindResult.success(dataSource);

        int capacity = dataSource.capacity();
        if (capacity >= required) {
            return BindResult.success(dataSource);
        }

        if (dataSource.supportsResize()) {
            int resizedCapacity = dataSource.resize(required, request.resizePolicy());
            if (resizedCapacity >= required) {
                return BindResult.success(dataSource);
            }
            return BindResult.failure(
                    BindingFailureReason.INSUFFICIENT_CAPACITY,
                    "resize failed: required=" + required + ", actual=" + resizedCapacity
            );
        }

        return BindResult.failure(
                BindingFailureReason.INSUFFICIENT_CAPACITY,
                "required=" + required + ", actual=" + capacity
        );
    }

    private static ContainerDataSource resolveSavedData(BindRequest request) {
        Map<String, String> args = request.args();
        String dataName = normalizeText(args.get("dataName"), "apricityui_saved");
        String inventoryKey = normalizeText(args.get("inventoryKey"), request.player().getStringUUID());
        int declaredSlotCount = parsePositiveInt(args.get("slotCount"), Math.max(1, request.requiredCapacity()));
        int initialCapacity = Math.max(1, Math.max(declaredSlotCount, request.requiredCapacity()));

        ApricitySavedData savedData = ApricitySavedData.get(request.player().server, dataName);
        ItemStackHandler handler = savedData.getOrCreate(inventoryKey, initialCapacity, request.resizePolicy());
        return new SavedDataDataSource(request.bindType(), savedData, inventoryKey, handler);
    }

    private static ContainerDataSource resolveBlockEntity(BindRequest request) {
        Map<String, String> args = request.args();
        Integer x = parseRequiredInt(args, "x");
        Integer y = parseRequiredInt(args, "y");
        Integer z = parseRequiredInt(args, "z");
        if (x == null || y == null || z == null) {
            return null;
        }

        Direction side = parseDirection(args.get("side"));
        BlockPos pos = new BlockPos(x, y, z);
        ServerLevel level = request.player().serverLevel();
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }

        IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
        if (handler == null || handler.getSlots() <= 0) {
            return null;
        }

        Class<?> expectedType = blockEntity.getClass();
        BlockPos immutablePos = pos.immutable();
        return new ForgeItemHandlerDataSource(
                request.bindType(),
                handler,
                player -> {
                    if (player == null) return false;
                    ServerLevel currentLevel = player.serverLevel();
                    if (!currentLevel.hasChunkAt(immutablePos)) return false;
                    BlockEntity current = currentLevel.getBlockEntity(immutablePos);
                    return current != null && expectedType.isInstance(current);
                }
        );
    }

    private static ContainerDataSource resolveEntity(BindRequest request) {
        UUID uuid = parseRequiredUuid(request.args(), "uuid");
        if (uuid == null) return null;

        Entity target = findEntityByUuid(request.player(), uuid);
        if (!(target instanceof LivingEntity livingEntity)) {
            return null;
        }
        IItemHandler handler = livingEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null || handler.getSlots() <= 0) {
            return null;
        }

        Class<?> expectedType = livingEntity.getClass();
        return new ForgeItemHandlerDataSource(
                request.bindType(),
                handler,
                player -> {
                    Entity current = findEntityByUuid(player, uuid);
                    return current != null && expectedType.isInstance(current);
                }
        );
    }

    private static Entity findEntityByUuid(ServerPlayer player, UUID uuid) {
        if (player == null || player.server == null || uuid == null) return null;
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static String normalizeText(String raw, String fallback) {
        if (raw == null) return fallback;
        String normalized = raw.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static int parsePositiveInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return Math.max(1, fallback);
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : Math.max(1, fallback);
        } catch (NumberFormatException ignored) {
            return Math.max(1, fallback);
        }
    }

    private static Integer parseRequiredInt(Map<String, String> args, String key) {
        String raw = args.get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static UUID parseRequiredUuid(Map<String, String> args, String key) {
        String raw = args.get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Direction parseDirection(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Direction.byName(raw.trim().toLowerCase(Locale.ROOT));
    }
}

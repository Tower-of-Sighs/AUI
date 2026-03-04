package com.sighs.apricityui.instance.element;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.ApricitySavedData;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.container.datasource.ContainerDataSource;
import com.sighs.apricityui.instance.container.datasource.ForgeItemHandlerDataSource;
import com.sighs.apricityui.instance.container.datasource.PlayerInventoryDataSource;
import com.sighs.apricityui.instance.container.datasource.SavedDataDataSource;
import com.sighs.apricityui.registry.annotation.ElementRegister;
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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@ElementRegister(Container.TAG_NAME)
public class Container extends MinecraftElement {
    public static final String TAG_NAME = "CONTAINER";

    public Container(Document document) {
        super(document, TAG_NAME);
    }

    public int resolveSlotSizePx(int fallback) {
        int safeFallback = Math.max(1, fallback);
        String rawSlotSize = getAttribute("slot-size");
        int parsedSize = com.sighs.apricityui.style.Size.parse(rawSlotSize);
        return parsedSize > 0 ? parsedSize : safeFallback;
    }

    public static boolean hasBindingDataSource(ContainerBindType bindType) {
        return bindType != null && bindType != ContainerBindType.VIRTUAL_UI;
    }

    public static ContainerDataSource resolveBinding(
            ServerPlayer player,
            String containerId,
            ContainerBindType bindType,
            Map<String, String> args,
            int requiredCapacity,
            OpenBindPlan.ResizePolicy resizePolicy
    ) {
        if (player == null) {
            return null;
        }
        if (bindType == null) {
            return null;
        }
        if (!hasBindingDataSource(bindType)) {
            return null;
        }

        String normalizedContainerId = containerId == null ? "" : containerId.trim();
        int normalizedRequiredCapacity = Math.max(0, requiredCapacity);
        OpenBindPlan.ResizePolicy normalizedResizePolicy = resizePolicy == null
                ? OpenBindPlan.ResizePolicy.KEEP_OVERFLOW
                : resizePolicy;

        LinkedHashMap<String, String> normalizedArgs = new LinkedHashMap<>();
        if (args != null) {
            args.forEach((key, value) -> {
                if (key == null) return;
                String normalizedKey = key.trim();
                if (normalizedKey.isEmpty()) return;
                normalizedArgs.put(normalizedKey, value == null ? "" : value);
            });
        }
        Map<String, String> safeArgs = Map.copyOf(normalizedArgs);

        try {
            ContainerDataSource dataSource = switch (bindType) {
                case PLAYER -> new PlayerInventoryDataSource(player);
                case SAVED_DATA -> resolveSavedData(
                        player,
                        bindType,
                        safeArgs,
                        normalizedRequiredCapacity,
                        normalizedResizePolicy
                );
                case BLOCK_ENTITY -> resolveBlockEntity(player, bindType, safeArgs);
                case ENTITY -> resolveEntity(player, bindType, safeArgs);
                case VIRTUAL_UI -> null;
            };
            if (dataSource == null) {
                return null;
            }
            return ensureCapacity(
                    normalizedContainerId,
                    bindType,
                    normalizedRequiredCapacity,
                    normalizedResizePolicy,
                    dataSource
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private static ContainerDataSource ensureCapacity(
            String containerId,
            ContainerBindType bindType,
            int requiredCapacity,
            OpenBindPlan.ResizePolicy resizePolicy,
            ContainerDataSource dataSource
    ) {
        if (requiredCapacity <= 0) return dataSource;

        int capacity = dataSource.capacity();
        if (capacity >= requiredCapacity) {
            return dataSource;
        }

        if (dataSource.supportsResize()) {
            int resizedCapacity = dataSource.resize(requiredCapacity, resizePolicy);
            if (resizedCapacity >= requiredCapacity) {
                return dataSource;
            }
            ApricityUI.LOGGER.warn(
                    "Bind resolve failed: container={} bindType={} reason={} detail={}",
                    containerId,
                    bindType.id(),
                    "INSUFFICIENT_CAPACITY",
                    "resize failed: required=" + requiredCapacity + ", actual=" + resizedCapacity
            );
            return null;
        }

        ApricityUI.LOGGER.warn(
                "Bind resolve failed: container={} bindType={} reason={} detail={}",
                containerId,
                bindType.id(),
                "INSUFFICIENT_CAPACITY",
                "required=" + requiredCapacity + ", actual=" + capacity
        );
        return null;
    }

    private static ContainerDataSource resolveSavedData(
            ServerPlayer player,
            ContainerBindType bindType,
            Map<String, String> args,
            int requiredCapacity,
            OpenBindPlan.ResizePolicy resizePolicy
    ) {
        String dataName = normalizeText(args.get("dataName"), "apricityui_saved");
        String inventoryKey = normalizeText(args.get("inventoryKey"), player.getStringUUID());
        int declaredSlotCount = parsePositiveInt(args.get("slotCount"), Math.max(1, requiredCapacity));
        int initialCapacity = Math.max(1, Math.max(declaredSlotCount, requiredCapacity));

        ApricitySavedData savedData = ApricitySavedData.get(player.server, dataName);
        ItemStackHandler handler = savedData.getOrCreate(inventoryKey, initialCapacity, resizePolicy);
        return new SavedDataDataSource(bindType, savedData, inventoryKey, handler);
    }

    private static ContainerDataSource resolveBlockEntity(
            ServerPlayer player,
            ContainerBindType bindType,
            Map<String, String> args
    ) {
        Integer x = parseRequiredInt(args, "x");
        Integer y = parseRequiredInt(args, "y");
        Integer z = parseRequiredInt(args, "z");
        if (x == null || y == null || z == null) {
            return null;
        }

        Direction side = parseDirection(args.get("side"));
        BlockPos pos = new BlockPos(x, y, z);
        ServerLevel level = player.serverLevel();
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
                bindType,
                handler,
                currentPlayer -> {
                    if (currentPlayer == null) return false;
                    ServerLevel currentLevel = currentPlayer.serverLevel();
                    if (!currentLevel.hasChunkAt(immutablePos)) return false;
                    BlockEntity current = currentLevel.getBlockEntity(immutablePos);
                    return current != null && expectedType.isInstance(current);
                }
        );
    }

    private static ContainerDataSource resolveEntity(
            ServerPlayer player,
            ContainerBindType bindType,
            Map<String, String> args
    ) {
        UUID uuid = parseRequiredUuid(args, "uuid");
        if (uuid == null) return null;

        Entity target = findEntityByUuid(player, uuid);
        if (!(target instanceof LivingEntity livingEntity)) {
            return null;
        }
        IItemHandler handler = livingEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null || handler.getSlots() <= 0) {
            return null;
        }

        Class<?> expectedType = livingEntity.getClass();
        return new ForgeItemHandlerDataSource(
                bindType,
                handler,
                currentPlayer -> {
                    Entity current = findEntityByUuid(currentPlayer, uuid);
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

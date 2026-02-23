package com.sighs.apricityui.instance.container.bind;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import com.sighs.apricityui.util.StringUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 数据源解析
 */
public final class ApricityDataSourceResolver {
    public static boolean has(ContainerSchema.Descriptor.BindType bindType) {
        return bindType != null && bindType != ContainerSchema.Descriptor.BindType.VIRTUAL_UI;
    }

    public static ApricityMenuSlotSource resolve(
            ServerPlayerEntity player,
            ContainerSchema.Descriptor.BindType bindType,
            Map<String, String> args,
            int requiredSlotCount
    ) {
        if (player == null) {
            ApricityUI.LOGGER.warn("Data source resolve failed: player is required");
            return null;
        }
        if (bindType == null) {
            ApricityUI.LOGGER.warn("Data source resolve failed: bindType is null");
            return null;
        }
        if (bindType == ContainerSchema.Descriptor.BindType.PLAYER) return null;

        Map<String, String> safeArgs = args == null ? new HashMap<>() : args;
        ApricityMenuSlotSource source = resolveBuiltin(player, bindType, safeArgs, requiredSlotCount);
        if (source == null) {
            ApricityUI.LOGGER.warn("No data source found for bindType: {}", bindType.id());
            return null;
        }
        if (source.slotCount() < requiredSlotCount) {
            ApricityUI.LOGGER.warn("Insufficient data source slots, bindType={} / required={} / actual={}",
                    bindType.id(), requiredSlotCount, source.slotCount());
            return null;
        }
        return source;
    }

    private static ApricityMenuSlotSource resolveBuiltin(
            ServerPlayerEntity player,
            ContainerSchema.Descriptor.BindType bindType,
            Map<String, String> args,
            int requiredSlotCount
    ) {
        if (bindType == ContainerSchema.Descriptor.BindType.SAVED_DATA) {
            String dataName = normalizeText(args.get("dataName"), "apricityui_saved");
            String inventoryKey = normalizeText(args.get("inventoryKey"), player.getStringUUID());
            int slotCount = parsePositiveInt(args.get("slotCount"), Math.max(1, requiredSlotCount));
            return createSavedSource(bindType, player, dataName, inventoryKey, slotCount);
        }
        if (bindType == ContainerSchema.Descriptor.BindType.BLOCK_ENTITY) {
            Integer x = parseRequiredInt(args, "x");
            Integer y = parseRequiredInt(args, "y");
            Integer z = parseRequiredInt(args, "z");
            if (x == null || y == null || z == null) return null;
            Direction side = parseDirection(args.get("side"));
            return createBlockEntitySource(bindType, player, new BlockPos(x, y, z), side);
        }
        if (bindType == ContainerSchema.Descriptor.BindType.ENTITY) {
            UUID uuid = parseRequiredUuid(args, "uuid");
            if (uuid == null) return null;
            return createEntitySource(bindType, player, uuid);
        }
        ApricityUI.LOGGER.warn("Unsupported bindType: {}", bindType.id());
        return null;
    }

    private static String normalizeText(String raw, String fallback) {
        if (raw == null) return fallback;
        String normalized = raw.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static int parsePositiveInt(String raw, int fallback) {
        if (StringUtils.isNullOrEmptyEx(raw)) return Math.max(1, fallback);
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : Math.max(1, fallback);
        } catch (NumberFormatException ignored) {
            return Math.max(1, fallback);
        }
    }

    private static Integer parseRequiredInt(Map<String, String> args, String key) {
        String raw = args.get(key);
        if (StringUtils.isNullOrEmptyEx(raw)) {
            ApricityUI.LOGGER.warn("Missing argument: {}", key);
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            ApricityUI.LOGGER.warn("Invalid argument: {}={}", key, raw);
            return null;
        }
    }

    private static UUID parseRequiredUuid(Map<String, String> args, String key) {
        String raw = args.get(key);
        if (StringUtils.isNullOrEmptyEx(raw)) {
            ApricityUI.LOGGER.warn("Missing argument: {}", key);
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            ApricityUI.LOGGER.warn("Invalid uuid argument: {}={}", key, raw);
            return null;
        }
    }

    private static Direction parseDirection(String raw) {
        if (StringUtils.isNullOrEmptyEx(raw)) return null;
        return Direction.byName(raw.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private static Entity findEntityByUuid(ServerPlayerEntity player, UUID uuid) {
        if (player == null || player.server == null || uuid == null) return null;
        for (ServerWorld level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static ApricityMenuSlotSource createSavedSource(
            ContainerSchema.Descriptor.BindType bindType,
            ServerPlayerEntity player,
            String dataName,
            String inventoryKey,
            int slotCount
    ) {
        ApricitySavedData savedData = ApricitySavedData.get(player.server, dataName);
        ItemStackHandler handler = savedData.getOrCreate(inventoryKey, Math.max(1, slotCount));
        return new ApricityMenuSlotSource() {
            @Override
            public ContainerSchema.Descriptor.BindType bindType() {
                return bindType;
            }

            @Override
            public int slotCount() {
                return handler.getSlots();
            }

            @Override
            public net.minecraft.inventory.container.Slot createSlot(int slotIndex, int x, int y) {
                return new SlotItemHandler(handler, slotIndex, x, y);
            }

            @Override
            public void onClose(ServerPlayerEntity player) {
                savedData.setDirty();
            }
        };
    }

    private static ApricityMenuSlotSource createBlockEntitySource(
            ContainerSchema.Descriptor.BindType bindType,
            ServerPlayerEntity player,
            BlockPos pos,
            Direction side
    ) {
        ServerWorld level = player.getLevel();
        if (!level.hasChunkAt(pos)) {
            ApricityUI.LOGGER.warn("Target chunk not loaded, cannot bind {} @ {}", bindType.id(), pos);
            return null;
        }

        TileEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            ApricityUI.LOGGER.warn("Block entity not found, cannot bind {} @ {}", bindType.id(), pos);
            return null;
        }

        IItemHandler handler = blockEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side).orElse(null);
        if (handler == null || handler.getSlots() <= 0) {
            ApricityUI.LOGGER.warn("Block entity has no inventory capability, cannot bind {} @ {}", bindType.id(), pos);
            return null;
        }

        Class<?> expectedType = blockEntity.getClass();
        BlockPos immutablePos = pos.immutable();
        return new ApricityMenuSlotSource() {
            @Override
            public ContainerSchema.Descriptor.BindType bindType() {
                return bindType;
            }

            @Override
            public int slotCount() {
                return handler.getSlots();
            }

            @Override
            public net.minecraft.inventory.container.Slot createSlot(int slotIndex, int x, int y) {
                return new SlotItemHandler(handler, slotIndex, x, y);
            }

            @Override
            public boolean stillValid(ServerPlayerEntity player) {
                ServerWorld currentLevel = player.getLevel();
                if (!currentLevel.hasChunkAt(immutablePos)) return false;
                TileEntity current = currentLevel.getBlockEntity(immutablePos);
                return current != null && expectedType.isInstance(current);
            }
        };
    }

    private static ApricityMenuSlotSource createEntitySource(
            ContainerSchema.Descriptor.BindType bindType,
            ServerPlayerEntity player,
            UUID targetUuid
    ) {
        Entity target = findEntityByUuid(player, targetUuid);
        if (!(target instanceof LivingEntity)) {
            ApricityUI.LOGGER.warn("Entity source resolve failed: living entity not found, uuid={}", targetUuid);
            return null;
        }
        LivingEntity livingEntity = (LivingEntity) target;

        IItemHandler handler = livingEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).orElse(null);
        if (handler == null || handler.getSlots() <= 0) {
            ApricityUI.LOGGER.warn("Entity source resolve failed: no item handler capability, uuid={}", targetUuid);
            return null;
        }
        Class<?> expectedType = livingEntity.getClass();
        return new ApricityMenuSlotSource() {
            @Override
            public ContainerSchema.Descriptor.BindType bindType() {
                return bindType;
            }

            @Override
            public int slotCount() {
                return handler.getSlots();
            }

            @Override
            public net.minecraft.inventory.container.Slot createSlot(int slotIndex, int x, int y) {
                return new SlotItemHandler(handler, slotIndex, x, y);
            }

            @Override
            public boolean stillValid(ServerPlayerEntity player) {
                Entity current = findEntityByUuid(player, targetUuid);
                return current != null && expectedType.isInstance(current);
            }
        };
    }
}

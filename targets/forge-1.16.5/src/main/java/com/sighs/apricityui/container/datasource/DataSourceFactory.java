package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.config.ApricitySavedData;
import com.sighs.apricityui.container.bind.ContainerBindType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Map;

/**
 * 数据源工厂：根据绑定类型创建对应的 ContainerDataSource。
 */
public final class DataSourceFactory {
    /**
     * 根据绑定类型解析并创建数据源。
     *
     * @param player      服务端玩家
     * @param containerId 容器 ID
     * @param bindType    绑定类型
     * @param args        额外参数
     * @param capacity    请求容量
     * @return 数据源实例，无法解析时返回 null
     */
    public static ContainerDataSource resolve(ServerPlayer player,
                                              String containerId,
                                              ContainerBindType bindType,
                                              Map<String, String> args,
                                              int capacity) {
        if (player == null || bindType == null) return null;
        if (ContainerBindType.isPlayer(bindType)) return null;

        return switch (bindType) {
            case SAVED_DATA -> resolveSavedData(player, containerId, args, capacity);
            case BLOCK_ENTITY -> resolveBlockEntity(player, containerId, args, capacity);
            case ENTITY -> resolveEntity(player, containerId, args, capacity);
            default -> null;
        };
    }

    private static ContainerDataSource resolveSavedData(ServerPlayer player,
                                                        String containerId,
                                                        Map<String, String> args,
                                                        int capacity) {
        if (player.getServer() == null) return null;

        String dataName = getArg(args, "data_name", "apricityui_data");
        String inventoryKey = containerId != null && !containerId.isBlank() ? containerId : "__default__";
        int normalizedCapacity = Math.max(1, capacity);

        ApricitySavedData savedData = ApricitySavedData.get(player.getServer(), dataName);
        ItemStackHandler handler = savedData.getOrCreate(inventoryKey, normalizedCapacity);

        return new SavedDataDataSource(ContainerBindType.SAVED_DATA, savedData, inventoryKey, handler);
    }

    /**
     * 解析方块实体数据源。
     * 支持的 args：
     * - "x", "y", "z"：方块坐标（必填）
     */
    private static ContainerDataSource resolveBlockEntity(ServerPlayer player,
                                                          String containerId,
                                                          Map<String, String> args,
                                                          int capacity) {
        BlockPos pos = parseBlockPos(args);
        if (pos == null) return null;
        return BlockEntityDataSource.resolve(player, pos, capacity);
    }

    /**
     * 解析实体数据源。
     * 支持的 args：
     * - "entity_id"：实体网络 ID（必填）
     */
    private static ContainerDataSource resolveEntity(ServerPlayer player,
                                                     String containerId,
                                                     Map<String, String> args,
                                                     int capacity) {
        Integer entityId = parseIntArg(args, "entity_id");
        if (entityId == null) return null;
        return EntityDataSource.resolve(player, entityId, capacity);
    }

    private static BlockPos parseBlockPos(Map<String, String> args) {
        Integer x = parseIntArg(args, "x");
        Integer y = parseIntArg(args, "y");
        Integer z = parseIntArg(args, "z");
        if (x == null || y == null || z == null) return null;
        return new BlockPos(x, y, z);
    }

    private static Integer parseIntArg(Map<String, String> args, String key) {
        if (args == null || key == null) return null;
        String value = args.get(key);
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String getArg(Map<String, String> args, String key, String fallback) {
        if (args == null || key == null) return fallback;
        String value = args.get(key);
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }
}

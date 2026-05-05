package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.ApricitySavedData;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Map;

/**
 * 数据源工厂：根据绑定类型创建对应的 ContainerDataSource。
 */
public final class DataSourceFactory {

    private DataSourceFactory() {
    }

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
        if (ContainerBindType.isVirtualUi(bindType)) return null;

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

        String dataName = args != null ? args.getOrDefault("data_name", "apricityui_data") : "apricityui_data";
        String inventoryKey = containerId != null && !containerId.isBlank() ? containerId : "__default__";
        int normalizedCapacity = Math.max(1, capacity);

        ApricitySavedData savedData = ApricitySavedData.get(player.getServer(), dataName);
        ItemStackHandler handler = savedData.getOrCreate(inventoryKey, normalizedCapacity);

        return new SavedDataDataSource(ContainerBindType.SAVED_DATA, savedData, inventoryKey, handler);
    }

    private static ContainerDataSource resolveBlockEntity(ServerPlayer player,
                                                          String containerId,
                                                          Map<String, String> args,
                                                          int capacity) {
        // TODO: 实现 block_entity 数据源解析
        return null;
    }

    private static ContainerDataSource resolveEntity(ServerPlayer player,
                                                     String containerId,
                                                     Map<String, String> args,
                                                     int capacity) {
        // TODO: 实现 entity 数据源解析
        return null;
    }
}

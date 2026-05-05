package com.sighs.apricityui.instance.container.datasource;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.element.Container;
import net.minecraft.server.level.ServerPlayer;

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
        return resolve(player, containerId, bindType, args, capacity, OpenBindPlan.ResizePolicy.KEEP_OVERFLOW);
    }

    public static ContainerDataSource resolve(ServerPlayer player,
                                              String containerId,
                                              ContainerBindType bindType,
                                              Map<String, String> args,
                                              int capacity,
                                              OpenBindPlan.ResizePolicy resizePolicy) {
        return Container.resolveBinding(
                player,
                containerId,
                bindType,
                args == null ? Map.of() : args,
                capacity,
                resizePolicy
        );
    }
}

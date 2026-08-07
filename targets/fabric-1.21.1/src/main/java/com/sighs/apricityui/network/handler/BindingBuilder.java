package com.sighs.apricityui.network.handler;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.element.ContainerDeclaration;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 容器绑定构建器，用于链式声明 Screen 的数据绑定关系。
 * <p>
 * 使用示例：
 * <pre>
 * ApricityUI.menu(player, path).bind(b -> b.blockEntity(pos).player());
 * ApricityUI.menu(player, path).bind(b -> b.saveddata().player());
 * </pre>
 */
public final class BindingBuilder {

    private final List<ContainerDeclaration> declarations = new ArrayList<>();
    private final Map<String, Map<String, String>> argsById = new LinkedHashMap<>();
    private boolean primarySet = false;

    /**
     * 绑定玩家背包容器（36 格）。
     */
    public BindingBuilder player() {
        declarations.add(new ContainerDeclaration("player", ContainerBindType.PLAYER, 36, false));
        return this;
    }

    /**
     * 绑定 SavedData 容器（默认数据名 apricityui_data，默认 9 格）。
     */
    public BindingBuilder saveddata() {
        return saveddata("apricityui_data", 9);
    }

    /**
     * 绑定 SavedData 容器（自定义数据名，默认 9 格）。
     */
    public BindingBuilder saveddata(String dataName) {
        return saveddata(dataName, 9);
    }

    /**
     * 绑定 SavedData 容器（自定义数据名和容量）。
     */
    public BindingBuilder saveddata(String dataName, int capacity) {
        String id = "saved_data";
        boolean primary = !primarySet;
        if (primary) primarySet = true;
        declarations.add(new ContainerDeclaration(id, ContainerBindType.SAVED_DATA, capacity, primary));
        argsById.put(id, Map.of("data_name", dataName));
        return this;
    }

    /**
     * 绑定方块实体容器（容量由数据源自动推导）。
     */
    public BindingBuilder blockEntity(BlockPos pos) {
        return blockEntity(pos, 0);
    }

    /**
     * 绑定方块实体容器（显式指定容量）。
     */
    public BindingBuilder blockEntity(BlockPos pos, int capacity) {
        String id = "block_entity";
        boolean primary = !primarySet;
        if (primary) primarySet = true;
        declarations.add(new ContainerDeclaration(id, ContainerBindType.BLOCK_ENTITY, capacity, primary));
        argsById.put(id, Map.of(
                "x", String.valueOf(pos.getX()),
                "y", String.valueOf(pos.getY()),
                "z", String.valueOf(pos.getZ())
        ));
        return this;
    }

    /**
     * 绑定实体容器（容量由数据源自动推导）。
     */
    public BindingBuilder entity(int entityId) {
        return entity(entityId, 0);
    }

    /**
     * 绑定实体容器（显式指定容量）。
     */
    public BindingBuilder entity(int entityId, int capacity) {
        String id = "entity";
        boolean primary = !primarySet;
        if (primary) primarySet = true;
        declarations.add(new ContainerDeclaration(id, ContainerBindType.ENTITY, capacity, primary));
        argsById.put(id, Map.of("entity_id", String.valueOf(entityId)));
        return this;
    }

    /**
     * 获取已声明的容器列表（内部使用）。
     */
    List<ContainerDeclaration> declarations() {
        return declarations;
    }

    /**
     * 获取参数映射（内部使用）。
     */
    Map<String, Map<String, String>> argsById() {
        return argsById;
    }
}

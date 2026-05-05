package com.sighs.apricityui.instance.network.handler;

import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

/**
 * 容器绑定构建器，用于链式声明 Screen 的数据绑定关系。
 * <p>
 * 使用示例：
 * <pre>
 * ApricityUI.menu(player, path).bind(b -> b.blockEntity(pos).player());
 * ApricityUI.menu(player, path).bind(b -> b.savedData("data", "key").player());
 * </pre>
 */
public final class BindingBuilder {
    private static final String DEFAULT_BLOCK_ENTITY_ID = "block_entity";
    private static final String DEFAULT_ENTITY_ID = "entity";
    private static final String DEFAULT_PLAYER_ID = "player";
    private static final String DEFAULT_SAVED_DATA_ID = "saved_data";
    private static final String DEFAULT_SAVED_DATA_NAME = "apricityui_saved";
    private static final String DEFAULT_SAVED_DATA_KEY = "__default__";

    private final OpenBindPlan.Builder delegate;
    private boolean primarySet = false;

    BindingBuilder(String templatePath) {
        this.delegate = OpenBindPlan.builder().templatePath(templatePath);
    }

    /**
     * 绑定玩家背包容器（36 格）。
     */
    public BindingBuilder player() {
        delegate.bind(DEFAULT_PLAYER_ID).player();
        return this;
    }

    /**
     * 绑定 SavedData 容器（默认数据名、默认键和 9 格容量）。
     */
    public BindingBuilder savedData() {
        return savedData(DEFAULT_SAVED_DATA_NAME, DEFAULT_SAVED_DATA_KEY, 9);
    }

    /**
     * 绑定 SavedData 容器（自定义数据名，默认键和 9 格容量）。
     */
    public BindingBuilder savedData(String dataName) {
        return savedData(dataName, DEFAULT_SAVED_DATA_KEY, 9);
    }

    /**
     * 绑定 SavedData 容器（自定义数据名和容量，默认键）。
     */
    public BindingBuilder savedData(String dataName, int capacity) {
        return savedData(dataName, DEFAULT_SAVED_DATA_KEY, capacity);
    }

    /**
     * 绑定 SavedData 容器（自定义数据名、键和容量）。
     */
    public BindingBuilder savedData(String dataName, String inventoryKey, int capacity) {
        String containerId = markPrimaryIfNeeded(DEFAULT_SAVED_DATA_ID);
        delegate.bind(containerId).savedData(dataName, inventoryKey, Math.max(1, capacity));
        return this;
    }

    /**
     * 旧拼写兼容。
     */
    public BindingBuilder saveddata() {
        return savedData();
    }

    /**
     * 旧拼写兼容。
     */
    public BindingBuilder saveddata(String dataName) {
        return savedData(dataName);
    }

    /**
     * 旧拼写兼容。
     */
    public BindingBuilder saveddata(String dataName, int capacity) {
        return savedData(dataName, capacity);
    }

    /**
     * 绑定方块实体容器。
     */
    public BindingBuilder blockEntity(BlockPos pos) {
        return blockEntity(pos, null);
    }

    /**
     * 绑定方块实体容器。
     */
    public BindingBuilder blockEntity(BlockPos pos, Direction side) {
        if (pos == null) return this;
        String containerId = markPrimaryIfNeeded(DEFAULT_BLOCK_ENTITY_ID);
        delegate.bind(containerId).blockEntity(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                side == null ? "" : side.getName()
        );
        return this;
    }

    /**
     * 绑定实体容器。
     */
    public BindingBuilder entity(Entity entity) {
        if (entity == null) return this;
        return entity(entity.getUUID().toString());
    }

    /**
     * 绑定实体容器。
     */
    public BindingBuilder entity(String uuid) {
        String containerId = markPrimaryIfNeeded(DEFAULT_ENTITY_ID);
        delegate.bind(containerId).entity(uuid);
        return this;
    }

    OpenBindPlan build() {
        return delegate.build();
    }

    private String markPrimaryIfNeeded(String containerId) {
        if (!primarySet) {
            primarySet = true;
            delegate.primaryContainer(containerId);
        }
        return containerId;
    }
}

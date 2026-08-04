package com.sighs.apricityui.container;

import com.sighs.apricityui.container.bind.ContainerBindType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 容器槽位布局描述，用于客户端/服务端之间传递容器结构信息。
 */
public record SlotLayout(String templatePath, List<ContainerEntry> containers) {

    public SlotLayout {
        templatePath = templatePath == null ? "" : templatePath;
        containers = containers == null ? List.of() : List.copyOf(containers);
    }

    /**
     * 创建纯 UI 布局（无真实容器绑定）。
     */
    public static SlotLayout createUiOnly(String templatePath) {
        return new SlotLayout(templatePath, List.of());
    }

    /**
     * 是否为纯 UI 布局（无容器条目）。
     */
    public boolean isUiOnly() {
        return containers.isEmpty();
    }

    /**
     * 获取主容器 ID。优先返回标记为 primary 的容器，否则返回第一个。
     */
    public String primaryContainerId() {
        for (ContainerEntry entry : containers) {
            if (entry.primary()) return entry.id();
        }
        return containers.isEmpty() ? "" : containers.get(0).id();
    }

    /**
     * 按 ID 查找容器条目。
     */
    public ContainerEntry findContainer(String containerId) {
        if (containerId == null || containerId.isBlank()) return null;
        for (ContainerEntry entry : containers) {
            if (containerId.equals(entry.id())) return entry;
        }
        return null;
    }

    /**
     * 序列化到网络包。
     */
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(templatePath);
        buf.writeVarInt(containers.size());
        for (ContainerEntry entry : containers) {
            buf.writeUtf(entry.id());
            buf.writeUtf(entry.bindType().id());
            buf.writeVarInt(entry.baseIndex());
            buf.writeVarInt(entry.capacity());
            buf.writeBoolean(entry.primary());
        }
    }

    /**
     * 从网络包反序列化。
     */
    public static SlotLayout read(FriendlyByteBuf buf) {
        String templatePath = buf.readUtf();
        int count = buf.readVarInt();
        ArrayList<ContainerEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            String bindTypeId = buf.readUtf();
            ContainerBindType bindType = ContainerBindType.fromRaw(bindTypeId);
            if (bindType == null) bindType = ContainerBindType.PLAYER;
            int baseIndex = buf.readVarInt();
            int capacity = buf.readVarInt();
            boolean primary = buf.readBoolean();
            entries.add(new ContainerEntry(id, bindType, baseIndex, capacity, primary));
        }
        return new SlotLayout(templatePath, entries);
    }

    /**
     * 容器条目：描述一个容器在菜单中的槽位范围。
     */
    public record ContainerEntry(
            String id,
            ContainerBindType bindType,
            int baseIndex,
            int capacity,
            boolean primary
    ) {
        public ContainerEntry {
            id = id == null ? "" : id;
            bindType = bindType == null ? ContainerBindType.PLAYER : bindType;
            baseIndex = Math.max(0, baseIndex);
            capacity = Math.max(0, capacity);
        }

        /**
         * 将本地槽位索引转换为全局菜单槽位索引。
         */
        public Integer resolveGlobalSlotIndex(int localSlotIndex) {
            if (localSlotIndex < 0 || localSlotIndex >= capacity) return null;
            return baseIndex + localSlotIndex;
        }
    }
}

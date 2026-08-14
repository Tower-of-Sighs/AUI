package com.sighs.apricityui.container;

import com.sighs.apricityui.container.bind.ContainerBindType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 容器槽位布局描述，用于客户端/服务端之间传递容器结构及服务端声明的过滤 selector。
 */
public record SlotLayout(String templatePath,
                         List<ContainerEntry> containers,
                         Map<String, List<String>> filterSelectorsByContainer) {

    public SlotLayout {
        templatePath = templatePath == null ? "" : templatePath;
        containers = containers == null ? List.of() : List.copyOf(containers);
        LinkedHashMap<String, List<String>> normalizedSelectors = new LinkedHashMap<>();
        if (filterSelectorsByContainer != null) {
            filterSelectorsByContainer.forEach((containerId, selectors) -> {
                if (containerId == null || containerId.isBlank() || selectors == null || selectors.isEmpty()) return;
                ArrayList<String> usable = new ArrayList<>();
                for (String selector : selectors) {
                    if (selector != null && !selector.isBlank() && !usable.contains(selector)) usable.add(selector);
                }
                if (!usable.isEmpty()) normalizedSelectors.put(containerId, List.copyOf(usable));
            });
        }
        filterSelectorsByContainer = Map.copyOf(normalizedSelectors);
    }

    public SlotLayout(String templatePath, List<ContainerEntry> containers) {
        this(templatePath, containers, Map.of());
    }

    /**
     * 创建纯 UI 布局（无真实容器绑定）。
     */
    public static SlotLayout createUiOnly(String templatePath) {
        return new SlotLayout(templatePath, List.of(), Map.of());
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
     * 获取服务端声明、可由客户端解析的指定 Container selector 列表。
     */
    public List<String> filterSelectors(String containerId) {
        if (containerId == null || containerId.isBlank()) return List.of();
        return filterSelectorsByContainer.getOrDefault(containerId, List.of());
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
        buf.writeVarInt(filterSelectorsByContainer.size());
        for (Map.Entry<String, List<String>> entry : filterSelectorsByContainer.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (String selector : entry.getValue()) buf.writeUtf(selector);
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
        int selectorContainerCount = buf.readVarInt();
        LinkedHashMap<String, List<String>> selectorsByContainer = new LinkedHashMap<>();
        for (int i = 0; i < selectorContainerCount; i++) {
            String containerId = buf.readUtf();
            int selectorCount = buf.readVarInt();
            ArrayList<String> selectors = new ArrayList<>(selectorCount);
            for (int j = 0; j < selectorCount; j++) selectors.add(buf.readUtf());
            selectorsByContainer.put(containerId, selectors);
        }
        return new SlotLayout(templatePath, entries, selectorsByContainer);
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

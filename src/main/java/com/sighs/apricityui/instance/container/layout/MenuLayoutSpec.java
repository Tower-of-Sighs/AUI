package com.sighs.apricityui.instance.container.layout;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 菜单布局规格（服务端构建后同步给客户端）。
 */
public record MenuLayoutSpec(
        String templatePath,
        List<ContainerLayout> containers
) {
    public MenuLayoutSpec {
        templatePath = templatePath == null ? "" : templatePath.trim();
        ArrayList<ContainerLayout> normalized = new ArrayList<>();
        if (containers != null) {
            for (ContainerLayout container : containers) {
                if (container == null) continue;
                normalized.add(container);
            }
        }
        containers = List.copyOf(normalized);
    }

    public static MenuLayoutSpec createUiOnly(String templatePath) {
        return new MenuLayoutSpec(templatePath, List.of());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(templatePath == null ? "" : templatePath);
        buf.writeVarInt(containers.size());
        for (ContainerLayout container : containers) {
            buf.writeUtf(container.id());
            buf.writeUtf(container.bindType() == null ? "" : container.bindType().id());
            buf.writeVarInt(Math.max(0, container.baseIndex()));
            buf.writeVarInt(Math.max(0, container.capacity()));
            buf.writeBoolean(container.primary());
        }
    }

    public static MenuLayoutSpec read(FriendlyByteBuf buf) {
        String templatePath = buf.readUtf();
        int count = buf.readVarInt();
        ArrayList<ContainerLayout> containers = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            String rawBindType = buf.readUtf();
            int baseIndex = Math.max(0, buf.readVarInt());
            int capacity = Math.max(0, buf.readVarInt());
            boolean primary = buf.readBoolean();
            ContainerBindType bindType = ContainerBindType.fromRaw(rawBindType);
            if (bindType == null) bindType = ContainerBindType.PLAYER;
            containers.add(new ContainerLayout(id, bindType, baseIndex, capacity, primary));
        }
        return new MenuLayoutSpec(templatePath, containers);
    }

    public boolean isUiOnly() {
        return containers.isEmpty();
    }

    public List<String> containerIds() {
        ArrayList<String> ids = new ArrayList<>(containers.size());
        for (ContainerLayout container : containers) {
            ids.add(container.id());
        }
        return List.copyOf(ids);
    }

    public ContainerLayout findContainer(String containerId) {
        String normalized = normalizeContainerId(containerId);
        if (normalized == null) return null;
        for (ContainerLayout container : containers) {
            if (normalized.equals(container.id())) return container;
        }
        return null;
    }

    public String primaryContainerId() {
        for (ContainerLayout container : containers) {
            if (container.primary()) return container.id();
        }
        return "";
    }

    public Map<String, ContainerLayout> containersById() {
        LinkedHashMap<String, ContainerLayout> mapping = new LinkedHashMap<>();
        for (ContainerLayout container : containers) {
            mapping.put(container.id(), container);
        }
        return Map.copyOf(mapping);
    }

    private static String normalizeContainerId(String containerId) {
        if (containerId == null) return null;
        String normalized = containerId.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public record ContainerLayout(
            String id,
            ContainerBindType bindType,
            int baseIndex,
            int capacity,
            boolean primary
    ) {
        public ContainerLayout {
            id = normalizeContainerId(id);
            if (id == null) {
                throw new IllegalArgumentException("container id cannot be blank");
            }
            bindType = Objects.requireNonNull(bindType, "bindType");
            baseIndex = Math.max(0, baseIndex);
            capacity = Math.max(0, capacity);
        }

        public Integer resolveGlobalSlotIndex(int localSlotIndex) {
            if (localSlotIndex < 0 || localSlotIndex >= capacity) return null;
            return baseIndex + localSlotIndex;
        }

        private static String normalizeContainerId(String containerId) {
            if (containerId == null) return null;
            String normalized = containerId.trim().toLowerCase(Locale.ROOT);
            return normalized.isEmpty() ? null : normalized;
        }
    }
}

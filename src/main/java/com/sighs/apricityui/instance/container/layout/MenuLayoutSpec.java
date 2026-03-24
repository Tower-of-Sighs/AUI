package com.sighs.apricityui.instance.container.layout;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.util.common.NormalizeUtil;
import net.minecraft.network.FriendlyByteBuf;

import java.util.*;

/**
 * 菜单布局规格（服务端构建后同步给客户端）。
 */
public final class MenuLayoutSpec {
    private final String templatePath;
    private final List<ContainerLayout> containers;
    private final List<String> containerIds;
    private final Map<String, ContainerLayout> containersById;
    private final String primaryContainerId;

    public MenuLayoutSpec(String templatePath, List<ContainerLayout> containers) {
        this.templatePath = templatePath == null ? "" : templatePath.trim();

        ArrayList<ContainerLayout> normalizedContainers = new ArrayList<>();
        if (containers != null) {
            for (ContainerLayout container : containers) {
                if (container == null) continue;
                normalizedContainers.add(container);
            }
        }
        this.containers = List.copyOf(normalizedContainers);

        ArrayList<String> normalizedIds = new ArrayList<>(this.containers.size());
        LinkedHashMap<String, ContainerLayout> normalizedById = new LinkedHashMap<>();
        String resolvedPrimaryContainerId = "";
        for (ContainerLayout container : this.containers) {
            normalizedIds.add(container.id());
            normalizedById.put(container.id(), container);
            if (resolvedPrimaryContainerId.isEmpty() && container.primary()) {
                resolvedPrimaryContainerId = container.id();
            }
        }

        this.containerIds = List.copyOf(normalizedIds);
        this.containersById = Map.copyOf(normalizedById);
        this.primaryContainerId = resolvedPrimaryContainerId;
    }

    public static MenuLayoutSpec createUiOnly(String templatePath) {
        return new MenuLayoutSpec(templatePath, List.of());
    }

    public String templatePath() {
        return templatePath;
    }

    public List<ContainerLayout> containers() {
        return containers;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(templatePath);
        buf.writeVarInt(containers.size());
        for (ContainerLayout container : containers) {
            buf.writeUtf(container.id());
            buf.writeUtf(container.bindType().id());
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
        return containerIds;
    }

    public ContainerLayout findContainer(String containerId) {
        String normalized = NormalizeUtil.normalizeContainerId(containerId);
        if (normalized == null) return null;
        return containersById.get(normalized);
    }

    public String primaryContainerId() {
        return primaryContainerId;
    }

    public Map<String, ContainerLayout> containersById() {
        return containersById;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MenuLayoutSpec that)) return false;
        return templatePath.equals(that.templatePath) && containers.equals(that.containers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templatePath, containers);
    }

    @Override
    public String toString() {
        return "MenuLayoutSpec[" +
                "templatePath=" + templatePath +
                ", containers=" + containers +
                ']';
    }

    public record ContainerLayout(
            String id,
            ContainerBindType bindType,
            int baseIndex,
            int capacity,
            boolean primary
    ) {
        public ContainerLayout {
            id = NormalizeUtil.normalizeContainerId(id);
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
    }
}

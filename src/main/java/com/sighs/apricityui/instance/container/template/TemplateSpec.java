package com.sighs.apricityui.instance.container.template;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.util.common.NormalizeUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TemplateSpec {
    private final String templatePath;
    private final String primaryContainerId;
    private final List<ContainerSpec> containers;
    private final List<String> containerIds;
    private final Map<String, ContainerSpec> containersById;

    public TemplateSpec(String templatePath, String primaryContainerId, List<ContainerSpec> containers) {
        this.templatePath = templatePath == null ? "" : templatePath.trim();
        this.primaryContainerId = primaryContainerId == null ? "" : primaryContainerId.trim();

        ArrayList<ContainerSpec> normalizedContainers = new ArrayList<>();
        if (containers != null) {
            for (ContainerSpec container : containers) {
                if (container == null) continue;
                normalizedContainers.add(container);
            }
        }
        this.containers = List.copyOf(normalizedContainers);

        ArrayList<String> normalizedIds = new ArrayList<>(this.containers.size());
        LinkedHashMap<String, ContainerSpec> normalizedById = new LinkedHashMap<>();
        for (ContainerSpec container : this.containers) {
            normalizedIds.add(container.id());
            normalizedById.put(container.id(), container);
        }
        this.containerIds = List.copyOf(normalizedIds);
        this.containersById = Map.copyOf(normalizedById);
    }

    public String templatePath() {
        return templatePath;
    }

    public String primaryContainerId() {
        return primaryContainerId;
    }

    public List<ContainerSpec> containers() {
        return containers;
    }

    public List<String> containerIds() {
        return containerIds;
    }

    public ContainerSpec findContainer(String containerId) {
        String normalized = NormalizeUtil.normalizeContainerId(containerId);
        if (normalized == null) return null;
        return containersById.get(normalized);
    }

    public Map<String, ContainerSpec> containersById() {
        return containersById;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TemplateSpec that)) return false;
        return templatePath.equals(that.templatePath)
                && primaryContainerId.equals(that.primaryContainerId)
                && containers.equals(that.containers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templatePath, primaryContainerId, containers);
    }

    @Override
    public String toString() {
        return "TemplateSpec[" +
                "templatePath=" + templatePath +
                ", primaryContainerId=" + primaryContainerId +
                ", containers=" + containers +
                ']';
    }

    public record ContainerSpec(
            String id,
            ContainerBindType bindType,
            boolean primary,
            int requiredCapacity,
            String title
    ) {
        public ContainerSpec {
            id = NormalizeUtil.normalizeContainerId(Objects.requireNonNull(id, "container id cannot be null"));
            if (id == null) {
                throw new IllegalArgumentException("container id cannot be blank");
            }
            bindType = Objects.requireNonNull(bindType, "bindType cannot be null");
            requiredCapacity = Math.max(0, requiredCapacity);
            title = title == null ? "" : title.trim();
        }
    }
}

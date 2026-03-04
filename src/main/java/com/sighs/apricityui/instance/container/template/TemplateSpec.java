package com.sighs.apricityui.instance.container.template;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record TemplateSpec(
        String templatePath,
        String primaryContainerId,
        List<ContainerSpec> containers
) {
    public TemplateSpec {
        templatePath = templatePath == null ? "" : templatePath.trim();
        primaryContainerId = primaryContainerId == null ? "" : primaryContainerId.trim();

        ArrayList<ContainerSpec> normalized = new ArrayList<>();
        if (containers != null) {
            for (ContainerSpec container : containers) {
                if (container == null) continue;
                normalized.add(container);
            }
        }
        containers = List.copyOf(normalized);
    }

    public List<String> containerIds() {
        ArrayList<String> ids = new ArrayList<>(containers.size());
        for (ContainerSpec container : containers) {
            ids.add(container.id());
        }
        return List.copyOf(ids);
    }

    public ContainerSpec findContainer(String containerId) {
        String normalized = normalizeContainerId(containerId);
        if (normalized == null) return null;
        for (ContainerSpec container : containers) {
            if (normalized.equals(container.id())) return container;
        }
        return null;
    }

    public Map<String, ContainerSpec> containersById() {
        LinkedHashMap<String, ContainerSpec> mapping = new LinkedHashMap<>();
        for (ContainerSpec container : containers) {
            mapping.put(container.id(), container);
        }
        return Map.copyOf(mapping);
    }

    private static String normalizeContainerId(String containerId) {
        if (containerId == null) return null;
        String normalized = containerId.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public record ContainerSpec(
            String id,
            ContainerBindType bindType,
            boolean primary,
            int requiredCapacity,
            String title
    ) {
        public ContainerSpec {
            id = Objects.requireNonNull(id, "container id cannot be null")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                throw new IllegalArgumentException("container id cannot be blank");
            }
            bindType = Objects.requireNonNull(bindType, "bindType cannot be null");
            requiredCapacity = Math.max(0, requiredCapacity);
            title = title == null ? "" : title.trim();
        }
    }
}

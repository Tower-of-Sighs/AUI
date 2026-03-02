package com.sighs.apricityui.instance.container.template;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 服务端模板编译结果。
 * 仅描述容器声明，不包含运行时绑定结果。
 */
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
        if (containerId == null || containerId.isBlank()) return null;
        for (ContainerSpec container : containers) {
            if (containerId.equals(container.id())) return container;
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

    /**
     * 单个容器声明。
     */
    public record ContainerSpec(
            String id,
            ContainerBindType bindType,
            boolean primary,
            int requiredCapacity,
            int declaredSize,
            List<Integer> explicitIndices
    ) {
        public ContainerSpec {
            id = Objects.requireNonNull(id, "container id cannot be null").trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("container id cannot be blank");
            }
            bindType = Objects.requireNonNull(bindType, "bindType cannot be null");
            requiredCapacity = Math.max(0, requiredCapacity);
            declaredSize = Math.max(0, declaredSize);

            ArrayList<Integer> normalizedIndices = new ArrayList<>();
            if (explicitIndices != null) {
                for (Integer index : explicitIndices) {
                    if (index == null || index < 0) continue;
                    normalizedIndices.add(index);
                }
            }
            explicitIndices = List.copyOf(normalizedIndices);
        }
    }
}

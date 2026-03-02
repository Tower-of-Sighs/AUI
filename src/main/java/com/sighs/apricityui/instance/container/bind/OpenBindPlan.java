package com.sighs.apricityui.instance.container.bind;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 开屏绑定计划。
 * v2 以 containerId 为主键，同时保留 legacy primary/index API 兼容旧调用。
 */
public final class OpenBindPlan {
    private final String templatePath;
    private final String primaryContainerIdOverride;
    private final LinkedHashMap<String, ContainerOverride> containersById;
    private final Options options;

    @Deprecated
    private final BindingSpec primaryBinding;
    @Deprecated
    private final LinkedHashMap<Integer, BindingSpec> indexBindings;

    private OpenBindPlan(String templatePath,
                         String primaryContainerIdOverride,
                         Map<String, ContainerOverride> containersById,
                         Options options,
                         BindingSpec primaryBinding,
                         Map<Integer, BindingSpec> indexBindings) {
        this.templatePath = templatePath == null ? "" : templatePath.trim();
        this.primaryContainerIdOverride = primaryContainerIdOverride == null ? "" : primaryContainerIdOverride.trim();
        this.options = options == null ? new Options(ResizePolicy.KEEP_OVERFLOW) : options;

        this.containersById = new LinkedHashMap<>();
        if (containersById != null) {
            containersById.forEach((containerId, override) -> {
                String normalizedContainerId = normalizeContainerId(containerId);
                if (normalizedContainerId == null || override == null) return;
                this.containersById.put(normalizedContainerId, override);
            });
        }

        this.primaryBinding = primaryBinding;
        this.indexBindings = new LinkedHashMap<>();
        if (indexBindings != null) {
            indexBindings.forEach((index, spec) -> {
                if (index == null || index < 0 || spec == null) return;
                this.indexBindings.put(index, spec);
            });
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static OpenBindPlan merge(OpenBindPlan base, OpenBindPlan prefer) {
        if (base == null) return prefer;
        return base.merge(prefer);
    }

    public OpenBindPlan merge(OpenBindPlan override) {
        if (override == null) return this;

        LinkedHashMap<String, ContainerOverride> mergedContainers = new LinkedHashMap<>(containersById);
        override.containersById.forEach((containerId, overrideValue) -> {
            mergedContainers.compute(containerId, (k, current) -> current == null ? overrideValue : current.merge(overrideValue));
        });

        String mergedTemplatePath = override.templatePath.isBlank() ? templatePath : override.templatePath;
        String mergedPrimaryContainerId = override.primaryContainerIdOverride.isBlank()
                ? primaryContainerIdOverride
                : override.primaryContainerIdOverride;
        Options mergedOptions = options.merge(override.options);
        BindingSpec mergedPrimaryBinding = override.primaryBinding != null ? override.primaryBinding : primaryBinding;
        LinkedHashMap<Integer, BindingSpec> mergedIndexBindings = new LinkedHashMap<>(indexBindings);
        mergedIndexBindings.putAll(override.indexBindings);

        return new OpenBindPlan(
                mergedTemplatePath,
                mergedPrimaryContainerId,
                mergedContainers,
                mergedOptions,
                mergedPrimaryBinding,
                mergedIndexBindings
        );
    }

    public String templatePath() {
        return templatePath;
    }

    public String primaryContainerIdOverride() {
        return primaryContainerIdOverride;
    }

    public Map<String, ContainerOverride> containers() {
        return Map.copyOf(containersById);
    }

    public ContainerOverride container(String containerId) {
        String normalizedContainerId = normalizeContainerId(containerId);
        if (normalizedContainerId == null) return null;
        return containersById.get(normalizedContainerId);
    }

    public Options options() {
        return options;
    }

    @Deprecated
    public BindingSpec primaryBinding() {
        return primaryBinding;
    }

    @Deprecated
    public Map<Integer, BindingSpec> indexBindings() {
        return Map.copyOf(indexBindings);
    }

    @Deprecated
    public BindingSpec bindingForIndex(int index) {
        return indexBindings.get(index);
    }

    public enum DisplayMode {
        AUTO,
        CUSTOM,
        HIDDEN
    }

    public enum ResizePolicy {
        KEEP_OVERFLOW,
        TRUNCATE
    }

    public record BindOverride(ContainerBindType bindType, Map<String, String> args) {
        public BindOverride {
            if (bindType == null) {
                throw new IllegalArgumentException("bindType cannot be null");
            }
            if (bindType == ContainerBindType.VIRTUAL_UI) {
                throw new IllegalArgumentException("bindType is reserved for template virtual container: " + bindType);
            }

            LinkedHashMap<String, String> normalizedArgs = new LinkedHashMap<>();
            if (args != null) {
                args.forEach((key, value) -> {
                    if (key == null) return;
                    String normalizedKey = key.trim();
                    if (normalizedKey.isEmpty()) return;
                    normalizedArgs.put(normalizedKey, value == null ? "" : value);
                });
            }
            args = Map.copyOf(normalizedArgs);
        }
    }

    public record DisplayOverride(DisplayMode mode, List<Integer> indices) {
        public DisplayOverride {
            if (mode == null) mode = DisplayMode.AUTO;
            ArrayList<Integer> sanitized = new ArrayList<>();
            if (indices != null) {
                for (Integer index : indices) {
                    if (index == null || index < 0) continue;
                    sanitized.add(index);
                }
            }
            indices = List.copyOf(sanitized);
        }
    }

    public record CapacityOverride(Integer minCapacity, Integer exactCapacity, ResizePolicy resizePolicy) {
        public CapacityOverride {
            if (minCapacity != null && minCapacity < 0) minCapacity = 0;
            if (exactCapacity != null && exactCapacity < 0) exactCapacity = 0;
            if (resizePolicy == null) resizePolicy = ResizePolicy.KEEP_OVERFLOW;
        }
    }

    public record InteractionOverride(Boolean serverAllowInteraction, Set<Integer> disabledIndices) {
        public InteractionOverride {
            LinkedHashSet<Integer> sanitized = new LinkedHashSet<>();
            if (disabledIndices != null) {
                for (Integer index : disabledIndices) {
                    if (index == null || index < 0) continue;
                    sanitized.add(index);
                }
            }
            disabledIndices = Set.copyOf(sanitized);
        }
    }

    public record ContainerOverride(BindOverride bind,
                                    DisplayOverride display,
                                    CapacityOverride capacity,
                                    InteractionOverride interaction) {
        public ContainerOverride merge(ContainerOverride override) {
            if (override == null) return this;
            return new ContainerOverride(
                    override.bind != null ? override.bind : bind,
                    override.display != null ? override.display : display,
                    override.capacity != null ? override.capacity : capacity,
                    override.interaction != null ? override.interaction : interaction
            );
        }
    }

    public record Options(ResizePolicy defaultResizePolicy) {
        public Options {
            if (defaultResizePolicy == null) defaultResizePolicy = ResizePolicy.KEEP_OVERFLOW;
        }

        public Options merge(Options override) {
            if (override == null) return this;
            return new Options(override.defaultResizePolicy != null ? override.defaultResizePolicy : defaultResizePolicy);
        }
    }

    @Deprecated
    public record BindingSpec(ContainerBindType bindType, Map<String, String> args) {
        public BindingSpec {
            if (bindType == null) {
                throw new IllegalArgumentException("bindType cannot be null");
            }
            if (bindType == ContainerBindType.VIRTUAL_UI) {
                throw new IllegalArgumentException("bindType is reserved for template virtual container: " + bindType);
            }

            LinkedHashMap<String, String> normalizedArgs = new LinkedHashMap<>();
            if (args != null) {
                args.forEach((key, value) -> {
                    if (key == null) return;
                    String normalizedKey = key.trim();
                    if (normalizedKey.isEmpty()) return;
                    normalizedArgs.put(normalizedKey, value == null ? "" : value);
                });
            }
            args = Map.copyOf(normalizedArgs);
        }
    }

    public static final class Builder {
        private String templatePath;
        private String primaryContainerIdOverride;
        private final LinkedHashMap<String, ContainerOverride> containersById = new LinkedHashMap<>();
        private Options options = new Options(ResizePolicy.KEEP_OVERFLOW);

        @Deprecated
        private final LinkedHashMap<Integer, BindingSpec> indexBindings = new LinkedHashMap<>();
        @Deprecated
        private BindingSpec primaryBinding;

        private static String requireText(String value, String fieldName) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(fieldName + " cannot be blank");
            }
            return value.trim();
        }

        private static int requirePositive(int value, String fieldName) {
            if (value <= 0) throw new IllegalArgumentException(fieldName + " must be > 0");
            return value;
        }

        private static String requireContainerId(String containerId) {
            String normalized = normalizeContainerId(requireText(containerId, "containerId"));
            if (normalized == null) {
                throw new IllegalArgumentException("containerId cannot be blank");
            }
            return normalized;
        }

        private static BindOverride bindWithSingleArg(BindOverride base, String argKey, String argValue) {
            if (base == null) throw new IllegalArgumentException("base bind override cannot be null");
            LinkedHashMap<String, String> args = new LinkedHashMap<>(base.args());
            args.put(requireText(argKey, "argKey"), argValue == null ? "" : argValue);
            return new BindOverride(base.bindType(), args);
        }

        @Deprecated
        private static BindingSpec legacyWithSingleArg(BindingSpec base, String argKey, String argValue) {
            if (base == null) throw new IllegalArgumentException("base binding cannot be null");
            LinkedHashMap<String, String> args = new LinkedHashMap<>(base.args());
            args.put(requireText(argKey, "argKey"), argValue == null ? "" : argValue);
            return new BindingSpec(base.bindType(), args);
        }

        public Builder templatePath(String templatePath) {
            this.templatePath = templatePath == null ? null : templatePath.trim();
            return this;
        }

        public Builder primaryContainer(String containerId) {
            this.primaryContainerIdOverride = requireContainerId(containerId);
            return this;
        }

        public Builder defaultResizePolicy(ResizePolicy resizePolicy) {
            this.options = new Options(resizePolicy);
            return this;
        }

        public ContainerBindBuilder bind(String containerId) {
            return new ContainerBindBuilder(requireContainerId(containerId));
        }

        public ContainerBindBuilder primaryBind(String containerId) {
            String normalizedContainerId = requireContainerId(containerId);
            this.primaryContainerIdOverride = normalizedContainerId;
            return new ContainerBindBuilder(normalizedContainerId);
        }

        public Builder primarySavedData(String containerId, String dataName, String inventoryKey) {
            return primaryBind(containerId).savedData(dataName, inventoryKey).done();
        }

        public Builder containerBind(String containerId, ContainerBindType bindType) {
            return setContainerBind(containerId, new BindOverride(bindType, Map.of()));
        }

        public Builder containerBind(String containerId, ContainerBindType bindType, String argKey, String argValue) {
            return setContainerBind(containerId, bindWithSingleArg(new BindOverride(bindType, Map.of()), argKey, argValue));
        }

        public Builder containerPlayer(String containerId) {
            return containerBind(containerId, ContainerBindType.PLAYER);
        }

        public Builder containerSavedData(String containerId, String dataName, String inventoryKey, int slotCount) {
            LinkedHashMap<String, String> args = new LinkedHashMap<>();
            args.put("dataName", requireText(dataName, "dataName"));
            args.put("inventoryKey", requireText(inventoryKey, "inventoryKey"));
            args.put("slotCount", String.valueOf(requirePositive(slotCount, "slotCount")));
            return setContainerBind(containerId, new BindOverride(ContainerBindType.SAVED_DATA, args));
        }

        public Builder containerBlockEntity(String containerId, int x, int y, int z, String side) {
            LinkedHashMap<String, String> args = new LinkedHashMap<>();
            args.put("x", String.valueOf(x));
            args.put("y", String.valueOf(y));
            args.put("z", String.valueOf(z));
            if (side != null && !side.trim().isEmpty()) {
                args.put("side", side.trim());
            }
            return setContainerBind(containerId, new BindOverride(ContainerBindType.BLOCK_ENTITY, args));
        }

        public Builder containerEntity(String containerId, String uuid) {
            return setContainerBind(containerId, new BindOverride(
                    ContainerBindType.ENTITY,
                    Map.of("uuid", requireText(uuid, "uuid"))
            ));
        }

        public Builder containerEntity(String containerId, UUID uuid) {
            return containerEntity(containerId, uuid == null ? null : uuid.toString());
        }

        public Builder containerArg(String containerId, String argKey, String argValue) {
            String normalizedContainerId = requireContainerId(containerId);
            ContainerOverride current = containersById.get(normalizedContainerId);
            if (current == null || current.bind() == null) {
                throw new IllegalStateException("Call containerBind(containerId, bindType) before containerArg(...)");
            }
            return setContainerBind(normalizedContainerId, bindWithSingleArg(current.bind(), argKey, argValue));
        }

        public Builder containerDisplay(String containerId, DisplayMode mode, List<Integer> indices) {
            String normalizedContainerId = requireContainerId(containerId);
            ContainerOverride current = containersById.get(normalizedContainerId);
            ContainerOverride updated = new ContainerOverride(
                    current == null ? null : current.bind(),
                    new DisplayOverride(mode, indices),
                    current == null ? null : current.capacity(),
                    current == null ? null : current.interaction()
            );
            containersById.put(normalizedContainerId, updated);
            return this;
        }

        public Builder containerCapacity(String containerId, Integer minCapacity, Integer exactCapacity, ResizePolicy resizePolicy) {
            String normalizedContainerId = requireContainerId(containerId);
            ContainerOverride current = containersById.get(normalizedContainerId);
            CapacityOverride currentCapacity = current == null ? null : current.capacity();
            Integer mergedMinCapacity = minCapacity != null
                    ? minCapacity
                    : (currentCapacity == null ? null : currentCapacity.minCapacity());
            Integer mergedExactCapacity = exactCapacity != null
                    ? exactCapacity
                    : (currentCapacity == null ? null : currentCapacity.exactCapacity());
            ResizePolicy mergedResizePolicy = resizePolicy != null
                    ? resizePolicy
                    : (currentCapacity == null ? null : currentCapacity.resizePolicy());
            ContainerOverride updated = new ContainerOverride(
                    current == null ? null : current.bind(),
                    current == null ? null : current.display(),
                    new CapacityOverride(mergedMinCapacity, mergedExactCapacity, mergedResizePolicy),
                    current == null ? null : current.interaction()
            );
            containersById.put(normalizedContainerId, updated);
            return this;
        }

        public Builder containerInteraction(String containerId, Boolean serverAllowInteraction, Set<Integer> disabledIndices) {
            String normalizedContainerId = requireContainerId(containerId);
            ContainerOverride current = containersById.get(normalizedContainerId);
            ContainerOverride updated = new ContainerOverride(
                    current == null ? null : current.bind(),
                    current == null ? null : current.display(),
                    current == null ? null : current.capacity(),
                    new InteractionOverride(serverAllowInteraction, disabledIndices)
            );
            containersById.put(normalizedContainerId, updated);
            return this;
        }

        private Builder setContainerBind(String containerId, BindOverride bindOverride) {
            String normalizedContainerId = requireContainerId(containerId);
            ContainerOverride current = containersById.get(normalizedContainerId);
            ContainerOverride updated = new ContainerOverride(
                    bindOverride,
                    current == null ? null : current.display(),
                    current == null ? null : current.capacity(),
                    current == null ? null : current.interaction()
            );
            containersById.put(normalizedContainerId, updated);
            return this;
        }

        @Deprecated
        public Builder primary(ContainerBindType bindType) {
            this.primaryBinding = new BindingSpec(bindType, Map.of());
            return this;
        }

        @Deprecated
        public Builder primary(ContainerBindType bindType, String argKey, String argValue) {
            this.primaryBinding = legacyWithSingleArg(new BindingSpec(bindType, Map.of()), argKey, argValue);
            return this;
        }

        @Deprecated
        public Builder primaryPlayer() {
            this.primaryBinding = new BindingSpec(ContainerBindType.PLAYER, Map.of());
            return this;
        }

        @Deprecated
        public Builder primarySavedData(String dataName, String inventoryKey, int slotCount) {
            LinkedHashMap<String, String> args = new LinkedHashMap<>();
            args.put("dataName", requireText(dataName, "dataName"));
            args.put("inventoryKey", requireText(inventoryKey, "inventoryKey"));
            args.put("slotCount", String.valueOf(requirePositive(slotCount, "slotCount")));
            this.primaryBinding = new BindingSpec(ContainerBindType.SAVED_DATA, args);
            return this;
        }

        @Deprecated
        public Builder primaryBlockEntity(int x, int y, int z, String side) {
            LinkedHashMap<String, String> args = new LinkedHashMap<>();
            args.put("x", String.valueOf(x));
            args.put("y", String.valueOf(y));
            args.put("z", String.valueOf(z));
            if (side != null && !side.trim().isEmpty()) {
                args.put("side", side.trim());
            }
            this.primaryBinding = new BindingSpec(ContainerBindType.BLOCK_ENTITY, args);
            return this;
        }

        @Deprecated
        public Builder primaryEntity(String uuid) {
            this.primaryBinding = new BindingSpec(ContainerBindType.ENTITY, Map.of("uuid", requireText(uuid, "uuid")));
            return this;
        }

        @Deprecated
        public Builder primaryEntity(UUID uuid) {
            return primaryEntity(uuid == null ? null : uuid.toString());
        }

        @Deprecated
        public Builder primaryArg(String argKey, String argValue) {
            if (primaryBinding == null) {
                throw new IllegalStateException("Call primary(bindType) before primaryArg(...)");
            }
            this.primaryBinding = legacyWithSingleArg(primaryBinding, argKey, argValue);
            return this;
        }

        @Deprecated
        public Builder containerIndex(int index, ContainerBindType bindType) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            indexBindings.put(index, new BindingSpec(bindType, Map.of()));
            return this;
        }

        @Deprecated
        public Builder containerIndex(int index, ContainerBindType bindType, String argKey, String argValue) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            indexBindings.put(index, legacyWithSingleArg(new BindingSpec(bindType, Map.of()), argKey, argValue));
            return this;
        }

        @Deprecated
        public Builder containerIndexPlayer(int index) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            indexBindings.put(index, new BindingSpec(ContainerBindType.PLAYER, Map.of()));
            return this;
        }

        @Deprecated
        public Builder containerIndexSavedData(int index, String dataName, String inventoryKey, int slotCount) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            LinkedHashMap<String, String> args = new LinkedHashMap<>();
            args.put("dataName", requireText(dataName, "dataName"));
            args.put("inventoryKey", requireText(inventoryKey, "inventoryKey"));
            args.put("slotCount", String.valueOf(requirePositive(slotCount, "slotCount")));
            indexBindings.put(index, new BindingSpec(ContainerBindType.SAVED_DATA, args));
            return this;
        }

        @Deprecated
        public Builder containerIndexBlockEntity(int index, int x, int y, int z, String side) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            LinkedHashMap<String, String> args = new LinkedHashMap<>();
            args.put("x", String.valueOf(x));
            args.put("y", String.valueOf(y));
            args.put("z", String.valueOf(z));
            if (side != null && !side.trim().isEmpty()) {
                args.put("side", side.trim());
            }
            indexBindings.put(index, new BindingSpec(ContainerBindType.BLOCK_ENTITY, args));
            return this;
        }

        @Deprecated
        public Builder containerIndexEntity(int index, String uuid) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            indexBindings.put(index, new BindingSpec(
                    ContainerBindType.ENTITY,
                    Map.of("uuid", requireText(uuid, "uuid"))
            ));
            return this;
        }

        @Deprecated
        public Builder containerIndexEntity(int index, UUID uuid) {
            return containerIndexEntity(index, uuid == null ? null : uuid.toString());
        }

        @Deprecated
        public Builder containerArg(int index, String argKey, String argValue) {
            BindingSpec current = indexBindings.get(index);
            if (current == null) {
                throw new IllegalStateException("Call containerIndex(index, bindType) before containerArg(...)");
            }
            indexBindings.put(index, legacyWithSingleArg(current, argKey, argValue));
            return this;
        }

        public OpenBindPlan build() {
            return new OpenBindPlan(
                    templatePath,
                    primaryContainerIdOverride,
                    containersById,
                    options,
                    primaryBinding,
                    indexBindings
            );
        }

        public final class ContainerBindBuilder {
            private final String containerId;

            private ContainerBindBuilder(String containerId) {
                this.containerId = containerId;
            }

            public ContainerBindBuilder player() {
                Builder.this.containerPlayer(containerId);
                return this;
            }

            public ContainerBindBuilder savedData(String dataName, String inventoryKey) {
                LinkedHashMap<String, String> args = new LinkedHashMap<>();
                args.put("dataName", requireText(dataName, "dataName"));
                args.put("inventoryKey", requireText(inventoryKey, "inventoryKey"));
                Builder.this.setContainerBind(containerId, new BindOverride(ContainerBindType.SAVED_DATA, args));
                return this;
            }

            public ContainerBindBuilder savedData(String dataName, String inventoryKey, int slotCount) {
                Builder.this.containerSavedData(containerId, dataName, inventoryKey, slotCount);
                return this;
            }

            public ContainerBindBuilder blockEntity(int x, int y, int z, String side) {
                Builder.this.containerBlockEntity(containerId, x, y, z, side);
                return this;
            }

            public ContainerBindBuilder entity(String uuid) {
                Builder.this.containerEntity(containerId, uuid);
                return this;
            }

            public ContainerBindBuilder entity(UUID uuid) {
                Builder.this.containerEntity(containerId, uuid);
                return this;
            }

            public ContainerBindBuilder arg(String argKey, String argValue) {
                Builder.this.containerArg(containerId, argKey, argValue);
                return this;
            }

            public ContainerBindBuilder minCapacity(int minCapacity) {
                Builder.this.containerCapacity(containerId, minCapacity, null, null);
                return this;
            }

            public ContainerBindBuilder exactCapacity(int exactCapacity) {
                Builder.this.containerCapacity(containerId, null, exactCapacity, null);
                return this;
            }

            public ContainerBindBuilder policy(ResizePolicy resizePolicy) {
                Builder.this.containerCapacity(containerId, null, null, resizePolicy);
                return this;
            }

            public Builder done() {
                return Builder.this;
            }

            public OpenBindPlan build() {
                return Builder.this.build();
            }
        }
    }

    private static String normalizeContainerId(String containerId) {
        if (containerId == null) return null;
        String normalized = containerId.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}

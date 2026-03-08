package com.sighs.apricityui.instance.container.bind;


import com.sighs.apricityui.util.common.NormalizeUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class OpenBindPlan {
    private final String templatePath;
    private final String primaryContainerIdOverride;
    private final LinkedHashMap<String, ContainerOverride> containersById;
    private final Options options;

    private OpenBindPlan(String templatePath,
                         String primaryContainerIdOverride,
                         Map<String, ContainerOverride> containersById,
                         Options options) {
        this.templatePath = templatePath == null ? "" : templatePath.trim();
        this.primaryContainerIdOverride = primaryContainerIdOverride == null ? "" : primaryContainerIdOverride.trim();
        this.options = options == null ? new Options(ResizePolicy.KEEP_OVERFLOW) : options;

        this.containersById = new LinkedHashMap<>();
        if (containersById != null) {
            containersById.forEach((containerId, override) -> {
                String normalizedContainerId = NormalizeUtil.normalizeContainerId(containerId);
                if (normalizedContainerId == null || override == null) return;
                this.containersById.put(normalizedContainerId, override);
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

        return new OpenBindPlan(
                mergedTemplatePath,
                mergedPrimaryContainerId,
                mergedContainers,
                mergedOptions
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
        String normalizedContainerId = NormalizeUtil.normalizeContainerId(containerId);
        if (normalizedContainerId == null) return null;
        return containersById.get(normalizedContainerId);
    }

    public Options options() {
        return options;
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
                throw new IllegalArgumentException("bindType is reserved for template ui-only container: " + bindType);
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


    public record CapacityOverride(Integer minCapacity, Integer exactCapacity, ResizePolicy resizePolicy) {
        public CapacityOverride {
            if (minCapacity != null && minCapacity < 0) minCapacity = 0;
            if (exactCapacity != null && exactCapacity < 0) exactCapacity = 0;
            if (resizePolicy == null) resizePolicy = ResizePolicy.KEEP_OVERFLOW;
        }
    }

    public record ContainerOverride(BindOverride bind,
                                    CapacityOverride capacity) {
        public ContainerOverride merge(ContainerOverride override) {
            if (override == null) return this;
            return new ContainerOverride(
                    override.bind != null ? override.bind : bind,
                    override.capacity != null ? override.capacity : capacity
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

    /**
     * {@link OpenBindPlan} 的链式构建器。
     */
    public static final class Builder {
        private String templatePath;
        private String primaryContainerIdOverride;
        private final LinkedHashMap<String, ContainerOverride> containersById = new LinkedHashMap<>();
        private Options options = new Options(ResizePolicy.KEEP_OVERFLOW);

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
            String normalized = NormalizeUtil.normalizeContainerId(requireText(containerId, "containerId"));
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

        /**
         * 设置布局模板路径。
         *
         * @param templatePath 模板路径；传入 {@code null} 时表示不覆盖
         * @return 当前构建器
         */
        public Builder templatePath(String templatePath) {
            this.templatePath = templatePath == null ? null : templatePath.trim();
            return this;
        }

        /**
         * 指定主容器 ID（用于覆盖模板中的 primary 标记）。
         *
         * @param containerId 容器 ID（大小写不敏感，内部会标准化）
         * @return 当前构建器
         */
        public Builder primaryContainer(String containerId) {
            this.primaryContainerIdOverride = requireContainerId(containerId);
            return this;
        }

        /**
         * 设置全局默认容量裁剪策略。
         *
         * @param resizePolicy 裁剪策略；为 {@code null} 时回退为 KEEP_OVERFLOW
         * @return 当前构建器
         */
        public Builder defaultResizePolicy(ResizePolicy resizePolicy) {
            this.options = new Options(resizePolicy);
            return this;
        }

        /**
         * 进入指定容器的链式绑定子构建器。
         *
         * @param containerId 目标容器 ID
         * @return 容器级子构建器
         */
        public ContainerBindBuilder bind(String containerId) {
            return new ContainerBindBuilder(requireContainerId(containerId));
        }

        /**
         * 进入主容器绑定子构建器，并将该容器标记为 primary。
         *
         * @param containerId 主容器 ID
         * @return 容器级子构建器
         */
        public ContainerBindBuilder primaryBind(String containerId) {
            String normalizedContainerId = requireContainerId(containerId);
            this.primaryContainerIdOverride = normalizedContainerId;
            return new ContainerBindBuilder(normalizedContainerId);
        }

        private Builder appendBindArg(String containerId, String argKey, String argValue) {
            String normalizedContainerId = requireContainerId(containerId);
            ContainerOverride current = containersById.get(normalizedContainerId);
            if (current == null || current.bind() == null) {
                throw new IllegalStateException("Call bind(containerId).bindType(...) before arg(...)");
            }
            return applyBind(normalizedContainerId, bindWithSingleArg(current.bind(), argKey, argValue));
        }

        private Builder mergeCapacity(String containerId, Integer minCapacity, Integer exactCapacity, ResizePolicy resizePolicy) {
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
                    new CapacityOverride(mergedMinCapacity, mergedExactCapacity, mergedResizePolicy)
            );
            containersById.put(normalizedContainerId, updated);
            return this;
        }

        private Builder applyBind(String containerId, BindOverride bindOverride) {
            String normalizedContainerId = requireContainerId(containerId);
            ContainerOverride current = containersById.get(normalizedContainerId);
            ContainerOverride updated = new ContainerOverride(
                    bindOverride,
                    current == null ? null : current.capacity()
            );
            containersById.put(normalizedContainerId, updated);
            return this;
        }

        private static Map<String, String> savedDataArgs(String dataName, String inventoryKey, Integer slotCount) {
            LinkedHashMap<String, String> args = new LinkedHashMap<>();
            args.put("dataName", requireText(dataName, "dataName"));
            args.put("inventoryKey", requireText(inventoryKey, "inventoryKey"));
            if (slotCount != null) {
                args.put("slotCount", String.valueOf(requirePositive(slotCount, "slotCount")));
            }
            return Map.copyOf(args);
        }

        private static Map<String, String> blockEntityArgs(int x, int y, int z, String side) {
            LinkedHashMap<String, String> args = new LinkedHashMap<>();
            args.put("x", String.valueOf(x));
            args.put("y", String.valueOf(y));
            args.put("z", String.valueOf(z));
            if (side != null && !side.trim().isEmpty()) {
                args.put("side", side.trim());
            }
            return Map.copyOf(args);
        }

        private static BindOverride entityBind(String uuid) {
            return new BindOverride(
                    ContainerBindType.ENTITY,
                    Map.of("uuid", requireText(uuid, "uuid"))
            );
        }

        /**
         * 生成不可变的 {@link OpenBindPlan}。
         *
         * @return 构建完成的绑定计划
         */
        public OpenBindPlan build() {
            return new OpenBindPlan(
                    templatePath,
                    primaryContainerIdOverride,
                    containersById,
                    options
            );
        }

        /**
         * 容器级链式配置器，聚焦单个 containerId 的绑定与容量策略设置。
         */
        public final class ContainerBindBuilder {
            private final String containerId;

            private ContainerBindBuilder(String containerId) {
                this.containerId = containerId;
            }

            /**
             * 设置通用绑定类型（无额外参数）。
             *
             * @param bindType 绑定类型
             * @return 当前容器子构建器
             */
            public ContainerBindBuilder bindType(ContainerBindType bindType) {
                Builder.this.applyBind(containerId, new BindOverride(bindType, Map.of()));
                return this;
            }

            /**
             * 结束当前容器链并返回上层构建器。
             *
             * @return 上层 {@link Builder}
             */
            public Builder bind() {
                return Builder.this;
            }

            /**
             * 结束当前容器链并进入另一个容器绑定链。
             *
             * @param containerId 下一个容器 ID
             * @return 下一个容器的子构建器
             */
            public ContainerBindBuilder bind(String containerId) {
                return Builder.this.bind(containerId);
            }

            /**
             * 结束当前容器链并进入新的主容器绑定链。
             *
             * @param containerId 下一个主容器 ID
             * @return 下一个容器的子构建器
             */
            public ContainerBindBuilder primaryBind(String containerId) {
                return Builder.this.primaryBind(containerId);
            }

            /**
             * 设置为玩家库存绑定。
             *
             * @return 当前容器子构建器
             */
            public ContainerBindBuilder player() {
                return bindType(ContainerBindType.PLAYER);
            }

            /**
             * 设置为 SAVED_DATA 绑定（不显式写 slotCount）。
             *
             * @param dataName SavedData 名称
             * @param inventoryKey SavedData 中的库存键
             * @return 当前容器子构建器
             */
            public ContainerBindBuilder savedData(String dataName, String inventoryKey) {
                Builder.this.applyBind(containerId, new BindOverride(
                        ContainerBindType.SAVED_DATA,
                        savedDataArgs(dataName, inventoryKey, null)
                ));
                return this;
            }

            /**
             * 设置为 SAVED_DATA 绑定并指定容量。
             *
             * @param dataName SavedData 名称
             * @param inventoryKey SavedData 中的库存键
             * @param slotCount 槽位数量（>0）
             * @return 当前容器子构建器
             */
            public ContainerBindBuilder savedData(String dataName, String inventoryKey, int slotCount) {
                Builder.this.applyBind(containerId, new BindOverride(
                        ContainerBindType.SAVED_DATA,
                        savedDataArgs(dataName, inventoryKey, slotCount)
                ));
                return this;
            }

            /**
             * 设置为方块实体绑定。
             *
             * @param x 方块 X 坐标
             * @param y 方块 Y 坐标
             * @param z 方块 Z 坐标
             * @param side 可选朝向
             * @return 当前容器子构建器
             */
            public ContainerBindBuilder blockEntity(int x, int y, int z, String side) {
                Builder.this.applyBind(containerId, new BindOverride(
                        ContainerBindType.BLOCK_ENTITY,
                        blockEntityArgs(x, y, z, side)
                ));
                return this;
            }

            /**
             * 设置为实体绑定（字符串 UUID）。
             *
             * @param uuid 实体 UUID 字符串
             * @return 当前容器子构建器
             */
            public ContainerBindBuilder entity(String uuid) {
                Builder.this.applyBind(containerId, entityBind(uuid));
                return this;
            }

            /**
             * 设置为实体绑定（UUID 对象）。
             *
             * @param uuid 实体 UUID
             * @return 当前容器子构建器
             */
            public ContainerBindBuilder entity(UUID uuid) {
                Builder.this.applyBind(containerId, entityBind(uuid == null ? null : uuid.toString()));
                return this;
            }

            /**
             * 追加或覆盖绑定参数。
             *
             * @param argKey 参数键
             * @param argValue 参数值
             * @return 当前容器子构建器
             */
            public ContainerBindBuilder arg(String argKey, String argValue) {
                Builder.this.appendBindArg(containerId, argKey, argValue);
                return this;
            }

            /**
             * 设置最小容量。
             *
             * @param minCapacity 最小容量
             * @return 当前容器子构建器
             */
            public ContainerBindBuilder minCapacity(int minCapacity) {
                Builder.this.mergeCapacity(containerId, minCapacity, null, null);
                return this;
            }

            /**
             * 设置精确容量。
             *
             * @param exactCapacity 精确容量
             * @return 当前容器子构建器
             */
            public ContainerBindBuilder exactCapacity(int exactCapacity) {
                Builder.this.mergeCapacity(containerId, null, exactCapacity, null);
                return this;
            }

            /**
             * 设置容量裁剪策略。
             *
             * @param resizePolicy 裁剪策略
             * @return 当前容器子构建器
             */
            public ContainerBindBuilder policy(ResizePolicy resizePolicy) {
                Builder.this.mergeCapacity(containerId, null, null, resizePolicy);
                return this;
            }

            /**
             * 直接完成整个计划构建。
             *
             * @return 构建完成的绑定计划
             */
            public OpenBindPlan build() {
                return Builder.this.build();
            }
        }
    }
}

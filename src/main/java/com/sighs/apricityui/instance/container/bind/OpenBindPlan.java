package com.sighs.apricityui.instance.container.bind;

import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import com.sighs.apricityui.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 开屏绑定计划。
 * primary 对应主容器绑定；indexBindings 对应按顶层 container 顺序索引绑定。
 */
public final class OpenBindPlan {
    private final BindingSpec primaryBinding;
    private final LinkedHashMap<Integer, BindingSpec> indexBindings;

    private OpenBindPlan(BindingSpec primaryBinding, Map<Integer, BindingSpec> indexBindings) {
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

    public BindingSpec primaryBinding() {
        return primaryBinding;
    }

    public Map<Integer, BindingSpec> indexBindings() {
        return new HashMap<>(indexBindings);
    }

    public BindingSpec bindingForIndex(int index) {
        return indexBindings.get(index);
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class BindingSpec {
        private ContainerSchema.Descriptor.BindType bindType;
        private Map<String, String> args;

        public BindingSpec() {
            if (bindType == null) {
                throw new IllegalArgumentException("bindType cannot be null");
            }
            if (bindType == ContainerSchema.Descriptor.BindType.VIRTUAL_UI) {
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
            args = new HashMap<>(normalizedArgs);
        }
    }

    /**
     * 用于逐步构建 {@link OpenBindPlan}。
     * <p>
     * 关于 {@code argKey}/{@code argValue}：
     * 这组参数会进入绑定配置的 {@code args} 字典，并在服务端解析数据源时透传给
     * {@code ApricityDataSourceResolver.resolve(...)}。
     * 框架不会在 Builder 阶段解释其业务含义，只做“键名规范化 + 字典存储”。
     * <p>
     * 常见内建 bindType 参数约定：
     * {@code saved_data}: {@code dataName}, {@code inventoryKey}, {@code slotCount}
     * {@code block_entity}: {@code x}, {@code y}, {@code z}, {@code side}
     * {@code entity}: {@code uuid}
     * {@code player}: 无需额外参数
     */
    public static final class Builder {
        private final LinkedHashMap<Integer, BindingSpec> indexBindings = new LinkedHashMap<>();
        private BindingSpec primaryBinding;

        private static BindingSpec withSingleArg(BindingSpec base, String argKey, String argValue) {
            if (base == null) throw new IllegalArgumentException("base binding cannot be null");
            if (StringUtils.isNullOrEmptyEx(argKey)) {
                throw new IllegalArgumentException("argKey cannot be blank");
            }
            LinkedHashMap<String, String> args = new LinkedHashMap<>(base.args());
            args.put(argKey.trim(), argValue == null ? "" : argValue);
            return new BindingSpec(base.bindType(), args);
        }

        private static String requireText(String value, String fieldName) {
            if (StringUtils.isNullOrEmptyEx(value)) {
                throw new IllegalArgumentException(fieldName + " cannot be blank");
            }
            return value.trim();
        }

        private static int requirePositive(int value, String fieldName) {
            if (value <= 0) throw new IllegalArgumentException(fieldName + " must be > 0");
            return value;
        }

        private static BindingSpec savedDataSpec(String dataName, String inventoryKey, int slotCount) {
            LinkedHashMap<String, String> args = new LinkedHashMap<>();
            args.put("dataName", requireText(dataName, "dataName"));
            args.put("inventoryKey", requireText(inventoryKey, "inventoryKey"));
            args.put("slotCount", String.valueOf(requirePositive(slotCount, "slotCount")));
            return new BindingSpec(ContainerSchema.Descriptor.BindType.SAVED_DATA, args);
        }

        private static BindingSpec blockEntitySpec(int x, int y, int z, String side) {
            LinkedHashMap<String, String> args = new LinkedHashMap<>();
            args.put("x", String.valueOf(x));
            args.put("y", String.valueOf(y));
            args.put("z", String.valueOf(z));
            if (StringUtils.isNotNullOrEmptyEx(side)) {
                args.put("side", side.trim());
            }
            return new BindingSpec(ContainerSchema.Descriptor.BindType.BLOCK_ENTITY, args);
        }

        private static BindingSpec entitySpec(String uuid) {
            return new BindingSpec(ContainerSchema.Descriptor.BindType.ENTITY, new HashMap<String, String>() {{
                put("uuid", requireText(uuid, "uuid"));
            }});
        }

        /**
         * 设置主容器（primary container）的绑定规则，不附带参数。
         * <p>
         * 若此前已调用过 {@code primary(...)}，本次调用会覆盖旧的主容器绑定配置。
         *
         * @param bindType 绑定类型
         * @return 当前 Builder，支持链式调用
         * @throws IllegalArgumentException 当 bindType 非法时抛出
         */
        public Builder primary(ContainerSchema.Descriptor.BindType bindType) {
            this.primaryBinding = new BindingSpec(bindType, new HashMap<>());
            return this;
        }

        /**
         * 设置主容器（primary container）的绑定规则，并在创建时附加一个参数。
         * <p>
         * 该方法等价于先调用 {@link #primary(ContainerSchema.Descriptor.BindType)}，再调用一次 {@link #primaryArg(String, String)}。
         * {@code argKey/argValue} 会写入该主容器绑定的参数字典，后续由数据源解析器按 bindType 使用。
         *
         * @param bindType 绑定类型
         * @param argKey   参数名（不能为空白，例如 {@code slotCount}/{@code dataName}/{@code hand}）
         * @param argValue 参数值（例如 {@code 27}/{@code my_inv}/{@code off_hand}）；为 {@code null} 时会归一化为空字符串
         * @return 当前 Builder，支持链式调用
         * @throws IllegalArgumentException 当 bindType 非法或 argKey 为空白时抛出
         */
        public Builder primary(ContainerSchema.Descriptor.BindType bindType, String argKey, String argValue) {
            this.primaryBinding = withSingleArg(new BindingSpec(bindType, new HashMap<>()), argKey, argValue);
            return this;
        }

        /**
         * 将主容器绑定为玩家库存。
         *
         * @return 当前 Builder，支持链式调用
         */
        public Builder primaryPlayer() {
            this.primaryBinding = new BindingSpec(ContainerSchema.Descriptor.BindType.PLAYER, new HashMap<>());
            return this;
        }

        /**
         * 将主容器绑定为 SavedData 存储源。
         *
         * @param dataName     SavedData 名称
         * @param inventoryKey 逻辑背包键
         * @param slotCount    槽位数量（必须大于 0）
         * @return 当前 Builder，支持链式调用
         */
        public Builder primarySavedData(String dataName, String inventoryKey, int slotCount) {
            this.primaryBinding = savedDataSpec(dataName, inventoryKey, slotCount);
            return this;
        }

        /**
         * 将主容器绑定为方块实体库存源。
         *
         * @param x    方块 X
         * @param y    方块 Y
         * @param z    方块 Z
         * @param side 可选能力侧（如 north/up），为空表示不指定
         * @return 当前 Builder，支持链式调用
         */
        public Builder primaryBlockEntity(int x, int y, int z, String side) {
            this.primaryBinding = blockEntitySpec(x, y, z, side);
            return this;
        }

        /**
         * 将主容器绑定为实体库存源（按 UUID 定位）。
         *
         * @param uuid 实体 UUID 字符串
         * @return 当前 Builder，支持链式调用
         */
        public Builder primaryEntity(String uuid) {
            this.primaryBinding = entitySpec(uuid);
            return this;
        }

        /**
         * 将主容器绑定为实体库存源（按 UUID 定位）。
         *
         * @param uuid 实体 UUID
         * @return 当前 Builder，支持链式调用
         */
        public Builder primaryEntity(UUID uuid) {
            return primaryEntity(uuid == null ? null : uuid.toString());
        }

        /**
         * 为主容器绑定追加或覆盖一个参数。
         * <p>
         * 调用前必须已通过 {@link #primary(ContainerSchema.Descriptor.BindType)} 或 {@link #primary(ContainerSchema.Descriptor.BindType, String, String)}
         * 初始化主容器绑定，否则会抛出状态异常。
         * 若 {@code argKey} 已存在，则本次调用会覆盖旧值。
         *
         * @param argKey   参数名（不能为空白，例如 {@code slotCount}/{@code inventoryKey}）
         * @param argValue 参数值；为 {@code null} 时会归一化为空字符串
         * @return 当前 Builder，支持链式调用
         * @throws IllegalStateException    当主容器绑定尚未初始化时抛出
         * @throws IllegalArgumentException 当 argKey 为空白时抛出
         */
        public Builder primaryArg(String argKey, String argValue) {
            if (primaryBinding == null) {
                throw new IllegalStateException("Call primary(bindType) before primaryArg(...)");
            }
            this.primaryBinding = withSingleArg(primaryBinding, argKey, argValue);
            return this;
        }

        /**
         * 按顶层容器顺序索引设置某个容器的绑定规则，不附带参数。
         * <p>
         * 若同一 {@code index} 已存在配置，本次调用会覆盖旧配置。
         *
         * @param index    顶层容器索引（从 0 开始）
         * @param bindType 绑定类型
         * @return 当前 Builder，支持链式调用
         * @throws IllegalArgumentException 当 index 小于 0 或 bindType 非法时抛出
         */
        public Builder containerIndex(int index, ContainerSchema.Descriptor.BindType bindType) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            indexBindings.put(index, new BindingSpec(bindType, new HashMap<>()));
            return this;
        }

        /**
         * 按顶层容器顺序索引设置某个容器的绑定规则，并在创建时附加一个参数。
         * <p>
         * 若同一 {@code index} 已存在配置，本次调用会覆盖旧配置。
         * {@code argKey/argValue} 仅作用于该索引容器，不影响 primary 或其他索引容器。
         *
         * @param index    顶层容器索引（从 0 开始）
         * @param bindType 绑定类型
         * @param argKey   参数名（不能为空白，例如 {@code x}/{@code y}/{@code z}/{@code side}）
         * @param argValue 参数值（例如 {@code 10}/{@code north}）；为 {@code null} 时会归一化为空字符串
         * @return 当前 Builder，支持链式调用
         * @throws IllegalArgumentException 当 index 小于 0、bindType 非法或 argKey 为空白时抛出
         */
        public Builder containerIndex(int index, ContainerSchema.Descriptor.BindType bindType, String argKey, String argValue) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            indexBindings.put(index, withSingleArg(new BindingSpec(bindType, new HashMap<>()), argKey, argValue));
            return this;
        }

        /**
         * 将指定索引容器绑定为玩家库存。
         *
         * @param index 顶层容器索引（从 0 开始）
         * @return 当前 Builder，支持链式调用
         */
        public Builder containerIndexPlayer(int index) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            indexBindings.put(index, new BindingSpec(ContainerSchema.Descriptor.BindType.PLAYER, new HashMap<>()));
            return this;
        }

        /**
         * 将指定索引容器绑定为 SavedData 存储源。
         *
         * @param index        顶层容器索引（从 0 开始）
         * @param dataName     SavedData 名称
         * @param inventoryKey 逻辑背包键
         * @param slotCount    槽位数量（必须大于 0）
         * @return 当前 Builder，支持链式调用
         */
        public Builder containerIndexSavedData(int index, String dataName, String inventoryKey, int slotCount) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            indexBindings.put(index, savedDataSpec(dataName, inventoryKey, slotCount));
            return this;
        }

        /**
         * 将指定索引容器绑定为方块实体库存源。
         *
         * @param index 顶层容器索引（从 0 开始）
         * @param x     方块 X
         * @param y     方块 Y
         * @param z     方块 Z
         * @param side  可选能力侧（如 north/up），为空表示不指定
         * @return 当前 Builder，支持链式调用
         */
        public Builder containerIndexBlockEntity(int index, int x, int y, int z, String side) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            indexBindings.put(index, blockEntitySpec(x, y, z, side));
            return this;
        }

        /**
         * 将指定索引容器绑定为实体库存源（按 UUID 定位）。
         *
         * @param index 顶层容器索引（从 0 开始）
         * @param uuid  实体 UUID 字符串
         * @return 当前 Builder，支持链式调用
         */
        public Builder containerIndexEntity(int index, String uuid) {
            if (index < 0) throw new IllegalArgumentException("container index must be >= 0");
            indexBindings.put(index, entitySpec(uuid));
            return this;
        }

        /**
         * 将指定索引容器绑定为实体库存源（按 UUID 定位）。
         *
         * @param index 顶层容器索引（从 0 开始）
         * @param uuid  实体 UUID
         * @return 当前 Builder，支持链式调用
         */
        public Builder containerIndexEntity(int index, UUID uuid) {
            return containerIndexEntity(index, uuid == null ? null : uuid.toString());
        }

        /**
         * 为指定索引容器绑定追加或覆盖一个参数。
         * <p>
         * 调用前必须已通过 {@link #containerIndex(int, ContainerSchema.Descriptor.BindType)} 或
         * {@link #containerIndex(int, ContainerSchema.Descriptor.BindType, String, String)} 初始化该索引的绑定。
         * 该方法常用于分多步补齐参数（例如先设置 bindType，再逐个补充 {@code x/y/z/side}）。
         *
         * @param index    顶层容器索引（从 0 开始）
         * @param argKey   参数名（不能为空白；若已存在会被覆盖）
         * @param argValue 参数值；为 {@code null} 时会归一化为空字符串
         * @return 当前 Builder，支持链式调用
         * @throws IllegalStateException    当 index 对应绑定尚未初始化时抛出
         * @throws IllegalArgumentException 当 argKey 为空白时抛出
         */
        public Builder containerArg(int index, String argKey, String argValue) {
            BindingSpec current = indexBindings.get(index);
            if (current == null) {
                throw new IllegalStateException("Call containerIndex(index, bindType) before containerArg(...)");
            }
            indexBindings.put(index, withSingleArg(current, argKey, argValue));
            return this;
        }

        /**
         * 构建不可变的开屏绑定计划对象。
         * <p>
         * 返回对象会拷贝当前 Builder 状态；后续继续修改 Builder 不会影响已构建结果。
         *
         * @return {@link OpenBindPlan} 实例
         */
        public OpenBindPlan build() {
            return new OpenBindPlan(primaryBinding, indexBindings);
        }
    }
}

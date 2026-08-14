package com.sighs.apricityui.network.handler;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.container.filter.ContainerSlotSelector;
import com.sighs.apricityui.container.filter.FilterUtil;
import com.sighs.apricityui.element.ContainerDeclaration;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 容器绑定构建器，用于链式声明 Screen 的数据绑定关系。
 */
public final class BindingBuilder {
    private final List<ContainerDeclaration> declarations = new ArrayList<>();
    private final Map<String, Map<String, String>> argsById = new LinkedHashMap<>();
    private final Map<ContainerSlotSelector, FilterUtil> filtersBySelector = new LinkedHashMap<>();
    private final BindingStep baseStep = new BaseStep();
    private boolean primarySet;

    /** 后续绑定的基础步骤；玩家绑定不提供 slot 或 filter。 */
    public interface BindingStep {
        BindingStep player();

        SlotBindingStep saveddata();

        SlotBindingStep saveddata(String dataName);

        SlotBindingStep saveddata(String dataName, int capacity);

        SlotBindingStep blockEntity(BlockPos pos);

        SlotBindingStep blockEntity(BlockPos pos, int capacity);

        SlotBindingStep entity(int entityId);

        SlotBindingStep entity(int entityId, int capacity);
    }

    /** 最近一个非玩家绑定源的后续步骤，可用 CSS selector 选择其直接归属的槽位。 */
    public interface SlotBindingStep extends BindingStep {
        FilterableSlotStep slot(String selector);
    }

    /** 选择到的槽位后续步骤。filter 只限制放入资格，不影响提取或数据源自身限制。 */
    public interface FilterableSlotStep extends SlotBindingStep {
        FilterableSlotStep filter(FilterUtil filter);
    }

    /** 绑定玩家背包容器（36 格）。 */
    public BindingStep player() {
        declarations.add(new ContainerDeclaration("player", ContainerBindType.PLAYER, 36, false));
        return baseStep;
    }

    public SlotBindingStep saveddata() {
        return saveddata("apricityui_data", 9);
    }

    public SlotBindingStep saveddata(String dataName) {
        return saveddata(dataName, 9);
    }

    public SlotBindingStep saveddata(String dataName, int capacity) {
        declare("saved_data", ContainerBindType.SAVED_DATA, capacity, Map.of("data_name", dataName));
        return new SlotStep("saved_data");
    }

    public SlotBindingStep blockEntity(BlockPos pos) {
        return blockEntity(pos, 0);
    }

    public SlotBindingStep blockEntity(BlockPos pos, int capacity) {
        declare("block_entity", ContainerBindType.BLOCK_ENTITY, capacity, Map.of(
                "x", String.valueOf(pos.getX()),
                "y", String.valueOf(pos.getY()),
                "z", String.valueOf(pos.getZ())
        ));
        return new SlotStep("block_entity");
    }

    public SlotBindingStep entity(int entityId) {
        return entity(entityId, 0);
    }

    public SlotBindingStep entity(int entityId, int capacity) {
        declare("entity", ContainerBindType.ENTITY, capacity, Map.of("entity_id", String.valueOf(entityId)));
        return new SlotStep("entity");
    }

    private void declare(String id, ContainerBindType bindType, int capacity, Map<String, String> args) {
        boolean primary = !primarySet;
        if (primary) primarySet = true;
        declarations.add(new ContainerDeclaration(id, bindType, capacity, primary));
        argsById.put(id, args);
    }

    private void addFilter(ContainerSlotSelector selector, FilterUtil filter) {
        if (selector == null || !selector.isValid() || filter == null) return;
        filtersBySelector.merge(selector, filter, FilterUtil::and);
    }

    List<ContainerDeclaration> declarations() {
        return declarations;
    }

    Map<String, Map<String, String>> argsById() {
        return argsById;
    }

    Map<ContainerSlotSelector, FilterUtil> filtersBySelector() {
        return filtersBySelector;
    }

    private class BaseStep implements BindingStep {
        @Override
        public BindingStep player() {
            return BindingBuilder.this.player();
        }

        @Override
        public SlotBindingStep saveddata() {
            return BindingBuilder.this.saveddata();
        }

        @Override
        public SlotBindingStep saveddata(String dataName) {
            return BindingBuilder.this.saveddata(dataName);
        }

        @Override
        public SlotBindingStep saveddata(String dataName, int capacity) {
            return BindingBuilder.this.saveddata(dataName, capacity);
        }

        @Override
        public SlotBindingStep blockEntity(BlockPos pos) {
            return BindingBuilder.this.blockEntity(pos);
        }

        @Override
        public SlotBindingStep blockEntity(BlockPos pos, int capacity) {
            return BindingBuilder.this.blockEntity(pos, capacity);
        }

        @Override
        public SlotBindingStep entity(int entityId) {
            return BindingBuilder.this.entity(entityId);
        }

        @Override
        public SlotBindingStep entity(int entityId, int capacity) {
            return BindingBuilder.this.entity(entityId, capacity);
        }
    }

    private class SlotStep extends BaseStep implements SlotBindingStep {
        private final String containerId;

        private SlotStep(String containerId) {
            this.containerId = containerId;
        }

        @Override
        public FilterableSlotStep slot(String selector) {
            return new FilterableStep(new ContainerSlotSelector(containerId, selector));
        }
    }

    private final class FilterableStep extends SlotStep implements FilterableSlotStep {
        private final ContainerSlotSelector selector;

        private FilterableStep(ContainerSlotSelector selector) {
            super(selector.containerId());
            this.selector = selector;
        }

        @Override
        public FilterableSlotStep filter(FilterUtil filter) {
            addFilter(selector, filter);
            return this;
        }
    }
}

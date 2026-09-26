package com.sighs.apricityui.spi;

import net.minecraft.core.BlockPos;

/**
 * Loader-neutral binding builder exposed by {@link AuiPendingMenu}.
 *
 * <p>Loader implementations may return more specific step types, while this
 * contract keeps the public menu API strongly typed across loaders.</p>
 */
public interface AuiBindingBuilder {
    BindingStep player();

    SlotBindingStep saveddata();

    SlotBindingStep saveddata(String dataName);

    SlotBindingStep saveddata(String dataName, int capacity);

    SlotBindingStep blockEntity(BlockPos pos);

    SlotBindingStep blockEntity(BlockPos pos, int capacity);

    SlotBindingStep entity(int entityId);

    SlotBindingStep entity(int entityId, int capacity);

    interface BindingStep {
        BindingStep player();

        SlotBindingStep saveddata();

        SlotBindingStep saveddata(String dataName);

        SlotBindingStep saveddata(String dataName, int capacity);

        SlotBindingStep blockEntity(BlockPos pos);

        SlotBindingStep blockEntity(BlockPos pos, int capacity);

        SlotBindingStep entity(int entityId);

        SlotBindingStep entity(int entityId, int capacity);
    }

    interface SlotBindingStep extends BindingStep {
        FilterableSlotStep slot(String selector);
    }

    interface FilterableSlotStep extends SlotBindingStep {
        FilterableSlotStep filter(AuiBindingFilter filter);
    }
}

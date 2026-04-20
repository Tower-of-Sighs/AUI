package com.sighs.apricityui.mixin.accessor;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Slot.class, remap = false)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void setX(int value);

    @Mutable
    @Accessor("y")
    void setY(int value);
}

package com.sighs.apricityui.item;

import com.sighs.apricityui.registry.ApricityItems;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.FluidKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

public final class WrappedGenericStackItem extends Item {
    public WrappedGenericStackItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack wrap(GenericStack stack) {
        ItemStack result = new ItemStack(ApricityItems.WRAPPED_GENERIC_STACK.get());
        result.setTag(GenericStack.writeTag(stack));
        return result;
    }

    public static GenericStack unwrap(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof WrappedGenericStackItem)) return null;
        return GenericStack.readTag(stack.getTag());
    }

    @Override
    public Component getName(ItemStack stack) {
        GenericStack generic = unwrap(stack);
        return generic == null ? super.getName(stack) : generic.what().displayName();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        GenericStack generic = unwrap(stack);
        if (generic == null) return;
        String suffix = generic.what() instanceof FluidKey ? " mB" : "";
        tooltip.add(Component.literal(generic.amount() + suffix).withStyle(ChatFormatting.GRAY));
    }
}

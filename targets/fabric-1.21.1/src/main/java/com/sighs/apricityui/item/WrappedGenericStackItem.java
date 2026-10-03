package com.sighs.apricityui.item;

import com.sighs.apricityui.registry.ApricityItems;
import com.sighs.apricityui.stack.FluidKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.GenericStackTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class WrappedGenericStackItem extends Item {
    public WrappedGenericStackItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack wrap(GenericStack stack) {
        ItemStack result = new ItemStack(ApricityItems.WRAPPED_GENERIC_STACK);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(GenericStackTypes.writeStack(stack)));
        return result;
    }

    public static GenericStack unwrap(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof WrappedGenericStackItem)) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : GenericStackTypes.readStack(data.copyTag());
    }

    @Override
    public Component getName(ItemStack stack) {
        GenericStack generic = unwrap(stack);
        return generic == null ? super.getName(stack) : generic.key().displayName();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GenericStack generic = unwrap(stack);
        if (generic != null) tooltip.add(Component.literal(generic.amount() + (generic.key() instanceof FluidKey ? " mB" : "")).withStyle(ChatFormatting.GRAY));
    }
}

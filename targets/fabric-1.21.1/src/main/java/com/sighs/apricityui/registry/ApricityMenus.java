package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.container.SlotLayout;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

public final class ApricityMenus {
    private static final StreamCodec<RegistryFriendlyByteBuf, SlotLayout> SLOT_LAYOUT_CODEC =
            StreamCodec.of((buf, layout) -> layout.write(buf), SlotLayout::read);

    public static final MenuType<ApricityContainerMenu> APRICITY_CONTAINER = Registry.register(
            BuiltInRegistries.MENU,
            ResourceLocation.fromNamespaceAndPath(ApricityUI.MODID, "apricity_container"),
            new ExtendedScreenHandlerType<>(ApricityContainerMenu::new, SLOT_LAYOUT_CODEC)
    );

    private ApricityMenus() {
    }

    public static void register() {
    }
}

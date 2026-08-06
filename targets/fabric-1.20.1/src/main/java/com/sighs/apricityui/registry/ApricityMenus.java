package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

public final class ApricityMenus {
    public static final MenuType<ApricityContainerMenu> APRICITY_CONTAINER = Registry.register(
            BuiltInRegistries.MENU,
            new ResourceLocation(ApricityUI.MODID, "apricity_container"),
            new ExtendedScreenHandlerType<>(ApricityContainerMenu::new)
    );
    private ApricityMenus() { }
    public static void register() { }
}

package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ApricityMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ApricityUI.MODID);

    public static final RegistryObject<MenuType<ApricityContainerMenu>> APRICITY_CONTAINER =
            MENUS.register("apricity_container", () -> IForgeMenuType.create(ApricityContainerMenu::new));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}

package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject;

public final class ApricityMenus {
    // Forge 36 uses the legacy CONTAINERS registry for menu types.
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.CONTAINERS, ApricityUI.MODID);

    public static final RegistryObject<MenuType<ApricityContainerMenu>> APRICITY_CONTAINER =
            MENUS.register("apricity_container", () -> IForgeContainerType.create(ApricityContainerMenu::new));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}

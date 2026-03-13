package com.sighs.apricityui.registry;

import cc.sighs.oelib.registry.DeferredRegister;
import cc.sighs.oelib.registry.RegisterSupplier;
import cc.sighs.oelib.registry.extra.MenuRegister;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.ApricityContainerScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public final class ApricityMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ApricityUI.MODID);

    public static final RegisterSupplier<MenuType<ApricityContainerMenu>> APRICITY_CONTAINER =
            MENUS.register("apricity_container", () -> MenuRegister.ofExtended(ApricityContainerMenu::new));

    public static void register() {
        MENUS.register();
    }

    @SuppressWarnings("RedundantTypeArguments")
    public static void registerScreen() {
        MenuScreens.<ApricityContainerMenu, ApricityContainerScreen>register( // 如果 IDE 这里让你移除类型实参，不必理会，移了会炸
                ApricityMenus.APRICITY_CONTAINER.get(),
                (menu, inventory, title) -> new ApricityContainerScreen(menu, inventory, title)
        );
    }
}

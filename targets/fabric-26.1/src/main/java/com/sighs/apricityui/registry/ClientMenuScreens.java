package com.sighs.apricityui.registry;

import com.sighs.apricityui.screen.ApricityContainerMenu;
import com.sighs.apricityui.screen.ApricityContainerScreen;
import net.minecraft.client.gui.screens.MenuScreens;

public final class ClientMenuScreens {
    private ClientMenuScreens() { }
    public static void register() {
        MenuScreens.register(ApricityMenus.APRICITY_CONTAINER, ApricityContainerScreen::new);
    }
}

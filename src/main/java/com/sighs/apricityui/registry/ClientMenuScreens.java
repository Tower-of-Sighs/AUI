package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.ApricityContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = ApricityUI.MOD_ID, value = Dist.CLIENT)
public class ClientMenuScreens {
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.<ApricityContainerMenu, ApricityContainerScreen>register(
                ApricityMenus.APRICITY_CONTAINER.get(),
                ApricityContainerScreen::new
        );
    }
}

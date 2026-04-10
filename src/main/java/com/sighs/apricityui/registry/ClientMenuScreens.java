package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.ApricityContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class ClientMenuScreens {
    @SubscribeEvent
    @SuppressWarnings("RedundantTypeArguments")
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        // 如果 IDE 这里让你移除类型实参，不必理会，移了会炸
        event.<ApricityContainerMenu, ApricityContainerScreen>register(
                ApricityMenus.APRICITY_CONTAINER.get(),
                ApricityContainerScreen::new
        );
    }
}
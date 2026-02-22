package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.ApricityContainerScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientMenuScreens {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(
                () -> MenuScreens.<ApricityContainerMenu, ApricityContainerScreen>register(
                        ApricityMenus.APRICITY_CONTAINER.get(),
                        (menu, inventory, title) -> new ApricityContainerScreen(menu, inventory, title)
                )
        );
    }
}

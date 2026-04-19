package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.lang.reflect.Constructor;

@EventBusSubscriber(modid = ApricityUI.MOD_ID, value = Dist.CLIENT)
public class ClientMenuScreens {
    private static final String CONTAINER_SCREEN_CLASS = "com.sighs.apricityui.instance.ApricityContainerScreen";

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(
                ApricityMenus.APRICITY_CONTAINER.get(),
                ClientMenuScreens::createScreen
        );
    }

    @SuppressWarnings("unchecked")
    private static AbstractContainerScreen<ApricityContainerMenu> createScreen(ApricityContainerMenu menu, Inventory inventory, Component title) {
        try {
            Class<?> screenClass = Class.forName(CONTAINER_SCREEN_CLASS);
            Constructor<?> constructor = screenClass.getConstructor(ApricityContainerMenu.class, Inventory.class, Component.class);
            return (AbstractContainerScreen<ApricityContainerMenu>) constructor.newInstance(menu, inventory, title);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create Apricity container screen", exception);
        }
    }
}

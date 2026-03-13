package com.sighs.apricityui;

import com.sighs.apricityui.instance.*;
import com.sighs.apricityui.instance.dom.expander.RecipeExpander;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.registry.Keybindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.io.IOException;

public class ApricityUIClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Keybindings.registerKeyMapping();
        ApricityUIRegistry.register();
        InitEvent.initLocalStorage();
        ApricityMenus.registerScreen();
        registerClientEvents();
        onRegisterShaders();
    }

    private void onRegisterShaders() {
        try {
            ShaderRegistry.register();
        } catch (IOException ignored) {
        }
    }

    private void registerClientEvents() {
        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
            Client.tickStart();
        });

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            Client.icon();
            Client.mouseMove();
            InitEvent.onClientTick();
        });

        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            if (Minecraft.getInstance().screen == null) {
                Client.drawOverlayLike(guiGraphics);
            } else {
                Client.drawScreenLike(guiGraphics);
            }
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!WorldWindow.hasWindows()) return;
            WorldWindow.renderAll(context.matrixStack(), context.projectionMatrix(), context.tickDelta());
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            Loader.setup();
            RecipeExpander.RecipeResolver.onRecipesUpdated();
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenMouseEvents.afterMouseScroll(screen).register((Screen target, double mouseX, double mouseY, double horizontal, double vertical) -> {
                if (vertical != 0.0) {
                    if (screen instanceof TitleScreen) {
                        Client.handleScreenScroll(vertical);
                    } else {
                        Client.handleMouseScroll(vertical);
                    }
                }
            });
            ScreenEvents.afterRender(screen).register((Screen target, GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta) -> {
                Client.drawScreenLike(guiGraphics);
            });
        });
    }
}

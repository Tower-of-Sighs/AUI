package com.sighs.apricityui.fabric;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.registry.ClientMenuScreens;
import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.world.WorldWindow;
import com.sighs.apricityui.network.fabric.NetworkManagerImpl;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ApricityUIFabricClient implements ClientModInitializer {
    public void onInitializeClient() {
        FabricServicesBootstrap.initClient();
        NetworkManagerImpl.initializeClient();
        // The common entrypoint runs before the client service exists. Register
        // the scan scope after installing the real Fabric client service.
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element");
        FabricShaderRegistry.register();
        registerKeys();
        ApricityUIRegistry.register();
        ClientMenuScreens.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> Client.tick());
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> Client.drawOverlayLike(graphics));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (WorldWindow.windows.isEmpty()) return;
            for (WorldWindow window : WorldWindow.windows) window.render(context.matrixStack(), context.projectionMatrix(), context.tickDelta());
        });
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                ScreenEvents.afterRender(screen).register((Screen target, GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) -> Client.drawScreenLike(graphics)));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return new ResourceLocation(ApricityUI.MODID, "client_resources");
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                Minecraft.getInstance().execute(com.sighs.apricityui.loader.ClientLoader::reloadResources);
            }
        });
    }

    private static void registerKeys() {
        KeyBindingHelper.registerKeyBinding(Keybindings.RELEASE_MOUSE);
        KeyBindingHelper.registerKeyBinding(Keybindings.RELOAD);
        KeyBindingHelper.registerKeyBinding(Keybindings.DEV_TOOLS);
        KeyBindingHelper.registerKeyBinding(Keybindings.RESOURCE_MANAGER);
    }
}

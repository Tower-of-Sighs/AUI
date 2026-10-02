package com.sighs.apricityui.fabric;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.client.DebugAIScreenshotTicker;
import com.sighs.apricityui.client.DebugReloadWatcher;
import com.sighs.apricityui.client.ClientRuntimeSelfTest;
import com.sighs.apricityui.client.ClientWptSnapshotRunner;
import com.sighs.apricityui.client.InitEvent;
import com.sighs.apricityui.client.ApricityUIClientCommands;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.dom.expander.RecipeExpander;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.registry.ClientMenuScreens;
import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.world.WorldWindow;
import com.sighs.apricityui.world.WorldWindowTestSpawner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;

public final class ApricityUIFabricClient implements ClientModInitializer {
    /** HUD 元素 id；AUI overlay 排在其他 HUD 元素之后，保证盖在原版 HUD 上。 */
    private static final Identifier HUD_LAYER_ID = Identifier.fromNamespaceAndPath(ApricityUI.MODID, "overlay");

    public void onInitializeClient() {
        FabricServicesBootstrap.initClient();
        // The common entrypoint runs before the client service exists. Register
        // the scan scope after installing the real Fabric client service.
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element");
        InitEvent.init();
        registerKeys();
        // AUI 文档以原版 PIP 状态提交，需要先把渲染器与自定义 pipeline 注册/编译好。
        ApricityGuiLayers.registerPictureInPictureRenderers();
        PipelineRegistry.registerPipelines();
        ApricityUIRegistry.register();
        ClientMenuScreens.register();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                ApricityUIClientCommands.register(dispatcher));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> RecipeExpander.clearCache());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RecipeExpander.clearCache());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Client.tick();
            InitEvent.tick();
            DebugReloadWatcher.tick();
            DebugAIScreenshotTicker.tick();
            ClientRuntimeSelfTest.tick();
            ClientWptSnapshotRunner.tick();
            WorldWindowTestSpawner.tick();
        });
        // 26.1 用 HudElementRegistry 取代 HudRenderCallback：AUI 文档以 PIP 状态提交，
        // 由原版 GuiRenderer 在同一 GUI 合成阶段栅格化。
        HudElementRegistry.addLast(HUD_LAYER_ID, (guiGraphics, deltaTracker) -> Client.drawOverlayLike(guiGraphics));
        // 26.1 用 LevelRenderEvents 取代 WorldRenderEvents。AFTER_TRANSLUCENT_FEATURES 是
        // 半透明地形与半透明实体都画完之后的阶段，对应 1.21.1 的 AFTER_TRANSLUCENT。
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            if (WorldWindow.windows.isEmpty()) return;
            // 26.1 不再把投影矩阵交给回调，改从 level render state 里取。
            Matrix4f projectionMatrix = context.levelState().cameraRenderState.projectionMatrix;
            // poseStack 同样是局部栈，相机视图矩阵在 render state 里单独存。
            Matrix4f viewMatrix = context.levelState().cameraRenderState.viewRotationMatrix;
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            // 先把 AUI 记录的投影同步成世界投影，世界文档里的滤镜通道才能取回正确矩阵。
            RenderService.INSTANCE.setProjectionMatrix(new Matrix4f(projectionMatrix));
            for (WorldWindow window : WorldWindow.windows) {
                window.render(context.poseStack(), viewMatrix, projectionMatrix, partialTick);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                ScreenEvents.afterExtract(screen).register((Screen target, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) -> Client.drawScreenLike(graphics)));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath(ApricityUI.MODID, "client_resources");
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                RecipeExpander.clearCache();
                Minecraft.getInstance().execute(com.sighs.apricityui.loader.ClientLoader::reloadResources);
            }
        });
    }

    private static void registerKeys() {
        KeyMappingHelper.registerKeyMapping(Keybindings.RELEASE_MOUSE);
        KeyMappingHelper.registerKeyMapping(Keybindings.RELOAD);
        KeyMappingHelper.registerKeyMapping(Keybindings.DEV_TOOLS);
        KeyMappingHelper.registerKeyMapping(Keybindings.RESOURCE_MANAGER);
    }
}

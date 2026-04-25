package com.sighs.apricityui;

import com.mojang.logging.LogUtils;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.instance.*;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.container.bind.ScreenOpenRequest;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.script.KubeJS;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Mod(ApricityUI.MODID)
public class ApricityUI {
    public static final String MODID = "apricityui";
    public static final Logger LOGGER = LogUtils.getLogger();

    @HideFromJS
    public ApricityUI() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ApricityUIConfig.CLIENT_SPEC);
        if (ModList.get().isLoaded("kubejs")) {
            KubeJS.scanPackage("com.sighs.apricityui.util.kjs");
        }
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element", "com.sighs.apricityui.instance.element");
        ApricityMenus.register(modEventBus);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ApricityUIRegistry.register();
            modEventBus.addListener(this::onRegisterShaders);
        }
    }

    private void onRegisterShaders(RegisterShadersEvent event) {
        try {
            ShaderRegistry.register(event);
        } catch (IOException ignored) {
        }
    }

    public static Window getWindow() {
        return Window.window;
    }

    public static Document createDocument(String path) {
        return Document.create(path);
    }

    public static Document createInWorldDocument(String path) {
        return Document.createInWorld(path);
    }

    public static void removeDocument(String path) {
        Document.remove(path);
    }

    public static ArrayList<Document> getDocument(String path) {
        return Document.get(path);
    }

    public static Document getDocumentByUUID(String uuid) {
        return Document.getByUUID(uuid);
    }

    public static List<Document> getAllDocument() {
        return Document.getAll();
    }

    public static ScreenOpenRequest screen(String path) {
        return ScreenOpenRequest.of(path);
    }

    public static void previewScreen(String path) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.setScreen(new ApricityContainerScreen(path));
    }

    public static void closePreview() {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof ApricityContainerScreen)) return;
        minecraft.setScreen(null);
    }

    /**
     * @deprecated 请改用 {@link #previewScreen(String)}。
     */
    @Deprecated
    public static void openScreen(String path) {
        previewScreen(path);
    }

    /**
     * @deprecated 请改用 {@link #screen(String)} 链式接口。
     */
    @Deprecated
    public static void openScreen(ServerPlayer player, String path, OpenBindPlan plan) {
        screen(path).withPlan(plan).open(player);
    }

    /**
     * @deprecated 请改用 {@link #closePreview()}。
     */
    @Deprecated
    public static void closeScreen() {
        closePreview();
    }

    public static OpenBindPlan.Builder bind() {
        return OpenBindPlan.builder();
    }

    public static boolean hasDataSource(ContainerBindType bindType) {
        return Container.hasBindingDataSource(bindType);
    }

    public static WorldWindow createWorldWindow(String documentPath, Vec3 position, float width, float height, int maxDistance) {
        WorldWindow window = new WorldWindow(documentPath, position, width, height, maxDistance);
        WorldWindow.addWindow(window);
        return window;
    }

    public static FollowFacingWorldWindow createFollowFacingWorldWindow(String documentPath, Vec3 position, float width, float height, int maxDistance, float followFactor) {
        FollowFacingWorldWindow window = new FollowFacingWorldWindow(documentPath, position, width, height, maxDistance, followFactor);
        WorldWindow.addWindow(window);
        return window;
    }

    public static void removeWorldWindow(WorldWindow window) {
        if (window == null) return;
        WorldWindow.removeWindow(window);
    }

    public static void clearWorldWindows() {
        WorldWindow.clear();
    }
}

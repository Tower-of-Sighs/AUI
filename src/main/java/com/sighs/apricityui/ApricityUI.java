package com.sighs.apricityui;

import com.mojang.logging.LogUtils;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.instance.ApricityUIConfig;
import com.sighs.apricityui.instance.FollowFacingWorldWindow;
import com.sighs.apricityui.instance.WorldWindow;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(ApricityUI.MODID)
public class ApricityUI {
    public static final String MODID = "apricityui";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ApricityUI(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ApricityUIConfig.CLIENT_SPEC);
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element", "com.sighs.apricityui.instance.element");
        ApricityMenus.register(modEventBus);
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

    public static void openScreen(String path) {
        ApricityScreenNetworkHandler.requestOpenScreen(path);
    }

    public static void openScreen(ServerPlayer player, String path, OpenBindPlan plan) {
        ApricityScreenNetworkHandler.openScreen(player, path, plan);
    }

    public static void closeScreen() {
        ApricityScreenNetworkHandler.requestCloseScreen();
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

package com.sighs.apricityui;

import com.mojang.logging.LogUtils;
import com.sighs.apricityui.dev.TestBlockEntity;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.instance.ApricityUIConfig;
import com.sighs.apricityui.instance.FollowFacingWorldWindow;
import com.sighs.apricityui.instance.ShaderRegistry;
import com.sighs.apricityui.instance.WorldWindow;
import com.sighs.apricityui.instance.network.ApricityNetwork;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.instance.network.handler.PendingMenu;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.script.KubeJS;
import dev.latvian.mods.rhino.util.HideFromJS;
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
        TestBlockEntity.register(modEventBus);
        ApricityNetwork.register();

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

    /**
     * 客户端打开纯展示 UI Screen（不涉及服务端容器绑定）。
     */
    public static void screen(String path) {
        ApricityScreenNetworkHandler.requestOpenScreen(path);
    }

    /**
     * 服务端创建带容器绑定的菜单 Screen。
     * <p>
     * 使用示例：
     * <pre>
     * ApricityUI.menu(player, "test/test.html").bind(b -> b.blockEntity(pos).player());
     * </pre>
     *
     * @param player       服务端玩家
     * @param templatePath 模板路径
     * @return 待绑定的菜单对象，调用 {@code .bind()} 后立即打开
     */
    public static PendingMenu menu(ServerPlayer player, String templatePath) {
        return new PendingMenu(player, templatePath);
    }

    /**
     * 客户端请求打开 Screen（发送网络包到服务端解析容器）。
     *
     * @deprecated 使用 {@link #screen(String)} 替代
     */
    @Deprecated
    public static void openScreen(String path) {
        screen(path);
    }

    /**
     * 客户端请求关闭当前 Screen。
     */
    public static void closeScreen() {
        ApricityScreenNetworkHandler.requestCloseScreen();
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

package com.sighs.apricityui;

import com.mojang.logging.LogUtils;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.spi.AuiPendingMenu;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.world.WorldWindow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * ApricityUI public API facade.
 *
 * <p>Loader-agnostic: the Forge entry point is {@code com.sighs.apricityui.forge.ApricityUIForge},
 * which registers the loader-side services this class uses. Everything here compiles inside
 * {@code common} without referencing loader source classes.</p>
 */
public final class ApricityUI {
    public static final String MODID = "apricityui";
    public static final Logger LOGGER = LogUtils.getLogger();

    private ApricityUI() {
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
     * 纯客户端直接渲染，无需服务端安装本 mod。
     */
    public static void screen(String path) {
        AuiServices.client().openScreen(path);
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
    public static AuiPendingMenu menu(ServerPlayer player, String templatePath) {
        return AuiServices.network().pendingMenu(player, templatePath);
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
     * 客户端关闭当前 Screen。纯客户端操作，无需服务端安装本 mod。
     * 容器屏幕关闭时 vanilla 会自动向服务端发送容器关闭包。
     */
    public static void closeScreen() {
        AuiServices.client().closeScreen();
    }

    /**
     * @deprecated Configure the logical size through {@code aui-viewport} and use
     *             {@link #createWorldWindow(String, Vec3, int)}.
     */
    @Deprecated
    public static WorldWindow createWorldWindow(String documentPath, Vec3 position, float width, float height, int maxDistance) {
        WorldWindow window = new WorldWindow(documentPath, position, width, height, maxDistance);
        WorldWindow.addWindow(window);
        return window;
    }

    public static WorldWindow createWorldWindow(String documentPath, Vec3 position, int maxDistance) {
        WorldWindow window = new WorldWindow(documentPath, position, maxDistance);
        WorldWindow.addWindow(window);
        return window;
    }

    /** Creates and registers a world window with an independent display-distance limit. */
    public static WorldWindow createWorldWindow(String documentPath, Vec3 position,
                                                int maxDistance, int maxDisplayDistance) {
        WorldWindow window = createWorldWindow(documentPath, position, maxDistance);
        window.setMaxDisplayDistance(maxDisplayDistance);
        return window;
    }

    public static WorldWindow createWorldWindow(String documentPath,
                                                double x, double y, double z, int maxDistance) {
        WorldWindow window = new WorldWindow(documentPath, x, y, z, maxDistance);
        WorldWindow.addWindow(window);
        return window;
    }

    public static WorldWindow createWorldWindow(String documentPath,
                                                double x, double y, double z,
                                                int maxDistance, int maxDisplayDistance) {
        WorldWindow window = createWorldWindow(documentPath, x, y, z, maxDistance);
        window.setMaxDisplayDistance(maxDisplayDistance);
        return window;
    }

    public static WorldWindow createWorldWindow(String documentPath, Vec3 position,
                                                int maxDistance, float yaw, float pitch) {
        WorldWindow window = new WorldWindow(documentPath, position, maxDistance, yaw, pitch);
        WorldWindow.addWindow(window);
        return window;
    }

    public static WorldWindow createWorldWindow(String documentPath, Vec3 position,
                                                int maxDistance, float yaw, float pitch, float roll) {
        WorldWindow window = new WorldWindow(documentPath, position, maxDistance, yaw, pitch, roll);
        WorldWindow.addWindow(window);
        return window;
    }

    public static WorldWindow createWorldWindow(String documentPath, Vec3 position,
                                                int maxDistance, Vec3 eulerDegrees) {
        WorldWindow window = new WorldWindow(documentPath, position, maxDistance, eulerDegrees);
        WorldWindow.addWindow(window);
        return window;
    }

    public static WorldWindow createWorldWindow(String documentPath, Vec3 position,
                                                int maxDistance, Quaternionf orientation) {
        WorldWindow window = new WorldWindow(documentPath, position, maxDistance, orientation);
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

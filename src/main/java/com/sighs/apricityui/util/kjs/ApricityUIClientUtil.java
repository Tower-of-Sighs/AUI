package com.sighs.apricityui.util.kjs;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.instance.screen.ApricityScreen;
import com.sighs.apricityui.instance.world.FollowFacingWorldWindow;
import com.sighs.apricityui.instance.world.WorldWindow;
import com.sighs.apricityui.registry.annotation.KJSBindings;
import com.sighs.apricityui.ui.ToastManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@KJSBindings(value = "ApricityUI", isClient = true)
public class ApricityUIClientUtil {
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

    /**
     * Returns the document currently bound to the active {@link ApricityScreen}, or {@code null}.
     */
    public static Document getCurrentScreenDocument() {
        if (Minecraft.getInstance().screen instanceof ApricityScreen screen) {
            return screen.getLinkedDocument();
        }
        return null;
    }

    public static List<Document> getAllDocument() {
        return Document.getAll();
    }

    public static String toast(String message) {
        return ToastManager.show(message);
    }

    public static String toast(String message, int durationMs) {
        return ToastManager.show(message, durationMs);
    }

    public static String toast(String message, int durationMs, String backgroundColor, String textColor, String borderColor, boolean dismissOnClick, String customStyle) {
        ToastManager.ToastOptions options = new ToastManager.ToastOptions(
                durationMs,
                dismissOnClick,
                backgroundColor,
                textColor,
                borderColor,
                customStyle
        );
        return ToastManager.show(message, options);
    }

    public static void dismissToast(String id) {
        ToastManager.dismiss(id);
    }

    public static void clearToasts() {
        ToastManager.clear();
    }

    /**
     * 客户端打开纯展示 UI Screen（纯客户端渲染，无需服务端）。
     */
    public static void screen(String path) {
        ApricityUI.screen(path);
    }

    /**
     * @deprecated 使用 {@link #screen(String)} 替代
     */
    @Deprecated
    public static void openScreen(String path) {
        screen(path);
    }

    public static void closeScreen() {
        ApricityUI.closeScreen();
    }

    /**
     * @deprecated Configure the logical size with the document's {@code aui-viewport} meta.
     */
    @Deprecated
    public static WorldWindow createWorldWindow(String path, double x, double y, double z, float width, float height, int maxDistance) {
        WorldWindow window = new WorldWindow(path, new Vec3(x, y, z), width, height, maxDistance);
        WorldWindow.addWindow(window);
        return window;
    }

    public static WorldWindow createWorldWindow(String path, double x, double y, double z, int maxDistance) {
        WorldWindow window = new WorldWindow(path, new Vec3(x, y, z), maxDistance);
        WorldWindow.addWindow(window);
        return window;
    }

    public static WorldWindow createWorldWindow(String path, double x, double y, double z,
                                                int maxDistance, int maxDisplayDistance) {
        WorldWindow window = createWorldWindow(path, x, y, z, maxDistance);
        window.setMaxDisplayDistance(maxDisplayDistance);
        return window;
    }

    public static WorldWindow createWorldWindow(String path, double x, double y, double z,
                                                int maxDistance, float yaw, float pitch) {
        WorldWindow window = new WorldWindow(path, new Vec3(x, y, z),
                maxDistance, yaw, pitch);
        WorldWindow.addWindow(window);
        return window;
    }

    public static WorldWindow createWorldWindow(String path, double x, double y, double z,
                                                int maxDistance, float yaw, float pitch, float roll) {
        WorldWindow window = new WorldWindow(path, new Vec3(x, y, z), maxDistance,
                new Vec3(pitch, yaw, roll));
        WorldWindow.addWindow(window);
        return window;
    }

    /**
     * @deprecated Configure the logical size with the document's {@code aui-viewport} meta.
     */
    @Deprecated
    public static FollowFacingWorldWindow createFollowFacingWorldWindow(String path, double x, double y, double z, float width, float height, int maxDistance, float followFactor) {
        FollowFacingWorldWindow window = new FollowFacingWorldWindow(path, new Vec3(x, y, z), width, height, maxDistance, followFactor);
        WorldWindow.addWindow(window);
        return window;
    }

    /**
     * @deprecated Use {@link #createWorldWindow(String, double, double, double, int)}
     * and configure follow/facing on the returned window.
     */
    @Deprecated
    public static FollowFacingWorldWindow createFollowFacingWorldWindow(String path, double x, double y, double z, int maxDistance, float followFactor) {
        FollowFacingWorldWindow window = new FollowFacingWorldWindow(path, new Vec3(x, y, z), maxDistance, followFactor);
        WorldWindow.addWindow(window);
        return window;
    }

    /**
     * @deprecated Use {@link #createWorldWindow(String, double, double, double, int)}
     * and configure follow/facing on the returned window.
     */
    @Deprecated
    public static FollowFacingWorldWindow createFollowFacingWorldWindow(String path,
                                                                        double x, double y, double z,
                                                                        int maxDistance,
                                                                        int maxDisplayDistance,
                                                                        float followFactor) {
        FollowFacingWorldWindow window = createFollowFacingWorldWindow(
                path, x, y, z, maxDistance, followFactor);
        window.setMaxDisplayDistance(maxDisplayDistance);
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

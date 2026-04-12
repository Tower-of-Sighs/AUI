package com.sighs.apricityui.util.kjs;

import com.sighs.apricityui.dev.ToastManager;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.instance.FollowFacingWorldWindow;
import com.sighs.apricityui.instance.WorldWindow;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.annotation.NJSBindings;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@NJSBindings(value = "ApricityUI", isClient = true)
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

    public static void openScreen(String path) {
        ApricityScreenNetworkHandler.requestOpenScreen(path);
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

    public static WorldWindow createWorldWindow(String path, double x, double y, double z, float width, float height, int maxDistance) {
        WorldWindow window = new WorldWindow(path, new Vec3(x, y, z), width, height, maxDistance);
        WorldWindow.addWindow(window);
        return window;
    }

    public static FollowFacingWorldWindow createFollowFacingWorldWindow(String path, double x, double y, double z, float width, float height, int maxDistance, float followFactor) {
        FollowFacingWorldWindow window = new FollowFacingWorldWindow(path, new Vec3(x, y, z), width, height, maxDistance, followFactor);
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

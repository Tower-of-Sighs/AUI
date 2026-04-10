package com.sighs.apricityui.script.nekojs;

import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.util.kjs.ApricityUIServerUtil;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;
import java.util.List;

/**
 * A single NekoJS binding named {@code ApricityUI}.
 *
 * <p>NekoJS currently does not namespace bindings per {@code ScriptType}; names are global.
 * That means we cannot register both a SERVER and a CLIENT binding using the same variable name.
 *
 * <p>To preserve the original scripting API, this proxy provides both sets of methods:
 * - Server-side methods call directly into {@link ApricityUIServerUtil}.
 * - Client-side methods delegate via reflection to avoid eagerly loading client-only classes on a dedicated server.
 */
public final class ApricityUIBindings {
    private static final String CLIENT_UTIL = "com.sighs.apricityui.util.kjs.ApricityUIClientUtil";

    private ApricityUIBindings() {
    }

    // ================= Common / Server =================

    public static OpenBindPlan.Builder bind() {
        return OpenBindPlan.builder();
    }

    public static boolean hasDataSource(ContainerBindType bindType) {
        return Container.hasBindingDataSource(bindType);
    }

    public static void openScreen(ServerPlayer player, String path, OpenBindPlan plan) {
        ApricityUIServerUtil.openScreen(player, path, plan);
    }

    // ================= Client =================

    public static Object getWindow() {
        return invokeClient("getWindow");
    }

    public static Object createDocument(String path) {
        return invokeClient("createDocument", String.class, path);
    }

    public static void removeDocument(String path) {
        invokeClient("removeDocument", String.class, path);
    }

    public static List<?> getDocument(String path) {
        Object result = invokeClient("getDocument", String.class, path);
        return result instanceof List<?> list ? list : List.of();
    }

    public static Object getDocumentByUUID(String uuid) {
        return invokeClient("getDocumentByUUID", String.class, uuid);
    }

    public static List<?> getAllDocument() {
        Object result = invokeClient("getAllDocument");
        return result instanceof List<?> list ? list : List.of();
    }

    public static String toast(String message) {
        Object result = invokeClient("toast", String.class, message);
        return result == null ? "" : String.valueOf(result);
    }

    public static String toast(String message, int durationMs) {
        Object result = invokeClient("toast", String.class, int.class, message, durationMs);
        return result == null ? "" : String.valueOf(result);
    }

    public static String toast(String message,
                               int durationMs,
                               String backgroundColor,
                               String textColor,
                               String borderColor,
                               boolean dismissOnClick,
                               String customStyle) {
        Object result = invokeClient(
                "toast",
                String.class, int.class, String.class, String.class, String.class, boolean.class, String.class,
                message, durationMs, backgroundColor, textColor, borderColor, dismissOnClick, customStyle
        );
        return result == null ? "" : String.valueOf(result);
    }

    public static void dismissToast(String id) {
        invokeClient("dismissToast", String.class, id);
    }

    public static void clearToasts() {
        invokeClient("clearToasts");
    }

    public static void openScreen(String path) {
        invokeClient("openScreen", String.class, path);
    }

    public static void closeScreen() {
        invokeClient("closeScreen");
    }

    // ================= internals =================

    private static void ensureClient() {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            throw new IllegalStateException("ApricityUI client bindings were called on non-client dist: " + FMLEnvironment.getDist());
        }
    }

    private static Object invokeClient(String methodName, Class<?> paramType, Object param) {
        return invokeClient(methodName, new Class<?>[]{paramType}, new Object[]{param});
    }

    private static Object invokeClient(String methodName, Class<?> paramType1, Class<?> paramType2, Object arg1, Object arg2) {
        return invokeClient(methodName, new Class<?>[]{paramType1, paramType2}, new Object[]{arg1, arg2});
    }

    private static Object invokeClient(String methodName) {
        return invokeClient(methodName, new Class<?>[0], new Object[0]);
    }

    private static Object invokeClient(String methodName,
                                       Class<?> p1, Class<?> p2, Class<?> p3, Class<?> p4, Class<?> p5, Class<?> p6, Class<?> p7,
                                       Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) {
        return invokeClient(methodName, new Class<?>[]{p1, p2, p3, p4, p5, p6, p7}, new Object[]{a1, a2, a3, a4, a5, a6, a7});
    }

    private static Object invokeClient(String methodName, Class<?>[] paramTypes, Object[] args) {
        ensureClient();
        try {
            Class<?> clientUtil = Class.forName(CLIENT_UTIL);
            Method method = clientUtil.getMethod(methodName, paramTypes);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke client binding method: " + methodName, e);
        }
    }
}

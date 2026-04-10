package com.sighs.apricityui.script;

import com.sighs.apricityui.script.bridge.ApricityScriptBridge;
import net.neoforged.fml.ModList;

/**
 * Facade for optional JS execution.
 *
 * <p>Implementation is loaded reflectively to avoid hard-linking against optional mods.</p>
 */
public final class ApricityJS {
    private static final String NEKOJS_BRIDGE_CLASS = "com.sighs.apricityui.script.nekojs.NekoJsBridge";

    private static volatile ApricityScriptBridge bridge;

    private ApricityJS() {
    }

    private static ApricityScriptBridge getBridge() {
        ApricityScriptBridge cached = bridge;
        if (cached != null) return cached;

        if (!ModList.get().isLoaded("nekojs")) return null;

        synchronized (ApricityJS.class) {
            if (bridge != null) return bridge;
            try {
                Class<?> clazz = Class.forName(NEKOJS_BRIDGE_CLASS, true, ApricityJS.class.getClassLoader());
                bridge = (ApricityScriptBridge) clazz.getDeclaredConstructor().newInstance();
                return bridge;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    public static void eval(String code) {
        ApricityScriptBridge impl = getBridge();
        if (impl == null) return;
        impl.eval(code);
    }

    public static void reload() {
        ApricityScriptBridge impl = getBridge();
        if (impl == null) return;
        impl.reload();
    }
}
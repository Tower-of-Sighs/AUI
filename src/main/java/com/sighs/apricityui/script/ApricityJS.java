package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import net.neoforged.fml.ModList;

public class ApricityJS {
    public static void eval(String code) {
        if (!ApricityUI.isKubeJSLoaded()) return;
        try {
            var manager = KubeJS.getClientScriptManager();
            var context = (KubeJSContext) manager.contextFactory.enter();
            context.evaluateString(context.topLevelScope, code, "eval", 1, null);
        } catch (Exception e) {
            ApricityUI.LOGGER.error("[ApricityJS] Error evaluating script", e);
        }
    }

    public static void reload() {
        if (!ModList.get().isLoaded("kubejs")) return;
        KubeJS.PROXY.reloadClientInternal();
    }
}

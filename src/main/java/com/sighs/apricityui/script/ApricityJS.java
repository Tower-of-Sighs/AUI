package com.sighs.apricityui.script;

import dev.latvian.mods.kubejs.KubeJS;
import net.fabricmc.loader.api.FabricLoader;

public class ApricityJS {
    public static void eval(String code) {
        if (!FabricLoader.getInstance().isModLoaded("kubejs")) return;
        var manager = KubeJS.getClientScriptManager();
        var context = manager.context;
        var top = manager.topLevelScope;
        context.evaluateString(top, code, "eval", 1, null);
    }
}

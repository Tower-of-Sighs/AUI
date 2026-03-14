package com.sighs.apricityui.script;

import dev.latvian.mods.kubejs.KubeJS;
import net.minecraftforge.fml.ModList;

public class ApricityJS {
    public static void eval(String code) {
        if (!ModList.get().isLoaded("kubejs")) return;
        var manager = KubeJS.getClientScriptManager();
        var context = manager.context;
        var top = manager.topLevelScope;
        context.evaluateString(top, code, "eval", 1, null);
    }
    public static void reload() {
        if (!ModList.get().isLoaded("kubejs")) return;
        KubeJS.PROXY.reloadClientInternal();
    }
}

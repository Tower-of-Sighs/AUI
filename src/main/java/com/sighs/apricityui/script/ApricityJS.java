package com.sighs.apricityui.script;

import dev.latvian.kubejs.KubeJS;
import dev.latvian.mods.rhino.Context;
import lombok.var;
import net.minecraftforge.fml.ModList;

public class ApricityJS {
    public static void eval(String code) {
        if (!ModList.get().isLoaded("kubejs")) return;
        var manager = KubeJS.clientScriptManager;
        if (manager == null || manager.packs == null || manager.packs.isEmpty()) return;
        var pack = manager.packs.values().iterator().next();
        var scope = pack.scope;
        if (scope == null) return;
        var cx = Context.enter();
        try {
            cx.evaluateString(scope, code, "eval", 1, null);
        } finally {
            Context.exit();
        }
    }
}

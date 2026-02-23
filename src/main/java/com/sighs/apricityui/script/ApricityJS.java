package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import dev.latvian.mods.kubejs.KubeJS;

public class ApricityJS {
    public static void eval(String code) {
        if (!ApricityUI.isKubeJSLoaded()) return;
        var manager = KubeJS.getClientScriptManager();
        var context = manager.contextFactory.enter();
        var top = context.getTopCallScope();
        context.evaluateString(top, code, "eval", 1, null);
    }
}

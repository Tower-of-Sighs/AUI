package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

public class KubeJS extends KubeJSPlugin {
    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("ApricityUI", ApricityUI.class);
    }
}

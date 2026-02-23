package com.sighs.apricityui.script;

import com.sighs.apricityui.ApricityUI;
import dev.latvian.kubejs.KubeJSPlugin;
import dev.latvian.kubejs.script.BindingsEvent;

public class KubeJS extends KubeJSPlugin {
    @Override
    public void addBindings(BindingsEvent event) {
        event.add("ApricityUI", ApricityUI.class);
    }
}

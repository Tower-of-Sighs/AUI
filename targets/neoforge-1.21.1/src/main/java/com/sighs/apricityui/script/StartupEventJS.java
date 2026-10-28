package com.sighs.apricityui.script;

import com.sighs.apricityui.startup.StartupContext;
import dev.latvian.mods.kubejs.event.KubeEvent;

public class StartupEventJS implements KubeEvent {
    private final StartupContext context;

    public StartupEventJS(StartupContext context) {
        this.context = context;
    }

    public StartupContext getContext() {
        return context;
    }
}

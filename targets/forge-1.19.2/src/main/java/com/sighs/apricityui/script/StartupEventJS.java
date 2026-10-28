package com.sighs.apricityui.script;

import com.sighs.apricityui.startup.StartupContext;
import dev.latvian.mods.kubejs.event.EventJS;

public class StartupEventJS extends EventJS {
    private final StartupContext context;

    public StartupEventJS(StartupContext context) {
        this.context = context;
    }

    public StartupContext getContext() {
        return context;
    }
}

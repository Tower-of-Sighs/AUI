package com.sighs.apricityui.fabric;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.spi.AuiScriptService;

import java.util.function.Consumer;

/** KubeJS is optional on Fabric; the core remains functional without it. */
public final class FabricScriptService implements AuiScriptService {
    public static final FabricScriptService INSTANCE = new FabricScriptService();
    private FabricScriptService() { }
    public void eval(String code, Event event, String source) { }
    public void reload() { }
    public Consumer<Event> browserEventListener(Object listener, Object currentTarget) { return null; }
}

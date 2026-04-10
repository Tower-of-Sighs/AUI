package com.sighs.apricityui.script.nekojs;

import com.sighs.apricityui.script.bridge.ApricityScriptBridge;
import com.tkisor.nekojs.core.NekoSandboxBuilder;
import com.tkisor.nekojs.script.ScriptType;
import org.graalvm.polyglot.Context;

public final class NekoJsBridge implements ApricityScriptBridge {
    private final Object lock = new Object();
    private Context clientContext;

    private Context getOrCreateClientContext() {
        Context ctx = clientContext;
        if (ctx != null) return ctx;

        synchronized (lock) {
            if (clientContext != null) return clientContext;
            clientContext = NekoSandboxBuilder.build(ScriptType.CLIENT);
            return clientContext;
        }
    }

    @Override
    public void eval(String code) {
        if (code == null || code.isBlank()) return;
        Context ctx = getOrCreateClientContext();
        synchronized (lock) {
            ctx.eval("js", code);
        }
    }

    @Override
    public void reload() {
        synchronized (lock) {
            if (clientContext != null) {
                try {
                    clientContext.close();
                } catch (Exception ignored) {
                }
                clientContext = null;
            }
        }
    }
}


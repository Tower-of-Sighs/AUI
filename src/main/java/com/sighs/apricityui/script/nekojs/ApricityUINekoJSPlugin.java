package com.sighs.apricityui.script.nekojs;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingsRegister;
import com.tkisor.nekojs.script.ScriptType;

@RegisterNekoJSPlugin
public final class ApricityUINekoJSPlugin implements NekoJSPlugin {
    @Override
    public void registerBindings(BindingsRegister registry) {
        // NekoJS bindings are global by name only, so we must not register the same name twice.
        // Expose a common proxy that delegates to side-specific implementations at runtime.
        registry.register(Binding.of(ScriptType.COMMON, "ApricityUI", ApricityUIBindings.class));
    }
}

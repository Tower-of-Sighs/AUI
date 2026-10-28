package com.sighs.apricityui.script;

import com.sighs.apricityui.forge.ReflectionUtils;
import com.sighs.apricityui.registry.annotation.KJSBindings;
import com.sighs.apricityui.startup.StartupContext;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraftforge.fml.ModList;

public class KubeJS extends KubeJSPlugin {
    /** {@code ApricityEvents.startup(event => ...)} — startup_scripts only. */
    public static final EventGroup APRICITY_EVENTS = EventGroup.of("ApricityEvents");
    public static final EventHandler STARTUP = APRICITY_EVENTS.startup("startup", () -> StartupEventJS.class);

    public static void scanPackage(String basePackage) {
        ReflectionUtils.addScanPackage(basePackage);
    }

    public static void scanPackages(String... basePackages) {
        ReflectionUtils.addScanPackages(basePackages);
    }

    @Override
    public void registerEvents() {
        APRICITY_EVENTS.register();
    }

    /**
     * Raises the startup event with the loading screen's context; a no-op when nothing listens.
     */
    public static void fireStartup(StartupContext context) {
        if (context == null || !STARTUP.hasListeners()) {
            return;
        }
        STARTUP.post(new StartupEventJS(context));
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        ScriptType scriptType = event.getType();
        ReflectionUtils.findAnnotationClasses(KJSBindings.class, data -> {
            String modId = data.getOrDefault("modId", "").toString();
            if (modId.isEmpty()) return true;
            return ModList.get().isLoaded(modId);
        }, clazz -> {
            KJSBindings annotation = clazz.getAnnotation(KJSBindings.class);
            String value = annotation.value();
            boolean isClient = annotation.isClient();
            if (value.isEmpty()) value = clazz.getSimpleName();
            if (isClient && scriptType != ScriptType.SERVER) {
                event.add(value, clazz);
            } else if (!isClient && scriptType != ScriptType.CLIENT) {
                event.add(value, clazz);
            }
        }, () -> {
        });
    }
}

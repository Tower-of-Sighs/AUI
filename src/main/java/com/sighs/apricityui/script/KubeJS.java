package com.sighs.apricityui.script;

import com.sighs.apricityui.registry.annotation.KJSBindings;
import com.sighs.apricityui.util.ReflectionUtils;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.neoforged.fml.ModList;

public class KubeJS implements KubeJSPlugin {
    @Override
    public void init() {
        KubeJSPlugin.super.init();
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        KubeJSPlugin.super.registerEvents(registry);
    }

    @Override
    public void registerBindings(BindingRegistry event) {
        ScriptType scriptType = event.type();
        ReflectionUtils.findAnnotationClasses(KJSBindings.class, data -> {
            String modId = data.getOrDefault("modId", "").toString();
            if (modId.isEmpty()) return true;
            return ModList.get().isLoaded(modId);
        }, clazz -> {
            KJSBindings annotation = clazz.getAnnotation(KJSBindings.class);
            String value = annotation.value();
            boolean isClient = annotation.isClient();
            if (value.isEmpty()) value = clazz.getSimpleName();
            if (isClient && scriptType == ScriptType.CLIENT) {
                event.add(value, clazz);
            } else if (!isClient && scriptType == ScriptType.SERVER) {
                event.add(value, clazz);
            }
        }, () -> {
        });
    }
}

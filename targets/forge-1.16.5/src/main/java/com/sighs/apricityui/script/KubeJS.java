package com.sighs.apricityui.script;

import com.sighs.apricityui.forge.ReflectionUtils;
import com.sighs.apricityui.registry.annotation.KJSBindings;
import dev.latvian.kubejs.KubeJSPlugin;
import dev.latvian.kubejs.script.BindingsEvent;
import dev.latvian.kubejs.script.ScriptType;
import net.minecraftforge.fml.ModList;

public class KubeJS extends KubeJSPlugin {
    public static void scanPackage(String basePackage) {
        ReflectionUtils.addScanPackage(basePackage);
    }

    public static void scanPackages(String... basePackages) {
        ReflectionUtils.addScanPackages(basePackages);
    }

    @Override
    public void addBindings(BindingsEvent event) {
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
                event.addClass(value, clazz);
            } else if (!isClient && scriptType != ScriptType.CLIENT) {
                event.addClass(value, clazz);
            }
        }, () -> {
        });
    }
}

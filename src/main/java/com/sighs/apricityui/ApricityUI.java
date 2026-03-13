package com.sighs.apricityui;

import com.mojang.logging.LogUtils;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.script.KubeJS;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class ApricityUI implements ModInitializer {
    public static final String MODID = "apricityui";
    public static final Logger LOGGER = LogUtils.getLogger();

    @HideFromJS
    @Override
    public void onInitialize() {
        KubeJS.scanPackage("com.sighs.apricityui.util.kjs");
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element", "com.sighs.apricityui.instance.element");
        ApricityMenus.register();
    }
}

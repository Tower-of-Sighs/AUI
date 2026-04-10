package com.sighs.apricityui;

import com.mojang.logging.LogUtils;
import com.sighs.apricityui.instance.ApricityUIConfig;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(ApricityUI.MODID)
public class ApricityUI {
    public static final String MODID = "apricityui";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ApricityUI(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ApricityUIConfig.CLIENT_SPEC);
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element", "com.sighs.apricityui.instance.element");
        ApricityMenus.register(modEventBus);
    }
}

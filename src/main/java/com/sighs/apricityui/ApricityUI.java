package com.sighs.apricityui;

import com.mojang.logging.LogUtils;
import com.sighs.apricityui.instance.ShaderRegistry;
import com.sighs.apricityui.instance.network.ApricityNetwork;
import com.sighs.apricityui.registry.ApricityMenus;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.io.IOException;

@Mod(ApricityUI.MODID)
public class ApricityUI {
    public static final String MODID = "apricityui";
    public static final Logger LOGGER = LogUtils.getLogger();

    @HideFromJS
    public ApricityUI() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ApricityMenus.register(modEventBus);
        ApricityNetwork.register();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::onRegisterShaders);
        }
    }

    private void onRegisterShaders(RegisterShadersEvent event) {
        try {
            ShaderRegistry.register(event);
        } catch (IOException ignored) {
        }
    }
}

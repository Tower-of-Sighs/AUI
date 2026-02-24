package com.sighs.apricityui;

import com.mojang.logging.LogUtils;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.instance.ShaderRegistry;
import com.sighs.apricityui.instance.container.bind.ApricityDataSourceResolver;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import com.sighs.apricityui.instance.network.ApricityNetwork;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.ApricityMenus;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        } catch (IOException ignored) {}
    }

    public static Window getWindow() {
        return Window.window;
    }

    public static Document createDocument(String path) {
        return Document.create(path);
    }

    public static void removeDocument(String path) {
        Document.remove(path);
    }

    public static ArrayList<Document> getDocument(String path) {
        return Document.get(path);
    }

    public static Document getDocumentByUUID(String uuid) {
        return Document.getByUUID(uuid);
    }

    public static List<Document> getAllDocument() {
        return Document.getAll();
    }

    public static void openScreen(String path) {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ApricityScreenNetworkHandler.requestOpenScreen(path));
    }

    public static void openScreen(ServerPlayer player, String path, OpenBindPlan plan) {
        ApricityScreenNetworkHandler.openScreen(player, path, plan);
    }

    public static void closeScreen() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ApricityScreenNetworkHandler::requestCloseScreen);
    }

    public static OpenBindPlan.Builder bind() {
        return OpenBindPlan.builder();
    }

    public static boolean hasDataSource(ContainerSchema.Descriptor.BindType bindType) {
        return ApricityDataSourceResolver.has(bindType);
    }
}

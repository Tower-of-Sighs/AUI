package com.sighs.apricityui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.instance.ShaderRegistry;
import com.sighs.apricityui.instance.container.bind.ApricityDataSourceResolver;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.container.schema.ContainerSchema;
import com.sighs.apricityui.instance.network.ApricityNetwork;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.Keybindings;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(ApricityUI.MODID)
public class ApricityUI {
    public static final String MODID = "apricityui";
    public static final Logger LOGGER = LogManager.getLogger();

    @HideFromJS
    public ApricityUI() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ApricityMenus.register(modEventBus);
        ApricityNetwork.register();

        modEventBus.addListener(this::onClientSetup);
    }

    public void onClientSetup(final FMLClientSetupEvent event) {
        Keybindings.registerKeyMapping();
        ShaderRegistry.init();
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

    public static void openScreen(ServerPlayerEntity player, String path, OpenBindPlan plan) {
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

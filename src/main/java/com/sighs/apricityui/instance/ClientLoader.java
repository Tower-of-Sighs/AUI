package com.sighs.apricityui.instance;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.dev.ToastManager;
import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.script.ApricityJS;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class ClientLoader extends Loader {
    public ClientLoader(String extension) {
        super(extension);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(ClientLoader::reload);
    }

    public static void reload() {
        long beginNs = System.nanoTime();
        ApricityJS.reload();
        ensureAsyncHandlersInitialized();
        AbstractAsyncHandler.clearAllAndBumpGeneration();
        ImageDrawer.clearRenderTypeCache();
        FontDrawer.clearCache();
        Font.clear();

        long scanStartNs = System.nanoTime();
        HTML.scan();
        long scanCostMs = (System.nanoTime() - scanStartNs) / 1_000_000L;

        long refreshStartNs = System.nanoTime();
        Document.refreshAll();
        WorldWindow.windows.forEach(worldWindow -> worldWindow.document.refresh());
        DevTools.refresh();
        com.sighs.apricityui.dev.ResourceManager.refresh();
        long refreshCostMs = (System.nanoTime() - refreshStartNs) / 1_000_000L;

        long totalCostMs = (System.nanoTime() - beginNs) / 1_000_000L;
        ToastManager.show(
                "重载完成 " + totalCostMs + "ms (扫描 " + scanCostMs + "ms, 刷新 " + refreshCostMs + "ms)",
                new ToastManager.ToastOptions(4200, true, "#0f172a", "#e2e8f0", "#334155", "")
        );
    }

    private static void ensureAsyncHandlersInitialized() {
        ImageAsyncHandler.INSTANCE.id();
        StyleAsyncHandler.INSTANCE.id();
        NetworkAsyncHandler.INSTANCE.id();
    }

    public static InputStream getResourceStream(String path) {
        InputStream filesystemStream = Loader.getResourceStream(path);
        if (filesystemStream != null) {
            return filesystemStream;
        }
        if (path == null || path.isEmpty()) return null;
        try {
            Identifier resourceLocation = Identifier.fromNamespaceAndPath(ApricityUI.MODID, "apricity/" + path);
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(resourceLocation);
            if (resource.isPresent()) return resource.get().open();
        } catch (IOException ignored) {
        }
        return null;
    }

    public static List<StaticResourceEntry> listFinalStaticResources() {
        LinkedHashMap<String, StaticResourceEntry> merged = new LinkedHashMap<>();
        loadResourcePackEntries(merged);
        loadFilesystemStaticResources(merged);
        return merged.values().stream()
                .sorted(Comparator.comparing(StaticResourceEntry::path))
                .toList();
    }

    public static String readGlobalCSS() {
        try (InputStream stream = getResourceStream("global.css")) {
            if (stream != null) return IOUtils.toString(stream, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        return null;
    }

    private static void loadResourcePackEntries(Map<String, StaticResourceEntry> merged) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<Identifier, Resource> resources = manager.listResources("apricity", location -> true);
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier location = entry.getKey();
            String path = location.getPath();
            if (path.startsWith("apricity/")) path = path.substring(9);
            if (path.isBlank()) continue;
            Resource resource = entry.getValue();
            String sourcePack = Loader.safe(resource.sourcePackId());
            merged.put(path, new StaticResourceEntry(
                    path,
                    Loader.extensionOf(path),
                    ResourceLayer.RESOURCE_PACK,
                    "resource-pack",
                    sourcePack,
                    -1L
            ));
        }
    }

    public void loadResources(BiConsumer<String, String> handler) {
        this.handler = handler;
        loadFromResourcePack();
        loadFromLocalFolder();
        loadFromDevFolders();
    }

    private void loadFromResourcePack() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<Identifier, Resource> resources = manager.listResources("apricity",
                location -> location.getPath().endsWith("." + extension));

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            try (InputStream stream = entry.getValue().open()) {
                String path = entry.getKey().getPath();
                if (path.startsWith("apricity/")) path = path.substring(9);
                handler.accept(path, IOUtils.toString(stream, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }
}

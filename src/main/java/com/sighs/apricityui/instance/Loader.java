package com.sighs.apricityui.instance;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

@EventBusSubscriber(modid = ApricityUI.MOD_ID)
public class Loader {
    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(Loader::reload);
    }

    public static void reload() {
        ensureAsyncHandlersInitialized();
        AbstractAsyncHandler.clearAllAndBumpGeneration();
        ImageDrawer.clearRenderTypeCache();
        Font.clear();
        HTML.scan();
        Document.refreshAll();
        WorldWindow.windows.forEach(worldWindow -> worldWindow.document.refresh());
    }

    private static void ensureAsyncHandlersInitialized() {
        ImageAsyncHandler.INSTANCE.id();
        StyleAsyncHandler.INSTANCE.id();
        NetworkAsyncHandler.INSTANCE.id();
    }

    public static InputStream getResourceStream(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            Path devPath = FMLPaths.GAMEDIR.get().resolve("../../src/main/resources/assets/apricityui/apricity" + path);
            if (Files.exists(devPath)) return Files.newInputStream(devPath);
            Path local = FMLPaths.GAMEDIR.get().resolve("apricity/" + path);
            if (Files.exists(local)) return Files.newInputStream(local);

            // 资源包
            ResourceLocation rl = ApricityUI.id("apricity/" + path);
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (res.isPresent()) return res.get().open();
        } catch (IOException ignored) {
        }
        return null;
    }

    public static boolean isRemotePath(String path) {
        if (path == null) return false;
        String trimmed = path.trim();
        return trimmed.regionMatches(true, 0, "https://", 0, "https://".length());
    }

    public static String resolve(String context, String raw) {
        if (raw == null) return "";
        String trimmedRaw = raw.trim();
        if (trimmedRaw.isEmpty()) return "";
        if (isRemotePath(trimmedRaw)) return trimmedRaw;
        if (trimmedRaw.startsWith("/")) return trimmedRaw.substring(1); // 绝对路径

        String safeContext = context == null ? "" : context;
        String base = safeContext.contains("/") ? safeContext.substring(0, safeContext.lastIndexOf('/')) : "";
        String[] parts = (base + "/" + trimmedRaw).split("/");

        java.util.Stack<String> stack = new java.util.Stack<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!stack.isEmpty()) stack.pop();
            } else {
                stack.push(part);
            }
        }
        return String.join("/", stack);
    }

    private final String extension;
    private BiConsumer<String, String> handler = (k, c) -> {
    };

    public Loader(String extension) {
        this.extension = extension;
    }

    public void loadResources(BiConsumer<String, String> handler) {
        this.handler = handler;
        loadFromResourcePack();
        loadFromLocalFolder();
    }

    private void loadFromResourcePack() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> resources = manager.listResources("apricity",
                loc -> loc.getPath().endsWith("." + extension));

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (InputStream stream = entry.getValue().open()) {
                String path = entry.getKey().getPath(); // "apricity/modid/index.html"
                if (path.startsWith("apricity/")) path = path.substring(9);
                handler.accept(path, IOUtils.toString(stream, StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadFromLocalFolder() {
        try {
            Path root = FMLPaths.GAMEDIR.get().resolve("apricity");
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                return;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith("." + extension))
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p, StandardCharsets.UTF_8);
                                String relPath = root.relativize(p).toString().replace("\\", "/");
                                handler.accept(relPath, content);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    public static String readGlobalCSS() {
        try (InputStream is = getResourceStream("global.css")) {
            if (is != null) return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        return null;
    }
}

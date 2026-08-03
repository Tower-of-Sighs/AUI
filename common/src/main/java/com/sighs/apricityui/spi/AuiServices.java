package com.sighs.apricityui.spi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.dom.DocumentExpander;
import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Central holder for loader-side services used by {@code common}.
 *
 * <p>The loader registers concrete implementations at mod construction. Before
 * that (headless tests, class loading) safe defaults are used that mirror the
 * "no Minecraft client" behavior. The first access also attempts to bootstrap
 * real implementations by loading the loader bootstrap class, so headless test
 * JVMs that have the loader on the classpath get the real services.</p>
 */
public final class AuiServices {
    private static volatile AuiClientService client = Defaults.CLIENT;
    private static volatile AuiNetworkService network = Defaults.NETWORK;
    private static volatile DocumentExpander expander = Defaults.EXPANDER;
    private static volatile AuiConfigService config = Defaults.CONFIG;
    private static volatile AuiResourceService resources = Defaults.RESOURCES;
    private static volatile AuiKeyService keys = Defaults.KEYS;
    private static volatile AuiScriptService script = Defaults.SCRIPT;
    private static volatile boolean bootstrapped;

    private AuiServices() {
    }

    public static void setClient(AuiClientService implementation) {
        client = implementation == null ? Defaults.CLIENT : implementation;
    }

    public static void setNetwork(AuiNetworkService implementation) {
        network = implementation == null ? Defaults.NETWORK : implementation;
    }

    public static void setExpander(DocumentExpander implementation) {
        expander = implementation == null ? Defaults.EXPANDER : implementation;
    }

    public static void setConfig(AuiConfigService implementation) {
        config = implementation == null ? Defaults.CONFIG : implementation;
    }

    public static void setResources(AuiResourceService implementation) {
        resources = implementation == null ? Defaults.RESOURCES : implementation;
    }

    public static void setKeys(AuiKeyService implementation) {
        keys = implementation == null ? Defaults.KEYS : implementation;
    }

    public static void setScript(AuiScriptService implementation) {
        script = implementation == null ? Defaults.SCRIPT : implementation;
    }

    public static AuiClientService client() {
        bootstrap();
        return client;
    }

    public static AuiNetworkService network() {
        bootstrap();
        return network;
    }

    public static DocumentExpander expander() {
        bootstrap();
        return expander;
    }

    public static AuiConfigService config() {
        bootstrap();
        return config;
    }

    public static AuiResourceService resources() {
        bootstrap();
        return resources;
    }

    public static AuiKeyService keys() {
        bootstrap();
        return keys;
    }

    public static AuiScriptService script() {
        bootstrap();
        return script;
    }

    /**
     * Loads the loader bootstrap class so its static initializer can register
     * the real implementations. No-op when the loader is not on the classpath.
     */
    private static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        try {
            Class.forName("com.sighs.apricityui.forge.AuiServicesBootstrap");
        } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
            // Loader not present; keep the safe defaults.
        }
    }

    /** Safe headless defaults. */
    private static final class Defaults {
        static final AuiClientService CLIENT = new AuiClientService() {
            @Override
            public Size getWindowSize() {
                return new Size(1920, 1080);
            }

            @Override
            public Position getMousePosition() {
                return new Position(0, 0);
            }

            @Override
            public Position getMousePositionDirectly() {
                return null;
            }

            @Override
            public double getWindowWidth() {
                return 1920;
            }

            @Override
            public double getWindowHeight() {
                return 1080;
            }

            @Override
            public int getScaledWidth() {
                return 1920;
            }

            @Override
            public int getScaledHeight() {
                return 1080;
            }

            @Override
            public int getDefaultFontWidth(String text, boolean bold, boolean oblique, double strokeWidth) {
                // Matches the loader's headless fallback (Client.getDefaultFontWidth) so
                // standalone common tests measure text deterministically the same way.
                double stroke = Math.max(0, strokeWidth) * 2;
                int fontStyle = java.awt.Font.PLAIN;
                if (bold) fontStyle |= java.awt.Font.BOLD;
                if (oblique) fontStyle |= java.awt.Font.ITALIC;
                java.awt.Font fallbackFont = new java.awt.Font("Microsoft YaHei", fontStyle, 16);
                int width = new java.awt.Canvas().getFontMetrics(fallbackFont).stringWidth(text == null ? "" : text);
                return (int) Math.ceil(width + stroke);
            }

            @Override
            public void drawDefaultFont(PoseStack poseStack, Text text, String content, Position position) {
            }

            @Override
            public boolean isKeyPressed(String keyName) {
                return false;
            }

            @Override
            public Position getMousePositionForWorldInteraction() {
                return null;
            }

            @Override
            public void drawPersistentScreenDocuments(GuiGraphics guiGraphics, Document excludedDocument) {
            }

            @Override
            public Path getGameDirectory() {
                return Path.of("").toAbsolutePath().normalize();
            }
        };

        static final AuiNetworkService NETWORK = new AuiNetworkService() {
            @Override
            public AuiPendingMenu pendingMenu(ServerPlayer player, String templatePath) {
                throw new IllegalStateException("AUI network services are not registered (requires a live Minecraft session)");
            }

            @Override
            public void openScreen(ServerPlayer player, String templatePath, List<ContainerDeclaration> declarations) {
                throw new IllegalStateException("AUI network services are not registered (requires a live Minecraft session)");
            }
        };

        static final DocumentExpander EXPANDER = document -> {
            // No loader implementation available; pure expansion is loader-side.
        };

        static final AuiConfigService CONFIG = new AuiConfigService() {
            @Override
            public boolean debugAutoReload() {
                return false;
            }

            @Override
            public void setDebugAutoReload(boolean value) {
            }

            @Override
            public boolean aiAutoScreenshot() {
                return false;
            }

            @Override
            public void setAiAutoScreenshot(boolean value) {
            }

            @Override
            public boolean frameTimingHud() {
                return false;
            }

            @Override
            public void setFrameTimingHud(boolean value) {
            }

            @Override
            public boolean remoteDebug() {
                return false;
            }

            @Override
            public void setRemoteDebug(boolean value) {
            }

            @Override
            public boolean resourceManagerWorldWindow() {
                return false;
            }

            @Override
            public void setResourceManagerWorldWindow(boolean value) {
            }

            @Override
            public boolean viewportZoomPassThrough() {
                return true;
            }

            @Override
            public void setViewportZoomPassThrough(boolean value) {
            }

            @Override
            public float worldWindowDepthOffsetScale() {
                return 0.01f;
            }

            @Override
            public void setWorldWindowDepthOffsetScale(double value) {
            }

            @Override
            public int worldWindowMaxDisplayDistance() {
                return 128;
            }

            @Override
            public void setWorldWindowMaxDisplayDistance(int value) {
            }

            @Override
            public boolean worldWindowLodEnabled() {
                return false;
            }

            @Override
            public void setWorldWindowLodEnabled(boolean value) {
            }

            @Override
            public int worldWindowFullDetailDistance() {
                return 16;
            }

            @Override
            public void setWorldWindowFullDetailDistance(int value) {
            }

            @Override
            public int worldWindowReducedDetailDistance() {
                return 48;
            }

            @Override
            public void setWorldWindowReducedDetailDistance(int value) {
            }

            @Override
            public void save() {
            }

            @Override
            public void markClientReloadPending() {
            }

            @Override
            public boolean consumeClientReloadPending() {
                return false;
            }
        };

        static final AuiResourceService RESOURCES = new AuiResourceService() {
            @Override
            public Optional<Resource> getResource(ResourceLocation location) {
                return Optional.empty();
            }

            @Override
            public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
                return Map.of();
            }

            @Override
            public ResourceLocation locationOf(String key) {
                if (key == null) return null;
                String sanitizedPath = key.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
                int hash = Math.floorMod(key.hashCode(), 1 << 24);
                return new ResourceLocation("apricityui", "dynamic/" + sanitizedPath + "-" + Integer.toHexString(hash));
            }

            @Override
            public RenderType smoothRenderType(ResourceLocation location, boolean blur, boolean depthTest) {
                // Image rendering requires the loader render backend; there is no
                // loader-less fallback. The render path is never exercised headless.
                throw new UnsupportedOperationException("AUI smooth image rendering requires the loader render backend");
            }
        };

        static final AuiKeyService KEYS = new AuiKeyService() {
            @Override
            public boolean isReleaseMouseDown() {
                return false;
            }

            @Override
            public int devToolsKey() {
                return -1;
            }

            @Override
            public int resourceManagerKey() {
                return -1;
            }

            @Override
            public int reloadKey() {
                return -1;
            }
        };

        static final AuiScriptService SCRIPT = new AuiScriptService() {
            @Override
            public void eval(String code, Event event, String source) {
                // No KubeJS runtime available; matches the "no KubeJS loaded" path.
            }

            @Override
            public void reload() {
            }

            @Override
            public Consumer<Event> browserEventListener(Object listener, Object currentTarget) {
                return null;
            }
        };
    }
}

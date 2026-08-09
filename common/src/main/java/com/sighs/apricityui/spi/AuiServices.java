package com.sighs.apricityui.spi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.dom.DocumentExpander;
import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.lang.annotation.Annotation;
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
    private static volatile AuiRenderService render = Defaults.RENDER;
    private static volatile AuiItemRenderService items = Defaults.ITEMS;
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

    public static void setRender(AuiRenderService implementation) {
        render = implementation == null ? Defaults.RENDER : implementation;
    }

    public static void setItems(AuiItemRenderService implementation) {
        items = implementation == null ? Defaults.ITEMS : implementation;
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

    public static AuiRenderService render() {
        bootstrap();
        return render;
    }

    public static AuiItemRenderService items() {
        bootstrap();
        return items;
    }

    /**
     * No-op guard that keeps the default implementations until the loader entry
     * point triggers its own bootstrap (e.g. {@code AuiServicesBootstrap} in the
     * forge/neoforge targets). The loader target knows its bootstrap class; this
     * class must not, so common stays loader-neutral. Headless test JVMs never
     * trigger a bootstrap and simply keep the safe defaults.
     */
    private static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
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
            public void openScreen(String templatePath) {
            }

            @Override
            public Path getGameDirectory() {
                return null;
            }

            @Override
            public Path getConfigDirectory() {
                return null;
            }

            @Override
            public boolean isProduction() {
                return true;
            }

            @Override
            public void addScanPackage(String basePackage) {
            }

            @Override
            public void addScanPackages(String... basePackages) {
            }

            @Override
            public void scanAnnotationClasses(Class<? extends Annotation> annotationClass,
                                              Predicate<Map<String, Object>> annotationPredicate,
                                              Consumer<Class<?>> consumer,
                                              Runnable onFinished) {
            }

            @Override
            public void openUri(java.net.URI uri) {
            }

            @Override
            public void openFile(java.io.File file) {
            }

            @Override
            public long getWindowHandle() {
                return 0L;
            }

            @Override
            public Vec3 getCameraPosition() {
                return new Vec3(0.0, 0.0, 0.0);
            }

            @Override
            public Vector3f getCameraLookVector() {
                return new Vector3f(0.0F, 0.0F, -1.0F);
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
            public Optional<InputStream> openResource(String path) {
                return Optional.empty();
            }

            @Override
            public Map<String, String> listResourcePaths(String path, String suffix) {
                return Map.of();
            }

            @Override
            public TextureKey locationOf(String key) {
                if (key == null) return null;
                String sanitizedPath = key.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
                int hash = Math.floorMod(key.hashCode(), 1 << 24);
                return TextureKey.of("dynamic/" + sanitizedPath + "-" + Integer.toHexString(hash));
            }

            @Override
            public TextureKey tryParseTextureKey(String src) {
                if (src == null || src.isBlank()) return null;
                // Mirrors ResourceLocation's syntax rules without importing the
                // version-bound MC type: optional "namespace:path", namespace
                // allows [a-z0-9_.-], path allows [a-z0-9/._-].
                int colon = src.indexOf(':');
                if (colon >= 0) {
                    String namespace = src.substring(0, colon);
                    if (namespace.isEmpty() || !namespace.matches("[a-z0-9_.-]+")) return null;
                }
                String path = colon >= 0 ? src.substring(colon + 1) : src;
                if (path.isEmpty() || !path.matches("[a-z0-9/._-]+")) return null;
                return TextureKey.of(src);
            }

            @Override
            public Object textureLocation(TextureKey key) {
                // No loader location type exists headless.
                return null;
            }

            @Override
            public RenderHandle smoothRenderType(TextureKey key, boolean blur, boolean depthTest) {
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

        };

        static final AuiItemRenderService ITEMS = request -> {
        };

        static final AuiRenderService RENDER = new AuiRenderService() {
            @Override
            public void setProjectionMatrix(Matrix4f matrix) {
            }

            @Override
            public Matrix4f getProjectionMatrix() {
                return new Matrix4f();
            }

            @Override
            public void enableDepthTest() {
            }

            @Override
            public void disableDepthTest() {
            }

            @Override
            public void enableBlend() {
            }

            @Override
            public void setBlendFunc(int srcFactor, int dstFactor) {
            }

            @Override
            public MeshBuilder beginMesh(MeshMode mode, MeshFormat format) {
                return MeshBuilder.of(new Object());
            }

            @Override
            public void emitVertex(Object mesh, Matrix4f mat, float x, float y, float z, int r, int g, int b, int a) {
            }

            @Override
            public void submitMesh(Object mesh) {
            }

            @Override
            public Object beginTextureBatch(RenderHandle render) {
                return null;
            }

            @Override
            public void emitTextureQuad(Object batch, Matrix4f mat, float x, float y, float width, float height,
                                        float u0, float v0, float u1, float v1) {
            }

            @Override
            public void flushTextureBatch(Object batch, RenderHandle render) {
            }

            @Override
            public void emitVertexUV(Object mesh, Matrix4f mat, float x, float y, float z, float u, float v) {
            }

            @Override
            public FboHandle createOffscreenTarget(int width, int height, boolean useDepth) {
                return null;
            }

            @Override
            public FboHandle getMainRenderTarget() {
                return null;
            }

            @Override
            public void enableStencil(FboHandle target) {
            }

            @Override
            public void destroyBuffers(FboHandle target) {
            }

            @Override
            public void clear(FboHandle target, float r, float g, float b, float a) {
            }

            @Override
            public void bindWrite(FboHandle target, boolean setViewport) {
            }

            @Override
            public void bindColorTexture(FboHandle target, int unit) {
            }

            @Override
            public void blitFramebuffer(FboHandle source, FboHandle target, int srcX0, int srcY0, int srcX1, int srcY1) {
            }

            @Override
            public Object createDynamicTexture(String name, Object nativeImage, boolean linear) {
                return null;
            }

            @Override
            public void uploadTextureRegion(Object texture, Object nativeImage, int x, int y, int width, int height, boolean linear) {
            }

            @Override
            public void setImagePixel(Object nativeImage, int x, int y, int pixel) {
            }

            @Override
            public void closeTexture(Object texture) {
            }

            @Override
            public void registerTexture(Object texture, Object location) {
            }

            @Override
            public void releaseTexture(Object location) {
            }

            @Override
            public void setShader(Object shader) {
            }

            @Override
            public void setPositionColorShader() {
            }

            @Override
            public void setShaderColor(float a, float r, float g, float b) {
            }

            @Override
            public Object getFilterShader() {
                return null;
            }

            @Override
            public Object getFilterBlurShader() {
                return null;
            }

            @Override
            public void setDepthFunc(int func) {
            }

            @Override
            public void setDepthMask(boolean write) {
            }

            @Override
            public boolean isDepthTestEnabled() {
                return true;
            }

            @Override
            public boolean isDepthMaskEnabled() {
                return true;
            }

            @Override
            public void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
            }

            @Override
            public void disableBlend() {
            }

            @Override
            public void enableCull() {
            }

            @Override
            public void disableCull() {
            }

            @Override
            public boolean isCullEnabled() {
                return false;
            }

            @Override
            public void enablePolygonOffset() {
            }

            @Override
            public void disablePolygonOffset() {
            }

            @Override
            public void polygonOffset(float factor, float units) {
            }

            @Override
            public void enableScissorTest() {
            }

            @Override
            public void scissorBox(int x, int y, int width, int height) {
            }

            @Override
            public void disableScissorTest() {
            }

            @Override
            public void enableStencilTest() {
            }

            @Override
            public void disableStencilTest() {
            }

            @Override
            public void setStencilMask(int mask) {
            }

            @Override
            public void setStencilFunc(int func, int ref, int mask) {
            }

            @Override
            public void setStencilOp(int sfail, int dpfail, int dppass) {
            }

            @Override
            public void clearStencilBuffer() {
            }

            @Override
            public void setColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
            }

            @Override
            public boolean isOnRenderThread() {
                return true;
            }

            @Override
            public void recordRenderCall(Runnable task) {
                task.run();
            }

            @Override
            public String getGLVersionString() {
                return "1.0";
            }

            @Override
            public void flushSharedBuffers() {
            }

            @Override
            public void setShaderUniformFloat(String name, float value) {
            }

            @Override
            public void setShaderUniform2f(String name, float a, float b) {
            }

            @Override
            public void setShaderUniform3f(String name, float a, float b, float c) {
            }

            @Override
            public void setShaderUniform4f(String name, float a, float b, float c, float d) {
            }

            @Override
            public void setShaderUniformI(String name, int value) {
            }
        };
    }
}

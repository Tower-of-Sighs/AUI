package com.sighs.apricityui.viewport;

import com.mojang.blaze3d.platform.Window;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.spi.AuiServices;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import com.sighs.apricityui.parser.CSS;

/**
 * Resolves the logical document viewport and the render transform used by screen documents.
 *
 * <p>Mode summary:
 * <ul>
 *     <li>{@code gui}: Minecraft GUI-scaled logical viewport.</li>
 *     <li>{@code window}/{@code native}: browser-like CSS viewport with a fixed monitor-derived
 *     width. Its height follows the current window's CSS height while the render scale remains
 *     fixed, so horizontal layout does not change when the Minecraft window is resized.</li>
 *     <li>{@code browser}/{@code css}: fixed CSS width scaled to the current MC window.
 *     The CSS height is derived from the window height so both axes fill the available GUI
 *     viewport.</li>
 *     <li>{@code screen}/{@code fullscreen}: compatibility aliases for {@code window}.</li>
 *     <li>{@code fixed}: explicit logical viewport dimensions.</li>
 * </ul>
 */
public record ApricityViewport(
        int layoutWidth,
        int layoutHeight,
        float renderScale,
        double scissorScale,
        double zoom
) {
    private static final double MAX_DOCUMENT_GUI_SCALE = 5.0d;
    private static final int DEFAULT_FIXED_WIDTH = 427;
    private static final int DEFAULT_FIXED_HEIGHT = 249;
    private static final int DEFAULT_BROWSER_WIDTH = 1920;
    private static final int DEFAULT_BROWSER_HEIGHT = 1080;
    private static final String META_NAME = "aui-viewport";
    private static final Object ZOOM_STORE_LOCK = new Object();
    private static final Map<String, State> STATES = new ConcurrentHashMap<>();
    private static final Properties STORED_ZOOMS = new Properties();
    private static volatile boolean zoomStoreLoaded = false;

    public ApricityViewport(int layoutWidth, int layoutHeight, float renderScale, double scissorScale) {
        this(layoutWidth, layoutHeight, renderScale, scissorScale, 1.0d);
    }

    public static ApricityViewport resolve(String templatePath, Window window) {
        Spec spec = spec(templatePath);
        return spec.resolve(window, spec.initialZoom());
    }

    public static Spec spec(String templatePath) {
        String raw = HTML.findMetaContent(templatePath, META_NAME);
        Map<String, String> options = parseOptions(raw);
        String mode = options.getOrDefault("mode", options.getOrDefault("type", "gui")).trim().toLowerCase(Locale.ROOT);
        double initialZoom = parseDouble(options.get("zoom"), 1.0d);
        double minZoom = parseDouble(options.get("min-zoom"), 0.5d);
        double maxZoom = parseDouble(options.get("max-zoom"), 3.0d);
        double zoomStep = parseDouble(options.get("zoom-step"), 0.1d);
        boolean userScalable = parseBoolean(options.get("user-scalable"), true);
        return new Spec(mode, options, initialZoom, minZoom, maxZoom, zoomStep, userScalable);
    }

    private static ApricityViewport resolveBase(String mode, Map<String, String> options, Window window) {
        double actualGuiScale = Math.max(1.0d, window.getGuiScale());
        return switch (mode) {
            case "window", "native", "screen", "fullscreen" -> browser(window, actualGuiScale, options);
            case "browser", "css", "web" -> windowViewport(window, actualGuiScale, options);
            case "fixed" -> fixed(window, actualGuiScale, options);
            case "gui", "mc", "default", "" -> gui(window, actualGuiScale);
            default -> gui(window, actualGuiScale);
        };
    }

    private static ApricityViewport applyZoom(ApricityViewport base, double zoom) {
        double safeZoom = zoom > 0 && Double.isFinite(zoom) ? zoom : 1.0d;
        int width = Math.max(1, (int) Math.round(base.layoutWidth() / safeZoom));
        int height = Math.max(1, (int) Math.round(base.layoutHeight() / safeZoom));
        return new ApricityViewport(
                width,
                height,
                (float) (base.renderScale() * safeZoom),
                base.scissorScale() * safeZoom,
                safeZoom
        );
    }

    private static ApricityViewport gui(Window window, double actualGuiScale) {
        double documentGuiScale = Math.min(actualGuiScale, MAX_DOCUMENT_GUI_SCALE);
        float renderScale = (float) (documentGuiScale / actualGuiScale);
        int width = Math.max(1, (int) Math.round(window.getScreenWidth() / documentGuiScale));
        int height = Math.max(1, (int) Math.round(window.getScreenHeight() / documentGuiScale));
        return new ApricityViewport(width, height, renderScale, documentGuiScale);
    }

    private static ApricityViewport browserReference(Window window, double actualGuiScale,
                                                     Map<String, String> options) {
        GLFWVidMode videoMode = resolveVideoMode(window);
        double cssScale = browserCssScale(window);
        int physicalWidth = videoMode == null ? DEFAULT_BROWSER_WIDTH : videoMode.width();
        int physicalHeight = videoMode == null ? DEFAULT_BROWSER_HEIGHT : videoMode.height();
        int width = Math.max(1, parseInt(options.get("width"), (int) Math.round(physicalWidth / cssScale)));
        int height = Math.max(1, parseInt(options.get("height"), (int) Math.round(physicalHeight / cssScale)));
        float renderScale = (float) Math.max(0.0001d, cssScale / actualGuiScale);
        return new ApricityViewport(width, height, renderScale, cssScale);
    }

    private static ApricityViewport browser(Window window, double actualGuiScale,
                                            Map<String, String> options) {
        ApricityViewport reference = browserReference(window, actualGuiScale, options);
        if (options.containsKey("height")) return reference;

        double cssScale = browserCssScale(window);
        int height = reference.layoutHeight();
        if (window != null) {
            try {
                int physicalHeight = window.getScreenHeight();
                if (physicalHeight > 0) {
                    height = Math.max(1, (int) Math.round(physicalHeight / cssScale));
                }
            } catch (Throwable ignored) {
                // Keep the monitor-derived fallback when the window is unavailable.
            }
        }
        return new ApricityViewport(
                reference.layoutWidth(),
                height,
                reference.renderScale(),
                reference.scissorScale()
        );
    }

    private static ApricityViewport windowViewport(Window window, double actualGuiScale,
                                                   Map<String, String> options) {
        ApricityViewport browserViewport = browserReference(window, actualGuiScale, options);
        // GuiGraphics uses GUI-scaled coordinates. getScreenWidth()/getScreenHeight() are
        // GLFW window coordinates and can differ from the framebuffer-backed GUI viewport on
        // high-DPI windowed displays.
        double guiWidth = Math.max(1.0d, window.getGuiScaledWidth());
        double guiHeight = Math.max(1.0d, window.getGuiScaledHeight());
        float renderScale = (float) Math.max(0.0001d,
                guiWidth / Math.max(1.0d, browserViewport.layoutWidth()));
        int layoutHeight = Math.max(1, (int) Math.round(guiHeight / renderScale));
        return new ApricityViewport(
                browserViewport.layoutWidth(),
                layoutHeight,
                renderScale,
                actualGuiScale * renderScale
        );
    }

    private static double browserCssScale(Window window) {
        if (window == null || AuiServices.client().getWindowHandle() == 0L) return 1.0d;
        float[] xScale = new float[1];
        float[] yScale = new float[1];
        try {
            GLFW.glfwGetWindowContentScale(AuiServices.client().getWindowHandle(), xScale, yScale);
            double scale = Math.max(xScale[0], yScale[0]);
            return scale > 0 && Double.isFinite(scale) ? scale : 1.0d;
        } catch (Throwable ignored) {
            return 1.0d;
        }
    }

    private static ApricityViewport fixed(Window window, double actualGuiScale, Map<String, String> options) {
        int width = parseInt(options.get("width"), DEFAULT_FIXED_WIDTH);
        int height = parseInt(options.get("height"), DEFAULT_FIXED_HEIGHT);
        width = Math.max(1, width);
        height = Math.max(1, height);

        String scaleOption = options.getOrDefault("scale", "1").trim().toLowerCase(Locale.ROOT);
        float renderScale;
        if ("fit".equals(scaleOption) || "contain".equals(scaleOption)) {
            double guiWidth = window.getScreenWidth() / actualGuiScale;
            double guiHeight = window.getScreenHeight() / actualGuiScale;
            renderScale = (float) Math.max(0.0001d, Math.min(guiWidth / width, guiHeight / height));
        } else if ("window".equals(scaleOption) || "native".equals(scaleOption)) {
            renderScale = (float) (1.0d / actualGuiScale);
        } else if ("gui".equals(scaleOption) || "mc".equals(scaleOption)) {
            renderScale = 1.0f;
        } else {
            renderScale = (float) Math.max(0.0001d, parseDouble(scaleOption, 1.0d));
        }
        return new ApricityViewport(width, height, renderScale, actualGuiScale * renderScale);
    }

    private static GLFWVidMode resolveVideoMode(Window window) {
        if (window == null || AuiServices.client().getWindowHandle() == 0L) return null;
        try {
            long monitor = GLFW.glfwGetWindowMonitor(AuiServices.client().getWindowHandle());
            if (monitor == 0L) {
                monitor = GLFW.glfwGetPrimaryMonitor();
            }
            return monitor == 0L ? null : GLFW.glfwGetVideoMode(monitor);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Map<String, String> parseOptions(String raw) {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return options;

        for (String token : raw.split("[,;]")) {
            if (token == null) continue;
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;

            int equals = trimmed.indexOf('=');
            if (equals < 0) {
                options.putIfAbsent("mode", trimmed);
                continue;
            }
            String key = trimmed.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = trimmed.substring(equals + 1).trim();
            if (!key.isEmpty()) options.put(key, value);
        }
        return options;
    }

    private static int parseInt(String raw, int fallback) {
        return (int) Math.round(parseDouble(raw, fallback));
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> fallback;
        };
    }

    public record Spec(
            String mode,
            Map<String, String> options,
            double initialZoom,
            double minZoom,
            double maxZoom,
            double zoomStep,
            boolean userScalable
    ) {
        public Spec {
            mode = mode == null || mode.isBlank() ? "gui" : mode.trim().toLowerCase(Locale.ROOT);
            options = options == null ? Map.of() : Map.copyOf(options);
            double low = sanitizeZoom(minZoom, 0.1d);
            double high = sanitizeZoom(maxZoom, 10.0d);
            if (high < low) {
                double temp = low;
                low = high;
                high = temp;
            }
            minZoom = low;
            maxZoom = high;
            initialZoom = clamp(sanitizeZoom(initialZoom, 1.0d), minZoom, maxZoom);
            zoomStep = clamp(sanitizeZoom(zoomStep, 0.1d), 0.01d, 5.0d);
        }

        public ApricityViewport resolve(Window window, double zoom) {
            return applyZoom(resolveBase(mode, options, window), clamp(sanitizeZoom(zoom, initialZoom), minZoom, maxZoom));
        }

        /** Resolves the same CSS viewport contract when no Minecraft window is available. */
        public ApricityViewport resolveHeadless(int availableWidth, int availableHeight, double zoom) {
            int fallbackWidth = Math.max(1, availableWidth);
            int fallbackHeight = Math.max(1, availableHeight);
            ApricityViewport base;
            if ("fixed".equals(mode)) {
                int width = Math.max(1, parseInt(options.get("width"), DEFAULT_FIXED_WIDTH));
                int height = Math.max(1, parseInt(options.get("height"), DEFAULT_FIXED_HEIGHT));
                String scaleOption = options.getOrDefault("scale", "1").trim().toLowerCase(Locale.ROOT);
                double scale = switch (scaleOption) {
                    case "fit", "contain" -> Math.min((double) fallbackWidth / width, (double) fallbackHeight / height);
                    case "window", "native", "gui", "mc" -> 1.0d;
                    default -> Math.max(0.0001d, parseDouble(scaleOption, 1.0d));
                };
                base = new ApricityViewport(width, height, (float) scale, scale);
            } else if (isWindowMode(mode)) {
                int width = Math.max(1, parseInt(options.get("width"), DEFAULT_BROWSER_WIDTH));
                int height = Math.max(1, parseInt(options.get("height"), fallbackHeight));
                base = new ApricityViewport(width, height, 1.0f, 1.0d);
            } else if (isBrowserMode(mode)) {
                int width = Math.max(1, parseInt(options.get("width"), DEFAULT_BROWSER_WIDTH));
                double scale = Math.max(0.0001d, (double) fallbackWidth / width);
                int height = Math.max(1, (int) Math.round(fallbackHeight / scale));
                base = new ApricityViewport(width, height, (float) scale, scale);
            } else {
                base = new ApricityViewport(fallbackWidth, fallbackHeight, 1.0f, 1.0d);
            }
            return applyZoom(base, clamp(sanitizeZoom(zoom, initialZoom), minZoom, maxZoom));
        }

        private static boolean isBrowserMode(String mode) {
            return "browser".equals(mode) || "css".equals(mode) || "web".equals(mode);
        }

        private static boolean isWindowMode(String mode) {
            return "window".equals(mode) || "native".equals(mode)
                    || "screen".equals(mode) || "fullscreen".equals(mode);
        }

        public State createState() {
            return new State(this);
        }

        public State createState(String templatePath) {
            String key = normalizeTemplatePath(templatePath);
            return STATES.compute(key, (ignored, existing) -> {
                if (existing == null) return new State(key, this);
                existing.updateSpec(this);
                return existing;
            });
        }
    }

    public static final class State {
        private final String templatePath;
        private volatile Spec spec;
        private double zoom;

        private State(Spec spec) {
            this("", spec);
        }

        private State(String templatePath, Spec spec) {
            this.templatePath = normalizeTemplatePath(templatePath);
            this.spec = spec;
            this.zoom = readStoredZoom(this.templatePath, spec.initialZoom());
            this.zoom = clamp(sanitizeZoom(this.zoom, spec.initialZoom()), spec.minZoom(), spec.maxZoom());
        }

        public ApricityViewport resolve(Window window) {
            return spec.resolve(window, zoom);
        }

        public ApricityViewport resolveHeadless(int availableWidth, int availableHeight) {
            return spec.resolveHeadless(availableWidth, availableHeight, zoom);
        }

        public synchronized boolean zoomIn() {
            if (!spec.userScalable()) return false;
            return setZoom(zoom + spec.zoomStep());
        }

        public synchronized boolean zoomOut() {
            if (!spec.userScalable()) return false;
            return setZoom(zoom - spec.zoomStep());
        }

        public synchronized boolean resetZoom() {
            if (!spec.userScalable()) return false;
            return setZoom(spec.initialZoom());
        }

        public boolean canUserScale() {
            return spec.userScalable();
        }

        public synchronized double zoom() {
            return zoom;
        }

        private synchronized void updateSpec(Spec nextSpec) {
            if (nextSpec == null) return;
            spec = nextSpec;
            double clamped = clamp(sanitizeZoom(zoom, nextSpec.initialZoom()), nextSpec.minZoom(), nextSpec.maxZoom());
            if (Math.abs(clamped - zoom) >= 0.000001d) {
                zoom = clamped;
                writeStoredZoom(templatePath, zoom);
            }
        }

        /** Sets an editor-controlled zoom value, even when wheel scaling is disabled. */
        public synchronized boolean setZoom(double nextZoom) {
            double clamped = clamp(sanitizeZoom(nextZoom, zoom), spec.minZoom(), spec.maxZoom());
            if (Math.abs(clamped - zoom) < 0.000001d) return false;
            zoom = clamped;
            writeStoredZoom(templatePath, zoom);
            return true;
        }
    }

    private static String normalizeTemplatePath(String templatePath) {
        return templatePath == null ? "" : templatePath.trim().replace('\\', '/');
    }

    private static double readStoredZoom(String templatePath, double fallback) {
        ensureZoomStoreLoaded();
        synchronized (ZOOM_STORE_LOCK) {
            return parseDouble(STORED_ZOOMS.getProperty(templatePath), fallback);
        }
    }

    private static void writeStoredZoom(String templatePath, double zoom) {
        if (templatePath == null || templatePath.isBlank()) return;
        ensureZoomStoreLoaded();
        synchronized (ZOOM_STORE_LOCK) {
            STORED_ZOOMS.setProperty(templatePath, String.format(Locale.ROOT, "%.6f", zoom));
            Path file = zoomStorePath();
            try {
                Files.createDirectories(file.getParent());
                try (OutputStream out = Files.newOutputStream(file)) {
                    STORED_ZOOMS.store(out, "ApricityUI viewport zoom values");
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static void ensureZoomStoreLoaded() {
        if (zoomStoreLoaded) return;
        synchronized (ZOOM_STORE_LOCK) {
            if (zoomStoreLoaded) return;
            Path file = zoomStorePath();
            if (Files.exists(file) && Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    STORED_ZOOMS.load(in);
                } catch (IOException ignored) {
                }
            }
            zoomStoreLoaded = true;
        }
    }

    private static Path zoomStorePath() {
        return configDir().resolve("apricityui").resolve("viewport-zoom.properties");
    }

    private static Path configDir() {
        Path dir = com.sighs.apricityui.spi.AuiServices.client().getConfigDirectory();
        return dir != null ? dir : Path.of("config").toAbsolutePath().normalize();
    }

    private static double sanitizeZoom(double value, double fallback) {
        return value > 0 && Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

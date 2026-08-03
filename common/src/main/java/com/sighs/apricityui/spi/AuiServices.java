package com.sighs.apricityui.spi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.dom.DocumentExpander;
import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Consumer;

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

        static final Consumer<Object> NOOP_BINDER = ignored -> {
        };
    }
}

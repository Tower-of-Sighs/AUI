package com.sighs.apricityui.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.dev.ResourceManager;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.task.FrameScheduler;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.DocumentLayerOrder;
import com.sighs.apricityui.render.FrameTimingHud;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.ui.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.config.ApricityUIConfig;
import com.sighs.apricityui.screen.ApricityContainerScreen;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.world.WorldWindow;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class Client {
    public static final HashMap<String, Integer> KEY_MAP = new HashMap<>();
    private static int lastWindowWidth = -1;
    private static int lastWindowHeight = -1;
    private static int lastFramebufferWidth = -1;
    private static int lastFramebufferHeight = -1;
    private static double lastGuiScale = -1.0d;

    static {
        KEY_MAP.put("key.keyboard.unknown", -1);
        KEY_MAP.put("key.mouse.left", 0);
        KEY_MAP.put("key.mouse.right", 1);
        KEY_MAP.put("key.mouse.middle", 2);
        KEY_MAP.put("key.mouse.4", 3);
        KEY_MAP.put("key.mouse.5", 4);
        KEY_MAP.put("key.mouse.6", 5);
        KEY_MAP.put("key.mouse.7", 6);
        KEY_MAP.put("key.mouse.8", 7);
        KEY_MAP.put("key.keyboard.0", 48);
        KEY_MAP.put("key.keyboard.1", 49);
        KEY_MAP.put("key.keyboard.2", 50);
        KEY_MAP.put("key.keyboard.3", 51);
        KEY_MAP.put("key.keyboard.4", 52);
        KEY_MAP.put("key.keyboard.5", 53);
        KEY_MAP.put("key.keyboard.6", 54);
        KEY_MAP.put("key.keyboard.7", 55);
        KEY_MAP.put("key.keyboard.8", 56);
        KEY_MAP.put("key.keyboard.9", 57);
        KEY_MAP.put("key.keyboard.a", 65);
        KEY_MAP.put("key.keyboard.b", 66);
        KEY_MAP.put("key.keyboard.c", 67);
        KEY_MAP.put("key.keyboard.d", 68);
        KEY_MAP.put("key.keyboard.e", 69);
        KEY_MAP.put("key.keyboard.f", 70);
        KEY_MAP.put("key.keyboard.g", 71);
        KEY_MAP.put("key.keyboard.h", 72);
        KEY_MAP.put("key.keyboard.i", 73);
        KEY_MAP.put("key.keyboard.j", 74);
        KEY_MAP.put("key.keyboard.k", 75);
        KEY_MAP.put("key.keyboard.l", 76);
        KEY_MAP.put("key.keyboard.m", 77);
        KEY_MAP.put("key.keyboard.n", 78);
        KEY_MAP.put("key.keyboard.o", 79);
        KEY_MAP.put("key.keyboard.p", 80);
        KEY_MAP.put("key.keyboard.q", 81);
        KEY_MAP.put("key.keyboard.r", 82);
        KEY_MAP.put("key.keyboard.s", 83);
        KEY_MAP.put("key.keyboard.t", 84);
        KEY_MAP.put("key.keyboard.u", 85);
        KEY_MAP.put("key.keyboard.v", 86);
        KEY_MAP.put("key.keyboard.w", 87);
        KEY_MAP.put("key.keyboard.x", 88);
        KEY_MAP.put("key.keyboard.y", 89);
        KEY_MAP.put("key.keyboard.z", 90);
        KEY_MAP.put("key.keyboard.f1", 290);
        KEY_MAP.put("key.keyboard.f2", 291);
        KEY_MAP.put("key.keyboard.f3", 292);
        KEY_MAP.put("key.keyboard.f4", 293);
        KEY_MAP.put("key.keyboard.f5", 294);
        KEY_MAP.put("key.keyboard.f6", 295);
        KEY_MAP.put("key.keyboard.f7", 296);
        KEY_MAP.put("key.keyboard.f8", 297);
        KEY_MAP.put("key.keyboard.f9", 298);
        KEY_MAP.put("key.keyboard.f10", 299);
        KEY_MAP.put("key.keyboard.f11", 300);
        KEY_MAP.put("key.keyboard.f12", 301);
        KEY_MAP.put("key.keyboard.f13", 302);
        KEY_MAP.put("key.keyboard.f14", 303);
        KEY_MAP.put("key.keyboard.f15", 304);
        KEY_MAP.put("key.keyboard.f16", 305);
        KEY_MAP.put("key.keyboard.f17", 306);
        KEY_MAP.put("key.keyboard.f18", 307);
        KEY_MAP.put("key.keyboard.f19", 308);
        KEY_MAP.put("key.keyboard.f20", 309);
        KEY_MAP.put("key.keyboard.f21", 310);
        KEY_MAP.put("key.keyboard.f22", 311);
        KEY_MAP.put("key.keyboard.f23", 312);
        KEY_MAP.put("key.keyboard.f24", 313);
        KEY_MAP.put("key.keyboard.f25", 314);
        KEY_MAP.put("key.keyboard.num.lock", 282);
        KEY_MAP.put("key.keyboard.keypad.0", 320);
        KEY_MAP.put("key.keyboard.keypad.1", 321);
        KEY_MAP.put("key.keyboard.keypad.2", 322);
        KEY_MAP.put("key.keyboard.keypad.3", 323);
        KEY_MAP.put("key.keyboard.keypad.4", 324);
        KEY_MAP.put("key.keyboard.keypad.5", 325);
        KEY_MAP.put("key.keyboard.keypad.6", 326);
        KEY_MAP.put("key.keyboard.keypad.7", 327);
        KEY_MAP.put("key.keyboard.keypad.8", 328);
        KEY_MAP.put("key.keyboard.keypad.9", 329);
        KEY_MAP.put("key.keyboard.keypad.add", 334);
        KEY_MAP.put("key.keyboard.keypad.decimal", 330);
        KEY_MAP.put("key.keyboard.keypad.enter", 335);
        KEY_MAP.put("key.keyboard.keypad.equal", 336);
        KEY_MAP.put("key.keyboard.keypad.multiply", 332);
        KEY_MAP.put("key.keyboard.keypad.divide", 331);
        KEY_MAP.put("key.keyboard.keypad.subtract", 333);
        KEY_MAP.put("key.keyboard.down", 264);
        KEY_MAP.put("key.keyboard.left", 263);
        KEY_MAP.put("key.keyboard.right", 262);
        KEY_MAP.put("key.keyboard.up", 265);
        KEY_MAP.put("key.keyboard.apostrophe", 39);
        KEY_MAP.put("key.keyboard.backslash", 92);
        KEY_MAP.put("key.keyboard.comma", 44);
        KEY_MAP.put("key.keyboard.equal", 61);
        KEY_MAP.put("key.keyboard.grave.accent", 96);
        KEY_MAP.put("key.keyboard.left.bracket", 91);
        KEY_MAP.put("key.keyboard.minus", 45);
        KEY_MAP.put("key.keyboard.period", 46);
        KEY_MAP.put("key.keyboard.right.bracket", 93);
        KEY_MAP.put("key.keyboard.semicolon", 59);
        KEY_MAP.put("key.keyboard.slash", 47);
        KEY_MAP.put("key.keyboard.space", 32);
        KEY_MAP.put("key.keyboard.tab", 258);
        KEY_MAP.put("key.keyboard.left.alt", 342);
        KEY_MAP.put("key.keyboard.left.control", 341);
        KEY_MAP.put("key.keyboard.left.shift", 340);
        KEY_MAP.put("key.keyboard.left.win", 343);
        KEY_MAP.put("key.keyboard.right.alt", 346);
        KEY_MAP.put("key.keyboard.right.control", 345);
        KEY_MAP.put("key.keyboard.right.shift", 344);
        KEY_MAP.put("key.keyboard.right.win", 347);
        KEY_MAP.put("key.keyboard.enter", 257);
        KEY_MAP.put("key.keyboard.escape", 256);
        KEY_MAP.put("key.keyboard.backspace", 259);
        KEY_MAP.put("key.keyboard.delete", 261);
        KEY_MAP.put("key.keyboard.end", 269);
        KEY_MAP.put("key.keyboard.home", 268);
        KEY_MAP.put("key.keyboard.insert", 260);
        KEY_MAP.put("key.keyboard.page.down", 267);
        KEY_MAP.put("key.keyboard.page.up", 266);
        KEY_MAP.put("key.keyboard.caps.lock", 280);
        KEY_MAP.put("key.keyboard.pause", 284);
        KEY_MAP.put("key.keyboard.scroll.lock", 281);
        KEY_MAP.put("key.keyboard.menu", 348);
        KEY_MAP.put("key.keyboard.print.screen", 283);
        KEY_MAP.put("key.keyboard.world.1", 161);
        KEY_MAP.put("key.keyboard.world.2", 162);
    }

    @SubscribeEvent
    public static void updateTooltipPosition(ScreenEvent.Render.Pre event) {
        Position mousePosition = new Position(event.getMouseX(), event.getMouseY());
        Tooltip.moveActiveFromScreen(mousePosition);
        DevTools.handleInspectMouseMove(mousePosition);
    }

    @SubscribeEvent
    public static void drawScreen(ScreenEvent.Render.Post event) {
        if (Minecraft.getInstance().screen instanceof ApricityContainerScreen) {
            return;
        }
        if (Minecraft.getInstance().screen instanceof ApricityScreen) {
            return;
        }
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().screen != null) {
            FrameTimingHud.beginFrame();
            try {
                drawPersistentScreenDocuments(event.getGuiGraphics());
                com.sighs.apricityui.dev.resource.ResourcePreviewDialog.draw(event.getGuiGraphics().pose());
                event.getGuiGraphics().flush();
                Cursor.drawPseudoCursor(event.getGuiGraphics().pose());
                event.getGuiGraphics().flush();
            } finally {
                FrameTimingHud.endFrame();
                drawFrameTimingHud(event.getGuiGraphics());
            }
//            com.sighs.apricityui.dev.BackdropFilterTestRunner.onRenderGuiPost();
        }
    }

    @SubscribeEvent
    public static void drawOverlay(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            // RenderGuiEvent is also emitted for the in-world HUD. Keep DevTools'
            // world-window hover state in sync even when no Minecraft Screen exists.
            DevTools.handleInspectMouseMove(getMousePositionDirectly());
            FrameTimingHud.beginFrame();
            try {
                for (Document document : DocumentLayerOrder.backToFront(Document.getAll())) {
                    if (document == null || document.inWorld || document.isManuallyRendered()) continue;
                    Base.drawOverlayDocument(event.getGuiGraphics().pose(), document);
                }
                com.sighs.apricityui.dev.resource.ResourcePreviewDialog.draw(event.getGuiGraphics().pose());
                event.getGuiGraphics().flush();
                Cursor.drawPseudoCursor(event.getGuiGraphics().pose());
                event.getGuiGraphics().flush();
            } finally {
                FrameTimingHud.endFrame();
                drawFrameTimingHud(event.getGuiGraphics());
            }
//            com.sighs.apricityui.dev.BackdropFilterTestRunner.onRenderGuiPost();
        }
    }

    public static void drawPersistentScreenDocuments(net.minecraft.client.gui.GuiGraphics guiGraphics) {
        drawPersistentScreenDocuments(guiGraphics, null);
    }

    public static void drawPersistentScreenDocuments(net.minecraft.client.gui.GuiGraphics guiGraphics, Document excludedDocument) {
        for (Document document : DocumentLayerOrder.backToFront(Document.getAll())) {
            if (document == null || document == excludedDocument || document.inWorld || document.isManuallyRendered() || !document.isReloadPersistent()) {
                continue;
            }
            Base.drawOverlayDocument(guiGraphics.pose(), document);
        }
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (Minecraft.getInstance().screen != null) return;
        if (handleViewportZoomAtMouse(event.getScrollDelta() > 0)) {
            event.setCanceled(true);
            return;
        }
        boolean nativeConsumed = Operation.scroll(event.getScrollDelta());
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                MouseEvent mouseEvent = new MouseEvent("wheel", realPos);
                mouseEvent.deltaY = -event.getScrollDelta() * 50;
                mouseEvent.scrollDelta = mouseEvent.deltaY;
                mouseEvent.cancelable = true;
                MouseEvent.tiggerEvent(mouseEvent, window.document);
                nativeConsumed |= mouseEvent.isNativeConsumed();
            }
        }
        if (nativeConsumed || CursorReleaseController.isActive()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (handleViewportZoomAtMouse(event.getScrollDelta() > 0)) {
            event.setCanceled(true);
            return;
        }
        if (Operation.scroll(event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (SharedConstants.isAllowedChatCharacter(event.getCodePoint())) {
            if (Operation.onCharTyped(event.getCodePoint())) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void mouseButton(InputEvent.MouseButton.Pre event) {
        boolean nativeConsumed = false;
        if (event.getAction() == InputConstants.PRESS) nativeConsumed = Operation.onMouseDown(event.getButton());
        if (event.getAction() == InputConstants.RELEASE) nativeConsumed = Operation.onMouseUp(event.getButton());
        boolean devToolsInspectConsumed = Operation.wasDevToolsInspectConsumed();
        if (Minecraft.getInstance().screen != null) {
            if (nativeConsumed) event.setCanceled(true);
            return;
        }
        // DevTools picking is an inspection gesture, not an application click.
        if (devToolsInspectConsumed) {
            event.setCanceled(true);
            return;
        }
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                MouseEvent mouseEvent;
                if (event.getAction() == InputConstants.PRESS) {
                    mouseEvent = new MouseEvent("mousedown", realPos, event.getButton());
                } else {
                    mouseEvent = new MouseEvent("mouseup", realPos, event.getButton());
                }
                MouseEvent.tiggerEvent(mouseEvent, window.document);
                nativeConsumed |= mouseEvent.isNativeConsumed();
            }
        }
        if (nativeConsumed || CursorReleaseController.isActive()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void mouseMove(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Operation.onMouseMove(getMousePosition());
            for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
                Position realPos = window.getRealPos();
                if (realPos != null) {
                    MouseEvent moveEvent = new MouseEvent("mousemove", realPos);
                    MouseEvent.tiggerEvent(moveEvent, window.document);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(InputEvent.Key event) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        int action = event.getAction();
        if (action != InputConstants.PRESS && action != InputConstants.REPEAT && action != InputConstants.RELEASE) return;
        if (action == InputConstants.PRESS && handleViewportZoomKeyAtMouse(event.getKey(), event.getModifiers())) {
            return;
        }
        boolean canceled = Operation.handleKeyInput(
                event.getKey(),
                event.getScanCode(),
                action,
                event.getModifiers(),
                action == InputConstants.REPEAT,
                com.sighs.apricityui.event.KeyEvent.Source.INPUT_EVENT
        );
//        if (canceled) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (handleViewportZoomKeyAtMouse(event.getKeyCode(), event.getModifiers())) {
            event.setCanceled(true);
            return;
        }
        int action = InputConstants.PRESS;
        boolean canceled = Operation.handleKeyInput(
                event.getKeyCode(),
                event.getScanCode(),
                action,
                event.getModifiers(),
                false,
                com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT
        );
        if (canceled) event.setCanceled(true);
    }

    private static boolean handleViewportZoomAtMouse(boolean zoomIn) {
        if (!isControlDown()) return false;
        Document target = findViewportZoomTargetAtMouse();
        if (target == null) return false;
        ApricityUI.LOGGER.info("[AUI Viewport] wheel zoomIn={} target={}", zoomIn, target.getPath());
        return target.handleViewportZoom(zoomIn);
    }

    private static boolean handleViewportZoomKeyAtMouse(int keyCode, int modifiers) {
        if (!isControlModifier(modifiers)) return false;

        boolean zoomIn = keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD;
        boolean zoomOut = keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT;
        boolean reset = keyCode == GLFW.GLFW_KEY_0 || keyCode == GLFW.GLFW_KEY_KP_0;
        if (!zoomIn && !zoomOut && !reset) return false;

        Document target = findViewportZoomTargetAtMouse();
        if (target == null) return false;
        ApricityUI.LOGGER.info("[AUI Viewport] key zoomIn={} reset={} target={}", zoomIn, reset, target.getPath());
        return reset ? target.resetViewportZoom() : target.handleViewportZoom(zoomIn);
    }

    private static Document findViewportZoomTargetAtMouse() {
        Position mouse = Operation.getMousePositionDirectly();
        if (mouse == null) return null;
        boolean passThrough = ApricityUIConfig.CLIENT.viewportZoomPassThrough.get();
        for (Document document : DocumentLayerOrder.frontToBack(Document.getAll())) {
            if (document == null || document.inWorld || document.isManuallyRendered() || !document.isActive()) continue;
            if (document.hitTest(document.screenToDocumentPosition(mouse)) != null) {
                if (passThrough && document.isReloadPersistent() && !document.interceptsMouseEvents()) {
                    continue;
                }
                return document;
            }
        }
        return null;
    }

    private static boolean isControlModifier(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || isControlDown();
    }

    private static boolean isControlDown() {
        if (Screen.hasControlDown()) return true;
        try {
            Window window = Minecraft.getInstance().getWindow();
            long handle = window == null ? 0L : window.getWindow();
            return handle != 0L
                    && (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        int action = InputConstants.RELEASE;
        Operation.handleKeyInput(
                event.getKeyCode(),
                event.getScanCode(),
                action,
                event.getModifiers(),
                false,
                com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT
        );
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            CursorReleaseController.tick();
            if (ApricityUIConfig.consumeClientReloadPending()) {
                com.sighs.apricityui.dev.debug.ExternalDebugServer.reconcileConfiguration();
            }
            com.sighs.apricityui.dev.debug.ExternalDebugServer.tick();
            FrameScheduler.tick();
            ResourceManager.reconcileConfiguredMode();
//            com.sighs.apricityui.dev.BackdropFilterTestRunner.tick();
            DebugReloadWatcher.tick();
            DebugAIScreenshotTicker.tick();
            DevTools.drainLogs();
            com.sighs.apricityui.forge.RenderService.INSTANCE.reconcileFabulousChainStencil();
            Window mcWindow = Minecraft.getInstance().getWindow();
            int w = mcWindow.getScreenWidth();
            int h = mcWindow.getScreenHeight();
            int framebufferWidth = mcWindow.getWidth();
            int framebufferHeight = mcWindow.getHeight();
            double guiScale = mcWindow.getGuiScale();
            if (lastWindowWidth != w || lastWindowHeight != h
                    || lastFramebufferWidth != framebufferWidth
                    || lastFramebufferHeight != framebufferHeight
                    || Double.compare(lastGuiScale, guiScale) != 0) {
                lastWindowWidth = w;
                lastWindowHeight = h;
                lastFramebufferWidth = framebufferWidth;
                lastFramebufferHeight = framebufferHeight;
                lastGuiScale = guiScale;
                for (Document document : Document.getAll()) {
                    if (document != null && !document.isDisposed()) {
                        document.applyViewport(true);
                    }
                }
                com.sighs.apricityui.init.Window.window.fireResizeEvent();
            }
        }
    }

    /**
     * FIXME:
     * 如果在某些情况（如窗口拖动等）鼠标位置缓存为空或者是读到旧的缓存值时请参考 {@link #getMousePositionDirectly()} 的实现。
     * 未来建议重构，统一输入源，或在输入更新链中保证鼠标坐标始终同步。
     *
     * @see Operation#getMousePosition()
     */
    public static Position getMousePosition() {
        Minecraft mc = Minecraft.getInstance();
        MouseHandler mouseHandler = mc.mouseHandler;
        Window window = mc.getWindow();

        return MouseCoordinates.toGui(mouseHandler.xpos(), mouseHandler.ypos(),
                window.getScreenWidth(), window.getScreenHeight(),
                window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    /**
     * Returns the pointer position represented by the screen for world-window picking.
     * A grabbed GLFW cursor has virtual coordinates that track look movement, while
     * the visible crosshair remains fixed at the center of the GUI viewport.
     */
    public static Position getMousePositionForWorldInteraction() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (mc.mouseHandler.isMouseGrabbed()) {
            return new Position(
                    window.getGuiScaledWidth() * 0.5d,
                    window.getGuiScaledHeight() * 0.5d
            );
        }
        return getMousePosition();
    }

    /** Returns the live cursor position directly from the GLFW window handle. */
    public static Position getMousePositionDirectly() {
        Window window = Minecraft.getInstance().getWindow();
        long handle = window.getWindow();
        if (handle != 0L) {
            double[] xBuf = new double[1];
            double[] yBuf = new double[1];
            GLFW.glfwGetCursorPos(handle, xBuf, yBuf);
            return MouseCoordinates.toGui(xBuf[0], yBuf[0],
                    window.getScreenWidth(), window.getScreenHeight(),
                    window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
        return null;
    }

    public static boolean isKeyPressed(String keyName) {
        if (keyName == null || keyName.isEmpty()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        long windowHandle = minecraft.getWindow().getWindow();
        if (windowHandle == 0L) return false;

        try {
            Integer glfwKey = KEY_MAP.get(keyName);
            if (glfwKey == null) {
                return false;
            }

            if (keyName.startsWith("key.mouse.")) {
                return GLFW.glfwGetMouseButton(windowHandle, glfwKey) == GLFW.GLFW_PRESS;
            }

            if (keyName.startsWith("key.keyboard.")) {
                return GLFW.glfwGetKey(windowHandle, glfwKey) == GLFW.GLFW_PRESS;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static Window getWindow() {
        return Minecraft.getInstance().getWindow();
    }

    public static Size getWindowSize() {
        Window window = Minecraft.getInstance().getWindow();
        return new Size(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public static int getDefaultFontWidth(String text) {
        return getDefaultFontWidth(text, false, false, 0);
    }

    public static int getDefaultFontWidth(String text, boolean bold) {
        return getDefaultFontWidth(text, bold, false, 0);
    }

    public static int getDefaultFontWidth(String text, boolean bold, boolean oblique) {
        return getDefaultFontWidth(text, bold, oblique, 0);
    }

    public static int getDefaultFontWidth(String text, boolean bold, boolean oblique, double strokeWidth) {
        double stroke = Math.max(0, strokeWidth) * 2;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            int fontStyle = java.awt.Font.PLAIN;
            if (bold) fontStyle |= java.awt.Font.BOLD;
            if (oblique) fontStyle |= java.awt.Font.ITALIC;
            java.awt.Font fallbackFont = new java.awt.Font("Microsoft YaHei", fontStyle, 16);
            int width = new java.awt.Canvas().getFontMetrics(fallbackFont).stringWidth(text == null ? "" : text);
            return (int) Math.ceil(width + stroke);
        }
        if (!bold && !oblique) return (int) Math.ceil(minecraft.font.width(text) + stroke);
        MutableComponent renderText = Component.literal(text);
        if (bold) renderText = renderText.withStyle(ChatFormatting.BOLD);
        if (oblique) renderText = renderText.withStyle(ChatFormatting.ITALIC);
        return (int) Math.ceil(minecraft.font.width(renderText) + stroke);
    }

    public static void drawDefaultFont(PoseStack poseStack, Text text, String content, Position position) {
        poseStack.pushPose();
        poseStack.translate(position.x, position.y, 0);
        // 默认字体也要保留 z 轴缩放，避免在容器 Screen 中把文本深度压扁后被后续菜单/物品绘制覆盖。
        float scale = (float) text.defaultFontScale();
        poseStack.scale(scale, scale, 1f);
        MutableComponent renderText = Component.literal(content == null ? "" : content);
        if (text.isBold()) renderText = renderText.withStyle(ChatFormatting.BOLD);
        if (text.isOblique()) renderText = renderText.withStyle(ChatFormatting.ITALIC);
        if (text.isUnderlined()) renderText = renderText.withStyle(ChatFormatting.UNDERLINE);
        if (text.isStrikethrough()) renderText = renderText.withStyle(ChatFormatting.STRIKETHROUGH);
        int stroke = Math.max(0, (int) Math.ceil(text.strokeWidth));
        if (stroke > 0) {
            int strokeColor = text.strokeColor.getValue();
            for (int ox = -stroke; ox <= stroke; ox++) {
                for (int oy = -stroke; oy <= stroke; oy++) {
                    if (ox == 0 && oy == 0) continue;
                    if (ox * ox + oy * oy > stroke * stroke) continue;
                    Minecraft.getInstance().font.drawInBatch(renderText.getVisualOrderText(), ox, oy, strokeColor, false, poseStack.last().pose(), Minecraft.getInstance().renderBuffers().bufferSource(), net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
                }
            }
        }
        Minecraft.getInstance().font.drawInBatch(renderText.getVisualOrderText(), 0, 0, text.color.getValue(), false, poseStack.last().pose(), Minecraft.getInstance().renderBuffers().bufferSource(), net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
        poseStack.popPose();
    }

    public static void drawDefaultFont(PoseStack poseStack, Text text, Position position) {
        drawDefaultFont(poseStack, text, text.content, position);
    }

    /** Draws the frame-timing HUD overlay, if enabled. */
    public static void drawFrameTimingHud(GuiGraphics guiGraphics) {
        if (guiGraphics == null || !FrameTimingHud.isEnabled()) return;
        String text = FrameTimingHud.frameStatsText();
        if (text == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.font.width(text) + 8;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 1000);
        guiGraphics.fill(2, 2, 2 + width, 16, 0xCC000000);
        guiGraphics.drawString(minecraft.font, text, 6, 6, 0xFF00FF66, false);
        guiGraphics.pose().popPose();
    }
}





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
import com.sighs.apricityui.task.MouseMoveEngine;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.render.DocumentLayerOrder;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLScancode;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;

import com.sighs.apricityui.config.ApricityUIConfig;
import com.sighs.apricityui.world.WorldWindow;

@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class Client {
    private static int lastWindowWidth = -1;
    private static int lastWindowHeight = -1;
    private static int lastFramebufferWidth = -1;
    private static int lastFramebufferHeight = -1;
    private static double lastGuiScale = -1.0d;

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (Minecraft.getInstance().gui.screen() != null) return;
        if (handleViewportZoomAtMouse(event.getScrollDeltaY() > 0)) {
            event.setCanceled(true);
            return;
        }
        boolean nativeConsumed = Operation.scroll(event.getScrollDeltaY());
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                MouseEvent mouseEvent = new MouseEvent("wheel", realPos);
                mouseEvent.deltaY = -event.getScrollDeltaY() * 50;
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
        if (handleViewportZoomAtMouse(event.getScrollDeltaY() > 0)) {
            event.setCanceled(true);
            return;
        }
        if (Operation.scroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (StringUtil.isAllowedChatCharacter(event.getCodePoint())) {
            if (Operation.onCharTyped((char) event.getCodePoint())) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void mouseButton(InputEvent.MouseButton.Pre event) {
        // 26.3 forwards SDL's 1-based button ids (1 left, 2 middle, 3 right);
        // Operation/MouseEvent/DevTools all speak AUI's 0=left, 1=right,
        // 2=middle numbering, so translate once here at the boundary.
        int button = toAuiButton(event.getButton());
        boolean nativeConsumed = false;
        if (event.getAction() == InputConstants.PRESS) nativeConsumed = Operation.onMouseDown(button);
        if (event.getAction() == InputConstants.RELEASE) nativeConsumed = Operation.onMouseUp(button);
        boolean devToolsInspectConsumed = Operation.wasDevToolsInspectConsumed();
        if (Minecraft.getInstance().gui.screen() != null) {
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
                    mouseEvent = new MouseEvent("mousedown", realPos, button);
                } else {
                    mouseEvent = new MouseEvent("mouseup", realPos, button);
                }
                MouseEvent.tiggerEvent(mouseEvent, window.document);
                nativeConsumed |= mouseEvent.isNativeConsumed();
            }
        }
        if (nativeConsumed || CursorReleaseController.isActive()) event.setCanceled(true);
    }

    /**
     * Maps an SDL mouse button id (1 = left, 2 = middle, 3 = right, 4.. = X
     * buttons) onto AUI's GLFW-era numbering (0 = left, 1 = right, 2 = middle,
     * 3 = X1 ...), which {@code Operation}, {@link MouseEvent} and DevTools use.
     */
    private static int toAuiButton(int sdlButton) {
        return switch (sdlButton) {
            case 1 -> 0;   // SDL_BUTTON_LEFT
            case 2 -> 2;   // SDL_BUTTON_MIDDLE
            case 3 -> 1;   // SDL_BUTTON_RIGHT
            default -> Math.max(0, sdlButton - 1);
        };
    }

    @SubscribeEvent
    public static void mouseMove(RenderFrameEvent.Pre event) {
        // 渲染帧仅作轮询载具：60Hz 固定节拍由 MouseMoveEngine 调度，
        // 未到期时一次 nanoTime 比较即返回，不会随刷新率放大分发频率。
        MouseMoveEngine.poll(Client::getMousePosition);
    }

    /**
     * Tracks the pointer for DevTools element-inspection and screen tooltips
     * while a Minecraft Screen is open. The GUI layer (which calls
     * {@code DevTools.handleInspectMouseMove}) is disabled whenever a screen is
     * showing, so without this hook the translucent box-model highlight never
     * updates while inspecting on the title screen, a container screen, or the
     * pause screen (regression from the 1.21 {@code updateTooltipPosition}).
     */
    @SubscribeEvent
    public static void updateTooltipPosition(ScreenEvent.Render.Pre event) {
        Position mousePosition = new Position(event.getMouseX(), event.getMouseY());
        com.sighs.apricityui.ui.Tooltip.moveActiveFromScreen(mousePosition);
        DevTools.handleInspectMouseMove(mousePosition);
    }

    @SubscribeEvent
    public static void onKeyPressed(InputEvent.Key event) {
        if (Minecraft.getInstance().gui.screen() != null) {
            return;
        }
        int action = event.getAction();
        if (action != InputConstants.PRESS && action != InputConstants.REPEAT && action != InputConstants.RELEASE)
            return;
        // getKey() is the SDL scancode (what InputConstants.KEY_* and KeyMapping
        // use); getKeycode() is the layout-dependent SDLK.
        if (action == InputConstants.PRESS && handleViewportZoomKeyAtMouse(event.getKey(), event.getModifiers())) {
            return;
        }
        boolean canceled = Operation.handleKeyInput(
                event.getKey(),
                event.getKeycode(),
                action,
                event.getModifiers(),
                action == InputConstants.REPEAT,
                com.sighs.apricityui.event.KeyEvent.Source.INPUT_EVENT
        );
//        if (canceled) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (handleViewportZoomKeyAtMouse(event.getKey(), event.getModifiers())) {
            event.setCanceled(true);
            return;
        }
        int action = InputConstants.PRESS;
        boolean canceled = Operation.handleKeyInput(
                event.getKey(),
                event.getKeycode(),
                action,
                event.getModifiers(),
                false,
                com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT
        );
        if (canceled) event.setCanceled(true);
    }

    private static boolean handleViewportZoomAtMouse(boolean zoomIn) {
        if (!isControlDown()) return false;
        if (Operation.shouldBlockScreenMouseEvents()) return false;
        Document target = findViewportZoomTargetAtMouse();
        if (target == null) return false;
        ApricityUI.LOGGER.debug("[AUI Viewport] wheel zoomIn={} target={}", zoomIn, target.getPath());
        return target.handleViewportZoom(zoomIn);
    }

    private static boolean handleViewportZoomKeyAtMouse(int keyCode, int modifiers) {
        if (!isControlModifier(modifiers)) return false;

        // SDL scancodes (26.3), so the numpad keys sit at different values than
        // GLFW's keycodes: KP_ADD 87, KP_MINUS 86, KP_0 98.
        boolean zoomIn = keyCode == InputConstants.KEY_EQUALS || keyCode == InputConstants.KEY_ADD;
        boolean zoomOut = keyCode == InputConstants.KEY_MINUS || keyCode == SDLScancode.SDL_SCANCODE_KP_MINUS;
        boolean reset = keyCode == InputConstants.KEY_0 || keyCode == InputConstants.KEY_NUMPAD0;
        if (!zoomIn && !zoomOut && !reset) return false;

        Document target = findViewportZoomTargetAtMouse();
        if (target == null) return false;
        ApricityUI.LOGGER.debug("[AUI Viewport] key zoomIn={} reset={} target={}", zoomIn, reset, target.getPath());
        return reset ? target.resetViewportZoom() : target.handleViewportZoom(zoomIn);
    }

    private static Document findViewportZoomTargetAtMouse() {
        Position mouse = Operation.getMousePositionDirectly();
        if (mouse == null) return null;
        boolean passThrough = ApricityUIConfig.get(ApricityUIConfig.CLIENT.viewportZoomPassThrough);
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
        // 26.3 uses SDL_KMOD masks: MOD_CONTROL is 192 (left|right), not GLFW's 2.
        return (modifiers & InputConstants.MOD_CONTROL) != 0 || isControlDown();
    }

    private static boolean isControlDown() {
        try {
            return Minecraft.getInstance().hasControlDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        int action = InputConstants.RELEASE;
        Operation.handleKeyInput(
                event.getKey(),
                event.getKeycode(),
                action,
                event.getModifiers(),
                false,
                com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT
        );
    }

    /**
     * 26.3 GUI frames only record render state; AUI documents and their item
     * nodes are rasterised together by the fullscreen Picture-in-Picture layer,
     * which {@link com.sighs.apricityui.client.gui.ApricityGuiLayers} submits
     * from here for screens (the HUD layer does it for the in-game overlay).
     */
    @SubscribeEvent
    public static void drawScreen(ScreenEvent.Render.Post event) {
        com.sighs.apricityui.render.FrameTimingHud.beginFrame();
        try {
            // AuiLinkedScreens submit the UI PIP state during extraction so
            // frame-local floating items join the same document pass. Other
            // screens submit the normal document state here.
            if (!(Minecraft.getInstance().gui.screen() instanceof com.sighs.apricityui.screen.AuiLinkedScreen)) {
                com.sighs.apricityui.client.gui.ApricityGuiLayers.submitUi(event.getGuiGraphics());
            }
            com.sighs.apricityui.client.gui.ApricityGuiLayers.submitCursor(event.getGuiGraphics());
        } finally {
            com.sighs.apricityui.render.FrameTimingHud.endFrame();
            drawFrameTimingHud(event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        CursorReleaseController.tick();
        ClientRuntimeSelfTest.tick();
        com.sighs.apricityui.loader.ClientLoaderForge.tickTemplateScanRetry();
        if (ApricityUIConfig.consumeClientReloadPending()) {
            com.sighs.apricityui.dev.debug.ExternalDebugServer.reconcileConfiguration();
        }
        com.sighs.apricityui.dev.debug.ExternalDebugServer.tick();
        FrameScheduler.tick();
        DebugReloadWatcher.tick();
        DevTools.drainLogs();
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
     * A grabbed (pointer-locked) cursor has virtual coordinates that track look
     * movement, while the visible crosshair remains fixed at the center of the
     * GUI viewport.
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

    /**
     * Returns the live cursor position directly from SDL, bypassing the cached
     * {@link MouseHandler} position.
     *
     * <p>26.3 has no GLFW: {@code SDL_GetMouseState} reports the pointer in the
     * focused window's coordinates (the same space {@code MouseHandler} caches),
     * and the window handle is an {@code SDL_Window*} — it is only checked for
     * "is there a window at all".</p>
     */
    public static Position getMousePositionDirectly() {
        Window window = Minecraft.getInstance().getWindow();
        if (window == null || window.handle() == 0L) return null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xBuf = stack.mallocFloat(1);
            FloatBuffer yBuf = stack.mallocFloat(1);
            SDLMouse.SDL_GetMouseState(xBuf, yBuf);
            return MouseCoordinates.toGui(xBuf.get(0), yBuf.get(0),
                    window.getScreenWidth(), window.getScreenHeight(),
                    window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
    }

    /**
     * Polls the physical state of a {@code key.keyboard.*} / {@code key.mouse.*}
     * binding name.
     *
     * <p>26.3's {@link InputConstants#getKey(String)} owns the name table (the
     * old hand-written {@code KEY_MAP} duplicated it), and
     * {@code PhysicalKeyState} reads SDL's keyboard/mouse state in the same
     * scancode/1-based-button space, so no per-key table is needed here.</p>
     */
    public static boolean isKeyPressed(String keyName) {
        if (keyName == null || keyName.isEmpty()) {
            return false;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Window window = minecraft.getWindow();
            if (window == null || window.handle() == 0L) return false;
            return com.sighs.apricityui.spi.PhysicalKeyState.isDown(InputConstants.getKey(keyName));
        } catch (RuntimeException | LinkageError e) {
            // Unknown names throw IllegalArgumentException; SDL state reads are
            // only safe on the main thread.
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

    /** Draws native glyphs immediately, retaining the current target, clip and painter order. */
    public static void drawDefaultFont(PoseStack poseStack, Text text, String content, Position position) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) return;
        com.sighs.apricityui.neoforge.ItemRenderService.submitImmediately(
                com.sighs.apricityui.render.OutputTargets.currentTarget(),
                immediate -> submitDefaultFont(immediate, poseStack, text, content, position));
    }

    private static void submitDefaultFont(SubmitNodeCollector collector, PoseStack poseStack,
                                          Text text, String content, Position position) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        try {
            poseStack.translate(position.x, position.y, 0);
            // 默认字体也要保留 z 轴缩放，避免在容器 Screen 中把文本深度压扁后被后续菜单/物品绘制覆盖。
            float scale = (float) text.defaultFontScale();
            poseStack.scale(scale, scale, 1f);
            MutableComponent renderText = Component.literal(content == null ? "" : content);
            if (text.isBold()) renderText = renderText.withStyle(ChatFormatting.BOLD);
            if (text.isOblique()) renderText = renderText.withStyle(ChatFormatting.ITALIC);
            if (text.isUnderlined()) renderText = renderText.withStyle(ChatFormatting.UNDERLINE);
            if (text.isStrikethrough()) renderText = renderText.withStyle(ChatFormatting.STRIKETHROUGH);
            FormattedCharSequence sequence = renderText.getVisualOrderText();
            int stroke = Math.max(0, (int) Math.ceil(text.strokeWidth));
            if (stroke > 0) {
                int strokeColor = text.strokeColor.getValue();
                for (int ox = -stroke; ox <= stroke; ox++) {
                    for (int oy = -stroke; oy <= stroke; oy++) {
                        if (ox == 0 && oy == 0) continue;
                        if (ox * ox + oy * oy > stroke * stroke) continue;
                        com.sighs.apricityui.neoforge.FeatureRenderBridge.submitText(
                                collector, poseStack, font, sequence, ox, oy, strokeColor, false);
                    }
                }
            }
            com.sighs.apricityui.neoforge.FeatureRenderBridge.submitText(
                    collector, poseStack, font, sequence, 0, 0, text.color.getValue(), false);
        } finally {
            poseStack.popPose();
        }
    }

    public static void drawDefaultFont(PoseStack poseStack, Text text, Position position) {
        drawDefaultFont(poseStack, text, text.content, position);
    }

    /**
     * Draws the frame-timing HUD overlay, if enabled.
     */
    public static void drawFrameTimingHud(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics) {
        if (guiGraphics == null || !com.sighs.apricityui.render.FrameTimingHud.isEnabled()) return;
        String text = com.sighs.apricityui.render.FrameTimingHud.frameStatsText();
        if (text == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.font.width(text) + 8;
        guiGraphics.pose().pushMatrix();
        guiGraphics.fill(2, 2, 2 + width, 16, 0xCC000000);
        guiGraphics.text(minecraft.font, text, 6, 6, 0xFF00FF66, false);
        guiGraphics.pose().popMatrix();
    }

}

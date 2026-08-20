package com.sighs.apricityui.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.dev.ResourceManager;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.DocumentLayerOrder;
import com.sighs.apricityui.render.FrameTimingHud;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.screen.ApricityContainerScreen;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.task.FrameScheduler;
import com.sighs.apricityui.task.MouseMoveEngine;
import com.sighs.apricityui.ui.Tooltip;

import com.sighs.apricityui.world.WorldWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

/** Shared client facade used by common rendering code. */
public final class Client {
    private static int lastWindowWidth = -1;
    private static int lastWindowHeight = -1;
    private static int lastFramebufferWidth = -1;
    private static int lastFramebufferHeight = -1;
    private static double lastGuiScale = -1;

    private Client() { }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        CursorReleaseController.tick();
        FrameScheduler.tick();
        ResourceManager.reconcileConfiguredMode();
        DevTools.drainLogs();
        MouseMoveEngine.poll(Client::getMousePosition);
        Window window = minecraft.getWindow();
        int width = window.getScreenWidth();
        int height = window.getScreenHeight();
        int framebufferWidth = window.getWidth();
        int framebufferHeight = window.getHeight();
        double guiScale = window.getGuiScale();
        if (width != lastWindowWidth || height != lastWindowHeight
                || framebufferWidth != lastFramebufferWidth || framebufferHeight != lastFramebufferHeight
                || Double.compare(guiScale, lastGuiScale) != 0) {
            lastWindowWidth = width;
            lastWindowHeight = height;
            lastFramebufferWidth = framebufferWidth;
            lastFramebufferHeight = framebufferHeight;
            lastGuiScale = guiScale;
            for (Document document : Document.getAll()) if (document != null && !document.isDisposed()) document.applyViewport(true);
            com.sighs.apricityui.init.Window.window.fireResizeEvent();
        }
    }

    public static void drawScreenLike(GuiGraphics graphics) {
        // 渲染帧仅作轮询载具：60Hz 固定节拍由 MouseMoveEngine 调度，未到期零成本返回。
        MouseMoveEngine.poll(Client::getMousePosition);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ApricityContainerScreen || minecraft.screen instanceof ApricityScreen) return;
        Position mousePosition = getMousePosition();
        Tooltip.moveActiveFromScreen(mousePosition);
        DevTools.handleInspectMouseMove(mousePosition);
        FrameTimingHud.beginFrame();
        try {
            drawPersistentScreenDocuments(graphics);
            graphics.flush();
            Cursor.drawPseudoCursor(graphics.pose());
            graphics.flush();
        } finally {
            FrameTimingHud.endFrame();
            drawFrameTimingHud(graphics);
        }
    }

    public static void drawOverlayLike(GuiGraphics graphics) {
        // 渲染帧仅作轮询载具：60Hz 固定节拍由 MouseMoveEngine 调度，未到期零成本返回。
        MouseMoveEngine.poll(Client::getMousePosition);
        if (Minecraft.getInstance().screen != null) return;
        // F1(hideGui)隐藏原版 HUD 时,overlay 文档一并隐藏
        if (Minecraft.getInstance().options.hideGui) return;
        Position mousePosition = getMousePosition();
        Tooltip.moveActiveFromScreen(mousePosition);
        DevTools.handleInspectMouseMove(mousePosition);
        FrameTimingHud.beginFrame();
        try {
            for (Document document : DocumentLayerOrder.backToFront(Document.getAll())) {
                if (document == null || document.inWorld || document.isManuallyRendered()) continue;
                Base.drawOverlayDocument(graphics.pose(), document);
                ResourcePreviewDialog.draw(graphics.pose(), document);
            }
            graphics.flush();
            Cursor.drawPseudoCursor(graphics.pose());
            graphics.flush();
        } finally {
            FrameTimingHud.endFrame();
            drawFrameTimingHud(graphics);
        }
    }

    public static void drawPersistentScreenDocuments(GuiGraphics graphics) { drawPersistentScreenDocuments(graphics, null); }

    public static void drawPersistentScreenDocuments(GuiGraphics graphics, Document excludedDocument) {
        if (graphics == null) return;
        for (Document document : DocumentLayerOrder.backToFront(Document.getAll())) {
            if (document == null || document == excludedDocument || document.inWorld || document.isManuallyRendered() || !document.isReloadPersistent()) continue;
            Base.drawOverlayDocument(graphics.pose(), document);
            ResourcePreviewDialog.draw(graphics.pose(), document);
        }
    }


    /** Dispatches a native keyboard event to the common AUI input pipeline. */
    public static boolean handleKeyInput(int key, int scanCode, int action, int modifiers) {
        boolean screenEvent = Minecraft.getInstance().screen != null;
        return Operation.handleKeyInput(
                key,
                scanCode,
                action,
                modifiers,
                action == GLFW.GLFW_REPEAT,
                screenEvent
                        ? com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT
                        : com.sighs.apricityui.event.KeyEvent.Source.INPUT_EVENT
        );
    }

    /** Dispatches a native mouse button event and reports whether Minecraft should ignore it. */
    public static boolean handleMouseButton(int button, int action) {
        boolean nativeConsumed = false;
        if (action == GLFW.GLFW_PRESS) nativeConsumed = Operation.onMouseDown(button);
        if (action == GLFW.GLFW_RELEASE) nativeConsumed = Operation.onMouseUp(button);
        boolean devToolsInspectConsumed = Operation.wasDevToolsInspectConsumed();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return nativeConsumed || devToolsInspectConsumed;
        }
        if (devToolsInspectConsumed) return true;
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos == null) continue;
            MouseEvent mouseEvent = action == GLFW.GLFW_PRESS
                    ? new MouseEvent("mousedown", realPos, button)
                    : new MouseEvent("mouseup", realPos, button);
            MouseEvent.tiggerEvent(mouseEvent, window.document);
            nativeConsumed |= mouseEvent.isNativeConsumed();
        }
        return nativeConsumed || CursorReleaseController.isActive();
    }

    /** Dispatches a native scroll event and reports whether Minecraft should ignore it. */
    public static boolean handleMouseScroll(double delta) {
        boolean nativeConsumed = Operation.scroll(delta);
        if (Minecraft.getInstance().screen != null) return nativeConsumed;
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos == null) continue;
            MouseEvent mouseEvent = new MouseEvent("wheel", realPos);
            mouseEvent.deltaY = -delta * 50;
            mouseEvent.scrollDelta = mouseEvent.deltaY;
            mouseEvent.cancelable = true;
            MouseEvent.tiggerEvent(mouseEvent, window.document);
            nativeConsumed |= mouseEvent.isNativeConsumed();
        }
        return nativeConsumed || CursorReleaseController.isActive();
    }

    public static Position getMousePosition() {
        Minecraft minecraft = Minecraft.getInstance();
        MouseHandler mouse = minecraft.mouseHandler;
        Window window = minecraft.getWindow();
        return MouseCoordinates.toGui(mouse.xpos(), mouse.ypos(),
                window.getScreenWidth(), window.getScreenHeight(),
                window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public static Position getMousePositionForWorldInteraction() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.mouseHandler.isMouseGrabbed()) return new Position(getWindow().getGuiScaledWidth() / 2d, getWindow().getGuiScaledHeight() / 2d);
        return getMousePosition();
    }

    public static Position getMousePositionDirectly() {
        Window window = getWindow();
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(window.getWindow(), x, y);
        return MouseCoordinates.toGui(x[0], y[0],
                window.getScreenWidth(), window.getScreenHeight(),
                window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public static boolean isKeyPressed(String keyName) {
        if (keyName == null || keyName.isBlank()) return false;
        try {
            InputConstants.Key input = InputConstants.getKey(keyName);
            if (input == null || input == InputConstants.UNKNOWN) return false;
            long window = getWindow().getWindow();
            if (input.getType() == InputConstants.Type.MOUSE) {
                return GLFW.glfwGetMouseButton(window, input.getValue()) == GLFW.GLFW_PRESS;
            }
            return GLFW.glfwGetKey(window, input.getValue()) == GLFW.GLFW_PRESS;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static Window getWindow() { return Minecraft.getInstance().getWindow(); }
    public static Size getWindowSize() { Window window = getWindow(); return new Size(window.getGuiScaledWidth(), window.getGuiScaledHeight()); }
    public static int getDefaultFontWidth(String text) { return getDefaultFontWidth(text, false, false, 0); }
    public static int getDefaultFontWidth(String text, boolean bold) { return getDefaultFontWidth(text, bold, false, 0); }
    public static int getDefaultFontWidth(String text, boolean bold, boolean oblique) { return getDefaultFontWidth(text, bold, oblique, 0); }
    public static int getDefaultFontWidth(String text, boolean bold, boolean oblique, double strokeWidth) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.font == null) return 0;
        MutableComponent component = Component.literal(text == null ? "" : text);
        if (bold) component.withStyle(ChatFormatting.BOLD);
        if (oblique) component.withStyle(ChatFormatting.ITALIC);
        return (int) Math.ceil(minecraft.font.width(component) + Math.max(0, strokeWidth) * 2);
    }

    public static void drawDefaultFont(PoseStack pose, Text text, String content, Position position) {
        pose.pushPose();
        pose.translate(position.x, position.y, 0);
        pose.scale((float) text.defaultFontScale(), (float) text.defaultFontScale(), 1);
        MutableComponent component = Component.literal(content == null ? "" : content);
        if (text.isBold()) component.withStyle(ChatFormatting.BOLD);
        if (text.isOblique()) component.withStyle(ChatFormatting.ITALIC);
        if (text.isUnderlined()) component.withStyle(ChatFormatting.UNDERLINE);
        if (text.isStrikethrough()) component.withStyle(ChatFormatting.STRIKETHROUGH);
        Minecraft.getInstance().font.drawInBatch(component.getVisualOrderText(), 0, 0, text.color.getValue(), false,
                pose.last().pose(), Minecraft.getInstance().renderBuffers().bufferSource(),
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
        pose.popPose();
    }

    public static void drawDefaultFont(PoseStack pose, Text text, Position position) { drawDefaultFont(pose, text, text.content, position); }

    public static void drawFrameTimingHud(GuiGraphics graphics) {
        if (graphics == null || !FrameTimingHud.isEnabled()) return;
        String value = FrameTimingHud.frameStatsText();
        if (value == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        graphics.fill(2, 2, minecraft.font.width(value) + 8, 16, 0xCC000000);
        graphics.drawString(minecraft.font, value, 6, 6, 0xFF00FF66, false);
    }
}

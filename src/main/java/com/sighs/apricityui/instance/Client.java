package com.sighs.apricityui.instance;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Operation;
import com.sighs.apricityui.init.Runtime;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;

@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class Client {
    public static final HashMap<String, Integer> KEY_MAP = new HashMap<>();
    private static int lastWindowWidth = -1;
    private static int lastWindowHeight = -1;

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
    public static void icon(ClientTickEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof TitleScreen) {
            if (Document.get("apricityui/icon.html").isEmpty()) Document.create("apricityui/icon.html");
        } else Document.remove("apricityui/icon.html");
    }

    @SubscribeEvent
    public static void drawScreen(ScreenEvent.Render.Post event) {
        if (Minecraft.getInstance().screen instanceof ApricityContainerScreen) {
            return;
        }
        // 由于不可抗拒原因？，这里需以 pip 形式渲染
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().screen != null) {
            ApricityGuiLayers.submitOverlay(event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (Minecraft.getInstance().screen != null) return;
        boolean consumed = Operation.scroll(event.getScrollDeltaY());
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                MouseEvent mouseEvent = new MouseEvent("scroll", realPos);
                mouseEvent.scrollDelta = -event.getScrollDeltaY() * 50;
                consumed |= MouseEvent.tiggerEvent(mouseEvent, window.document);
            }
        }
        if (consumed) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (Operation.scroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        int codePoint = event.getCodePoint();
        if (StringUtil.isAllowedChatCharacter(codePoint)) {
            if (Operation.onCharTyped((char) codePoint)) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void mouseButton(InputEvent.MouseButton.Pre event) {
        boolean consumed = false;
        if (event.getAction() == InputConstants.PRESS) consumed = Operation.onMouseDown(event.getButton());
        if (event.getAction() == InputConstants.RELEASE) consumed = Operation.onMouseUp(event.getButton());
        if (Minecraft.getInstance().screen != null) {
            if (consumed) event.setCanceled(true);
            return;
        }
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                if (event.getAction() == InputConstants.PRESS) {
                    consumed |= MouseEvent.tiggerEvent(new MouseEvent("mousedown", realPos, event.getButton()), window.document);
                } else {
                    consumed |= MouseEvent.tiggerEvent(new MouseEvent("mouseup", realPos, event.getButton()), window.document);
                }
            }
        }
        if (consumed) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void mouseMove(ClientTickEvent.Post event) {
        Operation.onMouseMove(getMousePosition());
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                MouseEvent moveEvent = new MouseEvent("mousemove", realPos);
                MouseEvent.tiggerEvent(moveEvent, window.document);
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(InputEvent.Key event) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        int action = event.getAction();
        if (action != InputConstants.PRESS && action != InputConstants.REPEAT && action != InputConstants.RELEASE)
            return;
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
        int action = InputConstants.PRESS;
        boolean canceled = Operation.handleKeyInput(
                event.getKeyCode(),
                event.getScanCode(),
                action,
                event.getModifiers(),
                false,
                com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT
        );
//        if (canceled) event.setCanceled(true);
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
    public static void tick(ClientTickEvent.Pre event) {
        Runtime.tick();
//            com.sighs.apricityui.dev.BackdropFilterTestRunner.tick();
        DebugReloadWatcher.tick();
        DebugAIScreenshotTicker.tick();
        Size current = getWindowSize();
        int w = (int) current.width();
        int h = (int) current.height();
        if (lastWindowWidth != w || lastWindowHeight != h) {
            lastWindowWidth = w;
            lastWindowHeight = h;
            Document.getAll().forEach(document -> document.markDirty(Drawer.RELAYOUT));
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

        double mouseX = mouseHandler.xpos() * (double) window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double mouseY = mouseHandler.ypos() * (double) window.getGuiScaledHeight() / (double) window.getScreenHeight();

        return new Position(mouseX, mouseY);
    }

    /**
     * 通过 GLFW 直接从窗口句柄获取实时坐标
     */
    public static Position getMousePositionDirectly() {
        Window window = Minecraft.getInstance().getWindow();
        long handle = window.handle();
        if (handle != 0L) {
            double[] xBuf = new double[1];
            double[] yBuf = new double[1];
            GLFW.glfwGetCursorPos(handle, xBuf, yBuf);
            return new Position(xBuf[0] / window.getGuiScale(), yBuf[0] / window.getGuiScale());
        }
        return null;
    }

    public static boolean isKeyPressed(String keyName) {
        if (keyName == null || keyName.isEmpty()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        long windowHandle = minecraft.getWindow().handle();
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

    public static int getDefaultFontWidth(String text, boolean bold, boolean oblique, int strokeWidth) {
        int stroke = Math.max(0, strokeWidth) * 2;
        if (!bold && !oblique) return Minecraft.getInstance().font.width(text) + stroke;
        MutableComponent renderText = Component.literal(text);
        if (bold) renderText = renderText.withStyle(ChatFormatting.BOLD);
        if (oblique) renderText = renderText.withStyle(ChatFormatting.ITALIC);
        return Minecraft.getInstance().font.width(renderText) + stroke;
    }

    public static void drawDefaultFont(PoseStack poseStack, Text text, String content, Position position) {
        poseStack.pushPose();
        poseStack.translate(position.x, position.y, 0);
        poseStack.scale(text.fontSize / 9f, text.fontSize / 9f, 0f);
        MutableComponent renderText = Component.literal(content == null ? "" : content);
        if (text.isBold()) renderText = renderText.withStyle(ChatFormatting.BOLD);
        if (text.isOblique()) renderText = renderText.withStyle(ChatFormatting.ITALIC);
        int stroke = Math.max(0, text.strokeWidth);
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
}





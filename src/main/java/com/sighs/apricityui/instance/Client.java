package com.sighs.apricityui.instance;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Operation;
import com.sighs.apricityui.init.Runtime;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.util.StringUtils;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHelper;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.util.SharedConstants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
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
    public static void drawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (Minecraft.getInstance().screen instanceof ApricityContainerScreen) {
            return;
        }
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().screen != null) {
            Base.drawAllDocument(event.getMatrixStack());
            Cursor.drawPseudoCursor(event.getMatrixStack());
        }
    }

    @SubscribeEvent
    public static void drawOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (Minecraft.getInstance().screen == null) {
            Base.drawAllDocument(event.getMatrixStack());
            Cursor.drawPseudoCursor(event.getMatrixStack());
        }
    }

    @SubscribeEvent
    public static void scroll(GuiScreenEvent.MouseScrollEvent.Pre event) {
        Operation.scroll(event.getScrollDelta());
        for (WorldWindow window : WorldWindow.windows) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                MouseEvent mouseEvent = new MouseEvent("scroll", realPos);
                mouseEvent.scrollDelta = -event.getScrollDelta() * 50;
                MouseEvent.tiggerEvent(mouseEvent, window.document);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void scrollPost(GuiScreenEvent.MouseScrollEvent.Post event) {
        Operation.scroll(event.getScrollDelta());
    }

    @SubscribeEvent
    public static void onCharTyped(GuiScreenEvent.KeyboardCharTypedEvent.Pre event) {
        if (SharedConstants.isAllowedChatCharacter(event.getCodePoint())) {
            if (Operation.onCharTyped(event.getCodePoint())) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void mouseButton(InputEvent.RawMouseEvent event) {
        if (event.getAction() == GLFW.GLFW_PRESS) Operation.onMouseDown();
        if (event.getAction() == GLFW.GLFW_RELEASE) Operation.onMouseUp();
        if (Minecraft.getInstance().screen != null) return;
        for (WorldWindow window : WorldWindow.windows) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    MouseEvent.tiggerEvent(new MouseEvent("mousedown", realPos), window.document);
                } else {
                    MouseEvent.tiggerEvent(new MouseEvent("mouseup", realPos), window.document);
                }
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void mouseMove(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Operation.onMouseMove(getMousePosition());
            for (WorldWindow window : WorldWindow.windows) {
                Position realPos = window.getRealPos();
                if (realPos != null) {
                    MouseEvent moveEvent = new MouseEvent("mousemove", realPos);
                    MouseEvent.tiggerEvent(moveEvent, window.document);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(InputEvent.KeyInputEvent event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Operation.onKeyPressed(event.getKey());
        System.out.println(event.getKey());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Runtime.tick();
            Size current = getWindowSize();
            int w = (int) current.width();
            int h = (int) current.height();
            if (lastWindowWidth != w || lastWindowHeight != h) {
                lastWindowWidth = w;
                lastWindowHeight = h;
                Document.getAll().forEach(document -> document.markDirty(Drawer.RELAYOUT));
            }
        }
    }

    public static Position getMousePosition() {
        MouseHelper mouseHandler = Minecraft.getInstance().mouseHandler;
        MainWindow window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        return new Position(mouseHandler.xpos() / scale, mouseHandler.ypos() / scale);
    }

    public static boolean isKeyPressed(String keyName) {
        if (StringUtils.isNullOrEmpty(keyName)) {
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

    public static MainWindow getWindow() {
        return Minecraft.getInstance().getWindow();
    }

    public static Size getWindowSize() {
        MainWindow window = Minecraft.getInstance().getWindow();
        return new Size(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public static int getDefaultFontWidth(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    public static void drawDefaultFont(MatrixStack stack, Text text, Position position) {
        stack.pushPose();
        stack.translate(position.x, position.y, 0);
        stack.scale(text.fontSize / 9f, text.fontSize / 9f, 0f);
        IRenderTypeBuffer.Impl bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Minecraft.getInstance().font.drawInBatch(text.content, 0, 0, text.color.getValue(), false, stack.last().pose(), Minecraft.getInstance().renderBuffers().bufferSource(), false, 0, 15728880);
        bufferSource.endBatch();
        stack.popPose();
    }
}

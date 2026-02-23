package com.sighs.apricityui.instance;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Operation;
import com.sighs.apricityui.init.Runtime;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.StringUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;

@EventBusSubscriber(modid = ApricityUI.MOD_ID, value = Dist.CLIENT)
public class Client {
    public static final HashMap<String, Integer> KEY_MAP = new HashMap<>();

    @SubscribeEvent
    public static void drawScreen(ScreenEvent.Render.Post event) {
        if (Minecraft.getInstance().screen instanceof ApricityContainerScreen) {
            return;
        }
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().screen != null)
            Base.drawAllDocument(event.getGuiGraphics().pose());
    }

    @SubscribeEvent
    public static void drawOverlay(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) Base.drawAllDocument(event.getGuiGraphics().pose());
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        Operation.scroll(event.getScrollDeltaY());
        for (WorldWindow window : WorldWindow.windows) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                MouseEvent mouseEvent = new MouseEvent("scroll", realPos);
                mouseEvent.scrollDelta = -event.getScrollDeltaY() * 50;
                MouseEvent.tiggerEvent(mouseEvent, window.document);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void scroll(ScreenEvent.MouseScrolled.Post event) {
        Operation.scroll(event.getScrollDeltaY());
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (StringUtil.isAllowedChatCharacter(event.getCodePoint())) {
            if (Operation.onCharTyped(event.getCodePoint())) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void mouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == InputConstants.PRESS) Operation.onMouseDown();
        if (event.getAction() == InputConstants.RELEASE) Operation.onMouseUp();
        if (Minecraft.getInstance().screen != null) return;
        for (WorldWindow window : WorldWindow.windows) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                if (event.getAction() == InputConstants.PRESS) {
                    MouseEvent.tiggerEvent(new MouseEvent("mousedown", realPos), window.document);
                } else {
                    MouseEvent.tiggerEvent(new MouseEvent("mouseup", realPos), window.document);
                }
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void mouseMove(ClientTickEvent.Post event) {
        Operation.onMouseMove(getMousePosition());
        for (WorldWindow window : WorldWindow.windows) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                MouseEvent moveEvent = new MouseEvent("mousemove", realPos);
                MouseEvent.tiggerEvent(moveEvent, window.document);
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (Operation.onKeyPressed(event.getKeyCode())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Runtime.tick();
    }

    public static Position getMousePosition() {
        MouseHandler mouseHandler = Minecraft.getInstance().mouseHandler;
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        return new Position(mouseHandler.xpos() / scale, mouseHandler.ypos() / scale);
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

    public static Size getWindowSize() {
        Window window = Minecraft.getInstance().getWindow();
        return new Size(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public static int getDefaultFontWidth(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    public static void drawDefaultFont(PoseStack poseStack, Text text, Position position) {
        poseStack.pushPose();
        poseStack.translate(position.x, position.y, 0);
        poseStack.scale(text.fontSize / 9f, text.fontSize / 9f, 0f);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Minecraft.getInstance().font.drawInBatch(text.content, 0, 0, text.color.getValue(), false, poseStack.last().pose(), Minecraft.getInstance().renderBuffers().bufferSource(), net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
        bufferSource.endBatch();
        poseStack.popPose();
    }
}

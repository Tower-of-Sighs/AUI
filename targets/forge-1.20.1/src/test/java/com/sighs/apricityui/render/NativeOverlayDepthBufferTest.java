package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/** Real GL framebuffer regression, using a hidden 32x32 window and no Minecraft client. */
class NativeOverlayDepthBufferTest {
    private static Object call(String owner, String method, Class<?>[] signature, Object... args) throws Exception {
        return Class.forName(owner).getMethod(method, signature).invoke(null, args);
    }

    private static Object gl(String method, Class<?>[] signature, Object... args) throws Exception {
        return call("org.lwjgl.opengl.GL11", method, signature, args);
    }

    @Test
    void overlayCoversNativeItemWhilePreservingColorTransparencyAndLocalDepth() throws Exception {
        String glfw = "org.lwjgl.glfw.GLFW";
        String state = "com.mojang.blaze3d.platform.GlStateManager";
        var renderThread = Class.forName("com.mojang.blaze3d.systems.RenderSystem").getDeclaredField("renderThread");
        renderThread.setAccessible(true);
        Object previousThread = renderThread.get(null);
        assertEquals(true, call(glfw, "glfwInit", new Class<?>[0]));
        long window = 0;
        try {
            renderThread.set(null, Thread.currentThread());
            call(glfw, "glfwDefaultWindowHints", new Class<?>[0]);
            call(glfw, "glfwWindowHint", new Class<?>[]{int.class, int.class}, 0x00020004, 0); // GLFW_VISIBLE
            window = (long) call(glfw, "glfwCreateWindow",
                    new Class<?>[]{int.class, int.class, CharSequence.class, long.class, long.class},
                    32, 32, "AUI depth regression", 0L, 0L);
            assertNotEquals(0L, window);
            call(glfw, "glfwMakeContextCurrent", new Class<?>[]{long.class}, window);
            call("org.lwjgl.opengl.GL", "createCapabilities", new Class<?>[0]);
            gl("glViewport", new Class<?>[]{int.class, int.class, int.class, int.class}, 0, 0, 32, 32);
            gl("glClearColor", new Class<?>[]{float.class, float.class, float.class, float.class}, 0F, 0F, 0F, 1F);
            gl("glClearDepth", new Class<?>[]{double.class}, 1D);
            gl("glClear", new Class<?>[]{int.class}, 0x4000 | 0x0100);
            gl("glEnable", new Class<?>[]{int.class}, 0x0B71); // GL_DEPTH_TEST
            gl("glDepthFunc", new Class<?>[]{int.class}, 0x0203); // GL_LEQUAL
            quad(-1, 1, -0.8F, 1, 0, 0, 1); // near native item
            quad(-1, 0, 0, 1, 1, 1, 1); // farther AUI panel is wrongly rejected
            assertPixel(8, 255, 0, 0);

            // Clear must ignore inherited scissor and write-mask state, then restore them.
            call(state, "_depthMask", new Class<?>[]{boolean.class}, false);
            call(state, "_enableScissorTest", new Class<?>[0]);
            call(state, "_scissorBox", new Class<?>[]{int.class, int.class, int.class, int.class}, 0, 0, 1, 1);
            gl("glClearDepth", new Class<?>[]{double.class}, 0.25D);
            AuiRenderService backend = (AuiRenderService) Class.forName("com.sighs.apricityui.forge.RenderService")
                    .getField("INSTANCE").get(null);
            backend.clearDepthBuffer();
            assertEquals(false, gl("glGetBoolean", new Class<?>[]{int.class}, 0x0B72));
            assertEquals(true, gl("glIsEnabled", new Class<?>[]{int.class}, 0x0C11));
            assertEquals(0.25D, (double) gl("glGetDouble", new Class<?>[]{int.class}, 0x0B73), 0.0001D);
            assertPixel(8, 255, 0, 0); // only depth was discarded
            call(state, "_disableScissorTest", new Class<?>[0]);
            call(state, "_depthMask", new Class<?>[]{boolean.class}, true);

            quad(-1, 0, 0, 1, 1, 1, 1);
            assertPixel(8, 255, 255, 255); // opaque panel covers the native item
            assertPixel(24, 255, 0, 0); // uncovered native screen remains visible
            quad(-1, 0, 0.5F, 0, 1, 0, 1);
            assertPixel(8, 255, 255, 255); // depth within AUI is still enforced
            gl("glEnable", new Class<?>[]{int.class}, 0x0BE2); // GL_BLEND
            gl("glBlendFunc", new Class<?>[]{int.class, int.class}, 0x0302, 0x0303);
            quad(0, 1, 0, 1, 1, 1, 0.5F);
            assertPixel(24, 255, 128, 128); // translucent panel blends with preserved color
            assertEquals(0, gl("glGetError", new Class<?>[0]));
        } finally {
            renderThread.set(null, previousThread);
            if (window != 0) call(glfw, "glfwDestroyWindow", new Class<?>[]{long.class}, window);
            call(glfw, "glfwTerminate", new Class<?>[0]);
            call("org.lwjgl.opengl.GL", "setCapabilities", new Class<?>[]{Class.forName("org.lwjgl.opengl.GLCapabilities")}, (Object) null);
        }
    }

    private static void quad(float left, float right, float z, float r, float g, float b, float a) throws Exception {
        gl("glColor4f", new Class<?>[]{float.class, float.class, float.class, float.class}, r, g, b, a);
        gl("glBegin", new Class<?>[]{int.class}, 0x0007);
        for (float[] vertex : new float[][]{{left, -1}, {right, -1}, {right, 1}, {left, 1}}) {
            gl("glVertex3f", new Class<?>[]{float.class, float.class, float.class}, vertex[0], vertex[1], z);
        }
        gl("glEnd", new Class<?>[0]);
    }

    private static void assertPixel(int x, int r, int g, int b) throws Exception {
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        gl("glReadPixels", new Class<?>[]{int.class, int.class, int.class, int.class, int.class, int.class, ByteBuffer.class},
                x, 16, 1, 1, 0x1908, 0x1401, pixel);
        int[] expected = {r, g, b};
        for (int channel = 0; channel < 3; channel++) {
            assertEquals(expected[channel], Byte.toUnsignedInt(pixel.get(channel)), 1, "channel " + channel + " at x=" + x);
        }
    }
}

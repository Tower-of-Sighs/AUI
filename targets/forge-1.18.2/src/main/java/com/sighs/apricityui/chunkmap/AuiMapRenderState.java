package com.sighs.apricityui.chunkmap;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.sighs.apricityui.forge.RenderService;
import com.sighs.apricityui.spi.AuiRenderService;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** Restores framebuffer, viewport and vanilla's cached render state around a map pass. */
public final class AuiMapRenderState implements AutoCloseable {
    private final AuiRenderService.RenderStateScope vanilla = RenderService.INSTANCE.pushFilterRenderState();
    private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int array = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int buffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
    private final int unpackRow = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
    private final int unpackRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
    private final int unpackPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
    private final int unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
    private final int[] viewport = new int[4], scissor = new int[4];
    private final float[] clearColor = new float[4];
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean stencilEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    private final boolean[] colorMask = new boolean[4];
    private final AuiMapDepthTarget.Binding sceneDepth;

    public AuiMapRenderState(RenderTarget target) {
        this(target, null);
    }

    public AuiMapRenderState(RenderTarget target, AuiMapDepthTarget depth) {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        try (var stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colorMask[i] = mask.get(i) != 0;
        }
        if (target != null) target.bindWrite(true);
        RenderSystem.disableScissor();
        RenderService.INSTANCE.disableStencilTest();
        RenderSystem.colorMask(true, true, true, true);
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        sceneDepth = depth == null ? null : depth.bind(target);
    }

    @Override public void close() {
        if (sceneDepth != null) sceneDepth.close();
        GlStateManager._glBindVertexArray(array);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        BufferUploader.reset();
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, unpackRow);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, unpackRows);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, unpackPixels);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GlStateManager._clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        GlStateManager._clearDepth(clearDepth);
        GlStateManager._scissorBox(scissor[0], scissor[1], scissor[2], scissor[3]);
        if (scissorEnabled) GlStateManager._enableScissorTest(); else GlStateManager._disableScissorTest();
        if (stencilEnabled) RenderService.INSTANCE.enableStencilTest(); else RenderService.INSTANCE.disableStencilTest();
        RenderSystem.colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        vanilla.close();
    }
}

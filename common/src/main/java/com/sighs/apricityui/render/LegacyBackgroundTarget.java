package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.FboHandle;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class LegacyBackgroundTarget {
    private LegacyBackgroundTarget() {
    }

    public static void destroy(AuiRenderService render, FboHandle target) {
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            render.destroyBuffers(target);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        }
    }

    public static AuiRenderService.RenderStateScope open(AuiRenderService render, FboHandle target) {
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean stencilEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        AuiRenderService.RenderStateScope state = render.pushFilterRenderState();
        org.joml.Matrix4f modelView = new org.joml.Matrix4f(render.getModelViewMatrix());
        render.setModelViewMatrix(new org.joml.Matrix4f());
        if (target != null) render.bindWrite(target, true);
        render.disableScissorTest();
        render.disableStencilTest();
        return () -> {
            try {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
                GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                render.scissorBox(scissor[0], scissor[1], scissor[2], scissor[3]);
                if (scissorEnabled) render.enableScissorTest();
                else render.disableScissorTest();
                if (stencilEnabled) render.enableStencilTest();
                else render.disableStencilTest();
            } finally {
                render.setModelViewMatrix(modelView);
                if (state != null) state.close();
            }
        };
    }
}

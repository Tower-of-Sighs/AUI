package com.sighs.apricityui.chunkmap;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/** Shares native scene depth while preserving the owner's GUI depth attachment. */
public final class AuiMapDepthTarget implements AutoCloseable {
    private int texture, width, height;

    Binding bind(RenderTarget target) {
        if (texture == 0 || width != target.width || height != target.height) {
            close();
            texture = GL11.glGenTextures();
            width = target.width; height = target.height;
            GlStateManager._bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F, width, height,
                    0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        }
        Binding binding = new Binding(target.frameBufferId, attachment(GL30.GL_DEPTH_ATTACHMENT),
                attachment(GL30.GL_STENCIL_ATTACHMENT));
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            binding.close();
            throw new IllegalStateException("Native map depth attachment is incomplete");
        }
        return binding;
    }

    private static Attachment attachment(int slot) {
        int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, slot,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int name = type == GL11.GL_NONE ? 0 : GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_DRAW_FRAMEBUFFER, slot, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        return new Attachment(slot, type, name);
    }

    private record Attachment(int slot, int type, int name) {
        void restore() {
            if (type == GL30.GL_RENDERBUFFER) {
                GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, slot, GL30.GL_RENDERBUFFER, name);
            } else {
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, slot, GL11.GL_TEXTURE_2D, name, 0);
            }
        }
    }

    static final class Binding implements AutoCloseable {
        private final int framebuffer;
        private final Attachment depth, stencil;
        Binding(int framebuffer, Attachment depth, Attachment stencil) {
            this.framebuffer = framebuffer; this.depth = depth; this.stencil = stencil;
        }
        @Override public void close() {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            depth.restore(); stencil.restore();
        }
    }

    @Override public void close() {
        if (texture != 0) { GlStateManager._deleteTexture(texture); texture = 0; }
    }
}

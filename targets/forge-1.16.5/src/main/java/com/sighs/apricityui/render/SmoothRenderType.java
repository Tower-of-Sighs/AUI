package com.sighs.apricityui.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class SmoothRenderType extends RenderType {
    private SmoothRenderType(String name, VertexFormat format, int mode, int bufferSize,
                             boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
    public static RenderType createSmooth(ResourceLocation location, boolean blur, boolean depthTest) {
        return create("apricity_image", DefaultVertexFormats.POSITION_COLOR_TEX_LIGHTMAP, GL11.GL_QUADS, 256, true, true,
                RenderType.State.builder()
                        .setTextureState(new RenderState.TextureState(location, blur, false))
                        .setDepthTestState(depthTest ? LEQUAL_DEPTH_TEST : NO_DEPTH_TEST)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setAlphaState(NO_ALPHA)
                        .setFogState(NO_FOG)
                        .setLightmapState(NO_LIGHTMAP)
                        .setDiffuseLightingState(NO_DIFFUSE_LIGHTING)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }
}

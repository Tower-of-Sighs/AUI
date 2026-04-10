package com.sighs.apricityui.instance;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ShaderRegistry {
    private ShaderRegistry() {
    }

    private static RenderPipeline guiTriangles;
    private static RenderPipeline filterPipeline;
    private static RenderType guiTrianglesType;
    private static final Map<Integer, RenderType> GUI_TRIANGLES_MASKED = new ConcurrentHashMap<>();
    private static final Map<Integer, RenderPipeline> FILTER_PIPELINE_MASKED = new ConcurrentHashMap<>();

    public static RenderPipeline guiTriangles() {
        return guiTriangles;
    }

    public static RenderPipeline filterPipeline() {
        return filterPipeline;
    }

    public static RenderPipeline filterPipeline(int stencilMask) {
        if (stencilMask == 0) return filterPipeline;
        RenderPipeline base = filterPipeline;
        if (base == null) return null;

        return FILTER_PIPELINE_MASKED.computeIfAbsent(stencilMask, mask -> {
            StencilPerFaceTest face = new StencilPerFaceTest(StencilOperation.KEEP, StencilOperation.KEEP, StencilOperation.KEEP, CompareOp.EQUAL);
            StencilTest stencil = new StencilTest(face, mask, 0, mask);
            return base.toBuilder()
                    .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/filter/stencil/" + mask))
                    .withStencilTest(stencil)
                    .build();
        });
    }

    public static RenderType guiTrianglesType() {
        return guiTrianglesType;
    }

    public static RenderType guiTrianglesType(int stencilMask) {
        if (stencilMask == 0) return guiTrianglesType;
        RenderPipeline base = guiTriangles;
        if (base == null) return guiTrianglesType;

        return GUI_TRIANGLES_MASKED.computeIfAbsent(stencilMask, mask -> {
            StencilPerFaceTest face = new StencilPerFaceTest(StencilOperation.KEEP, StencilOperation.KEEP, StencilOperation.KEEP, CompareOp.EQUAL);
            StencilTest stencil = new StencilTest(face, mask, 0, mask);
            RenderPipeline pipeline = base.toBuilder()
                    .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/gui_triangles/stencil/" + mask))
                    .withStencilTest(stencil)
                    .build();
            return RenderType.create(
                    "apricityui_gui_triangles_stencil_" + mask,
                    RenderSetup.builder(pipeline).bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup()
            );
        });
    }

    public static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        guiTriangles = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/gui_triangles"))
                .withVertexShader(Identifier.withDefaultNamespace("core/gui"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/gui"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .build();
        event.registerPipeline(guiTriangles);
        guiTrianglesType = RenderType.create(
                "apricityui_gui_triangles",
                RenderSetup.builder(guiTriangles).bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup()
        );
        GUI_TRIANGLES_MASKED.clear();

        filterPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/filter"))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("apricityui", "core/filter"))
                .withSampler("InSampler")
                .withUniform("ApricityFilter", UniformType.UNIFORM_BUFFER)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .build();
        event.registerPipeline(filterPipeline);
        FILTER_PIPELINE_MASKED.clear();
    }
}
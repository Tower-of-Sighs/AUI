package com.sighs.apricityui.chunkmap;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

/** A continuous heightfield whose grid vertices are derived on the GPU. */
public final class AuiSurfaceRenderer implements AutoCloseable {
    private static final BindGroupLayout PARAMETERS = BindGroupLayout.builder()
            .withUniform("SurfaceParams", UniformType.UNIFORM_BUFFER)
            .withSampler("SurfaceColor").withSampler("SurfaceHeight").withSampler("Coverage").build();
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION).withBindGroupLayout(PARAMETERS)
            .withLocation(Identifier.fromNamespaceAndPath("apricityui", "native_surface"))
            .withVertexShader(Identifier.fromNamespaceAndPath("apricityui", "core/native_surface"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("apricityui", "core/native_surface"))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(true)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, 15)).build();
    private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("AUI surface projection");
    private final DynamicUniformStorage<Parameters> parameters = new DynamicUniformStorage<>("AUI surface parameters", 48, 2);
    private GpuTexture color, height;
    private GpuTextureView colorView, heightView;
    private ByteBuffer upload;
    private GpuBuffer heightUpload;
    private AuiNativeTerrainRenderer.Surface surface;
    private boolean closed;

    public void update(AuiNativeTerrainRenderer.Surface next) {
        requireRenderThread();
        if (closed) throw new IllegalStateException("Surface renderer is closed");
        if (next == surface) return;
        if (next == null) { reset(); return; }
        int cells = Math.multiplyExact(next.width(), next.depth());
        if (next.width() < 1 || next.depth() < 1 || next.step() < 1
                || next.heights().length != cells || next.rgba().length != Math.multiplyExact(cells, 4)) {
            throw new IllegalArgumentException("Invalid surface dimensions");
        }
        if (color == null || color.getWidth(0) != next.width() || color.getHeight(0) != next.depth()) {
            releaseTextures();
            var device = RenderSystem.getDevice();
            int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
            color = device.createTexture(() -> "AUI surface color", usage, GpuFormat.RGBA8_UNORM, next.width(), next.depth(), 1, 1);
            height = device.createTexture(() -> "AUI surface height", usage, GpuFormat.R32_FLOAT, next.width(), next.depth(), 1, 1);
            colorView = device.createTextureView(color);
            heightView = device.createTextureView(height);
        }
        int bytes = Math.multiplyExact(cells, 4);
        if (upload == null || upload.capacity() < bytes) {
            if (upload != null) MemoryUtil.memFree(upload);
            upload = MemoryUtil.memAlloc(bytes).order(ByteOrder.nativeOrder());
        }
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        upload.clear();
        upload.put(next.rgba()).flip();
        encoder.writeToTexture(color, upload, 0, 0, 0, 0, next.width(), next.depth());
        upload.clear();
        for (int y : next.heights()) upload.putFloat(y + 1.0f);
        upload.flip();
        if (heightUpload == null || heightUpload.size() < bytes) {
            if (heightUpload != null) heightUpload.close();
            heightUpload = RenderSystem.getDevice().createBuffer(() -> "AUI surface height transfer",
                    GpuBuffer.USAGE_COPY_SRC | GpuBuffer.USAGE_COPY_DST, bytes);
        }
        var heightSlice = heightUpload.slice(0, bytes);
        encoder.writeToBuffer(heightSlice, upload);
        encoder.copyBufferToTexture(heightSlice, 0, 0, next.width(), next.depth(), height,
                0, 0, next.width(), next.depth(), 0, 0);
        surface = next;
    }

    /** Call outside another render pass, before precise opaque geometry on the same attachments. */
    public boolean draw(AuiMapCamera camera, RenderTarget target, GpuTextureView coverage,
                        int coverageChunkX, int coverageChunkZ, float brightness, boolean clear, boolean underground) {
        requireRenderThread();
        if (closed || surface == null) return false;
        parameters.endFrame();
        var values = parameters.writeUniform(new Parameters(surface.width(), surface.depth(), surface.step(), brightness,
                (float) (camera.x() - coverageChunkX * 16L), (float) (camera.z() - coverageChunkZ * 16L),
                (float) (surface.x() - camera.x()), (float) -camera.y(), (float) (surface.z() - camera.z())));
        var transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(camera.viewMatrix()),
                new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        var previousType = RenderSystem.getProjectionType();
        RenderSystem.setProjectionMatrix(projection.getBuffer(camera.nativeProjectionMatrix(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne())),
                camera.mode().equals("street") ? ProjectionType.PERSPECTIVE : ProjectionType.ORTHOGRAPHIC);
        Optional<Vector4fc> clearColor = clear ? Optional.of(new Vector4f(underground ? 0 : 0.46f,
                underground ? 0 : 0.67f, underground ? 0 : 0.82f, 1)) : Optional.empty();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "AUI continuous terrain surface",
                target.getColorTextureView(), clearColor, target.getDepthTextureView(), clear ? OptionalDouble.of(0) : OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transform);
            pass.setUniform("SurfaceParams", values);
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.bindTexture("SurfaceColor", colorView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.bindTexture("SurfaceHeight", heightView, sampler);
            pass.bindTexture("Coverage", coverage, sampler);
            pass.draw(Math.multiplyExact(surface.width() * surface.depth(), 6), 1, 0, 0);
        } finally {
            RenderSystem.setProjectionMatrix(previousProjection, previousType);
        }
        return true;
    }

    public void reset() {
        requireRenderThread();
        surface = null;
        releaseTextures();
    }

    private void releaseTextures() {
        if (colorView != null) { colorView.close(); colorView = null; }
        if (heightView != null) { heightView.close(); heightView = null; }
        if (color != null) { color.close(); color = null; }
        if (height != null) { height.close(); height = null; }
    }

    private static void requireRenderThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Surface rendering requires the client thread");
    }

    @Override public void close() {
        requireRenderThread();
        if (closed) return;
        closed = true;
        reset();
        if (upload != null) { MemoryUtil.memFree(upload); upload = null; }
        if (heightUpload != null) { heightUpload.close(); heightUpload = null; }
        projection.close();
        parameters.close();
    }

    private record Parameters(float width, float depth, float step, float brightness, float coverageX, float coverageZ,
                              float originX, float originY, float originZ)
            implements DynamicUniformStorage.DynamicUniform {
        @Override public void write(ByteBuffer buffer) {
            buffer.putFloat(width).putFloat(depth).putFloat(step).putFloat(brightness);
            buffer.putFloat(coverageX).putFloat(coverageZ).putFloat(0).putFloat(0);
            buffer.putFloat(originX).putFloat(originY).putFloat(originZ).putFloat(0);
        }
    }
}

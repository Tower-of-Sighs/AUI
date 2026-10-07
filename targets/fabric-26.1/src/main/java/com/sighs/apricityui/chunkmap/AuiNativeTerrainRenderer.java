package com.sighs.apricityui.chunkmap;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Basic GPU chunk renderer: caller supplies projection, layers and target attachments. */
public final class AuiNativeTerrainRenderer implements AutoCloseable {
    /** chunkMask selects the caller's 2x2 local 16x16 chunks; projection and mesh ownership stay with the caller. */
    public record Draw(GpuMesh mesh, Matrix4f modelView, int chunkMask, float skyLightFactor) { }

    private static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV2", VertexFormatElement.UV2)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1)
            .build();
    private static final RenderPipeline OPAQUE = pipeline(false), TRANSPARENT = pipeline(true);

    private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("AUI map projection");
    private final DynamicUniformStorage<Parameters> parameters = new DynamicUniformStorage<>("AUI map parameters", 16, 1024);
    private final AtomicBoolean closed = new AtomicBoolean();

    public AuiNativeTerrainRenderer() { requireClientThread(); }

    private static RenderPipeline pipeline(boolean transparent) {
        var shader = Identifier.fromNamespaceAndPath("apricityui", "core/native_map");
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", transparent ? "native_map_transparent" : "native_map_opaque"))
                .withVertexShader(shader).withFragmentShader(shader)
                .withUniform("MapParams", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withVertexFormat(FORMAT, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .withColorTargetState(new ColorTargetState(transparent ? Optional.of(BlendFunction.TRANSLUCENT) : Optional.empty(),
                        15))
                .withCull(true)
                .build();
    }

    /** Copies the CPU mesh into a device buffer; the input mesh stays caller-owned. */
    public GpuMesh upload(AuiNativeMesh mesh) {
        requireClientThread();
        ByteBuffer vertices = mesh.vertices();
        long bytes = vertices.remaining();
        GpuBuffer buffer = bytes == 0 ? null : RenderSystem.getDevice().createBuffer(() -> "AUI terrain vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices);
        return new GpuMesh(buffer, mesh.opaque().count(), mesh.translucent().start(), mesh.translucent().count(),
                bytes, mesh.minY(), mesh.maxY());
    }

    public void drawOpaque(RenderTarget target, Matrix4f projection, boolean perspective, List<Draw> draws) {
        drawMeshes(target, projection, perspective, draws, false);
    }

    public void drawTransparent(RenderTarget target, Matrix4f projection, boolean perspective, List<Draw> draws) {
        drawMeshes(target, projection, perspective, draws, true);
    }

    private void drawMeshes(RenderTarget target, Matrix4f projection, boolean perspective, List<Draw> draws, boolean transparent) {
        if (closed.get()) return;
        requireClientThread();
        parameters.endFrame();
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        var previousType = RenderSystem.getProjectionType();
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection),
                perspective ? ProjectionType.PERSPECTIVE : ProjectionType.ORTHOGRAPHIC);
        var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "AUI native terrain", target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(transparent ? TRANSPARENT : OPAQUE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
            for (Draw draw : draws) {
                GpuMesh mesh = draw.mesh();
                int count = transparent ? mesh.translucentCount : mesh.opaqueCount;
                if (count == 0) continue;
                var transform = RenderSystem.getDynamicUniforms().writeTransform(draw.modelView(),
                        new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
                var params = parameters.writeUniform(new Parameters(draw.chunkMask(), draw.skyLightFactor()));
                pass.setUniform("DynamicTransforms", transform);
                pass.setUniform("MapParams", params);
                pass.setVertexBuffer(0, mesh.vertices);
                pass.draw(transparent ? mesh.translucentStart : 0, count);
            }
        } finally {
            RenderSystem.setProjectionMatrix(previousProjection, previousType);
        }
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("AUI terrain rendering must run on the client thread");
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        projectionBuffer.close();
        parameters.close();
    }

    private record Parameters(float mask, float brightness) implements DynamicUniformStorage.DynamicUniform {
        @Override public void write(ByteBuffer buffer) { buffer.putFloat(mask).putFloat(brightness).putFloat(0).putFloat(0); }
    }

    /** Caller-owned GPU mesh; release its native resources with close(). */
    public static final class GpuMesh implements AutoCloseable {
        private final GpuBuffer vertices;
        private final int opaqueCount, translucentStart, translucentCount;
        private final long bytes;
        private final float minY, maxY;
        private boolean deleted;

        private GpuMesh(GpuBuffer vertices, int opaqueCount, int translucentStart, int translucentCount,
                        long bytes, float minY, float maxY) {
            this.vertices = vertices; this.opaqueCount = opaqueCount; this.translucentStart = translucentStart;
            this.translucentCount = translucentCount; this.bytes = bytes; this.minY = minY; this.maxY = maxY;
        }

        public long bytes() { return bytes; }
        public float minY() { return minY; }
        public float maxY() { return maxY; }
        public int opaqueCount() { return opaqueCount; }
        public int translucentCount() { return translucentCount; }

        @Override public void close() {
            if (deleted) return;
            deleted = true;
            if (vertices != null) vertices.close();
        }
    }
}

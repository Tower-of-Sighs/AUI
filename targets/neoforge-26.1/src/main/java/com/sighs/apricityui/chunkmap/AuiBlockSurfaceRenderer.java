package com.sighs.apricityui.chunkmap;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.NativeImage;
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
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/** Block-resolved surface columns rendered from bounded native snapshots and the current block atlas. */
public final class AuiBlockSurfaceRenderer implements AutoCloseable {
    public record Tile(int x, int z, int width, int depth, float[] heights, int[] ranges, int[] runs,
                       float minY, float maxY) { }
    public record Surface(long revision, int x, int z, int width, int depth, List<Tile> tiles, float[] palette) { }
    public record Statistics(long revision, int uploaded, int total, long columns, int drawn, int geometryStep) { }
    private static final Direction[] FACES = {Direction.UP, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
    private static final int PALETTE_WIDTH = 30, RUN_WIDTH = 1024;
    public static final int HALO = 4;
    private static final RenderPipeline OPAQUE = pipeline(false), TRANSPARENT = pipeline(true);
    private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("AUI block surface projection");
    private final DynamicUniformStorage<Parameters> parameters = new DynamicUniformStorage<>("AUI block surface parameters", 64, 512);
    private final List<GpuTile> uploaded = new ArrayList<>();
    private Surface surface;
    private GpuTexture palette;
    private GpuTextureView paletteView;
    private ByteBuffer transfer;
    private long columns;
    private Statistics statistics = new Statistics(-1, 0, 0, 0, 0, 1);

    private static RenderPipeline pipeline(boolean transparent) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withUniform("BlockSurfaceParams", UniformType.UNIFORM_BUFFER).withSampler("ColumnHeight")
                .withSampler("ColumnRanges").withSampler("ColumnRuns").withSampler("Materials").withSampler("Sampler0")
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", transparent ? "native_block_surface_transparent" : "native_block_surface"))
                .withVertexShader(Identifier.fromNamespaceAndPath("apricityui", "core/native_block_surface"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("apricityui", "core/native_block_surface"))
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).withCull(false)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .withColorTargetState(new ColorTargetState(transparent ? Optional.of(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
                        : Optional.empty(), 15)).build();
    }

    public Statistics statistics() { return statistics; }

    /** The immutable surface may be reused across camera changes; uploads are limited to one region per frame. */
    public void update(Surface next) {
        requireRenderThread();
        if (next != surface) {
            reset();
            surface = next;
            if (next != null) {
                int rows = next.palette().length / (PALETTE_WIDTH * 4);
                palette = texture("AUI block surface materials", PALETTE_WIDTH * 4, rows);
                paletteView = RenderSystem.getDevice().createTextureView(palette);
                floats(palette, next.palette());
            }
        }
        if (next == null || uploaded.size() == next.tiles().size()) return;
        Tile tile = next.tiles().get(uploaded.size());
        int width = tile.width() + HALO * 2, depth = tile.depth() + HALO * 2;
        GpuTexture heights = texture("AUI block column heights", width * 4, depth);
        GpuTexture ranges = texture("AUI block column ranges", width * 2, depth);
        int rows = Math.max(1, Math.ceilDiv(tile.runs().length / 4, RUN_WIDTH));
        GpuTexture runs = texture("AUI block column runs", RUN_WIDTH * 4, rows);
        floats(heights, tile.heights());
        integers(ranges, tile.ranges(), tile.ranges().length);
        integers(runs, tile.runs(), RUN_WIDTH * rows * 4);
        uploaded.add(new GpuTile(tile, heights, ranges, runs));
        columns += (long) tile.width() * tile.depth();
        statistics = new Statistics(next.revision(), uploaded.size(), next.tiles().size(), columns, 0, 1);
    }

    public boolean draw(AuiMapCamera camera, RenderTarget target, float brightness) {
        requireRenderThread();
        if (surface == null || uploaded.size() != surface.tiles().size()) return false;
        double pixelsPerBlock = camera.pixelsPerBlock() * target.width / camera.width();
        int step = Math.clamp(Integer.highestOneBit(Math.max(1, (int) (1.0 / pixelsPerBlock))), 1, 4);
        var frustum = new FrustumIntersection(new Matrix4f(camera.projectionMatrix()).mul(camera.viewMatrix()));
        List<GpuTile> visible = uploaded.stream().filter(tile -> frustum.testAab(
                (float) (tile.data.x() - camera.x()), tile.data.minY() - (float) camera.y(), (float) (tile.data.z() - camera.z()),
                (float) (tile.data.x() + tile.data.width() - camera.x()), tile.data.maxY() - (float) camera.y(),
                (float) (tile.data.z() + tile.data.depth() - camera.z()))).toList();
        if (visible.isEmpty()) return false;
        parameters.endFrame();
        var transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(camera.viewMatrix()),
                new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
        var oldProjection = RenderSystem.getProjectionMatrixBuffer();
        var oldType = RenderSystem.getProjectionType();
        RenderSystem.setProjectionMatrix(projection.getBuffer(camera.nativeProjectionMatrix(RenderSystem.getDevice().isZZeroToOne())),
                camera.mode().equals("street") ? ProjectionType.PERSPECTIVE : ProjectionType.ORTHOGRAPHIC);
        var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        try {
            for (int layer : new int[]{0, 2, 1}) {
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "AUI block resolved surface",
                        target.getColorTextureView(), layer == 0 ? OptionalInt.of(0xFF75ABD1) : OptionalInt.empty(),
                        target.getDepthTextureView(), layer == 0 ? OptionalDouble.of(1) : OptionalDouble.empty())) {
                    pass.setPipeline(layer == 0 ? OPAQUE : TRANSPARENT);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", transform);
                    pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
                    var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                    pass.bindTexture("Materials", paletteView, nearest);
                    for (GpuTile tile : visible) {
                        var values = parameters.writeUniform(new Parameters(tile.data.width(), tile.data.depth(), step, layer,
                                (float) (tile.data.x() - camera.x()), (float) -camera.y(), (float) (tile.data.z() - camera.z()), brightness,
                                Math.sin(camera.yaw()) > 0 ? -1 : 1, Math.cos(camera.yaw()) > 0 ? 1 : -1));
                        pass.setUniform("BlockSurfaceParams", values);
                        pass.bindTexture("ColumnHeight", tile.heightView, nearest);
                        pass.bindTexture("ColumnRanges", tile.rangeView, nearest);
                        pass.bindTexture("ColumnRuns", tile.runView, nearest);
                        int vertices = layer == 0 ? 24 : layer == 2 ? 6 : 18;
                        pass.draw(0, Math.multiplyExact(Math.ceilDiv(tile.data.width(), step) * Math.ceilDiv(tile.data.depth(), step), vertices));
                    }
                }
            }
        } finally { RenderSystem.setProjectionMatrix(oldProjection, oldType); }
        statistics = new Statistics(surface.revision(), uploaded.size(), surface.tiles().size(), columns, visible.size(), step);
        return true;
    }

    private GpuTexture texture(String name, int width, int height) {
        return RenderSystem.getDevice().createTexture(() -> name, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                TextureFormat.RGBA8, width, height, 1, 1);
    }
    private void floats(GpuTexture texture, float[] values) {
        prepare(values.length * 4);
        for (float value : values) transfer.putFloat(value);
        upload(texture);
    }
    private void integers(GpuTexture texture, int[] values, int length) {
        prepare(length * 4);
        for (int value : values) transfer.putInt(value);
        while (transfer.position() < length * 4) transfer.putInt(0);
        upload(texture);
    }
    private void prepare(int bytes) {
        if (transfer == null || transfer.capacity() < bytes) {
            if (transfer != null) MemoryUtil.memFree(transfer);
            transfer = MemoryUtil.memAlloc(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }
        transfer.clear().limit(bytes);
    }
    private void upload(GpuTexture texture) {
        transfer.flip();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToTexture(texture, transfer, NativeImage.Format.RGBA,
                0, 0, 0, 0, texture.getWidth(0), texture.getHeight(0));
    }
    public void reset() {
        requireRenderThread();
        uploaded.forEach(GpuTile::close); uploaded.clear(); columns = 0; surface = null;
        if (paletteView != null) { paletteView.close(); paletteView = null; }
        if (palette != null) { palette.close(); palette = null; }
        statistics = new Statistics(-1, 0, 0, 0, 0, 1);
    }
    @Override public void close() {
        reset(); projection.close(); parameters.close();
        if (transfer != null) { MemoryUtil.memFree(transfer); transfer = null; }
    }
    private static void requireRenderThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Block surface GPU work requires the client thread");
    }
    private static final class GpuTile implements AutoCloseable {
        final Tile data; final GpuTexture height, range, run; final GpuTextureView heightView, rangeView, runView;
        GpuTile(Tile data, GpuTexture height, GpuTexture range, GpuTexture run) {
            this.data = data; this.height = height; this.range = range; this.run = run;
            var device = RenderSystem.getDevice(); heightView = device.createTextureView(height);
            rangeView = device.createTextureView(range); runView = device.createTextureView(run);
        }
        @Override public void close() { heightView.close(); rangeView.close(); runView.close(); height.close(); range.close(); run.close(); }
    }
    private record Parameters(float width, float depth, float step, float layer, float x, float y, float z, float brightness,
                              float directionX, float directionZ) implements DynamicUniformStorage.DynamicUniform {
        @Override public void write(ByteBuffer buffer) {
            buffer.putFloat(width).putFloat(depth).putFloat(step).putFloat(layer);
            buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(brightness);
            buffer.putFloat(directionX).putFloat(directionZ).putFloat(0).putFloat(0);
            buffer.putFloat(RUN_WIDTH).putFloat(0).putFloat(0).putFloat(0);
        }
    }

    /** Captures immutable model resources on the client thread; encoding then uses detached snapshots only. */
    public static final class Capture {
        private final BlockStateModelSet blocks;
        private final FluidStateModelSet fluids;
        private final Map<MaterialKey, Integer> materialIds = new HashMap<>();
        private final List<Material> materials = new ArrayList<>();
        private final ThreadLocal<Encoding> encoding = ThreadLocal.withInitial(Encoding::new);
        public Capture() {
            requireRenderThread();
            var models = Minecraft.getInstance().getModelManager(); blocks = models.getBlockStateModelSet(); fluids = models.getFluidStateModelSet();
            materials.add(new Material(new float[PALETTE_WIDTH * 4], 1, false));
        }
        public boolean resourcesCurrent() { return blocks == Minecraft.getInstance().getModelManager().getBlockStateModelSet(); }
        public synchronized float[] palette() {
            float[] result = new float[materials.size() * PALETTE_WIDTH * 4];
            for (int i = 0; i < materials.size(); i++) System.arraycopy(materials.get(i).data, 0, result, i * PALETTE_WIDTH * 4, PALETTE_WIDTH * 4);
            return result;
        }
        public Tile encode(ChunkMapSnapshot snapshot, int x, int z, int width, int depth, BooleanSupplier cancelled) {
            if (snapshot.step() != 1 || snapshot.verticalStep() != 1) throw new IllegalArgumentException("Block surface input must resolve individual blocks");
            int cells = (width + HALO * 2) * (depth + HALO * 2);
            float[] heights = new float[cells * 4]; int[] ranges = new int[cells * 2];
            var runs = new Ints(); float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            Encoding local = encoding.get();
            for (int dz = -HALO; dz < depth + HALO; dz++) {
                if (cancelled.getAsBoolean()) throw new CancellationException();
                for (int dx = -HALO; dx < width + HALO; dx++) {
                    int sx = x + dx - snapshot.originX(), sz = z + dz - snapshot.originZ();
                    if (sx < 0 || sz < 0 || sx >= snapshot.width() || sz >= snapshot.depth()) throw new IllegalArgumentException("Missing native column halo");
                    int index = (dz + HALO) * (width + HALO * 2) + dx + HALO;
                    var column = snapshot.column(sx, sz); var tint = snapshot.biome(sx, sz);
                    int visibleBottom = snapshot.minY();
                    if (sx > 0 && sz > 0 && sx + 1 < snapshot.width() && sz + 1 < snapshot.depth()) {
                        visibleBottom = Math.min(solidTop(snapshot, sx, sz), Math.min(Math.min(solidTop(snapshot, sx - 1, sz), solidTop(snapshot, sx + 1, sz)),
                                Math.min(solidTop(snapshot, sx, sz - 1), solidTop(snapshot, sx, sz + 1)))) - 1;
                    }
                    heights[index * 4] = snapshot.minY();
                    heights[index * 4 + 1] = heights[index * 4 + 2] = heights[index * 4 + 3] = -8192;
                    ranges[index * 2] = runs.size / 4;
                    for (var run : column) {
                        if (run.toY() <= visibleBottom) continue;
                        for (int y = Math.max(run.fromY(), visibleBottom); y < run.toY(); y++) {
                        local.random.setSeed(run.state().getSeed(local.pos.set(x + dx, y, z + dz)));
                        local.parts.clear();
                        boolean fluid = !run.state().getFluidState().isEmpty();
                        if (!fluid) blocks.get(run.state()).collectParts(local.random, local.parts);
                        int color = tint == null ? 0xffffff : fluid ? tint.water() : tint.grass();
                        MaterialKey key = new MaterialKey(run.state(), List.copyOf(local.parts), color);
                        int id; Material material;
                        synchronized (this) {
                            Integer known = materialIds.get(key);
                            if (known == null) { material = material(key); id = materials.size(); materials.add(material); materialIds.put(key, id); }
                            else { id = known; material = materials.get(id); }
                        }
                        int flags = material.transparent ? 1 : 0;
                        boolean merged = ranges[index * 2 + 1] > 0 && runs.data[runs.size - 3] == y + 32768
                                && runs.data[runs.size - 2] == id && runs.data[runs.size - 1] == flags;
                        if (merged) runs.data[runs.size - 3] = y + 1 + 32768;
                        else { runs.add(y + 32768, y + 1 + 32768, id, flags); ranges[index * 2 + 1]++; }
                        minY = Math.min(minY, y);
                        }
                    }
                    int first = ranges[index * 2], count = ranges[index * 2 + 1];
                    for (int r = 0; r < count; r++) {
                        int at = (first + r) * 4; Material material;
                        synchronized (this) { material = materials.get(runs.data[at + 2]); }
                        float top = runs.data[at + 1] - 32769 + material.height;
                        boolean exposed = r + 1 == count || runs.data[at + 1] != runs.data[at + 4]
                                || runs.data[at + 3] != runs.data[at + 7]
                                || runs.data[at + 3] == 1 && runs.data[at + 2] != runs.data[at + 6];
                        if (runs.data[at + 3] == 0) {
                            if (exposed) { heights[index * 4 + 3] = heights[index * 4]; heights[index * 4] = top; }
                        } else if (exposed) { heights[index * 4 + 2] = heights[index * 4 + 1]; heights[index * 4 + 1] = top; }
                        maxY = Math.max(maxY, top);
                    }
                }
            }
            return new Tile(x, z, width, depth, heights, ranges, runs.array(), minY, maxY);
        }
        private int solidTop(ChunkMapSnapshot snapshot, int x, int z) {
            var column = snapshot.column(x, z);
            for (int i = column.size() - 1; i >= 0; i--) {
                var run = column.get(i);
                if (run.state().getFluidState().isEmpty() && !blocks.get(run.state()).hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT)) return run.toY();
            }
            return snapshot.minY();
        }
        private Material material(MaterialKey key) {
            float[] data = new float[PALETTE_WIDTH * 4]; float height = 1; boolean transparent = false;
            if (!key.state.getFluidState().isEmpty()) {
                var model = fluids.get(key.state.getFluidState());
                for (int face = 0; face < FACES.length; face++) {
                    sprite(data, face * 24, face == 0 ? model.stillMaterial().sprite() : model.flowingMaterial().sprite(), key.color, 0.75f);
                    if (face != 0) { data[face * 24 + 2] *= 0.5f; data[face * 24 + 1] += data[face * 24 + 5] * 0.5f; data[face * 24 + 5] *= -0.5f; }
                }
                data[10] = 8f / 9f;
                return new Material(data, 8f / 9f, true);
            }
            for (int face = 0; face < FACES.length; face++) {
                List<BakedQuad> quads = new ArrayList<>();
                for (var part : key.parts) { quads.addAll(part.getQuads(FACES[face])); for (var quad : part.getQuads(null)) if (quad.direction() == FACES[face]) quads.add(quad); }
                if (quads.isEmpty() || quads.size() > 2) throw new IllegalArgumentException("Unsupported terrain column face " + key.state + " " + FACES[face] + " quads=" + quads.size());
                for (int q = 0; q < quads.size(); q++) {
                    var quad = quads.get(q); face(data, face * 24 + q * 12, quad, key.color);
                    transparent |= quad.materialInfo().layer().translucent();
                    if (face == 0) for (int v = 0; v < 4; v++) height = q == 0 && v == 0 ? quad.position(v).y() : Math.max(height, quad.position(v).y());
                }
            }
            data[10] = height;
            return new Material(data, height, transparent);
        }
        private static void face(float[] output, int at, BakedQuad quad, int tint) {
            int[] corners = new int[3]; float[] best = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            for (int i = 0; i < 4; i++) {
                var p = quad.position(i); boolean top = quad.direction() == Direction.UP;
                float a = top || quad.direction().getAxis() == Direction.Axis.Z ? p.x() : p.z(); float b = top ? p.z() : p.y();
                for (int c = 0; c < 3; c++) { float d = Math.abs(a - (c == 1 ? 1 : 0)) + Math.abs(b - (c == 2 ? 1 : 0)); if (d < best[c]) { best[c] = d; corners[c] = i; } }
            }
            float u = UVPair.unpackU(quad.packedUV(corners[0])), v = UVPair.unpackV(quad.packedUV(corners[0]));
            output[at] = u; output[at + 1] = v;
            output[at + 2] = UVPair.unpackU(quad.packedUV(corners[1])) - u; output[at + 3] = UVPair.unpackV(quad.packedUV(corners[1])) - v;
            output[at + 4] = UVPair.unpackU(quad.packedUV(corners[2])) - u; output[at + 5] = UVPair.unpackV(quad.packedUV(corners[2])) - v;
            boolean top = quad.direction() == Direction.UP;
            var a = quad.position(corners[0]); var b = quad.position(corners[1]); var c = quad.position(corners[2]);
            float width = top || quad.direction().getAxis() == Direction.Axis.Z ? b.x() - a.x() : b.z() - a.z();
            float height = top ? c.z() - a.z() : c.y() - a.y();
            output[at + 2] /= width; output[at + 3] /= width; output[at + 4] /= height; output[at + 5] /= height;
            color(output, at + 6, quad.materialInfo().isTinted() ? tint : 0xffffff, 1);
        }
        private static void sprite(float[] output, int at, TextureAtlasSprite sprite, int tint, float alpha) {
            output[at] = sprite.getU0(); output[at + 1] = sprite.getV0(); output[at + 2] = sprite.getU1() - sprite.getU0();
            output[at + 5] = sprite.getV1() - sprite.getV0(); color(output, at + 6, tint, alpha);
        }
        private static void color(float[] output, int at, int color, float alpha) {
            output[at] = (color >> 16 & 255) / 255f; output[at + 1] = (color >> 8 & 255) / 255f; output[at + 2] = (color & 255) / 255f; output[at + 3] = alpha;
        }
        private record MaterialKey(BlockState state, List<BlockStateModelPart> parts, int color) { }
        private record Material(float[] data, float height, boolean transparent) { }
        private static final class Encoding { final RandomSource random = RandomSource.create(0); final List<BlockStateModelPart> parts = new ArrayList<>(); final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(); }
        private static final class Ints {
            int[] data = new int[16384]; int size;
            void add(int a, int b, int c, int d) { if (size + 4 > data.length) data = java.util.Arrays.copyOf(data, data.length * 2); data[size++] = a; data[size++] = b; data[size++] = c; data[size++] = d; }
            int[] array() { return java.util.Arrays.copyOf(data, size); }
        }
    }
}

package com.sighs.apricityui.chunkmap;

import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.lwjgl.system.MemoryUtil;

/** Bakes detached block snapshots using the current Minecraft models and block atlas. */
public final class AuiChunkMeshBaker {
    public static final int STRIDE = 32;
    private static final int TILE_SIZE = 32;
    private static final int LIGHT_HALO = 16;
    private final Map<BlockState, BakedModel> blockModels;
    private final BlockColors blockColors;
    private final ActiveLiquidRenderer fluids;

    /** Capture a resource generation on the client thread; recreate after model reload. */
    public AuiChunkMeshBaker() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("Capture map models on the client thread");
        var shaper = minecraft.getBlockRenderer().getBlockModelShaper();
        Map<BlockState, BakedModel> models = new HashMap<>(Block.BLOCK_STATE_REGISTRY.size());
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) models.put(state, shaper.getBlockModel(state));
        blockModels = Map.copyOf(models);
        blockColors = minecraft.getBlockColors();
        fluids = new ActiveLiquidRenderer();
        fluids.initialize();
    }

    /** Positions are relative to the supplied tile origin in X/Z, and absolute in Y. */
    public Mesh bake(ChunkMapSnapshot snapshot, boolean[] knownColumns, int originX, int originZ,
                     BooleanSupplier cancelled) {
        if (snapshot.step() != 1 || snapshot.verticalStep() != 1
                || knownColumns.length != snapshot.width() * snapshot.depth()) {
            throw new IllegalArgumentException("A native tile requires a unit-scale snapshot and matching known columns");
        }
        SnapshotView level = new SnapshotView(snapshot, knownColumns, originX, originZ, cancelled);
        ModelBlockRenderer blocks = new ModelBlockRenderer(blockColors);
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        RandomSource random = RandomSource.create();
        try (VertexStream opaque = new VertexStream(); VertexStream translucent = new VertexStream()) {
            QuadConsumer vertices = new QuadConsumer(opaque);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int minX = Math.max(originX, snapshot.originX()), minZ = Math.max(originZ, snapshot.originZ());
            int maxX = Math.min(originX + TILE_SIZE, snapshot.originX() + snapshot.width());
            int maxZ = Math.min(originZ + TILE_SIZE, snapshot.originZ() + snapshot.depth());
            for (int z = minZ; z < maxZ; z++) {
                checkCancelled(cancelled);
                for (int x = minX; x < maxX; x++) {
                    int sx = x - snapshot.originX(), sz = z - snapshot.originZ();
                    if (!level.known[(z - level.minZ) * level.width + x - level.minX]) continue;
                    for (ChunkMapSnapshot.Run run : snapshot.column(sx, sz)) {
                        BlockState state = run.state();
                        if (state.isAir()) continue;
                        for (int y = run.fromY(); y < run.toY(); y++) {
                            if ((y & 31) == 0) checkCancelled(cancelled);
                            pos.set(x, y, z);
                            if (state.getRenderShape() == RenderShape.MODEL && !level.enclosed(pos, state)) {
                                BakedModel model = blockModels.get(state);
                                if (model != null) {
                                    vertices.offset(0, 0, 0);
                                    pose.pushPose();
                                    pose.translate(x - originX, y, z - originZ);
                                    random.setSeed(state.getSeed(pos));
                                    RenderType renderType = ItemBlockRenderTypes.getChunkRenderType(state);
                                    vertices.layer(renderType, opaque, translucent);
                                    long seed = state.getSeed(pos);
                                    random.setSeed(seed);
                                    blocks.tesselateBlock(level, model, state, pos, pose, vertices, true,
                                            random, seed, OverlayTexture.NO_OVERLAY);
                                    vertices.finish();
                                    pose.popPose();
                                }
                            }
                            FluidState fluid = state.getFluidState();
                            if (!fluid.isEmpty()) {
                                vertices.offset((x & ~15) - originX, y & ~15, (z & ~15) - originZ);
                                vertices.layer(ItemBlockRenderTypes.getRenderLayer(fluid), opaque, translucent);
                                fluids.tesselate(level, pos, vertices, state, fluid);
                                vertices.finish();
                            }
                        }
                    }
                }
            }
            checkCancelled(cancelled);
            int opaqueCount = opaque.size() / STRIDE, translucentCount = translucent.size() / STRIDE;
            int bytes = Math.addExact(opaque.size(), translucent.size());
            ByteBuffer joined = MemoryUtil.memAlloc(Math.max(1, bytes)).order(ByteOrder.nativeOrder());
            joined.put(opaque.contents()).put(translucent.contents()).flip();
            float minY = bytes == 0 ? 0 : Math.min(opaque.minY, translucent.minY);
            float maxY = bytes == 0 ? 0 : Math.max(opaque.maxY, translucent.maxY);
            return new Mesh(joined, new Range(0, opaqueCount), new Range(opaqueCount, translucentCount), minY, maxY);
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) throw new CancellationException();
    }

    public record Range(int start, int count) { }

    public static final class Mesh implements AutoCloseable {
        private final ByteBuffer vertices;
        private final Range opaque;
        private final Range translucent;
        private final float minY;
        private final float maxY;
        private boolean closed;

        private Mesh(ByteBuffer vertices, Range opaque, Range translucent, float minY, float maxY) {
            this.vertices = vertices;
            this.opaque = opaque;
            this.translucent = translucent;
            this.minY = minY;
            this.maxY = maxY;
        }

        public ByteBuffer vertices() {
            if (closed) throw new IllegalStateException("Map mesh is closed");
            return vertices.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
        }

        public int vertexCount() { return opaque.count() + translucent.count(); }
        public Range opaque() { return opaque; }
        public Range translucent() { return translucent; }
        public float minY() { return minY; }
        public float maxY() { return maxY; }

        @Override public void close() {
            if (!closed) { closed = true; MemoryUtil.memFree(vertices); }
        }
    }

    private static final class SnapshotView implements BlockAndTintGetter {
        private final ChunkMapSnapshot snapshot;
        private final boolean[] known;
        private final BlockState[] states;
        private final byte[] attenuation;
        private final boolean[] solid;
        private final byte[] sky;
        private final byte[] emitted;
        private final int minX, minZ, width, depth, plane;
        private final LevelLightEngine lightEngine;

        SnapshotView(ChunkMapSnapshot snapshot, boolean[] knownColumns, int originX, int originZ,
                     BooleanSupplier cancelled) {
            this.snapshot = snapshot;
            minX = Math.max(snapshot.originX(), originX - LIGHT_HALO);
            minZ = Math.max(snapshot.originZ(), originZ - LIGHT_HALO);
            width = Math.max(0, Math.min(snapshot.originX() + snapshot.width(), originX + TILE_SIZE + LIGHT_HALO) - minX);
            depth = Math.max(0, Math.min(snapshot.originZ() + snapshot.depth(), originZ + TILE_SIZE + LIGHT_HALO) - minZ);
            plane = width * depth;
            known = new boolean[plane];
            states = new BlockState[Math.multiplyExact(plane, snapshot.maxY() - snapshot.minY())];
            attenuation = new byte[states.length];
            solid = new boolean[states.length];
            Arrays.fill(states, Blocks.AIR.defaultBlockState());
            sky = new byte[states.length];
            emitted = new byte[states.length];
            for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
                checkCancelled(cancelled);
                int column = z * width + x;
                int sx = minX + x - snapshot.originX(), sz = minZ + z - snapshot.originZ();
                known[column] = knownColumns[sz * snapshot.width() + sx];
                if (!known[column]) continue;
                for (ChunkMapSnapshot.Run run : snapshot.column(sx, sz)) {
                    for (int y = run.fromY(); y < run.toY(); y++) states[(y - snapshot.minY()) * plane + column] = run.state();
                }
            }
            IntArrayFIFOQueue skyQueue = new IntArrayFIFOQueue();
            IntArrayFIFOQueue blockQueue = new IntArrayFIFOQueue();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            lightEngine = new LevelLightEngine(new LightChunkGetter() {
                @Override public LightChunk getChunkForLighting(int x, int z) { return null; }
                @Override public BlockGetter getLevel() { return SnapshotView.this; }
            }, false, false);
            for (int column = 0; column < plane; column++) {
                checkCancelled(cancelled);
                if (!known[column]) continue;
                int sunlight = 15;
                for (int y = snapshot.maxY() - 1; y >= snapshot.minY(); y--) {
                    int index = (y - snapshot.minY()) * plane + column;
                    BlockState state = states[index];
                    pos.set(minX + column % width, y, minZ + column / width);
                    attenuation[index] = (byte) state.getLightBlock(this, pos);
                    solid[index] = state.isSolidRender(this, pos);
                    sunlight = Math.max(0, sunlight - attenuation[index]);
                    sky[index] = (byte) sunlight;
                    if (sunlight > 1) skyQueue.enqueue(index);
                    int emission = state.getLightEmission();
                    emitted[index] = (byte) emission;
                    if (emission > 1) blockQueue.enqueue(index);
                }
            }
            spread(sky, skyQueue, cancelled);
            spread(emitted, blockQueue, cancelled);
        }

        private void spread(byte[] light, IntArrayFIFOQueue queue, BooleanSupplier cancelled) {
            int processed = 0;
            while (!queue.isEmpty()) {
                if ((processed++ & 4095) == 0) checkCancelled(cancelled);
                int index = queue.dequeueInt(), column = index % plane;
                int x = column % width, z = column / width;
                if (x > 0) propagate(light, queue, index, index - 1);
                if (x + 1 < width) propagate(light, queue, index, index + 1);
                if (z > 0) propagate(light, queue, index, index - width);
                if (z + 1 < depth) propagate(light, queue, index, index + width);
                if (index >= plane) propagate(light, queue, index, index - plane);
                if (index + plane < states.length) propagate(light, queue, index, index + plane);
            }
        }

        private void propagate(byte[] light, IntArrayFIFOQueue queue, int source, int destination) {
            if (!known[destination % plane]) return;
            int value = light[source] - Math.max(1, attenuation[destination]);
            if (value > light[destination]) {
                light[destination] = (byte) value;
                if (value > 1) queue.enqueue(destination);
            }
        }

        private int index(BlockPos pos) {
            int x = pos.getX() - minX, z = pos.getZ() - minZ, y = pos.getY() - snapshot.minY();
            if (x < 0 || x >= width || z < 0 || z >= depth || y < 0 || y >= getHeight()) return -1;
            return y * plane + z * width + x;
        }

        boolean enclosed(BlockPos pos, BlockState state) {
            int i = index(pos);
            if (i < 0 || !solid[i]) return false;
            int column = i % plane, x = column % width, z = column / width;
            return x > 0 && x + 1 < width && z > 0 && z + 1 < depth
                    && i >= plane && i + plane < states.length
                    && solid[i - 1] && solid[i + 1] && solid[i - width] && solid[i + width]
                    && solid[i - plane] && solid[i + plane];
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            int i = index(pos);
            return i < 0 ? Blocks.AIR.defaultBlockState() : states[i];
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return snapshot.maxY() - snapshot.minY(); }
        @Override public int getMinBuildHeight() { return snapshot.minY(); }
        @Override public float getShade(Direction direction, boolean shade) {
            if (!shade) return 1.0F;
            return switch (direction) {
                case DOWN -> 0.5F;
                case UP -> 1.0F;
                case NORTH, SOUTH -> 0.8F;
                case WEST, EAST -> 0.6F;
            };
        }
        @Override public LevelLightEngine getLightEngine() { return lightEngine; }
        @Override public int getBrightness(LightLayer layer, BlockPos pos) {
            int i = index(pos);
            if (i < 0) return layer == LightLayer.SKY && pos.getY() >= snapshot.minY() ? 15 : 0;
            return layer == LightLayer.SKY ? sky[i] : emitted[i];
        }
        @Override public int getRawBrightness(BlockPos pos, int darkening) {
            return Math.max(getBrightness(LightLayer.BLOCK, pos), getBrightness(LightLayer.SKY, pos) - darkening);
        }
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            int x = pos.getX() - snapshot.originX(), z = pos.getZ() - snapshot.originZ();
            if (x < 0 || x >= snapshot.width() || z < 0 || z >= snapshot.depth()) return -1;
            var tint = snapshot.biome(x, z);
            if (tint == null) return -1;
            if (resolver == BiomeColors.GRASS_COLOR_RESOLVER) return tint.grass();
            if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) return tint.foliage();
            if (resolver == BiomeColors.WATER_COLOR_RESOLVER) return tint.water();
            return -1;
        }
    }

    private static final class VertexStream implements AutoCloseable {
        private ByteBuffer data = MemoryUtil.memAlloc(65536).order(ByteOrder.nativeOrder());
        private float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        int size() { return data.position(); }
        ByteBuffer contents() { return data.duplicate().flip(); }
        void quad(ByteBuffer quad) {
            if (data.remaining() < 6 * STRIDE) data = MemoryUtil.memRealloc(data, Math.multiplyExact(data.capacity(), 2)).order(ByteOrder.nativeOrder());
            for (int vertex : TRIANGLE_VERTICES) {
                int offset = vertex * STRIDE;
                float y = quad.getFloat(offset + 4);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                for (int word = 0; word < STRIDE; word += Long.BYTES) data.putLong(quad.getLong(offset + word));
            }
        }
        @Override public void close() { MemoryUtil.memFree(data); }
    }

    private static final int[] TRIANGLE_VERTICES = {0, 1, 2, 2, 3, 0};

    private static final class QuadConsumer implements VertexConsumer {
        private final ByteBuffer quad = ByteBuffer.allocate(4 * STRIDE).order(ByteOrder.nativeOrder());
        private VertexStream output;
        private int count, threshold;
        private float offsetX, offsetY, offsetZ;
        private float x, y, z, u, v, nx, ny, nz;
        private int color = -1, light;
        private boolean pending;

        QuadConsumer(VertexStream output) { this.output = output; }
        void offset(float x, float y, float z) { offsetX = x; offsetY = y; offsetZ = z; }
        void layer(RenderType layer, VertexStream opaque, VertexStream translucent) {
            boolean transparent = layer == RenderType.translucent()
                    || layer == RenderType.translucentMovingBlock() || layer.sortOnUpload;
            output = transparent ? translucent : opaque;
            threshold = layer == RenderType.cutoutMipped() ? 128
                    : layer == RenderType.cutout() || transparent ? 26 : 0;
        }
        void finish() {
            flushPending();
            if (count != 0) throw new IllegalStateException("Minecraft terrain output ended inside a quad");
        }
        private void flushPending() {
            if (pending) { pending = false; emit(x, y, z, color, u, v, light, nx, ny, nz); }
        }
        private void emit(float x, float y, float z, int color, float u, float v, int light, float nx, float ny, float nz) {
            quad.putFloat(x + offsetX).putFloat(y + offsetY).putFloat(z + offsetZ);
            quad.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) 255);
            quad.putFloat(u).putFloat(v);
            quad.putShort((short) (LightTexture.block(light) | ((color >>> 24) << 4)));
            quad.putShort((short) (LightTexture.sky(light) | (threshold << 4)));
            quad.put(normal(nx)).put(normal(ny)).put(normal(nz)).put((byte) 0);
            if (++count == 4) { output.quad(quad); quad.clear(); count = 0; }
        }
        private static byte normal(float value) { return (byte) Math.round(net.minecraft.util.Mth.clamp(value, -1F, 1F) * 127); }
        @Override public VertexConsumer vertex(double x, double y, double z) {
            flushPending(); this.x = (float) x; this.y = (float) y; this.z = (float) z;
            pending = true; return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            color = a << 24 | r << 16 | g << 8 | b; return this;
        }
        @Override public VertexConsumer uv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { light = u | v << 16; return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { nx = x; ny = y; nz = z; return this; }
        @Override public void endVertex() { flushPending(); }
        @Override public void defaultColor(int r, int g, int b, int a) { color(r, g, b, a); }
        @Override public void unsetDefaultColor() { color = -1; }
    }

    private static final class ActiveLiquidRenderer extends LiquidBlockRenderer {
        void initialize() { setupSprites(); }
    }
}

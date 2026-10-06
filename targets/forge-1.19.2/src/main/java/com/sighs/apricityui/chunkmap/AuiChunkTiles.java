package com.sighs.apricityui.chunkmap;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MaterialColor;

/** Builds map tiles from captured Minecraft models and immutable block columns. */
public final class AuiChunkTiles implements AutoCloseable {
    public record Tile(int x, int z) { }
    public record NativeTile(Tile key, byte[] model, int[] heights) { }
    public record Rendered(long centerX, int centerY, long centerZ, int verticalRelief,
                           byte[] texturesJson, Map<Tile, byte[]> tiles, int scale, long span, int verticalScale,
                           long minX, long minZ, int width, int depth, byte[] surfaceJson,
                           int heightOrigin, byte[] surfaceTexture) { }

    private final AuiChunkMeshBaker baker;
    private final ExecutorService workers;
    private final AtomicBoolean closed = new AtomicBoolean();

    public AuiChunkTiles() {
        this(net.minecraft.util.Mth.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 4));
    }

    public AuiChunkTiles(int parallelism) {
        if (parallelism < 1) throw new IllegalArgumentException("Tile worker count must be positive");
        baker = new AuiChunkMeshBaker();
        workers = Executors.newFixedThreadPool(parallelism, task -> {
            Thread thread = new Thread(task, "AUI map tile baking");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
    }

    public Rendered render(ChunkMapSnapshot snapshot, BooleanSupplier cancelled) {
        return render(snapshot, cancelled, snapshot.step() > 1 || snapshot.verticalStep() > 1);
    }

    public Rendered renderSurface(ChunkMapSnapshot snapshot, BooleanSupplier cancelled) {
        return render(snapshot, cancelled, true);
    }

    public CompletableFuture<NativeTile> renderTile(ChunkMapSnapshot snapshot, Tile tile, BooleanSupplier cancelled) {
        boolean[] known = new boolean[snapshot.width() * snapshot.depth()];
        Arrays.fill(known, true);
        return renderNativeTile(snapshot, tile, known, cancelled);
    }

    public CompletableFuture<NativeTile> renderNativeTile(ChunkMapSnapshot snapshot, Tile tile,
                                                         boolean[] knownColumns, BooleanSupplier cancelled) {
        if (knownColumns.length != snapshot.width() * snapshot.depth()) {
            throw new IllegalArgumentException("Known columns must match the snapshot dimensions");
        }
        if (closed.get()) return CompletableFuture.failedFuture(new CancellationException());
        boolean[] known = knownColumns.clone();
        BooleanSupplier stopped = () -> closed.get() || cancelled.getAsBoolean();
        return CompletableFuture.supplyAsync(() -> {
            checkCancelled(stopped);
            int originX = Math.multiplyExact(tile.x(), 32), originZ = Math.multiplyExact(tile.z(), 32);
            try (var mesh = baker.bake(snapshot, known, originX, originZ, stopped)) {
                checkCancelled(stopped);
                int[] heights = new int[1024];
                Arrays.fill(heights, 32767);
                for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) {
                    int sx = originX + x - snapshot.originX(), sz = originZ + z - snapshot.originZ();
                    if (sx < 0 || sz < 0 || sx >= snapshot.width() || sz >= snapshot.depth()
                            || !known[sz * snapshot.width() + sx]) continue;
                    var column = snapshot.column(sx, sz);
                    heights[z * 32 + x] = column.isEmpty() ? snapshot.minY() : column.get(column.size() - 1).toY() - 1;
                }
                return new NativeTile(tile, AuiNativeMesh.encode(mesh), heights);
            }
        }, workers);
    }

    private Rendered render(ChunkMapSnapshot snapshot, BooleanSupplier cancelled, boolean surfaceOnly) {
        BooleanSupplier stopped = () -> closed.get() || cancelled.getAsBoolean();
        checkCancelled(stopped);
        int[] heights = new int[snapshot.width() * snapshot.depth()];
        byte[] surfaceTexture = new byte[heights.length * 8];
        SurfaceView view = new SurfaceView(snapshot);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < snapshot.depth(); z++) {
            checkCancelled(stopped);
            for (int x = 0; x < snapshot.width(); x++) {
                int index = z * snapshot.width() + x;
                var column = snapshot.column(x, z);
                int height = column.isEmpty() ? snapshot.minY() : column.get(column.size() - 1).toY() - 1;
                heights[index] = height;
                if (column.isEmpty()) continue;
                BlockState state = column.get(column.size() - 1).state();
                pos.set((snapshot.originX() + x) * snapshot.step(), height * snapshot.verticalStep(),
                        (snapshot.originZ() + z) * snapshot.step());
                MaterialColor mapColor = state.getMapColor(view, pos);
                int color = mapColor.col;
                var biome = snapshot.biome(x, z);
                if (biome != null) {
                    if (mapColor == MaterialColor.GRASS) color = biome.grass();
                    else if (mapColor == MaterialColor.PLANT) color = biome.foliage();
                    else if (mapColor == MaterialColor.WATER) color = biome.water();
                }
                int pixel = index * 4;
                surfaceTexture[pixel] = (byte) (color >> 16);
                surfaceTexture[pixel + 1] = (byte) (color >> 8);
                surfaceTexture[pixel + 2] = (byte) color;
                surfaceTexture[pixel + 3] = (byte) 255;
                int meta = heights.length * 4 + pixel, relativeHeight = height - snapshot.minY();
                surfaceTexture[meta] = 15;
                surfaceTexture[meta + 1] = (byte) (relativeHeight >> 8);
                surfaceTexture[meta + 2] = (byte) relativeHeight;
                surfaceTexture[meta + 3] = (byte) 255;
            }
        }
        Map<Tile, byte[]> models = new LinkedHashMap<>();
        if (!surfaceOnly) {
            Map<Tile, CompletableFuture<NativeTile>> pending = new LinkedHashMap<>();
            int minX = Math.floorDiv(snapshot.originX(), 32), minZ = Math.floorDiv(snapshot.originZ(), 32);
            int maxX = Math.floorDiv(snapshot.originX() + snapshot.width() - 1, 32);
            int maxZ = Math.floorDiv(snapshot.originZ() + snapshot.depth() - 1, 32);
            for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                Tile tile = new Tile(x, z);
                pending.put(tile, renderTile(snapshot, tile, stopped));
            }
            pending.forEach((tile, work) -> models.put(tile, work.join().model()));
        }
        checkCancelled(stopped);
        int low = Arrays.stream(heights).min().orElseThrow(), high = Arrays.stream(heights).max().orElseThrow();
        return new Rendered((snapshot.originX() + snapshot.width() / 2) * (long) snapshot.step(),
                (low + high) / 2 * snapshot.verticalStep(),
                (snapshot.originZ() + snapshot.depth() / 2) * (long) snapshot.step(),
                (high - low) * snapshot.verticalStep(), new byte[0], Map.copyOf(models), snapshot.step(),
                Math.max(snapshot.width(), snapshot.depth()) * (long) snapshot.step(), snapshot.verticalStep(),
                snapshot.originX() * (long) snapshot.step(), snapshot.originZ() * (long) snapshot.step(),
                snapshot.width(), snapshot.depth(), new Gson().toJson(heights).getBytes(StandardCharsets.UTF_8),
                snapshot.minY(), surfaceTexture);
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) throw new CancellationException();
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) workers.shutdown();
    }

    private record SurfaceView(ChunkMapSnapshot snapshot) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos pos) {
            return snapshot.block(Math.floorDiv(pos.getX(), snapshot.step()) - snapshot.originX(),
                    Math.floorDiv(pos.getY(), snapshot.verticalStep()),
                    Math.floorDiv(pos.getZ(), snapshot.step()) - snapshot.originZ());
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return (snapshot.maxY() - snapshot.minY()) * snapshot.verticalStep(); }
        @Override public int getMinBuildHeight() { return snapshot.minY() * snapshot.verticalStep(); }
    }
}

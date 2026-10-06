package com.sighs.apricityui.chunkmap;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.material.MapColor;

/** Detached procedural terrain feeding the native viewport with Minecraft's baked models. */
public final class AuiProceduralPreview implements AutoCloseable {
    public record Frame(long sceneKey, AuiNativeTerrainRenderer.Surface surface,
                        AuiChunkTileSource tiles, Map<AuiChunkTiles.Tile, Integer> masks,
                        ChunkMapSnapshot precise, String digest) { }
    private record CachedTile(String key, AuiChunkTiles.NativeTile tile) { }
    private record Source(int minY, int maxY, Map<AuiChunkTiles.Tile, CachedTile> data)
            implements AuiChunkTileSource {
        @Override public String contentKey(AuiChunkTiles.Tile tile) {
            var value = data.get(tile); return value == null ? null : value.key();
        }
        @Override public AuiChunkTiles.NativeTile tile(AuiChunkTiles.Tile tile) {
            var value = data.get(tile); return value == null ? null : value.tile();
        }
        @Override public boolean persistentCache() { return true; }
    }
    private enum Update { FRAME, SURFACE, DETAIL }
    private static final int TILE_SIZE = 32;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService coordinator = executor(1, 4, "AUI procedural preview");
    private final ExecutorService liveSurface = executor(1, 2, "AUI procedural surface");
    private final ExecutorService workers = executor(Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 4),
            8, "AUI procedural tile");
    private final BlockStateModelSet modelGeneration;
    private final AuiChunkMeshBaker baker;
    private final CompletableFuture<String> resourceKey;
    private final CompletableFuture<AuiNativeTileCache> cache;
    private final AtomicLong hits = new AtomicLong(), misses = new AtomicLong(), baked = new AtomicLong();
    private volatile Frame current;

    /** Construct on the client thread and replace after the model resource generation changes. */
    public AuiProceduralPreview(Path cacheDirectory) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("Capture preview resources on the client thread");
        modelGeneration = minecraft.getModelManager().getBlockStateModelSet();
        baker = new AuiChunkMeshBaker();
        Map<String, Resource> resources = new TreeMap<>();
        for (String directory : List.of("blockstates", "models", "textures", "atlases")) {
            minecraft.getResourceManager().listResources(directory, id -> true)
                    .forEach((id, resource) -> {
                        resources.put(id.toString(), resource);
                        minecraft.getResourceManager().getResource(id.withPath(id.getPath() + ".mcmeta"))
                                .ifPresent(metadata -> resources.put(id + ".mcmeta", metadata));
                    });
        }
        resourceKey = CompletableFuture.supplyAsync(() -> resourceFingerprint(resources), coordinator);
        cache = resourceKey.thenApply(key -> new AuiNativeTileCache(cacheDirectory.resolve("procedural-native-v1").resolve(key)));
    }

    public boolean resourcesCurrent() {
        return !closed.get() && modelGeneration == Minecraft.getInstance().getModelManager().getBlockStateModelSet();
    }
    public long cacheHits() { return hits.get(); }
    public long cacheMisses() { return misses.get(); }
    public long bakedTiles() { return baked.get(); }
    public Frame frame() { return current; }

    public CompletableFuture<Frame> renderWithDetail(ChunkMapSnapshot overview, ChunkMapSnapshot precise,
                                                     long sceneKey, BooleanSupplier cancelled) {
        return submit(sceneKey, cancelled, coordinator, Update.FRAME,
                () -> create(overview, precise, sceneKey, cancelled));
    }

    public CompletableFuture<Frame> render(ChunkMapSnapshot precise, long sceneKey, BooleanSupplier cancelled) {
        return renderWithDetail(precise, precise, sceneKey, cancelled);
    }

    public CompletableFuture<Frame> renderSurface(ChunkMapSnapshot overview, long sceneKey, BooleanSupplier cancelled) {
        return submit(sceneKey, cancelled, liveSurface, Update.SURFACE, () ->
                new Frame(sceneKey, surface(overview, sceneKey, cancelled), null, Map.of(), null,
                        fingerprint(overview, cancelled)));
    }

    public CompletableFuture<Frame> renderDetail(ChunkMapSnapshot precise, long sceneKey, BooleanSupplier cancelled) {
        return submit(sceneKey, cancelled, coordinator, Update.DETAIL,
                () -> create(null, precise, sceneKey, cancelled));
    }
    public CompletableFuture<Void> prewarm(ChunkMapSnapshot precise, BooleanSupplier cancelled) {
        return cache.thenApplyAsync(storage -> { create(null, precise, -1, cancelled); return null; }, coordinator);
    }

    private CompletableFuture<Frame> submit(long key, BooleanSupplier cancelled, ExecutorService executor,
                                             Update update, java.util.function.Supplier<Frame> work) {
        if (closed.get()) return CompletableFuture.failedFuture(new CancellationException());
        return cache.thenApplyAsync(storage -> {
            check(cancelled);
            Frame visible = current;
            if (visible != null && key < visible.sceneKey()) throw new CancellationException();
            return commit(work.get(), update, cancelled);
        }, executor);
    }

    private synchronized Frame commit(Frame candidate, Update update, BooleanSupplier cancelled) {
        check(cancelled);
        Frame latest = current;
        if (latest != null && candidate.sceneKey() < latest.sceneKey()) throw new CancellationException();
        boolean sameScene = latest != null && candidate.sceneKey() == latest.sceneKey();
        Frame result = switch (update) {
            case FRAME -> candidate;
            case SURFACE -> sameScene ? new Frame(candidate.sceneKey(), candidate.surface(), latest.tiles(),
                    latest.masks(), latest.precise(), latest.precise() == null ? candidate.digest() : latest.digest()) : candidate;
            case DETAIL -> {
                if (!sameScene) throw new CancellationException();
                yield new Frame(candidate.sceneKey(), latest.surface(), candidate.tiles(), candidate.masks(),
                        candidate.precise(), candidate.digest());
            }
        };
        current = result;
        return result;
    }
    private Frame create(ChunkMapSnapshot overview, ChunkMapSnapshot precise, long sceneKey, BooleanSupplier cancelled) {
        check(cancelled);
        if (precise.step() != 1 || precise.verticalStep() != 1) throw new IllegalArgumentException("Precise snapshot must use world blocks");
        String content = fingerprint(precise, cancelled);
        boolean[] known = new boolean[precise.width() * precise.depth()];
        java.util.Arrays.fill(known, true);
        Map<AuiChunkTiles.Tile, CachedTile> ready = new HashMap<>();
        Map<AuiChunkTiles.Tile, Integer> masks = new HashMap<>();
        List<CompletableFuture<CachedTile>> batch = new ArrayList<>();
        int firstX = Math.floorDiv(precise.originX(), TILE_SIZE), firstZ = Math.floorDiv(precise.originZ(), TILE_SIZE);
        int lastX = Math.floorDiv(precise.originX() + precise.width() - 1, TILE_SIZE);
        int lastZ = Math.floorDiv(precise.originZ() + precise.depth() - 1, TILE_SIZE);
        for (int z = firstZ; z <= lastZ; z++) for (int x = firstX; x <= lastX; x++) {
            check(cancelled);
            var tile = new AuiChunkTiles.Tile(x, z);
            batch.add(CompletableFuture.supplyAsync(() -> tile(precise, known, content, tile, cancelled), workers));
            if (batch.size() == 8) drain(batch, ready, masks);
        }
        drain(batch, ready, masks);
        check(cancelled);
        return new Frame(sceneKey, overview == null ? null : surface(overview, sceneKey, cancelled),
                new Source(precise.minY(), precise.maxY(), Map.copyOf(ready)), Map.copyOf(masks), precise,
                HexFormat.of().formatHex(digest().digest((resourceKey.join() + content).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    private static void drain(List<CompletableFuture<CachedTile>> batch, Map<AuiChunkTiles.Tile, CachedTile> ready,
                              Map<AuiChunkTiles.Tile, Integer> masks) {
        CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new)).join();
        for (var future : batch) {
            CachedTile result = future.join();
            ready.put(result.tile().key(), result);
            int mask = 0;
            for (int quadrant = 0; quadrant < 4; quadrant++) {
                boolean known = true;
                int x0 = (quadrant & 1) * 16, z0 = (quadrant >> 1) * 16;
                for (int z = z0; z < z0 + 16 && known; z++) for (int x = x0; x < x0 + 16; x++) {
                    if (result.tile().heights()[z * 32 + x] == 32767) { known = false; break; }
                }
                if (known) mask |= 1 << quadrant;
            }
            masks.put(result.tile().key(), mask);
        }
        batch.clear();
    }

    private CachedTile tile(ChunkMapSnapshot snapshot, boolean[] known, String content,
                            AuiChunkTiles.Tile tile, BooleanSupplier cancelled) {
        check(cancelled);
        String key = HexFormat.of().formatHex(digest().digest((resourceKey.join() + content + ":" + tile.x() + ":" + tile.z())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var storage = cache.join();
        var existing = storage.read(key, tile);
        if (existing.isPresent()) { hits.incrementAndGet(); return new CachedTile(key, existing.get()); }
        misses.incrementAndGet();
        int x0 = tile.x() * TILE_SIZE, z0 = tile.z() * TILE_SIZE;
        int[] heights = new int[TILE_SIZE * TILE_SIZE];
        for (int z = 0; z < TILE_SIZE; z++) for (int x = 0; x < TILE_SIZE; x++) {
            int sx = x0 + x - snapshot.originX(), sz = z0 + z - snapshot.originZ();
            if (sx < 0 || sz < 0 || sx >= snapshot.width() || sz >= snapshot.depth()) heights[z * 32 + x] = 32767;
            else {
                var column = snapshot.column(sx, sz);
                heights[z * 32 + x] = column.isEmpty() ? snapshot.minY() : column.getLast().toY() - 1;
            }
        }
        try (var mesh = baker.bake(snapshot, known, x0, z0, () -> closed.get() || cancelled.getAsBoolean())) {
            check(cancelled);
            var result = new AuiChunkTiles.NativeTile(tile, AuiNativeMesh.encode(mesh), heights);
            storage.write(key, result);
            baked.incrementAndGet();
            return new CachedTile(key, result);
        }
    }

    private AuiNativeTerrainRenderer.Surface surface(ChunkMapSnapshot snapshot, long revision, BooleanSupplier cancelled) {
        int count = snapshot.width() * snapshot.depth();
        int[] heights = new int[count];
        byte[] rgba = new byte[count * 4];
        for (int z = 0; z < snapshot.depth(); z++) {
            check(cancelled);
            for (int x = 0; x < snapshot.width(); x++) {
                int index = z * snapshot.width() + x;
                var column = snapshot.column(x, z);
                heights[index] = (column.isEmpty() ? snapshot.minY() : column.getLast().toY()) * snapshot.verticalStep() - 1;
                if (column.isEmpty()) continue;
                var state = column.getLast().state();
                MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                int color = mapColor.col;
                var tint = snapshot.biome(x, z);
                if (tint != null && mapColor == MapColor.GRASS) color = tint.grass();
                else if (tint != null && !state.getFluidState().isEmpty()) color = tint.water();
                rgba[index * 4] = (byte) (color >> 16); rgba[index * 4 + 1] = (byte) (color >> 8);
                rgba[index * 4 + 2] = (byte) color; rgba[index * 4 + 3] = (byte) 255;
            }
        }
        return new AuiNativeTerrainRenderer.Surface(snapshot.originX() * (long) snapshot.step(),
                snapshot.originZ() * (long) snapshot.step(), snapshot.width(), snapshot.depth(), snapshot.step(), rgba, heights, revision);
    }

    private String resourceFingerprint(Map<String, Resource> resources) {
        MessageDigest hash = digest();
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), hash))) {
            out.writeUTF("aui-procedural-native-26.2-v1");
            for (var entry : resources.entrySet()) {
                check(() -> false); out.writeUTF(entry.getKey());
                byte[] bytes;
                try (var input = entry.getValue().open()) { bytes = input.readAllBytes(); }
                out.writeInt(bytes.length); out.write(bytes);
            }
        } catch (IOException failure) { throw new CompletionException(failure); }
        return HexFormat.of().formatHex(hash.digest());
    }

    private String fingerprint(ChunkMapSnapshot snapshot, BooleanSupplier cancelled) {
        MessageDigest hash = digest();
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), hash))) {
            out.writeInt(snapshot.originX()); out.writeInt(snapshot.originZ());
            out.writeInt(snapshot.width()); out.writeInt(snapshot.depth()); out.writeInt(snapshot.step());
            out.writeInt(snapshot.verticalStep()); out.writeInt(snapshot.minY()); out.writeInt(snapshot.maxY());
            for (int z = 0; z < snapshot.depth(); z++) {
                check(cancelled);
                for (int x = 0; x < snapshot.width(); x++) {
                    var column = snapshot.column(x, z); out.writeInt(column.size());
                    for (var run : column) { out.writeInt(run.fromY()); out.writeInt(run.toY()); out.writeUTF(run.state().toString()); }
                    var tint = snapshot.biome(x, z); out.writeBoolean(tint != null);
                    if (tint != null) {
                        out.writeUTF(tint.namespace()); out.writeUTF(tint.path()); out.writeFloat(tint.temperature());
                        out.writeInt(tint.water()); out.writeInt(tint.grass()); out.writeInt(tint.foliage()); out.writeInt(tint.dryFoliage());
                    }
                }
            }
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return HexFormat.of().formatHex(hash.digest());
    }

    private void check(BooleanSupplier cancelled) { if (closed.get() || cancelled.getAsBoolean()) throw new CancellationException(); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static ExecutorService executor(int threads, int capacity, String name) {
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), task -> {
            Thread thread = new Thread(task, name); thread.setDaemon(true); return thread;
        });
    }
    @Override public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        current = null;
        coordinator.shutdown(); liveSurface.shutdown(); workers.shutdown();
    }
}

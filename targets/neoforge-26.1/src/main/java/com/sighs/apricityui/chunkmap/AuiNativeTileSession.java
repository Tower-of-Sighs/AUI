package com.sighs.apricityui.chunkmap;

import com.sighs.apricityui.ApricityUI;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Maintains reusable HiRes tiles while the world is active, independently of opening a map. */
public final class AuiNativeTileSession implements AutoCloseable, AuiChunkTileSource {
    public record Options(boolean prewarm, boolean persistentCache) {
        public static final Options DEFAULT = new Options(false, false);
        public static final Options WORLD_MAP = new Options(true, true);
    }
    private static final AuiTileGrid GRID = new AuiTileGrid(32, 0);
    private static final ConcurrentHashMap<BlockState, byte[]> STATE_KEYS = new ConcurrentHashMap<>();
    private static final long MEMORY_LIMIT = Math.min(512L << 20, Runtime.getRuntime().maxMemory() / 4);
    private static final class Entry {
        long sequence, nextAttempt;
        CompletableFuture<?> work;
        AtomicBoolean obsolete = new AtomicBoolean();
        boolean dirty = true;
        boolean preloadTried;
        int rank, misses;
        Pending queued;
    }
    private record Pending(AuiChunkTiles.Tile tile, Entry entry, long due, int rank) { }
    private final AuiActiveMapResources resources;
    private final ExecutorService conversion;
    private final AuiNativeChunkSource source;
    private final CompletableFuture<AuiNativeTileCache> cache;
    private final Map<AuiChunkTiles.Tile, Entry> entries = new HashMap<>();
    private final TreeSet<Pending> pending = new TreeSet<>(Comparator.comparingLong(Pending::due).thenComparingInt(Pending::rank));
    private record StoredTile(AuiNativeTileCache cache, String key, int[] heights, byte[] model) { }
    private final ConcurrentHashMap<AuiChunkTiles.Tile, StoredTile> ready = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AuiChunkTiles.Tile, Long> versions = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong revision = new AtomicLong();
    private final int parallelism;
    private final Options options;
    private final int minY, maxY;
    private int ceiling = Integer.MAX_VALUE, inFlight;
    private long residentBytes, lastSchedule, cacheHits, bakedTiles;
    private double focusX, focusZ;
    private record TileBounds(int minX, int maxX, int minZ, int maxZ) {
        boolean contains(AuiChunkTiles.Tile tile) {
            return tile.x() >= minX && tile.x() <= maxX && tile.z() >= minZ && tile.z() <= maxZ;
        }
    }
    private TileBounds scheduledBounds;

    public AuiNativeTileSession(ClientLevel level, AuiActiveMapResources resources, Path cacheRoot,
                                    String worldKey, int parallelism) {
        this(level, resources, cacheRoot, worldKey, parallelism, Options.DEFAULT);
    }

    public AuiNativeTileSession(ClientLevel level, AuiActiveMapResources resources, Path cacheRoot,
                                    String worldKey, int parallelism, Options options) {
        this.resources = resources; this.parallelism = parallelism;
        this.options = options;
        minY = level.getMinY(); maxY = level.getMaxY() + 1;
        conversion = Executors.newFixedThreadPool(parallelism, task -> {
            Thread thread = new Thread(task, "AUI native tile data");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
        source = new AuiNativeChunkSource(level, conversion);
        cache = resources.renderer().thenApplyAsync(renderer -> options.persistentCache() ? new AuiNativeTileCache(
                cacheRoot.resolve("native-models").resolve(hash(worldKey.getBytes(StandardCharsets.UTF_8)))
                        .resolve(resources.fingerprint() + "-native-2")) : null, conversion);
    }

    /** Main-thread scheduling only; snapshot conversion, disk cache and meshing stay asynchronous. */
    public void tick(double centerX, double centerZ, double radius, int requestedCeiling) {
        tick(centerX, centerZ, radius, requestedCeiling, true);
    }

    public void tick(double centerX, double centerZ, double radius, int requestedCeiling, boolean visible) {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Tile scheduling must use the client thread");
        if (closed.get() || resources.isClosed()) return;
        if (!visible && !options.prewarm()) return;
        focusX = centerX; focusZ = centerZ;
        if (ceiling != requestedCeiling) {
            ceiling = requestedCeiling;
            entries.values().forEach(entry -> entry.obsolete.set(true));
            entries.clear(); pending.clear(); scheduledBounds = null;
            ready.clear(); versions.clear(); residentBytes = 0; revision.incrementAndGet();
        }
        long now = System.nanoTime();
        int jobLimit = visible ? parallelism * 2 : 1;
        if (now - lastSchedule < 50_000_000L || inFlight >= jobLimit) return;
        lastSchedule = now;
        int minX = GRID.getCellX((int) Math.floor(centerX - radius)), maxX = GRID.getCellX((int) Math.ceil(centerX + radius));
        int minZ = GRID.getCellY((int) Math.floor(centerZ - radius)), maxZ = GRID.getCellY((int) Math.ceil(centerZ + radius));
        var bounds = new TileBounds(minX, maxX, minZ, maxZ);
        if (!bounds.equals(scheduledBounds)) {
            var next = new ArrayList<AuiChunkTiles.Tile>();
            for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) next.add(new AuiChunkTiles.Tile(x, z));
            next.sort(Comparator.comparingDouble(tile -> Math.hypot(GRID.getCellMinX(tile.x()) + 16 - centerX,
                    GRID.getCellMinY(tile.z()) + 16 - centerZ)));
            for (Pending previous : pending) previous.entry().queued = null;
            pending.clear(); scheduledBounds = bounds;
            int rank = 0;
            for (var tile : next) {
                Entry entry = entries.computeIfAbsent(tile, ignored -> new Entry());
                entry.rank = rank++;
                schedule(tile, entry);
            }
        }
        int started = 0;
        while (started < (visible ? 4 : 1) && inFlight < jobLimit && !pending.isEmpty()) {
            Pending task = pending.first();
            if (task.due() > now) break;
            pending.pollFirst(); task.entry().queued = null;
            if (task.entry().work != null || ready.containsKey(task.tile()) && !task.entry().dirty) continue;
            started++;
            start(task.tile(), task.entry());
        }
    }

    private void schedule(AuiChunkTiles.Tile tile, Entry entry) {
        if (entry.queued != null) { pending.remove(entry.queued); entry.queued = null; }
        if (closed.get() || resources.isClosed() || scheduledBounds == null || !scheduledBounds.contains(tile)
                || entry.work != null || !entry.dirty && ready.containsKey(tile)) return;
        entry.queued = new Pending(tile, entry, entry.nextAttempt, entry.rank);
        pending.add(entry.queued);
    }

    private void start(AuiChunkTiles.Tile tile, Entry entry) {
        AtomicBoolean obsolete = new AtomicBoolean(); entry.obsolete = obsolete;
        entry.dirty = false;
        long sequence = ++entry.sequence; inFlight++;
        int cut = ceiling;
        var cancelled = (java.util.function.BooleanSupplier) () -> closed.get() || resources.isClosed() || obsolete.get();
        if (options.persistentCache() && !entry.preloadTried) {
            entry.preloadTried = true;
            cache.thenApplyAsync(disk -> disk.readLatest(tile, cut).map(hit -> new StoredTile(
                    disk, hit.key(), hit.tile().heights(), encode(hit.tile().model()))).orElse(null), conversion)
                    .thenAccept(hit -> Minecraft.getInstance().execute(() -> {
                        if (hit != null && !cancelled.getAsBoolean() && entry.sequence == sequence
                                && entries.get(tile) == entry && !ready.containsKey(tile)) putReady(tile, hit);
                    }));
        }
        CompletableFuture<StoredTile> work = source.capture(tile, cut)
                .thenCombineAsync(cache, (capture, disk) -> {
                    if (capture == null || cancelled.getAsBoolean()) return null;
                    String key = fingerprint(capture, tile);
                    var hit = disk == null ? java.util.Optional.<AuiChunkTiles.NativeTile>empty() : disk.read(key, tile);
                    return new Prepared(capture, disk, key, hit.orElse(null));
                }, conversion).thenCompose(prepared -> {
                    if (prepared == null) return CompletableFuture.completedFuture(null);
                    if (prepared.hit != null) {
                        synchronized (this) { cacheHits++; }
                        var chosen = prepared.cache == null
                                ? new AuiNativeTileCache.Hit(prepared.key, prepared.hit)
                                : prepared.cache.commit(prepared.key, prepared.hit, cut);
                        return CompletableFuture.completedFuture(new StoredTile(prepared.cache, chosen.key(),
                                chosen.tile().heights(), encode(chosen.tile().model())));
                    }
                    return resources.renderer().thenCompose(renderer -> renderer.renderNativeTile(
                                    prepared.capture.snapshot(), tile, prepared.capture.knownColumns(), cancelled))
                            .thenApplyAsync(rendered -> {
                                if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
                                var chosen = prepared.cache == null
                                        ? new AuiNativeTileCache.Hit(prepared.key, rendered)
                                        : prepared.cache.commit(prepared.key, rendered, cut);
                                synchronized (this) { bakedTiles++; }
                                return new StoredTile(prepared.cache, chosen.key(), chosen.tile().heights(), encode(chosen.tile().model()));
                            }, conversion);
                });
        entry.work = work;
        work.whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
            inFlight--;
            if (closed.get() || entry.sequence != sequence || obsolete.get() || entries.get(tile) != entry) return;
            entry.work = null;
            boolean changedDuringWork = entry.dirty;
            boolean retry = failure != null || result == null || availableMask(result.heights()) != 15;
            if (failure != null) {
                if (!(failure instanceof java.util.concurrent.CancellationException))
                    ApricityUI.LOGGER.debug("Native model tile {},{} unavailable: {}", tile.x(), tile.z(), failure.toString());
            } else if (result != null) {
                putReady(tile, result);
            }
            entry.dirty = changedDuringWork || retry;
            if (retry && !changedDuringWork) {
                entry.misses = Math.min(6, entry.misses + 1);
                entry.nextAttempt = System.nanoTime() + Math.min(30L, 1L << (entry.misses - 1)) * 1_000_000_000L;
            } else {
                entry.misses = 0;
                entry.nextAttempt = 0;
            }
            schedule(tile, entry);
        }));
    }

    private void putReady(AuiChunkTiles.Tile tile, StoredTile result) {
            var existing = ready.get(tile);
            if (existing != null && (availableMask(existing.heights()) & availableMask(result.heights())) == availableMask(result.heights())
                    && availableMask(existing.heights()) != availableMask(result.heights())) return;
            if (existing != null && existing.key().equals(result.key())) return;
            var previous = ready.put(tile, result);
            if (previous != null) residentBytes -= previous.model().length + 4096L;
            residentBytes += result.model().length + 4096L;
            if (availableMask(result.heights()) != 0 && result.model().length > 0) versions.put(tile, revision.incrementAndGet());
            else { versions.remove(tile); revision.incrementAndGet(); }
            while (residentBytes > MEMORY_LIMIT) {
                var farthest = ready.entrySet().stream().filter(value -> value.getValue().model().length > 0)
                        .max(Comparator.comparingDouble(value -> Math.hypot(
                                GRID.getCellMinX(value.getKey().x()) + 16 - focusX,
                                GRID.getCellMinY(value.getKey().z()) + 16 - focusZ))).orElse(null);
                if (farthest == null) break;
                var stored = farthest.getValue();
                if (stored.cache() != null) {
                    ready.put(farthest.getKey(), new StoredTile(stored.cache(), stored.key(), stored.heights(), new byte[0]));
                    residentBytes -= stored.model().length;
                } else {
                    ready.remove(farthest.getKey()); versions.remove(farthest.getKey());
                    residentBytes -= stored.model().length + 4096L; revision.incrementAndGet();
                    Entry evicted = entries.get(farthest.getKey());
                    if (evicted != null) schedule(farthest.getKey(), evicted);
                }
            }
    }

    private record Prepared(AuiNativeChunkSource.Capture capture, AuiNativeTileCache cache, String key,
                            AuiChunkTiles.NativeTile hit) { }

    /** Called by the HTTP worker; released mesh bytes remain available from their validated disk cache. */
    public AuiChunkTiles.NativeTile tile(AuiChunkTiles.Tile tile) {
        var stored = ready.get(tile);
        if (stored == null) return null;
        return stored.model().length > 0 ? new AuiChunkTiles.NativeTile(tile, decode(stored.model()), stored.heights())
                : stored.cache() == null ? new AuiChunkTiles.NativeTile(tile, new byte[0], stored.heights())
                : stored.cache().read(stored.key(), tile).orElse(null);
    }

    public String contentKey(AuiChunkTiles.Tile tile) { var stored = ready.get(tile); return stored == null ? null : stored.key(); }
    public int availableMask(AuiChunkTiles.Tile tile) { var stored = ready.get(tile); return stored == null ? 0 : availableMask(stored.heights()); }

    public byte[] compressedModel(AuiChunkTiles.Tile tile, String expectedKey) {
        var stored = ready.get(tile);
        if (stored == null || !stored.key().equals(expectedKey)) return null;
        if (stored.model().length > 0) return stored.model();
        return stored.cache() == null ? null : stored.cache().read(stored.key(), tile).map(model -> encode(model.model())).orElse(null);
    }

    private static int availableMask(int[] heights) {
        int mask = 0;
        for (int q = 0; q < 4; q++) {
            boolean known = true;
            int x0 = (q & 1) * 16, z0 = (q >> 1) * 16;
            for (int z = z0; z < z0 + 16 && known; z++) for (int x = x0; x < x0 + 16; x++)
                if (heights[z * 32 + x] == 32767) { known = false; break; }
            if (known) mask |= 1 << q;
        }
        return mask;
    }

    private static byte[] encode(byte[] raw) {
        if (raw.length == 0) return raw;
        try (var output = new java.io.ByteArrayOutputStream(); var gzip = new java.util.zip.GZIPOutputStream(output)) {
            gzip.write(raw); gzip.finish(); return output.toByteArray();
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private static byte[] decode(byte[] gzip) {
        try (var input = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gzip))) { return input.readAllBytes(); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    public Map<AuiChunkTiles.Tile, Long> versions() { return Map.copyOf(versions); }
    public Options options() { return options; }
    @Override public boolean persistentCache() { return options.persistentCache(); }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public long revision() { return revision.get(); }
    public synchronized String stats() { return "ready=" + ready.size() + " jobs=" + inFlight + " cache=" + cacheHits + " baked=" + bakedTiles; }

    public int heightAt(double x, double z, int unknown) {
        int worldX = (int) Math.floor(x), worldZ = (int) Math.floor(z);
        var model = ready.get(new AuiChunkTiles.Tile(GRID.getCellX(worldX), GRID.getCellY(worldZ)));
        if (model == null) return unknown;
        int value = model.heights()[GRID.getLocalY(worldZ) * 32 + GRID.getLocalX(worldX)];
        return value == 32767 ? unknown : value;
    }

    public void invalidate(int chunkX, int chunkZ) {
        if (closed.get()) return;
        source.invalidate(chunkX, chunkZ);
        int minX = GRID.getCellX(chunkX * 16 - 16), maxX = GRID.getCellX(chunkX * 16 + 31);
        int minZ = GRID.getCellY(chunkZ * 16 - 16), maxZ = GRID.getCellY(chunkZ * 16 + 31);
        for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
            var tile = new AuiChunkTiles.Tile(x, z);
            Entry entry = entries.get(tile);
            // Finish the detached snapshot already in flight; coalesce subsequent changes into one refresh.
            if (entry != null) {
                entry.dirty = true; entry.nextAttempt = 0; entry.misses = 0;
                schedule(tile, entry);
            }
        }
    }

    private static String fingerprint(AuiNativeChunkSource.Capture capture, AuiChunkTiles.Tile tile) {
        var snapshot = capture.snapshot();
        MessageDigest digest = digest();
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            out.writeUTF("AUI-native-model-2"); out.writeInt(tile.x()); out.writeInt(tile.z());
            out.writeInt(snapshot.originX()); out.writeInt(snapshot.originZ());
            out.writeInt(snapshot.width()); out.writeInt(snapshot.depth());
            out.writeInt(snapshot.minY()); out.writeInt(snapshot.maxY());
            for (int z = 0; z < snapshot.depth(); z++) for (int x = 0; x < snapshot.width(); x++) {
                boolean known = capture.knownColumns()[z * snapshot.width() + x];
                out.writeBoolean(known);
                if (!known) continue;
                var column = snapshot.column(x, z); out.writeInt(column.size());
                for (var run : column) {
                    out.writeInt(run.fromY()); out.writeInt(run.toY());
                    byte[] state = STATE_KEYS.computeIfAbsent(run.state(), value -> value.toString().getBytes(StandardCharsets.UTF_8));
                    out.writeInt(state.length); out.write(state);
                }
                var biome = snapshot.biome(x, z);
                out.writeUTF(biome.namespace()); out.writeUTF(biome.path()); out.writeFloat(biome.temperature());
                out.writeInt(biome.water()); out.writeInt(biome.grass()); out.writeInt(biome.foliage()); out.writeInt(biome.dryFoliage());
            }
        } catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String hash(byte[] value) { return HexFormat.of().formatHex(digest().digest(value)); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        entries.values().forEach(entry -> { entry.obsolete.set(true); if (entry.work != null) entry.work.cancel(false); });
        entries.clear(); pending.clear(); ready.clear(); versions.clear(); source.close(); conversion.shutdown();
    }
}

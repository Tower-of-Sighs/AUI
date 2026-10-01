package com.sighs.apricityui.chunkmap;

import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3i;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.HiresModelManager;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.mask.Mask;
import de.bluecolored.bluemap.core.map.mask.BoxMask;
import de.bluecolored.bluemap.core.resources.pack.PackVersion;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.storage.GridStorage;
import de.bluecolored.bluemap.core.storage.compression.CompressedInputStream;
import de.bluecolored.bluemap.core.util.Grid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;

/** Runs BlueMap's official resource and PRBM tile pipeline on immutable chunks. */
public final class BlueMapChunkTiles implements AutoCloseable {
    public record Tile(int x, int z) { }
    public record Rendered(long centerX, int centerY, long centerZ, int verticalRelief,
                           byte[] texturesJson,
                           Map<Tile, byte[]> tiles, int scale, long span, int verticalScale,
                           long minX, long minZ, int width, int depth, byte[] surfaceJson) { }

    private final ResourcePack resourcePack;
    private final TextureGallery textureGallery;
    private final byte[] texturesJson;
    private final ExecutorService workers = Executors.newFixedThreadPool(
            Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 4));

    public BlueMapChunkTiles(List<Path> packRoots, Path cacheDirectory)
            throws IOException, InterruptedException {
        var format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        resourcePack = new ResourcePack(new PackVersion(format.major(), format.minor()));
        Files.createDirectories(cacheDirectory);
        Path extensions = cacheDirectory.resolve("resourceExtensions-5.24.zip");
        if (!Files.isRegularFile(extensions)) {
            try (InputStream source = BlueMapChunkTiles.class.getResourceAsStream(
                    "/de/bluecolored/bluemap/resourceExtensions.zip")) {
                if (source == null) throw new IOException("BlueMap resource extensions are missing");
                Files.copy(source, extensions, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        List<Path> resources = new ArrayList<>(packRoots);
        resources.add(resources.size() - 1, extensions);
        resourcePack.loadResources(resources);
        textureGallery = new TextureGallery();
        textureGallery.put(resourcePack.getTextures());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        textureGallery.writeTexturesFile(out);
        texturesJson = out.toByteArray();
    }

    public Rendered render(ChunkMapSnapshot snapshot, BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) throw new CancellationException();
        Grid grid = new Grid(32, 2);
        TileStorage storage = new TileStorage();
        Mask renderArea = new BoxMask(
                new Vector3i(snapshot.originX(), snapshot.minY(), snapshot.originZ()),
                new Vector3i(snapshot.originX() + snapshot.width() - 1,
                        snapshot.maxY() - 1, snapshot.originZ() + snapshot.depth() - 1));
        RenderSettings settings = new RenderSettings() {
            @Override public int getRemoveCavesBelowY() { return snapshot.minY(); }
            @Override public int getCaveDetectionOceanFloor() { return 8; }
            @Override public boolean isCaveDetectionUsesBlockLight() { return false; }
            @Override public float getAmbientLight() { return 0.2F; }
            @Override public Mask getRenderMask() { return renderArea; }
            @Override public boolean isRenderEdges() { return false; }
            @Override public boolean isSaveHiresLayer() { return true; }
            @Override public boolean isRenderTopOnly() { return false; }
            @Override public boolean isIgnoreMissingLightData() { return true; }
        };
        HiresModelManager renderer = new HiresModelManager(new BlueMapChunkWorld(snapshot),
                storage, resourcePack, textureGallery, settings, grid);
        int minX = grid.getCellX(snapshot.originX());
        int minZ = grid.getCellY(snapshot.originZ());
        int maxX = grid.getCellX(snapshot.originX() + snapshot.width() - 1);
        int maxZ = grid.getCellY(snapshot.originZ() + snapshot.depth() - 1);
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int tileX = x;
                int tileZ = z;
                tasks.add(CompletableFuture.runAsync(() -> {
                    if (cancelled.getAsBoolean()) throw new CancellationException();
                    renderer.render(new Vector2i(tileX, tileZ), (bx, bz, color, height, light) -> {}, true);
                }, workers));
            }
        }
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        if (cancelled.getAsBoolean()) throw new CancellationException();
        int[] surface = new int[snapshot.width() * snapshot.depth()];
        for (int z = 0; z < snapshot.depth(); z++) {
            for (int x = 0; x < snapshot.width(); x++) {
                var column = snapshot.column(x, z);
                surface[z * snapshot.width() + x] = column.isEmpty()
                        ? snapshot.minY() : column.getLast().toY() - 1;
            }
        }
        int low = Arrays.stream(surface).min().orElseThrow();
        int high = Arrays.stream(surface).max().orElseThrow();
        return new Rendered((snapshot.originX() + snapshot.width() / 2) * (long) snapshot.step(),
                (low + high) / 2 * snapshot.verticalStep(),
                (snapshot.originZ() + snapshot.depth() / 2) * (long) snapshot.step(),
                (high - low) * snapshot.verticalStep(), texturesJson, Map.copyOf(storage.tiles), snapshot.step(),
                Math.max(snapshot.width(), snapshot.depth()) * (long) snapshot.step(), snapshot.verticalStep(),
                snapshot.originX() * (long) snapshot.step(), snapshot.originZ() * (long) snapshot.step(),
                snapshot.width(), snapshot.depth(), new com.google.gson.Gson().toJson(surface)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override public void close() { workers.shutdownNow(); }

    private static final class TileStorage implements GridStorage {
        private final Map<Tile, byte[]> tiles = new ConcurrentHashMap<>();

        @Override
        public OutputStream write(int x, int z) {
            return new ByteArrayOutputStream() {
                @Override public void close() throws IOException {
                    super.close();
                    tiles.put(new Tile(x, z), toByteArray());
                }
            };
        }

        @Override public CompressedInputStream read(int x, int z) { return null; }
        @Override public void delete(int x, int z) { tiles.remove(new Tile(x, z)); }
        @Override public boolean exists(int x, int z) { return tiles.containsKey(new Tile(x, z)); }
        @Override public Cell cell(int x, int z) { return new GridStorage.GridStorageCell(this, x, z); }
        @Override public Stream<Cell> stream() {
            return tiles.keySet().stream().map(tile -> cell(tile.x(), tile.z()));
        }
        @Override public boolean isClosed() { return false; }
    }
}

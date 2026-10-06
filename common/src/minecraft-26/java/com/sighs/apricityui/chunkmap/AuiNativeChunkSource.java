package com.sighs.apricityui.chunkmap;

import com.sighs.apricityui.ApricityUI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/** Copies loaded chunks on their owner, or parses existing singleplayer storage without loading chunks. */
final class AuiNativeChunkSource implements AutoCloseable {
    static final String CACHE_SUFFIX = "-native-2";
    static final String MODEL_FORMAT = "AUI-native-model-2";

    int minY() { return level.getMinY(); }
    int maxY() { return level.getMaxY() + 1; }

    record Capture(ChunkMapSnapshot snapshot, boolean[] knownColumns) { }
    private record ChunkCopy(LevelChunkSection[] sections, int firstSectionY) {
        LevelChunkSection section(int y) {
            int index = (y >> 4) - firstSectionY;
            return index >= 0 && index < sections.length ? sections[index] : null;
        }
    }
    private record StoredContext(MinecraftServer server, ChunkMap storage, Path regions,
                                 PalettedContainerFactory factory, LevelHeightAccessor height, CompoundTag datafix) { }
    private static final AuiTileGrid GRID = new AuiTileGrid(32, 0);
    private static final int HALO = 16;
    private static final int SNAPSHOT_SIZE = 32 + HALO * 2;
    private static final long MISSING_RETRY_NANOS = 5_000_000_000L;
    private final ClientLevel level;
    private final Executor conversion;
    private final Holder<Biome> fallbackBiome;
    private final CompletableFuture<StoredContext> stored;
    private final ConcurrentHashMap<Long, CompletableFuture<ChunkCopy>> chunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> missingUntil = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    AuiNativeChunkSource(ClientLevel level, Executor conversion) {
        this.level = level; this.conversion = conversion;
        fallbackBiome = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        stored = server == null ? CompletableFuture.completedFuture(null) : server.submit(() -> {
            var world = server.getLevel(level.dimension());
            if (world == null || closed.get()) return null;
            var storage = world.getChunkSource().chunkMap;
            Path regions = DimensionType.getStorageFolder(world.dimension(), server.getWorldPath(LevelResource.ROOT)).resolve("region");
            return new StoredContext(server, storage, regions, world.palettedContainerFactory(),
                    LevelHeightAccessor.create(world.getMinY(), world.getHeight()),
                    ChunkMap.getChunkDataFixContextTag(world.dimension(), world.getChunkSource().getGenerator().getTypeNameForDataFixer()));
        });
    }

    CompletableFuture<Capture> capture(AuiChunkTiles.Tile tile, int ceiling) {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Capture must start on the client thread");
        int originX = GRID.getCellMinX(tile.x()) - HALO, originZ = GRID.getCellMinY(tile.z()) - HALO;
        int minChunkX = Math.floorDiv(originX, 16), minChunkZ = Math.floorDiv(originZ, 16);
        int maxChunkX = Math.floorDiv(originX + SNAPSHOT_SIZE - 1, 16), maxChunkZ = Math.floorDiv(originZ + SNAPSHOT_SIZE - 1, 16);
        int width = maxChunkX - minChunkX + 1, depth = maxChunkZ - minChunkZ + 1;
        @SuppressWarnings("unchecked") CompletableFuture<ChunkCopy>[] copies = new CompletableFuture[width * depth];
        for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++)
            copies[z * width + x] = chunk(minChunkX + x, minChunkZ + z);
        int minY = level.getMinY(), maxY = Math.min(level.getMaxY() + 1, ceiling);
        return CompletableFuture.allOf(copies).thenApplyAsync(ignored -> {
            if (closed.get()) throw new java.util.concurrent.CancellationException();
            ChunkCopy[] ready = Arrays.stream(copies).map(CompletableFuture::join).toArray(ChunkCopy[]::new);
            if (maxY <= minY) return null;
            var builder = ChunkMapSnapshot.builder(originX, originZ, SNAPSHOT_SIZE, SNAPSHOT_SIZE, 1, minY, maxY);
            boolean[] known = new boolean[SNAPSHOT_SIZE * SNAPSHOT_SIZE];
            boolean corePresent = false;
            for (int z = 0; z < SNAPSHOT_SIZE; z++) for (int x = 0; x < SNAPSHOT_SIZE; x++) {
                if (closed.get()) throw new java.util.concurrent.CancellationException();
                int worldX = originX + x, worldZ = originZ + z;
                ChunkCopy copy = ready[(Math.floorDiv(worldZ, 16) - minChunkZ) * width + Math.floorDiv(worldX, 16) - minChunkX];
                if (copy == null) continue;
                known[z * SNAPSHOT_SIZE + x] = true;
                if (x >= HALO && x < HALO + 32 && z >= HALO && z < HALO + 32) corePresent = true;
                int columnMax = maxY;
                if (ceiling != Integer.MAX_VALUE) {
                    while (columnMax > minY) {
                        var section = copy.section(columnMax - 1);
                        if (section == null || !section.getBlockState(worldX & 15, (columnMax - 1) & 15, worldZ & 15).canOcclude()) break;
                        columnMax--;
                    }
                }
                BlockState previous = Blocks.AIR.defaultBlockState(); int start = minY, top = minY;
                for (int y = minY; y <= columnMax; y++) {
                    var section = copy.section(y);
                    BlockState state = y == columnMax || section == null ? Blocks.AIR.defaultBlockState()
                            : section.getBlockState(worldX & 15, y & 15, worldZ & 15);
                    if (state == previous) continue;
                    if (!previous.isAir()) { builder.addRun(x, z, start, y, previous); top = y - 1; }
                    previous = state; start = y;
                }
                var biomeSection = copy.section(top);
                Holder<Biome> biome = biomeSection == null ? fallbackBiome : biomeSection.getNoiseBiome(
                        (worldX & 15) >> 2, (top & 15) >> 2, (worldZ & 15) >> 2);
                builder.setBiome(x, z, biome, worldX, worldZ);
            }
            return corePresent ? new Capture(builder.build(), known) : null;
        }, conversion);
    }

    private CompletableFuture<ChunkCopy> chunk(int x, int z) {
        long key = ChunkPos.pack(x, z);
        Long retry = missingUntil.get(key);
        if (retry != null) {
            if (System.nanoTime() < retry && level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) == null)
                return CompletableFuture.completedFuture(null);
            missingUntil.remove(key, retry);
        }
        if (chunks.size() > 512) {
            for (var cached : chunks.entrySet()) {
                if (cached.getValue().isDone() && cached.getKey() != key) chunks.remove(cached.getKey(), cached.getValue());
                if (chunks.size() <= 512) break;
            }
        }
        var result = chunks.computeIfAbsent(key, ignored -> {
            LevelChunk live = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (live != null) return CompletableFuture.completedFuture(copy(live));
            return stored.thenCompose(context -> {
                if (context == null || closed.get() || context.server.isStopped()) return CompletableFuture.completedFuture(null);
                return context.server.submit(() -> {
                    if (closed.get() || context.server.isStopped()) return (ChunkCopy) null;
                    var world = context.server.getLevel(level.dimension());
                    if (world == null) return (ChunkCopy) null;
                    LevelChunk current = world.getChunkSource().getChunkNow(x, z);
                    return current == null ? null : copy(current);
                }).thenComposeAsync(current -> {
                    if (current != null) return CompletableFuture.completedFuture(current);
                    if (closed.get() || context.server.isStopped()) return CompletableFuture.completedFuture(null);
                    Path region = context.regions.resolve("r." + Math.floorDiv(x, 32) + "." + Math.floorDiv(z, 32) + ".mca");
                    if (!Files.isRegularFile(region)) return CompletableFuture.<ChunkCopy>completedFuture(null);
                    return context.storage.read(new ChunkPos(x, z)).thenApplyAsync(tag -> decode(context, tag), conversion);
                }, conversion);
            }).exceptionally(failure -> {
                if (!closed.get()) ApricityUI.LOGGER.debug("Existing map chunk {},{} unavailable: {}", x, z, failure.toString());
                return null;
            });
        });
        result.whenComplete((copy, failure) -> {
            if (copy != null) return;
            chunks.computeIfPresent(key, (ignored, current) -> {
                if (current != result) return current;
                if (!closed.get()) {
                    if (missingUntil.size() >= 65536) {
                        var keys = missingUntil.keys();
                        if (keys.hasMoreElements()) missingUntil.remove(keys.nextElement());
                    }
                    missingUntil.put(key, System.nanoTime() + MISSING_RETRY_NANOS);
                }
                return null;
            });
        });
        return result;
    }

    private ChunkCopy decode(StoredContext context, Optional<CompoundTag> tag) {
        if (closed.get() || tag.isEmpty()) return null;
        CompoundTag upgraded = context.storage.upgradeChunkTag(tag.get(), -1, context.datafix.copy(),
                SharedConstants.getCurrentVersion().dataVersion().version());
        var parsed = SerializableChunkData.parse(context.height, context.factory, upgraded);
        if (parsed == null || !parsed.chunkStatus().isOrAfter(ChunkStatus.FULL)) return null;
        LevelChunkSection[] sections = new LevelChunkSection[context.height.getSectionsCount()];
        for (var section : parsed.sectionData()) {
            int index = section.y() - context.height.getMinSectionY();
            if (index >= 0 && index < sections.length) sections[index] = section.chunkSection();
        }
        return new ChunkCopy(sections, context.height.getMinSectionY());
    }

    private static ChunkCopy copy(LevelChunk chunk) {
        LevelChunkSection[] source = chunk.getSections(), sections = new LevelChunkSection[source.length];
        for (int i = 0; i < source.length; i++) sections[i] = source[i].copy();
        return new ChunkCopy(sections, chunk.getMinSectionY());
    }

    void invalidate(int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        chunks.remove(key); missingUntil.remove(key);
    }

    @Override public void close() {
        closed.set(true); chunks.values().forEach(future -> future.cancel(false)); chunks.clear(); missingUntil.clear(); stored.cancel(false);
    }
}
